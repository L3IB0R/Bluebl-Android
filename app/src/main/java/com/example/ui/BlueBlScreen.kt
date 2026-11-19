package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ChatLine
import com.example.data.Density
import com.example.data.LineKind
import com.example.data.Panel
import com.example.ui.theme.BlueBlTheme
import com.example.ui.theme.LocalBlueBlPalette
import com.example.ui.theme.LocalHighContrast
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.Themes
import kotlinx.coroutines.launch

private val SLASH_SUGGESTIONS = listOf(
    "/nick ", "/who", "/theme", "/clear", "/help"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlueBlAppScreen(
    viewModel: BlueBlViewModel,
    onRequestBlePermissions: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryAndBackground: () -> Unit,
    onDisconnectAndExit: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BlueBlTheme(
        themeDef = state.themeDef,
        fontChoice = state.fontChoice,
        fontScale = state.fontScale,
        highContrast = state.highContrast,
        reduceMotion = state.reduceMotion
    ) {
        val pal = LocalBlueBlPalette.current
        val reduceMotion = LocalReduceMotion.current

        if (state.overlay != null) {
            BackHandler { viewModel.dismissOverlay() }
        } else if (state.activePanel == Panel.Chat) {
            BackHandler { viewModel.switchPanel(Panel.Pares) }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = pal.bg,
            contentColor = pal.fg,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val isWide = maxWidth >= 720.dp

                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(pal.headerGradientStart, pal.headerGradientEnd)
                                    )
                                )
                        ) {
                            TopMessengerHeader(
                                state = state,
                                onStatusClick = { viewModel.openRadioDeps() },
                                onDisconnectClick = { viewModel.openDisconnectConfirm() },
                                onSettingsClick = { viewModel.openSettings() }
                            )
                            if (!isWide) {
                                MessengerTabBar(
                                    activePanel = state.activePanel,
                                    peerCount = state.peers.size,
                                    onSelect = viewModel::switchPanel
                                )
                            }
                            HorizontalDivider(color = pal.border.copy(alpha = 0.55f))
                        }

                        when {
                            !state.hasBlePermissions -> {
                                PermissionBanner(
                                    icon = Icons.Rounded.Bluetooth,
                                    text = "Activa el permiso de Bluetooth cercano para descubrir dispositivos alrededor.",
                                    buttonText = "Activar",
                                    buttonTag = "grant_ble_banner_button",
                                    onRequest = onRequestBlePermissions
                                )
                            }
                            !state.hasNotificationPermission -> {
                                PermissionBanner(
                                    icon = Icons.Rounded.Notifications,
                                    text = "Permite las notificaciones para mantener la malla Bluetooth estable en segundo plano.",
                                    buttonText = "Permitir",
                                    buttonTag = "grant_notif_banner_button",
                                    onRequest = onRequestNotificationPermission
                                )
                            }
                            !state.isIgnoringBatteryOptimizations || state.isBackgroundRestricted -> {
                                PermissionBanner(
                                    icon = Icons.Rounded.BatteryChargingFull,
                                    text = "Permite ejecución en segundo plano sin optimización de batería para no perder conexión.",
                                    buttonText = "Permitir",
                                    buttonTag = "grant_battery_banner_button",
                                    onRequest = onRequestBatteryAndBackground
                                )
                            }
                        }

                        if (isWide) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(320.dp)
                                        .fillMaxHeight()
                                ) {
                                    PeersPane(
                                        peers = state.peers,
                                        recentLines = state.lines,
                                        showOpenChatAction = false,
                                        onOpenChatClick = { },
                                        onRefreshDiscoveryClick = { viewModel.restartRadioDiscovery() },
                                        meshEnabled = state.meshEnabled,
                                        onEnableMeshClick = { viewModel.setMeshEnabled(true) }
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(pal.border.copy(alpha = 0.55f))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    ChatPane(
                                        state = state,
                                        onInputChanged = viewModel::onInputChanged,
                                        onSend = viewModel::submitInput,
                                        onSuggestionClick = { viewModel.onInputChanged(it) }
                                    )
                                }
                            }
                        } else {
                            AnimatedContent(
                                targetState = state.activePanel,
                                transitionSpec = {
                                    if (reduceMotion) {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    } else {
                                        fadeIn(tween(180)) togetherWith fadeOut(tween(150))
                                    }
                                },
                                label = "panel_switch",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) { panel ->
                                when (panel) {
                                    Panel.Pares -> PeersPane(
                                        peers = state.peers,
                                        recentLines = state.lines,
                                        showOpenChatAction = true,
                                        onOpenChatClick = { viewModel.switchPanel(Panel.Chat) },
                                        onRefreshDiscoveryClick = { viewModel.restartRadioDiscovery() },
                                        meshEnabled = state.meshEnabled,
                                        onEnableMeshClick = { viewModel.setMeshEnabled(true) }
                                    )
                                    Panel.Chat -> ChatPane(
                                        state = state,
                                        onInputChanged = viewModel::onInputChanged,
                                        onSend = viewModel::submitInput,
                                        onSuggestionClick = { viewModel.onInputChanged(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        BlueBlOverlayHost(
            state = state,
            viewModel = viewModel,
            onRequestBlePermissions = onRequestBlePermissions,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestBatteryAndBackground = onRequestBatteryAndBackground,
            onDisconnectAndExit = onDisconnectAndExit
        )
    }
}

@Composable
private fun TopMessengerHeader(
    state: BlueBlUiState,
    onStatusClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val pal = LocalBlueBlPalette.current
    val reduceMotion = LocalReduceMotion.current

    val pulseAlpha = if (!reduceMotion && state.meshEnabled && state.radioReady && state.peers.isEmpty()) {
        val infiniteTransition = rememberInfiniteTransition(label = "ble_pulse")
        val a by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ble_dot_alpha"
        )
        a
    } else {
        1f
    }

    val subtitle = when {
        !state.meshEnabled -> "Malla Bluetooth apagada · Toca aquí"
        !state.hasBlePermissions -> "Permiso Bluetooth necesario"
        !state.radioReady -> state.radioStatus
        state.peers.isEmpty() -> "Buscando dispositivos…"
        state.peers.size == 1 -> "1 dispositivo disponible · Bluetooth activo"
        else -> "${state.peers.size} dispositivos disponibles · Bluetooth activo"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(pal.accentGradientStart, pal.accentGradientEnd)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Radar,
                contentDescription = null,
                tint = pal.onAccent,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onStatusClick)
                .padding(vertical = 2.dp)
                .testTag("header_status_diagnostics_button")
        ) {
            Text(
                text = "BlueBl",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = pal.headerFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(pulseAlpha)
                        .clip(CircleShape)
                        .background(
                            when {
                                !state.meshEnabled -> pal.dim
                                !state.radioReady -> pal.error
                                state.peers.isNotEmpty() -> Color(0xFF22C55E)
                                else -> pal.accent
                            }
                        )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = pal.headerSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onDisconnectClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("header_disconnect_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = "Desconectar y cerrar",
                tint = pal.error
            )
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("header_settings_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Ajustes",
                tint = pal.headerFg
            )
        }
    }
}

@Composable
private fun PermissionBanner(
    icon: ImageVector,
    text: String,
    buttonText: String,
    buttonTag: String,
    onRequest: () -> Unit
) {
    val pal = LocalBlueBlPalette.current
    Surface(
        color = pal.accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, pal.accent.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = pal.accent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = pal.fg,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            GradientButton(
                onClick = onRequest,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag(buttonTag)
            ) {
                Text(buttonText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MessengerTabBar(
    activePanel: Panel,
    peerCount: Int,
    onSelect: (Panel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MessengerTabPill(
            label = if (peerCount > 0) "Personas cerca ($peerCount)" else "Personas cerca",
            icon = Icons.Rounded.People,
            selected = activePanel == Panel.Pares,
            onClick = { onSelect(Panel.Pares) },
            testTag = "tab_peers",
            modifier = Modifier.weight(1f)
        )
        MessengerTabPill(
            label = Panel.Chat.title,
            icon = Icons.AutoMirrored.Rounded.Chat,
            selected = activePanel == Panel.Chat,
            onClick = { onSelect(Panel.Chat) },
            testTag = "tab_chat",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MessengerTabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val pal = LocalBlueBlPalette.current
    val highContrast = LocalHighContrast.current
    val shape = RoundedCornerShape(16.dp)
    val fgColor = if (selected) pal.onAccent else pal.fg.copy(alpha = 0.80f)

    val bgBrush = when {
        selected && highContrast -> Brush.horizontalGradient(listOf(pal.accent, pal.accent))
        selected -> Brush.horizontalGradient(listOf(pal.accentGradientStart, pal.accentGradientEnd))
        highContrast -> Brush.horizontalGradient(listOf(pal.peerBubbleBg, pal.peerBubbleBg))
        else -> Brush.horizontalGradient(listOf(pal.glassSurface, pal.glassSurface))
    }

    val borderStroke = when {
        selected && highContrast -> BorderStroke(1.2.dp, pal.border)
        selected -> BorderStroke(0.75.dp, Color.White.copy(alpha = if (pal.isLight) 0.38f else 0.18f))
        highContrast -> BorderStroke(1.dp, pal.border)
        else -> BorderStroke(1.dp, pal.glassBorder)
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(bgBrush)
            .border(borderStroke, shape)
            .testTag(testTag)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = fgColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatPane(
    state: BlueBlUiState,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSuggestionClick: (String) -> Unit
) {
    val pal = LocalBlueBlPalette.current
    val reduceMotion = LocalReduceMotion.current
    val highContrast = LocalHighContrast.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputFocused by remember { mutableStateOf(false) }

    val hasConversationMessages = remember(state.lines) {
        state.lines.any { it.kind == LineKind.Own || it.kind == LineKind.Peer || it.kind == LineKind.Action }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 4 && lastVisible < total - 2
        }
    }

    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty()) {
            if (reduceMotion) {
                listState.scrollToItem(state.lines.lastIndex)
            } else {
                listState.animateScrollToItem(state.lines.lastIndex)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            ChatWallpaperLayer(
                wallpaper = state.wallpaper,
                dim = state.wallpaperDim,
                customBitmap = state.customWallpaperBitmap,
                pal = pal,
                modifier = Modifier.fillMaxSize()
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
            ) {
                if (!hasConversationMessages) {
                    item(key = "empty_hero_card") {
                        EmptyChatWelcomeCard(
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }

                itemsIndexed(state.lines, key = { _, line -> line.id }) { index, line ->
                    val prev = state.lines.getOrNull(index - 1)
                    val next = state.lines.getOrNull(index + 1)

                    val isSameAsPrev = prev != null &&
                        prev.kind == line.kind &&
                        (line.kind == LineKind.Own || line.kind == LineKind.Peer) &&
                        prev.sender == line.sender &&
                        prev.senderId == line.senderId

                    val isSameAsNext = next != null &&
                        next.kind == line.kind &&
                        (line.kind == LineKind.Own || line.kind == LineKind.Peer) &&
                        next.sender == line.sender &&
                        next.senderId == line.senderId

                    val topGap: Dp = when (state.density) {
                        Density.Compacto -> if (isSameAsPrev) 2.dp else 6.dp
                        Density.Comodo -> if (isSameAsPrev) 3.dp else 10.dp
                        Density.Grande -> if (isSameAsPrev) 5.dp else 14.dp
                    }

                    AnimatedBubbleContainer(reduceMotion = reduceMotion) {
                        ChatBubbleRow(
                            line = line,
                            isFirstInGroup = !isSameAsPrev,
                            isLastInGroup = !isSameAsNext,
                            density = state.density,
                            modifier = Modifier.padding(top = topGap)
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showScrollToBottom,
                enter = if (reduceMotion) EnterTransition.None else fadeIn() + scaleIn(initialScale = 0.85f),
                exit = if (reduceMotion) ExitTransition.None else fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Surface(
                    onClick = {
                        scope.launch {
                            if (state.lines.isNotEmpty()) {
                                listState.animateScrollToItem(state.lines.lastIndex)
                            }
                        }
                    },
                    shape = CircleShape,
                    color = if (highContrast) pal.headerBg else pal.glassSurface,
                    border = BorderStroke(1.dp, if (highContrast) pal.border else pal.glassBorder),
                    shadowElevation = 0.dp,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Ir al último mensaje",
                            tint = pal.accent
                        )
                    }
                }
            }
        }

        // Composer bar (IME insets are already applied once by Scaffold safeDrawing + consumeWindowInsets)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            pal.composerBg.copy(alpha = 0.94f),
                            pal.composerBg
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = pal.border.copy(alpha = 0.35f))

                // Slash command autocomplete bar
                AnimatedVisibility(visible = state.inputText.startsWith('/')) {
                    val filtered = SLASH_SUGGESTIONS.filter {
                        it.startsWith(state.inputText.lowercase()) || state.inputText == "/"
                    }
                    if (filtered.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            filtered.forEach { cmd ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = pal.glassSurface,
                                    border = BorderStroke(1.dp, pal.accent.copy(alpha = 0.45f)),
                                    modifier = Modifier.clickable { onSuggestionClick(cmd) }
                                ) {
                                    Text(
                                        text = cmd.trim(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = pal.accent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val inputBorderColor = if (inputFocused) pal.accent else pal.border.copy(alpha = 0.7f)
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = pal.composerFieldBg,
                        border = BorderStroke(if (inputFocused) 1.5.dp else 1.dp, inputBorderColor),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 140.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (state.inputText.isEmpty()) {
                                    Text(
                                        text = "Escribe un mensaje…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = pal.dim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = state.inputText,
                                    onValueChange = onInputChanged,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = pal.fg),
                                    cursorBrush = SolidColor(pal.accent),
                                    singleLine = state.density == Density.Compacto,
                                    maxLines = when (state.density) {
                                        Density.Compacto -> 1
                                        Density.Comodo -> 4
                                        Density.Grande -> 5
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences,
                                        imeAction = ImeAction.Send
                                    ),
                                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { inputFocused = it.isFocused }
                                        .testTag("chat_input_field")
                                )
                            }
                        }
                    }

                    val hasText = state.inputText.isNotBlank()
                    val sendScale by animateFloatAsState(
                        targetValue = if (hasText || reduceMotion) 1f else 0.94f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "send_scale"
                    )

                    val sendBrush = if (highContrast) {
                        Brush.linearGradient(
                            listOf(
                                pal.accent.copy(alpha = if (hasText) 1f else 0.55f),
                                pal.accent.copy(alpha = if (hasText) 1f else 0.55f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                pal.accentGradientStart.copy(alpha = if (hasText) 1f else 0.55f),
                                pal.accentGradientEnd.copy(alpha = if (hasText) 1f else 0.55f)
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(sendScale)
                            .clip(CircleShape)
                            .background(sendBrush)
                            .border(
                                BorderStroke(
                                    0.75.dp,
                                    if (highContrast) pal.border else Color.White.copy(alpha = if (pal.isLight) 0.45f else 0.2f)
                                ),
                                CircleShape
                            )
                            .testTag("send_message_button")
                            .clickable(onClick = onSend),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Enviar mensaje",
                            tint = pal.onAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatWelcomeCard(
    modifier: Modifier = Modifier
) {
    val pal = LocalBlueBlPalette.current
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_chat),
                    contentDescription = "Ilustración de chat sin internet",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Text(
                    text = "Conversa sin internet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = pal.fg,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Envía un mensaje a los dispositivos cercanos por Bluetooth.",
                    style = MaterialTheme.typography.bodySmall,
                    color = pal.dim,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AnimatedBubbleContainer(
    reduceMotion: Boolean,
    content: @Composable () -> Unit
) {
    if (reduceMotion) {
        content()
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) +
            slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it / 3 }
            )
    ) {
        content()
    }
}

@Composable
private fun ChatBubbleRow(
    line: ChatLine,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    density: Density,
    modifier: Modifier = Modifier
) {
    val pal = LocalBlueBlPalette.current
    val highContrast = LocalHighContrast.current
    val bubblePadH = when (density) {
        Density.Compacto -> 10.dp
        Density.Comodo -> 13.dp
        Density.Grande -> 16.dp
    }
    val bubblePadV = when (density) {
        Density.Compacto -> 6.dp
        Density.Comodo -> 8.dp
        Density.Grande -> 11.dp
    }
    val peerBorder = if (highContrast) {
        BorderStroke(1.2.dp, pal.border)
    } else {
        BorderStroke(0.9.dp, pal.glassBorder)
    }
    val ownBorder = if (highContrast) {
        BorderStroke(1.2.dp, pal.border)
    } else {
        BorderStroke(0.75.dp, pal.glassBorder.copy(alpha = 0.7f))
    }

    when (line.kind) {
        LineKind.Own -> {
            val shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = if (isFirstInGroup) 5.dp else 10.dp,
                bottomEnd = if (isLastInGroup) 18.dp else 10.dp,
                bottomStart = 18.dp
            )
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 84.dp, max = 300.dp)
                        .clip(shape)
                        .background(
                            if (highContrast) {
                                Brush.linearGradient(listOf(pal.ownBubbleBg, pal.ownBubbleBg))
                            } else {
                                Brush.linearGradient(
                                    listOf(pal.ownBubbleGradientStart, pal.ownBubbleGradientEnd)
                                )
                            }
                        )
                        .border(ownBorder, shape)
                ) {
                    Column(modifier = Modifier.padding(horizontal = bubblePadH, vertical = bubblePadV)) {
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = pal.ownBubbleFg
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = line.time,
                                style = MaterialTheme.typography.labelSmall,
                                color = pal.ownBubbleMeta
                            )
                            Icon(
                                imageVector = Icons.Rounded.DoneAll,
                                contentDescription = "Enviado",
                                tint = pal.accent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        LineKind.Peer -> {
            val shape = RoundedCornerShape(
                topStart = if (isFirstInGroup) 5.dp else 10.dp,
                topEnd = 18.dp,
                bottomEnd = 18.dp,
                bottomStart = if (isLastInGroup) 18.dp else 10.dp
            )
            val senderColor = Themes.avatarColorFor(line.senderId.ifBlank { line.sender }, pal.isLight)
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = shape,
                    color = if (highContrast) pal.peerBubbleBg else pal.glassSurface,
                    shadowElevation = 0.dp,
                    border = peerBorder,
                    modifier = Modifier.widthIn(min = 88.dp, max = 300.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = bubblePadH, vertical = bubblePadV)) {
                        if (isFirstInGroup) {
                            Text(
                                text = line.sender,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = senderColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = pal.peerBubbleFg
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = line.time,
                            style = MaterialTheme.typography.labelSmall,
                            color = pal.peerBubbleMeta,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }

        LineKind.System -> {
            Box(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = pal.systemPillBg,
                    border = peerBorder,
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = pal.systemPillFg,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        LineKind.Error -> {
            Box(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = pal.error.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, pal.error.copy(alpha = 0.6f)),
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = pal.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        LineKind.Action -> {
            Box(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = pal.accent.copy(alpha = 0.14f),
                    border = peerBorder
                ) {
                    Text(
                        text = "${line.sender} ${line.text}",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = pal.fg,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
