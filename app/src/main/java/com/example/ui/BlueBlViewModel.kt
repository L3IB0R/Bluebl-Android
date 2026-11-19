package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.Identity
import com.example.data.AppConfig
import com.example.data.ChatLine
import com.example.data.Command
import com.example.data.Commands
import com.example.data.ConfigRepository
import com.example.data.Density
import com.example.data.FontChoice
import com.example.data.FontScaleOption
import com.example.data.GENERAL_CHANNEL
import com.example.data.LineKind
import com.example.data.MeshEvent
import com.example.data.Panel
import com.example.data.Peer
import com.example.data.RadioCheck
import com.example.data.WallpaperOption
import com.example.data.Wallpapers
import com.example.net.BackgroundPowerHelper
import com.example.net.BleMeshEngine
import com.example.net.MeshDiagnostics
import com.example.net.MeshNodeService
import com.example.net.MeshServiceBus
import com.example.ui.theme.BlueBlThemeDef
import com.example.ui.theme.Themes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SettingsSection(val title: String) {
    Root("Ajustes"),
    Themes("Apariencia y temas"),
    Wallpapers("Fondo del chat"),
    Accessibility("Accesibilidad y lectura")
}

sealed interface ActiveOverlay {
    data object Help : ActiveOverlay
    data class Settings(val section: SettingsSection = SettingsSection.Root) : ActiveOverlay
    data object RadioDeps : ActiveOverlay
    data object NickEditor : ActiveOverlay
    data object DisconnectConfirm : ActiveOverlay
}

data class BlueBlUiState(
    val peerIdHex: String = "",
    val nickname: String = "",
    val themeDef: BlueBlThemeDef = Themes.default(),
    val density: Density = Density.Comodo,
    val wallpaper: WallpaperOption = Wallpapers.default(),
    val wallpaperDim: Float = 0.08f,
    val customWallpaperBitmap: Bitmap? = null,
    val fontScale: FontScaleOption = FontScaleOption.Medium,
    val fontChoice: FontChoice = FontChoice.Sans,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val activePanel: Panel = Panel.Pares,
    val lines: List<ChatLine> = emptyList(),
    val peers: List<Peer> = emptyList(),
    val inputText: String = "",
    val radioStatus: String = "Buscando dispositivos…",
    val hasBlePermissions: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = true,
    val isBackgroundRestricted: Boolean = false,
    val radioReady: Boolean = false,
    val meshEnabled: Boolean = true,
    val radioChecks: List<RadioCheck> = emptyList(),
    val meshDiagnostics: MeshDiagnostics = MeshDiagnostics(),
    val overlay: ActiveOverlay? = null
)

class BlueBlViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ConfigRepository(application)
    private var config: AppConfig = repo.loadConfig()
    private val identity: Identity = repo.loadOrGenerateIdentity()

    private val meshEngine = BleMeshEngine(
        context = application.applicationContext,
        identity = identity,
        initialNickname = config.nick
    )

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<BlueBlUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            meshEngine.events.collect { event ->
                handleMeshEvent(event)
            }
        }
        viewModelScope.launch {
            MeshServiceBus.stopRequests.collect {
                handleNotificationStopRequest()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val savedBitmap = repo.loadCustomWallpaperBitmap()
            if (savedBitmap != null) {
                _uiState.update { it.copy(customWallpaperBitmap = savedBitmap) }
            }
        }
        refreshRadioDiagnostics()
    }

    private fun createInitialState(): BlueBlUiState {
        val theme = Themes.find(config.theme) ?: Themes.default()
        val density = Density.parse(config.density) ?: Density.Comodo
        val wallpaper = Wallpapers.find(config.wallpaperId)
        val fontScale = FontScaleOption.parse(config.fontScale)
        val fontChoice = FontChoice.parse(config.fontChoice)
        val appCtx = getApplication<Application>()
        val checks = BleMeshEngine.checkRadioDependencies(appCtx)
        val perms = BleMeshEngine.hasBlePermissions(appCtx)
        val notifOk = BackgroundPowerHelper.hasNotificationPermission(appCtx)
        val batteryOk = BackgroundPowerHelper.isIgnoringBatteryOptimizations(appCtx)
        val bgRestricted = BackgroundPowerHelper.isBackgroundRestricted(appCtx)
        val ready = isBluetoothRadioReady(checks)

        val welcome = ChatLine(
            time = nowHm(),
            kind = LineKind.System,
            sender = "",
            text = "Chat sin internet listo"
        )
        val initialStatus = when {
            !perms -> "Activa Bluetooth cercano"
            !ready -> "Bluetooth desactivado"
            else -> "Buscando dispositivos…"
        }

        return BlueBlUiState(
            peerIdHex = identity.peerIdHex(),
            nickname = config.nick,
            themeDef = theme,
            density = density,
            wallpaper = wallpaper,
            wallpaperDim = config.wallpaperDim,
            fontScale = fontScale,
            fontChoice = fontChoice,
            highContrast = config.highContrast,
            reduceMotion = config.reduceMotion,
            lines = listOf(welcome),
            radioStatus = initialStatus,
            hasBlePermissions = perms,
            hasNotificationPermission = notifOk,
            isIgnoringBatteryOptimizations = batteryOk,
            isBackgroundRestricted = bgRestricted,
            radioReady = ready,
            radioChecks = checks
        )
    }

    private fun isBluetoothRadioReady(checks: List<RadioCheck>): Boolean {
        return checks
            .filterNot { it.name == "Notificaciones" || it.name == "Segundo plano y batería" }
            .all { it.ok }
    }

    fun onPermissionsResult() {
        refreshRadioDiagnostics()
    }

    fun refreshRadioDiagnostics() {
        val appCtx = getApplication<Application>()
        val checks = BleMeshEngine.checkRadioDependencies(appCtx)
        val perms = BleMeshEngine.hasBlePermissions(appCtx)
        val notifOk = BackgroundPowerHelper.hasNotificationPermission(appCtx)
        val batteryOk = BackgroundPowerHelper.isIgnoringBatteryOptimizations(appCtx)
        val bgRestricted = BackgroundPowerHelper.isBackgroundRestricted(appCtx)
        val ready = isBluetoothRadioReady(checks)
        val enabled = _uiState.value.meshEnabled
        val peerCount = _uiState.value.peers.size

        if (enabled && perms && ready) {
            MeshNodeService.start(appCtx, peerCount = peerCount)
            meshEngine.start()
        } else if (!enabled) {
            meshEngine.stop()
            MeshNodeService.stop(appCtx)
        }

        val diag = meshEngine.diagnostics()
        _uiState.update { st ->
            val updatedStatus = when {
                !st.meshEnabled -> "Malla Bluetooth apagada"
                !perms -> "Activa Bluetooth cercano"
                !ready -> "Bluetooth desactivado"
                else -> "Buscando dispositivos…"
            }
            st.copy(
                hasBlePermissions = perms,
                hasNotificationPermission = notifOk,
                isIgnoringBatteryOptimizations = batteryOk,
                isBackgroundRestricted = bgRestricted,
                radioReady = ready,
                radioChecks = checks,
                meshDiagnostics = diag,
                radioStatus = updatedStatus
            )
        }
    }

    fun setMeshEnabled(enabled: Boolean) {
        if (enabled) {
            _uiState.update { it.copy(meshEnabled = true) }
            refreshRadioDiagnostics()
            if (_uiState.value.hasBlePermissions && _uiState.value.radioReady) {
                pushSystem("Malla Bluetooth encendida")
            }
        } else {
            meshEngine.stop()
            MeshNodeService.stop(getApplication())
            _uiState.update {
                it.copy(
                    meshEnabled = false,
                    peers = emptyList(),
                    meshDiagnostics = meshEngine.diagnostics(),
                    radioStatus = "Malla Bluetooth apagada"
                )
            }
            pushSystem("Malla Bluetooth apagada")
        }
    }

    private fun handleNotificationStopRequest() {
        if (!_uiState.value.meshEnabled) return
        meshEngine.stop()
        _uiState.update {
            it.copy(
                meshEnabled = false,
                peers = emptyList(),
                meshDiagnostics = meshEngine.diagnostics(),
                radioStatus = "Malla Bluetooth apagada"
            )
        }
        pushSystem("Malla Bluetooth apagada desde la notificación")
    }

    fun restartRadioDiscovery() {
        if (!_uiState.value.meshEnabled) {
            setMeshEnabled(true)
            return
        }
        refreshRadioDiagnostics()
        if (BleMeshEngine.hasBlePermissions(getApplication())) {
            meshEngine.restartDiscovery()
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun switchPanel(panel: Panel) {
        _uiState.update { it.copy(activePanel = panel) }
    }

    fun dismissOverlay() {
        _uiState.update { it.copy(overlay = null) }
    }

    fun openSettings(section: SettingsSection = SettingsSection.Root) {
        _uiState.update { it.copy(overlay = ActiveOverlay.Settings(section)) }
    }

    fun navigateSettingsBack() {
        val current = _uiState.value.overlay as? ActiveOverlay.Settings ?: return
        if (current.section == SettingsSection.Root) {
            dismissOverlay()
        } else {
            _uiState.update { it.copy(overlay = ActiveOverlay.Settings(SettingsSection.Root)) }
        }
    }

    fun openHelp() {
        _uiState.update { it.copy(overlay = ActiveOverlay.Help) }
    }

    fun openThemes() {
        openSettings(SettingsSection.Themes)
    }

    fun openRadioDeps() {
        refreshRadioDiagnostics()
        _uiState.update { it.copy(overlay = ActiveOverlay.RadioDeps) }
    }

    fun openNickEditor() {
        _uiState.update { it.copy(overlay = ActiveOverlay.NickEditor) }
    }

    fun openDisconnectConfirm() {
        _uiState.update { it.copy(overlay = ActiveOverlay.DisconnectConfirm) }
    }

    fun disconnectAndShutDown() {
        meshEngine.shutdown()
        MeshNodeService.stop(getApplication())
        _uiState.update {
            it.copy(
                meshEnabled = false,
                peers = emptyList(),
                overlay = null,
                meshDiagnostics = meshEngine.diagnostics(),
                radioStatus = "Desconectado"
            )
        }
    }

    fun submitInput() {
        val raw = _uiState.value.inputText
        _uiState.update { it.copy(inputText = "") }
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return

        if (trimmed.startsWith('/')) {
            val cmd = Commands.parse(trimmed)
            if (cmd != null) {
                runCommand(cmd)
            } else {
                pushError("Comando incompleto — escribe /help para ver opciones")
            }
        } else {
            _uiState.update { it.copy(activePanel = Panel.Chat) }
            pushLine(
                ChatLine(
                    time = nowHm(),
                    kind = LineKind.Own,
                    sender = _uiState.value.nickname,
                    text = trimmed,
                    senderId = identity.peerIdHex()
                )
            )
            val sent = meshEngine.sendBroadcastText(trimmed)
            if (!sent && _uiState.value.radioReady) {
                pushError("No se pudo enviar el mensaje por Bluetooth en este momento")
            }
        }
    }

    fun runCommand(cmd: Command) {
        when (cmd) {
            Command.Help -> openHelp()
            Command.Quit -> {
                pushSystem("Usa el botón atrás para salir de BlueBl.")
            }
            is Command.Nick -> {
                if (cmd.name.isEmpty()) {
                    pushSystem("Tu nombre visible es ${_uiState.value.nickname}")
                } else {
                    updateNickname(cmd.name)
                }
            }
            is Command.Me -> {
                if (cmd.action.isEmpty()) {
                    pushError("Escribe una acción después de /me")
                } else {
                    pushLine(
                        ChatLine(
                            time = nowHm(),
                            kind = LineKind.Action,
                            sender = _uiState.value.nickname,
                            text = cmd.action,
                            senderId = identity.peerIdHex()
                        )
                    )
                    meshEngine.sendBroadcastText("* ${_uiState.value.nickname} ${cmd.action}")
                }
            }
            Command.Who, Command.Peers -> {
                val peers = _uiState.value.peers
                if (peers.isEmpty()) {
                    pushSystem("Todavía no hay dispositivos cerca")
                } else {
                    val summary = peers.joinToString(", ") { it.nickname }
                    pushSystem("${peers.size} dispositivos cerca: $summary")
                }
            }
            is Command.Theme -> {
                if (cmd.id == null) {
                    openThemes()
                } else {
                    val found = Themes.find(cmd.id)
                    if (found != null) {
                        selectTheme(found)
                    } else {
                        pushError("Tema no encontrado: ${cmd.id}")
                    }
                }
            }
            is Command.DensityCmd -> {
                val d = Density.parse(cmd.name)
                if (d != null) {
                    selectDensity(d)
                } else {
                    pushError("Opciones de espaciado: compacto, cómodo o amplio")
                }
            }
            Command.Clear -> {
                _uiState.update { it.copy(lines = emptyList()) }
            }
            Command.Deps -> {
                refreshRadioDiagnostics()
                openRadioDeps()
            }
            is Command.Unknown -> {
                pushError("Comando desconocido: /${cmd.name}")
            }
        }
    }

    fun updateNickname(newNick: String) {
        val clean = newNick.trim()
        if (clean.isEmpty()) return
        val old = _uiState.value.nickname
        config = config.copy(nick = clean)
        repo.saveConfig(config)
        meshEngine.setNickname(clean)
        _uiState.update { it.copy(nickname = clean) }
        if (old != clean) {
            pushSystem("Ahora te muestras como $clean")
        }
    }

    fun selectTheme(theme: BlueBlThemeDef) {
        config = config.copy(theme = theme.id)
        repo.saveConfig(config)
        _uiState.update { it.copy(themeDef = theme) }
    }

    fun selectDensity(density: Density) {
        config = config.copy(density = density.id)
        repo.saveConfig(config)
        _uiState.update { it.copy(density = density) }
    }

    fun selectWallpaper(option: WallpaperOption) {
        config = config.copy(wallpaperId = option.id)
        repo.saveConfig(config)
        _uiState.update { it.copy(wallpaper = option) }
    }

    fun setWallpaperDim(dim: Float) {
        val clamped = dim.coerceIn(0f, 0.75f)
        config = config.copy(wallpaperDim = clamped)
        repo.saveConfig(config)
        _uiState.update { it.copy(wallpaperDim = clamped) }
    }

    fun importGalleryWallpaper(uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                repo.saveCustomWallpaperFromUri(getApplication(), uri)
            }
            if (bmp != null) {
                val galleryOption = Wallpapers.find("foto-galeria")
                config = config.copy(wallpaperId = galleryOption.id)
                repo.saveConfig(config)
                _uiState.update {
                    it.copy(
                        customWallpaperBitmap = bmp,
                        wallpaper = galleryOption
                    )
                }
                Toast.makeText(getApplication(), "Foto aplicada como fondo del chat", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(getApplication(), "No se pudo cargar esa imagen", Toast.LENGTH_SHORT).show()
                pushError("No se pudo cargar la imagen seleccionada")
            }
        }
    }

    fun clearGalleryWallpaper() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteCustomWallpaper()
        }
        val fallback = Wallpapers.default()
        config = config.copy(wallpaperId = fallback.id)
        repo.saveConfig(config)
        _uiState.update {
            it.copy(
                customWallpaperBitmap = null,
                wallpaper = fallback
            )
        }
    }

    fun setFontScale(scale: FontScaleOption) {
        config = config.copy(fontScale = scale.id)
        repo.saveConfig(config)
        _uiState.update { it.copy(fontScale = scale) }
    }

    fun setFontChoice(choice: FontChoice) {
        config = config.copy(fontChoice = choice.id)
        repo.saveConfig(config)
        _uiState.update { it.copy(fontChoice = choice) }
    }

    fun setHighContrast(enabled: Boolean) {
        config = config.copy(highContrast = enabled)
        repo.saveConfig(config)
        _uiState.update { it.copy(highContrast = enabled) }
    }

    fun setReduceMotion(enabled: Boolean) {
        config = config.copy(reduceMotion = enabled)
        repo.saveConfig(config)
        _uiState.update { it.copy(reduceMotion = enabled) }
    }

    private fun handleMeshEvent(event: MeshEvent) {
        when (event) {
            is MeshEvent.PeerJoined -> {
                val existingIdx = _uiState.value.peers.indexOfFirst { it.id == event.id }
                if (existingIdx < 0) {
                    val newPeer = Peer(
                        id = event.id,
                        nickname = event.nickname,
                        noisePublic = event.noisePublic,
                        signingPublic = event.signingPublic
                    )
                    _uiState.update {
                        it.copy(
                            peers = it.peers + newPeer,
                            meshDiagnostics = meshEngine.diagnostics()
                        )
                    }
                    MeshNodeService.updateStatus(
                        getApplication(),
                        peerCount = _uiState.value.peers.size
                    )
                } else {
                    _uiState.update { st ->
                        val updated = st.peers.toMutableList()
                        updated[existingIdx] = updated[existingIdx].copy(
                            nickname = event.nickname,
                            noisePublic = event.noisePublic,
                            signingPublic = event.signingPublic,
                            lastSeenMs = System.currentTimeMillis()
                        )
                        st.copy(
                            peers = updated,
                            meshDiagnostics = meshEngine.diagnostics()
                        )
                    }
                }
            }
            is MeshEvent.PeerLeft -> {
                val leaving = _uiState.value.peers.firstOrNull { it.id == event.id }
                if (leaving != null) {
                    _uiState.update { st ->
                        st.copy(
                            peers = st.peers.filterNot { it.id == event.id },
                            meshDiagnostics = meshEngine.diagnostics()
                        )
                    }
                    MeshNodeService.updateStatus(
                        getApplication(),
                        peerCount = _uiState.value.peers.size
                    )
                }
            }
            is MeshEvent.Message -> {
                if (event.from == identity.peerIdHex()) return
                if (event.channel != null && event.channel != GENERAL_CHANNEL) return
                val senderName = _uiState.value.peers.firstOrNull { it.id == event.from }?.nickname
                    ?: event.nick?.takeIf { it.isNotEmpty() }
                    ?: event.from.take(8)
                pushLine(
                    ChatLine(
                        time = nowHm(),
                        kind = LineKind.Peer,
                        sender = senderName,
                        text = event.text,
                        senderId = event.from
                    )
                )
            }
            is MeshEvent.Status -> {
                val friendly = when {
                    !_uiState.value.meshEnabled -> "Malla Bluetooth apagada"
                    event.text.contains("activa", ignoreCase = true) -> "Buscando dispositivos…"
                    event.text.contains("apagado", ignoreCase = true) -> "Bluetooth apagado"
                    event.text.contains("permisos", ignoreCase = true) -> "Activa Bluetooth cercano"
                    event.text.contains("sin hardware", ignoreCase = true) -> "Bluetooth no disponible"
                    else -> event.text.replaceFirstChar { it.uppercase() }
                }
                _uiState.update { st ->
                    st.copy(
                        radioStatus = friendly,
                        meshDiagnostics = meshEngine.diagnostics()
                    )
                }
            }
        }
    }

    private fun pushSystem(text: String) {
        pushLine(ChatLine(time = nowHm(), kind = LineKind.System, sender = "", text = text))
    }

    private fun pushError(text: String) {
        pushLine(ChatLine(time = nowHm(), kind = LineKind.Error, sender = "", text = text))
    }

    private fun pushLine(line: ChatLine) {
        _uiState.update { st ->
            val next = (st.lines + line).let {
                if (it.size > 2000) it.takeLast(2000) else it
            }
            st.copy(lines = next)
        }
    }

    private fun nowHm(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        MeshNodeService.stop(getApplication())
        meshEngine.shutdown()
    }
}
