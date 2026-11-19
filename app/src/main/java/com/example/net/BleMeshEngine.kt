package com.example.net

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.example.crypto.Identity
import com.example.crypto.toHex
import com.example.data.GENERAL_CHANNEL
import com.example.data.MeshEvent
import com.example.data.RadioCheck
import com.example.proto.Announcement
import com.example.proto.ChatMessage
import com.example.proto.Fragment
import com.example.proto.MeshFrag
import com.example.proto.MsgType
import com.example.proto.Packet
import com.example.proto.ProtoException
import com.example.proto.Reassembler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class MeshDiagnostics(
    val advertisingState: String = "Inactivo",
    val scanningState: String = "Inactivo",
    val seenCandidates: Int = 0,
    val connectedGattLinks: Int = 0,
    val packetsReceived: Int = 0,
    val lastEvent: String = "Sin eventos BLE todavía"
)

class BleMeshEngine(
    private val context: Context,
    private val identity: Identity,
    initialNickname: String
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<MeshEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<MeshEvent> = _events.asSharedFlow()

    @Volatile
    private var nickname: String = initialNickname

    @Volatile
    private var running = false

    @Volatile
    private var receiverRegistered = false

    private var announceJob: Job? = null
    private var scanRefreshJob: Job? = null
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null
    private var serverCharacteristic: BluetoothGattCharacteristic? = null

    private val advertiseStarted = AtomicBoolean(false)
    private val retriedAdvertiseCompact = AtomicBoolean(false)
    private val useUnfilteredScanNext = AtomicBoolean(false)

    @Volatile
    private var advertisingStateText = "Inactivo"

    @Volatile
    private var scanningStateText = "Inactivo"

    @Volatile
    private var lastDiagEvent = "Sin eventos BLE todavía"

    private val seenCandidatesSet = ConcurrentHashMap.newKeySet<String>()
    private val packetsReceivedCount = AtomicInteger(0)

    private class PeerLink(
        val gatt: BluetoothGatt,
        val characteristic: BluetoothGattCharacteristic,
        var peerHex: String?,
        @Volatile var mtu: Int = 23,
        @Volatile var ready: Boolean = false
    ) {
        val ioMutex = Mutex()
        @Volatile
        var pendingWrite: CompletableDeferred<Boolean>? = null
        @Volatile
        var pendingRead: CompletableDeferred<Boolean>? = null
        @Volatile
        var pendingDescriptor: CompletableDeferred<Boolean>? = null
    }

    private val centralLinks = ConcurrentHashMap<String, PeerLink>()
    private val negotiatedLinkMtu = ConcurrentHashMap<String, Int>()
    private val connectingJobs = ConcurrentHashMap<String, Job>()
    private val retryCooldownUntil = ConcurrentHashMap<String, Long>()
    private val subscribedCentrals = ConcurrentHashMap<String, BluetoothDevice>()
    private val centralMtu = ConcurrentHashMap<String, Int>()
    private val addressToPeerHex = ConcurrentHashMap<String, String>()

    private val notifyMutex = Mutex()
    @Volatile
    private var pendingNotify: CompletableDeferred<Boolean>? = null

    private val seenLock = Any()
    private val seen = HashSet<String>()
    private val seenOrder = ArrayDeque<String>()
    private val reassembler = Reassembler()

    @Volatile
    private var lastCharValue: ByteArray = ByteArray(0)

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON) {
                        scope.launch {
                            delay(350)
                            start()
                        }
                    } else if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                        stop()
                        emitStatus("Bluetooth apagado")
                    }
                }
                LocationManager.PROVIDERS_CHANGED_ACTION -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        scope.launch {
                            delay(250)
                            if (!running) start()
                        }
                    }
                }
            }
        }
    }

    init {
        registerSystemReceivers()
        lastCharValue = buildSignedAnnouncePacket()?.encode(false) ?: ByteArray(0)
    }

    fun diagnostics(): MeshDiagnostics {
        val links = centralLinks.values.count { it.ready } + subscribedCentrals.size
        return MeshDiagnostics(
            advertisingState = advertisingStateText,
            scanningState = scanningStateText,
            seenCandidates = seenCandidatesSet.size,
            connectedGattLinks = links,
            packetsReceived = packetsReceivedCount.get(),
            lastEvent = lastDiagEvent
        )
    }

    private fun registerSystemReceivers() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                bluetoothStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
    }

    @Synchronized
    fun start() {
        registerSystemReceivers()
        if (running) return
        if (!hasBlePermissions(appContext)) {
            advertisingStateText = "Sin permisos BLE"
            scanningStateText = "Sin permisos BLE"
            emitStatus("permisos BLE pendientes")
            return
        }
        val pm = appContext.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            advertisingStateText = "No disponible"
            scanningStateText = "No disponible"
            emitStatus("dispositivo sin hardware BLE")
            return
        }
        val mgr = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = mgr?.adapter
        if (adapter == null) {
            advertisingStateText = "Sin adaptador"
            scanningStateText = "Sin adaptador"
            emitStatus("sin adaptador Bluetooth")
            return
        }
        if (!adapter.isEnabled) {
            advertisingStateText = "Bluetooth apagado"
            scanningStateText = "Bluetooth apagado"
            emitStatus("Bluetooth apagado")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm?.isLocationEnabled == false) {
                scanningStateText = "Ubicación desactivada"
                emitStatus("ubicación desactivada (necesaria para BLE en Android ≤ 11)")
                return
            }
        }

        bluetoothManager = mgr
        bluetoothAdapter = adapter
        running = true
        advertiseStarted.set(false)
        retriedAdvertiseCompact.set(false)
        useUnfilteredScanNext.set(false)
        seenCandidatesSet.clear()
        lastCharValue = buildSignedAnnouncePacket()?.encode(false) ?: ByteArray(0)
        advertisingStateText = "Iniciando…"
        scanningStateText = "Iniciando…"
        lastDiagEvent = "Radio BLE iniciada"
        emitStatus("radio BLE activa")

        val added = startGattServer(mgr)
        if (!added) {
            advertiseStarted.set(true)
            startAdvertising(adapter)
        } else {
            scope.launch {
                delay(1200)
                if (running && advertiseStarted.compareAndSet(false, true)) {
                    startAdvertising(adapter)
                }
            }
        }
        startScanning(adapter, unfiltered = false)
        inspectAlreadyConnectedGatt(mgr)

        announceJob = scope.launch {
            while (isActive && running) {
                sendAnnounce()
                delay(ANNOUNCE_INTERVAL_MS)
            }
        }

        scanRefreshJob = scope.launch {
            while (isActive && running) {
                delay(SCAN_REFRESH_INTERVAL_MS)
                if (!running) break
                val sc = scanner
                val currentAdapter = bluetoothAdapter
                if (sc != null && currentAdapter != null && currentAdapter.isEnabled && hasScanPermission(appContext)) {
                    if (centralLinks.isEmpty() && subscribedCentrals.isEmpty()) {
                        runCatching { sc.stopScan(scanCallback) }
                        delay(200)
                        val unfiltered = useUnfilteredScanNext.getAndSet(!useUnfilteredScanNext.get())
                        startScanning(currentAdapter, unfiltered = unfiltered)
                        inspectAlreadyConnectedGatt(bluetoothManager)
                    }
                }
            }
        }
    }

    @Synchronized
    fun restartDiscovery() {
        stop()
        scope.launch {
            delay(250)
            start()
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        announceJob?.cancel()
        announceJob = null
        scanRefreshJob?.cancel()
        scanRefreshJob = null
        advertiseStarted.set(false)
        advertisingStateText = "Detenido"
        scanningStateText = "Detenido"

        connectingJobs.values.forEach { it.cancel() }
        connectingJobs.clear()
        retryCooldownUntil.clear()

        if (hasBlePermissions(appContext)) {
            runCatching { scanner?.stopScan(scanCallback) }
            runCatching { advertiser?.stopAdvertising(advertiseCallback) }
            centralLinks.values.forEach { link ->
                link.pendingWrite?.complete(false)
                link.pendingRead?.complete(false)
                link.pendingDescriptor?.complete(false)
                mainHandler.post {
                    runCatching {
                        link.gatt.disconnect()
                        link.gatt.close()
                    }
                }
            }
            mainHandler.post {
                runCatching { gattServer?.close() }
            }
        }
        pendingNotify?.complete(false)
        pendingNotify = null
        centralLinks.clear()
        negotiatedLinkMtu.clear()
        subscribedCentrals.clear()
        centralMtu.clear()
        addressToPeerHex.clear()
        gattServer = null
        serverCharacteristic = null
        emitStatus("radio detenida")
    }

    fun shutdown() {
        stop()
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(bluetoothStateReceiver) }
            receiverRegistered = false
        }
    }

    fun setNickname(newNick: String) {
        nickname = newNick
        lastCharValue = buildSignedAnnouncePacket()?.encode(false) ?: lastCharValue
        if (running) {
            scope.launch { sendAnnounce() }
        }
    }

    fun sendBroadcastText(text: String): Boolean {
        val pkt = Packet(
            msgType = MsgType.MESSAGE,
            sender = identity.peerId(),
            recipient = Packet.BROADCAST.copyOf(),
            payload = text.toByteArray(Charsets.UTF_8)
        )
        pkt.sign(identity)
        return sendPacket(pkt)
    }

    fun sendPacket(packet: Packet): Boolean {
        if (!running) return false
        return try {
            scope.launch {
                broadcastPacketAdaptive(packet, excludeAddress = null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildSignedAnnouncePacket(): Packet? {
        return runCatching {
            val ann = Announcement(
                nickname = nickname,
                noisePublic = identity.noisePublic(),
                signingPublic = identity.signingPublic()
            )
            val pkt = Packet(
                msgType = MsgType.ANNOUNCE,
                sender = identity.peerId(),
                recipient = Packet.BROADCAST.copyOf(),
                payload = ann.encode()
            )
            pkt.sign(identity)
            pkt
        }.getOrNull()
    }

    private suspend fun sendAnnounce(targetLink: PeerLink? = null, targetCentral: BluetoothDevice? = null) {
        if (!running) return
        val pkt = buildSignedAnnouncePacket() ?: return
        lastCharValue = pkt.encode(false)
        when {
            targetLink != null -> sendPacketToLink(targetLink, pkt)
            targetCentral != null -> notifyPacketToCentral(targetCentral, pkt)
            else -> broadcastPacketAdaptive(pkt, excludeAddress = null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer(mgr: BluetoothManager): Boolean {
        if (!hasConnectPermission(appContext)) return false
        val server = runCatching { mgr.openGattServer(appContext, gattServerCallback) }.getOrNull() ?: return false
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val ch = BluetoothGattCharacteristic(
            CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        ch.addDescriptor(cccd)
        service.addCharacteristic(ch)
        gattServer = server
        serverCharacteristic = ch
        return runCatching { server.addService(service) }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising(adapter: BluetoothAdapter, includeScanResponse: Boolean = true) {
        if (!running) return
        if (!hasAdvertisePermission(appContext)) {
            advertisingStateText = "Sin permiso anunciar"
            emitStatus("escuchando (sin permiso anunciar)")
            return
        }
        val adv = adapter.bluetoothLeAdvertiser
        if (adv == null) {
            advertisingStateText = "No soportado por hardware"
            emitStatus("escuchando (periférico BLE no soportado)")
            return
        }
        advertiser = adv
        runCatching { adv.stopAdvertising(advertiseCallback) }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        // Strictly <= 31 bytes per BLE legacy ADV packet:
        // Flags (3 bytes) + 128-bit Service UUID (18 bytes) = 21 bytes.
        val advData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        if (includeScanResponse) {
            // Scan Response carries ServiceData with our 8-byte peer ID (26 bytes <= 31).
            val scanResp = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceData(ParcelUuid(SERVICE_UUID), identity.peerId())
                .build()
            runCatching { adv.startAdvertising(settings, advData, scanResp, advertiseCallback) }
        } else {
            runCatching { adv.startAdvertising(settings, advData, advertiseCallback) }
        }
    }

    private fun buildScanFilters(): List<ScanFilter> {
        val list = mutableListOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build(),
            ScanFilter.Builder().setServiceData(ParcelUuid(SERVICE_UUID), ByteArray(0), ByteArray(0)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(TESTNET_SERVICE_UUID)).build()
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list.add(
                ScanFilter.Builder().setServiceSolicitationUuid(ParcelUuid(SERVICE_UUID)).build()
            )
        }
        return list
    }

    @SuppressLint("MissingPermission")
    private fun startScanning(adapter: BluetoothAdapter, unfiltered: Boolean) {
        if (!running || !hasScanPermission(appContext)) return
        val sc = adapter.bluetoothLeScanner
        if (sc == null) {
            scanningStateText = "Escáner no disponible"
            return
        }
        scanner = sc

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        val filters = if (unfiltered) null else buildScanFilters()
        val started = runCatching {
            sc.startScan(filters, settings, scanCallback)
            true
        }.getOrDefault(false)

        if (started) {
            scanningStateText = if (unfiltered) "Activo (amplio)" else "Activo (filtrado UUID)"
        } else if (!unfiltered) {
            val fallbackOk = runCatching {
                sc.startScan(null, settings, scanCallback)
                true
            }.getOrDefault(false)
            scanningStateText = if (fallbackOk) "Activo (amplio)" else "Error al iniciar escaneo"
        } else {
            scanningStateText = "Error al iniciar escaneo"
        }
    }

    @SuppressLint("MissingPermission")
    private fun inspectAlreadyConnectedGatt(mgr: BluetoothManager?) {
        if (!running || mgr == null || !hasConnectPermission(appContext)) return
        val now = System.currentTimeMillis()
        runCatching {
            val connected = mgr.getConnectedDevices(BluetoothProfile.GATT)
            for (device in connected) {
                val addr = device.address ?: continue
                if (centralLinks.containsKey(addr) || connectingJobs.containsKey(addr)) continue
                val cooldown = retryCooldownUntil[addr] ?: 0L
                if (now < cooldown) continue
                // Probe any connected LE/Dual GATT device (Linux BlueZ PCs often advertise system hostname)
                if (device.type != BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                    seenCandidatesSet.add(addr)
                    attemptConnectDevice(device, peerHexHint = null)
                }
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            advertisingStateText = "Activo (visible)"
            emitStatus("anunciando y buscando en la malla")
        }

        override fun onStartFailure(errorCode: Int) {
            if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE && retriedAdvertiseCompact.compareAndSet(false, true)) {
                val adapter = bluetoothAdapter
                if (adapter != null && running) {
                    scope.launch {
                        delay(150)
                        startAdvertising(adapter, includeScanResponse = false)
                    }
                    return
                }
            }
            advertisingStateText = "Error código $errorCode"
            lastDiagEvent = "Anuncio BLE falló (código $errorCode)"
            emitStatus("escuchando en la malla (anuncio $errorCode)")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (result in results) {
                handleScanResult(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanningStateText = "Error escaneo ($errorCode)"
            lastDiagEvent = "Escaneo BLE falló (código $errorCode)"
            if (errorCode != SCAN_FAILED_ALREADY_STARTED) {
                val adapter = bluetoothAdapter
                if (adapter != null && running && useUnfilteredScanNext.compareAndSet(false, true)) {
                    scope.launch {
                        delay(350)
                        startScanning(adapter, unfiltered = true)
                    }
                    return
                }
            }
            emitStatus("error escaneo BLE ($errorCode)")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        if (!running) return
        val device = result.device ?: return
        val addr = device.address ?: return
        val record = result.scanRecord

        val hasUuid = record?.serviceUuids?.any { it.uuid in MATCH_SERVICE_UUIDS } == true
        val hasSolicitation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            record?.serviceSolicitationUuids?.any { it.uuid in MATCH_SERVICE_UUIDS } == true
        } else {
            false
        }
        val serviceData = record?.getServiceData(ParcelUuid(SERVICE_UUID))
            ?: record?.getServiceData(ParcelUuid(TESTNET_SERVICE_UUID))
        val hasServiceData = serviceData != null
        val rawHasUuid = record?.bytes?.let { raw ->
            containsBytes(raw, SERVICE_UUID_LE_BYTES) || containsBytes(raw, TESTNET_UUID_LE_BYTES)
        } == true
        val advName = record?.deviceName ?: if (hasConnectPermission(appContext)) runCatching { device.name }.getOrNull() else null
        val matchesName = advName?.contains("bluebl", ignoreCase = true) == true ||
            advName?.contains("bitchat", ignoreCase = true) == true

        if (!hasUuid && !hasSolicitation && !hasServiceData && !rawHasUuid && !matchesName) return

        val peerHex = serviceData?.takeIf { it.size == 8 }?.toHex()
        if (peerHex != null && peerHex == identity.peerIdHex()) {
            return
        }

        seenCandidatesSet.add(addr)
        lastDiagEvent = "Detectado nodo BLE ${peerHex?.take(8) ?: addr.takeLast(8)}"

        if (peerHex != null && centralLinks.values.any { it.peerHex == peerHex && it.ready }) {
            return
        }
        if (centralLinks.containsKey(addr) || connectingJobs.containsKey(addr)) {
            return
        }
        val now = System.currentTimeMillis()
        val cooldown = retryCooldownUntil[addr] ?: 0L
        if (now < cooldown) return

        attemptConnectDevice(device, peerHex)
    }

    @SuppressLint("MissingPermission")
    private fun attemptConnectDevice(device: BluetoothDevice, peerHexHint: String?) {
        val addr = device.address ?: return
        if (!running || !hasConnectPermission(appContext)) return
        if (centralLinks.containsKey(addr) || connectingJobs.containsKey(addr)) return

        val timeoutJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (connectingJobs.remove(addr) != null) {
                retryCooldownUntil[addr] = System.currentTimeMillis() + CONNECT_RETRY_COOLDOWN_MS
                lastDiagEvent = "Timeout conectando a ${addr.takeLast(8)}"
            }
        }
        connectingJobs[addr] = timeoutJob

        mainHandler.post {
            if (!running || !hasConnectPermission(appContext)) {
                connectingJobs.remove(addr)?.cancel()
                return@post
            }
            val gatt = runCatching {
                device.connectGatt(appContext, false, createGattCallback(peerHexHint), BluetoothDevice.TRANSPORT_LE)
            }.getOrNull()
            if (gatt == null) {
                connectingJobs.remove(addr)?.cancel()
                retryCooldownUntil[addr] = System.currentTimeMillis() + CONNECT_RETRY_COOLDOWN_MS
            }
        }
    }

    private fun createGattCallback(peerHexHint: String?): BluetoothGattCallback {
        val discoveryTriggered = AtomicBoolean(false)
        return object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val addr = gatt.device.address
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    connectingJobs.remove(addr)?.cancel()
                    retryCooldownUntil.remove(addr)
                    discoveryTriggered.set(false)
                    lastDiagEvent = "Conectado GATT a ${addr.takeLast(8)}"

                    // Give BlueZ / Android BLE stack 200ms to settle before MTU negotiation
                    scope.launch {
                        delay(200)
                        if (!running) return@launch
                        val mtuStarted = CompletableDeferred<Boolean>()
                        mainHandler.post {
                            val ok = runCatching { gatt.requestMtu(517) }.getOrDefault(false)
                            mtuStarted.complete(ok)
                        }
                        val requested = mtuStarted.await()
                        if (!requested) {
                            if (discoveryTriggered.compareAndSet(false, true)) {
                                mainHandler.post { runCatching { gatt.discoverServices() } }
                            }
                        } else {
                            delay(MTU_FALLBACK_TIMEOUT_MS)
                            if (running && discoveryTriggered.compareAndSet(false, true)) {
                                mainHandler.post { runCatching { gatt.discoverServices() } }
                            }
                        }
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    connectingJobs.remove(addr)?.cancel()
                    centralLinks.remove(addr)?.let { removed ->
                        removed.pendingWrite?.complete(false)
                        removed.pendingRead?.complete(false)
                        removed.pendingDescriptor?.complete(false)
                        negotiatedLinkMtu.remove(addr)
                        retryCooldownUntil[addr] = System.currentTimeMillis() + CONNECT_RETRY_COOLDOWN_MS
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            lastDiagEvent = "Error GATT $status en ${addr.takeLast(8)}"
                        }
                        mainHandler.post { runCatching { gatt.close() } }
                        val hexId = removed.peerHex ?: addressToPeerHex.remove(addr) ?: peerHexHint
                        if (hexId != null && !hasActiveLinkForPeer(hexId)) {
                            scope.launch { _events.emit(MeshEvent.PeerLeft(hexId)) }
                        }
                    } ?: run {
                        negotiatedLinkMtu.remove(addr)
                        retryCooldownUntil[addr] = System.currentTimeMillis() + CONNECT_RETRY_COOLDOWN_MS
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            lastDiagEvent = "Error GATT $status en ${addr.takeLast(8)}"
                        }
                        mainHandler.post { runCatching { gatt.close() } }
                        val hexId = addressToPeerHex.remove(addr) ?: peerHexHint
                        if (hexId != null && !hasActiveLinkForPeer(hexId)) {
                            scope.launch { _events.emit(MeshEvent.PeerLeft(hexId)) }
                        }
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                val addr = gatt.device.address
                if (status == BluetoothGatt.GATT_SUCCESS && mtu >= 23) {
                    negotiatedLinkMtu[addr] = mtu
                    centralLinks[addr]?.mtu = mtu
                    lastDiagEvent = "MTU $mtu en ${addr.takeLast(8)}"
                }
                if (discoveryTriggered.compareAndSet(false, true)) {
                    mainHandler.post { runCatching { gatt.discoverServices() } }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val addr = gatt.device.address
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    retryCooldownUntil[addr] = System.currentTimeMillis() + CONNECT_RETRY_COOLDOWN_MS
                    mainHandler.post { runCatching { gatt.disconnect() } }
                    return
                }
                val service = gatt.getService(SERVICE_UUID) ?: gatt.getService(TESTNET_SERVICE_UUID)
                val ch = service?.getCharacteristic(CHAR_UUID)
                if (ch == null) {
                    retryCooldownUntil[addr] = System.currentTimeMillis() + NOT_BLUEBL_COOLDOWN_MS
                    mainHandler.post { runCatching { gatt.disconnect() } }
                    return
                }

                val link = PeerLink(
                    gatt = gatt,
                    characteristic = ch,
                    peerHex = peerHexHint,
                    mtu = negotiatedLinkMtu[addr] ?: 23,
                    ready = false
                )
                centralLinks[addr] = link
                if (peerHexHint != null) {
                    addressToPeerHex[addr] = peerHexHint
                }

                scope.launch {
                    setupLinkAndAnnounce(link, addr)
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                centralLinks[gatt.device.address]?.pendingDescriptor?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                centralLinks[gatt.device.address]?.pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                centralLinks[gatt.device.address]?.pendingRead?.complete(status == BluetoothGatt.GATT_SUCCESS)
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == CHAR_UUID && value.isNotEmpty()) {
                    handleIncomingPacket(value, fromAddress = gatt.device.address)
                }
            }

            @Suppress("DEPRECATION")
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                centralLinks[gatt.device.address]?.pendingRead?.complete(status == BluetoothGatt.GATT_SUCCESS)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                    status == BluetoothGatt.GATT_SUCCESS &&
                    characteristic.uuid == CHAR_UUID
                ) {
                    val value = characteristic.value ?: return
                    if (value.isNotEmpty()) {
                        handleIncomingPacket(value, fromAddress = gatt.device.address)
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                if (characteristic.uuid == CHAR_UUID) {
                    handleIncomingPacket(value, fromAddress = gatt.device.address)
                }
            }

            @Suppress("DEPRECATION")
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && characteristic.uuid == CHAR_UUID) {
                    val value = characteristic.value ?: return
                    handleIncomingPacket(value, fromAddress = gatt.device.address)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun setupLinkAndAnnounce(link: PeerLink, addr: String) {
        if (!running || !hasConnectPermission(appContext)) return
        link.ioMutex.withLock {
            runCatching { link.gatt.setCharacteristicNotification(link.characteristic, true) }
            val cccd = link.characteristic.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                val props = link.characteristic.properties
                val notifyVal = if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                }
                val def = CompletableDeferred<Boolean>()
                link.pendingDescriptor = def
                val started = writeDescriptorCompat(link.gatt, cccd, notifyVal)
                if (started) {
                    withTimeoutOrNull(GATT_OP_TIMEOUT_MS) { def.await() }
                }
                link.pendingDescriptor = null
            }

            link.ready = true

            // If remote exposes initial ANNOUNCE via characteristic read, fetch it now
            if ((link.characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                val readDef = CompletableDeferred<Boolean>()
                link.pendingRead = readDef
                if (runCatching { link.gatt.readCharacteristic(link.characteristic) }.getOrDefault(false)) {
                    withTimeoutOrNull(GATT_OP_TIMEOUT_MS) { readDef.await() }
                }
                link.pendingRead = null
            }
        }

        val label = link.peerHex?.take(8) ?: addr.replace(":", "").takeLast(8).lowercase()
        lastDiagEvent = "Enlace BlueBl listo ($label)"
        emitStatus("vecino conectado: $label")
        sendAnnounce(targetLink = link)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (!running) return
            if (status == BluetoothGatt.GATT_SUCCESS && service.uuid == SERVICE_UUID) {
                if (advertiseStarted.compareAndSet(false, true)) {
                    bluetoothAdapter?.let { startAdvertising(it) }
                }
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val addr = device.address ?: return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                lastDiagEvent = "Central conectado: ${addr.takeLast(8)}"
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                subscribedCentrals.remove(addr)
                centralMtu.remove(addr)
                val hexId = addressToPeerHex.remove(addr)
                if (hexId != null && !hasActiveLinkForPeer(hexId)) {
                    scope.launch { _events.emit(MeshEvent.PeerLeft(hexId)) }
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            val addr = device.address ?: return
            if (mtu >= 23) {
                centralMtu[addr] = mtu
                lastDiagEvent = "MTU central $mtu (${addr.takeLast(8)})"
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = lastCharValue.takeIf { it.isNotEmpty() }
                ?: buildSignedAnnouncePacket()?.encode(false)
                ?: ByteArray(0)
            val slice = if (offset < value.size) value.copyOfRange(offset, value.size) else ByteArray(0)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic.uuid == CHAR_UUID && value != null) {
                handleIncomingPacket(value, fromAddress = device.address)
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            var subscribedNow = false
            if (descriptor.uuid == CCCD_UUID && value != null) {
                val enable = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                if (enable) {
                    subscribedCentrals[device.address] = device
                    subscribedNow = true
                    lastDiagEvent = "Central suscrito (${device.address.takeLast(8)})"
                } else {
                    subscribedCentrals.remove(device.address)
                }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
            if (subscribedNow) {
                scope.launch {
                    delay(360)
                    sendAnnounce(targetCentral = device)
                }
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            pendingNotify?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    private fun hasActiveLinkForPeer(peerHex: String): Boolean {
        if (centralLinks.values.any { it.peerHex == peerHex }) return true
        return addressToPeerHex.entries.any { (addr, id) ->
            id == peerHex && subscribedCentrals.containsKey(addr)
        }
    }

    private fun handleIncomingPacket(bytes: ByteArray, fromAddress: String?) {
        var packet = try {
            Packet.decode(bytes)
        } catch (_: ProtoException) {
            return
        }

        packetsReceivedCount.incrementAndGet()

        if (packet.msgType == MsgType.FRAGMENT) {
            val frag = runCatching { Fragment.decode(packet.payload) }.getOrNull() ?: return
            val ttl = packet.ttl
            val sender = packet.sender
            val assembled = synchronized(reassembler) {
                reassembler.push(frag)
            } ?: return
            val inner = runCatching { Packet.decode(assembled.second) }.getOrNull() ?: return
            inner.ttl = minOf(ttl, inner.ttl)
            inner.sender = sender
            packet = inner
        }

        val myPeer = identity.peerId()
        val rec = packet.recipient
        if (rec != null && !rec.contentEquals(Packet.BROADCAST) && !rec.contentEquals(myPeer)) {
            return
        }

        val senderHex = packet.sender.toHex()
        if (fromAddress != null && senderHex.length == 16 && senderHex != identity.peerIdHex()) {
            addressToPeerHex[fromAddress] = senderHex
            centralLinks[fromAddress]?.let { link ->
                if (link.peerHex == null) link.peerHex = senderHex
            }
        }

        val key = MeshFrag.dedupKey(packet)
        val isNew = synchronized(seenLock) {
            if (seen.add(key)) {
                seenOrder.addLast(key)
                if (seenOrder.size > DEDUP_CAP) {
                    val old = seenOrder.removeFirst()
                    seen.remove(old)
                }
                true
            } else {
                false
            }
        }
        if (!isNew) return

        when (packet.msgType) {
            MsgType.ANNOUNCE -> {
                val ann = runCatching { Announcement.decode(packet.payload) }.getOrNull() ?: return
                val claimed = packet.sender.toHex()
                if (!Identity.matchesClaimedPeerId(claimed, ann.noisePublic)) return
                lastDiagEvent = "Recibido ANNOUNCE de ${ann.nickname}"
                scope.launch {
                    _events.emit(
                        MeshEvent.PeerJoined(
                            id = claimed,
                            nickname = ann.nickname,
                            noisePublic = ann.noisePublic.toHex(),
                            signingPublic = ann.signingPublic.toHex()
                        )
                    )
                }
                if (fromAddress != null) {
                    val central = subscribedCentrals[fromAddress]
                    if (central != null) {
                        scope.launch { sendAnnounce(targetCentral = central) }
                    }
                }
            }
            MsgType.MESSAGE -> {
                val decoded = runCatching { ChatMessage.decode(packet.payload) }
                    .getOrNull()
                    ?.takeIf { it.content.isNotEmpty() && it.sender.isNotEmpty() }

                val text: String
                val channel: String?
                val nick: String?
                if (decoded != null) {
                    text = decoded.content
                    channel = decoded.channel
                    nick = decoded.sender
                } else {
                    val plain = decodeStrictOrNull(packet.payload) ?: return
                    if (!isPlainChat(plain)) return
                    text = plain
                    channel = null
                    nick = null
                }

                if (text.isEmpty()) return
                if (channel != null && channel != GENERAL_CHANNEL) return

                lastDiagEvent = "Mensaje de ${nick ?: senderHex.take(6)}"
                val fromHex = packet.sender.toHex()
                scope.launch {
                    _events.emit(
                        MeshEvent.Message(
                            from = fromHex,
                            nick = nick,
                            channel = channel,
                            text = text
                        )
                    )
                }
            }
        }

        // Flood relay with TTL decrement
        if (packet.ttl > 1 && packet.isBroadcast()) {
            packet.ttl -= 1
            scope.launch {
                broadcastPacketAdaptive(packet, excludeAddress = fromAddress)
            }
        }
    }

    private fun maxAttPayloadFor(negotiatedMtu: Int?): Int {
        return when {
            negotiatedMtu == null -> 180
            negotiatedMtu >= 67 -> (negotiatedMtu - 3).coerceAtMost(512)
            else -> 180
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun broadcastPacketAdaptive(packet: Packet, excludeAddress: String?) {
        if (!running || !hasConnectPermission(appContext)) return
        lastCharValue = runCatching { packet.encode(false) }.getOrDefault(lastCharValue)

        for ((addr, link) in centralLinks) {
            if (addr == excludeAddress || !link.ready) continue
            sendPacketToLink(link, packet)
        }
        for ((addr, dev) in subscribedCentrals) {
            if (addr == excludeAddress) continue
            notifyPacketToCentral(dev, packet)
        }
    }

    private suspend fun sendPacketToLink(link: PeerLink, packet: Packet) {
        if (!running || !hasConnectPermission(appContext)) return
        val maxBytes = maxAttPayloadFor(link.mtu.takeIf { it >= 67 })
        val frames = runCatching { MeshFrag.fragmentForWireLimit(packet, maxBytes) }.getOrNull() ?: return
        for (frame in frames) {
            if (!running) break
            writePacketToLink(link, frame)
        }
    }

    private suspend fun notifyPacketToCentral(device: BluetoothDevice, packet: Packet) {
        if (!running || !hasConnectPermission(appContext)) return
        val maxBytes = maxAttPayloadFor(centralMtu[device.address])
        val frames = runCatching { MeshFrag.fragmentForWireLimit(packet, maxBytes) }.getOrNull() ?: return
        for (frame in frames) {
            if (!running) break
            notifySingleCentral(device, frame)
        }
    }

    private suspend fun writePacketToLink(link: PeerLink, bytes: ByteArray) {
        if (!running || !hasConnectPermission(appContext)) return
        link.ioMutex.withLock {
            val def = CompletableDeferred<Boolean>()
            link.pendingWrite = def
            val initiated = writeCharacteristicCompat(link.gatt, link.characteristic, bytes)
            if (initiated) {
                withTimeoutOrNull(GATT_OP_TIMEOUT_MS) { def.await() }
            }
            link.pendingWrite = null
        }
    }

    private suspend fun notifySingleCentral(device: BluetoothDevice, bytes: ByteArray) {
        val srv = gattServer ?: return
        val ch = serverCharacteristic ?: return
        if (!running || !hasConnectPermission(appContext)) return
        notifyMutex.withLock {
            val def = CompletableDeferred<Boolean>()
            pendingNotify = def
            val initiated = notifyCompat(srv, device, ch, bytes)
            if (initiated) {
                withTimeoutOrNull(GATT_OP_TIMEOUT_MS) { def.await() }
            }
            pendingNotify = null
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun writeCharacteristicCompat(
        gatt: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        val writeType = if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(ch, value, writeType) == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                ch.writeType = writeType
                ch.value = value
                gatt.writeCharacteristic(ch)
            }
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun writeDescriptorCompat(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value) == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                descriptor.value = value
                gatt.writeDescriptor(descriptor)
            }
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun notifyCompat(
        server: BluetoothGattServer,
        device: BluetoothDevice,
        ch: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                server.notifyCharacteristicChanged(device, ch, false, value) == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                ch.value = value
                server.notifyCharacteristicChanged(device, ch, false)
            }
        }.getOrDefault(false)
    }

    private fun decodeStrictOrNull(bytes: ByteArray): String? {
        return runCatching {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()
    }

    private fun emitStatus(text: String) {
        scope.launch { _events.emit(MeshEvent.Status(text)) }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("f47b5e2d-4a9e-4c5a-9b3f-8e1d2c3a4b5c")
        val TESTNET_SERVICE_UUID: UUID = UUID.fromString("f47b5e2d-4a9e-4c5a-9b3f-8e1d2c3a4b5a")
        val CHAR_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val MATCH_SERVICE_UUIDS = setOf(SERVICE_UUID, TESTNET_SERVICE_UUID)
        private val SERVICE_UUID_LE_BYTES = uuidToLittleEndianBytes(SERVICE_UUID)
        private val TESTNET_UUID_LE_BYTES = uuidToLittleEndianBytes(TESTNET_SERVICE_UUID)

        const val ANNOUNCE_INTERVAL_MS = 20_000L
        const val SCAN_REFRESH_INTERVAL_MS = 15_000L
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val CONNECT_RETRY_COOLDOWN_MS = 2_500L
        const val NOT_BLUEBL_COOLDOWN_MS = 25_000L
        const val MTU_FALLBACK_TIMEOUT_MS = 1_200L
        const val GATT_OP_TIMEOUT_MS = 2_000L
        const val DEDUP_CAP = 4096

        private fun uuidToLittleEndianBytes(uuid: UUID): ByteArray {
            val be = ByteBuffer.allocate(16)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(uuid.mostSignificantBits)
                .putLong(uuid.leastSignificantBits)
                .array()
            return be.reversedArray()
        }

        private fun containsBytes(haystack: ByteArray, needle: ByteArray): Boolean {
            if (needle.isEmpty() || haystack.size < needle.size) return false
            outer@ for (i in 0..(haystack.size - needle.size)) {
                for (j in needle.indices) {
                    if (haystack[i + j] != needle[j]) continue@outer
                }
                return true
            }
            return false
        }

        fun isPlainChat(s: String): Boolean {
            if (s.isEmpty() || s.contains('\uFFFD')) return false
            return s.all { !it.isISOControl() || it == '\n' || it == '\t' || it == '\r' }
        }

        fun requiredPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }

        fun missingStartupPermissions(context: Context): Array<String> {
            val missing = requiredPermissions().filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }.toMutableList()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                missing.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            return missing.toTypedArray()
        }

        fun hasBlePermissions(context: Context): Boolean {
            return requiredPermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }

        private fun hasScanPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
        }

        private fun hasAdvertisePermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
            } else true
        }

        private fun hasConnectPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else true
        }

        fun checkRadioDependencies(context: Context): List<RadioCheck> {
            val checks = mutableListOf<RadioCheck>()
            val hasBle = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
            checks.add(
                RadioCheck(
                    name = "Hardware BLE",
                    ok = hasBle,
                    detail = if (hasBle) "soporte Bluetooth Low Energy disponible" else "este dispositivo no reporta radio BLE"
                )
            )

            val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = mgr?.adapter
            val adapterPresent = adapter != null
            val adapterEnabled = adapter?.isEnabled == true
            checks.add(
                RadioCheck(
                    name = "Adaptador Bluetooth",
                    ok = adapterPresent && adapterEnabled,
                    detail = when {
                        !adapterPresent -> "no hay adaptador Bluetooth en el sistema"
                        !adapterEnabled -> "apagado — activa Bluetooth en ajustes"
                        else -> "encendido y listo"
                    }
                )
            )

            val permsOk = hasBlePermissions(context)
            checks.add(
                RadioCheck(
                    name = "Permisos cercanos (BLE)",
                    ok = permsOk,
                    detail = if (permsOk) {
                        "concedidos (escaneo, anuncio y conexión)"
                    } else {
                        "faltan permisos de dispositivos cercanos"
                    }
                )
            )

            val advSupported = adapter?.isMultipleAdvertisementSupported == true || adapter?.bluetoothLeAdvertiser != null
            checks.add(
                RadioCheck(
                    name = "Modo periférico (anuncio LE)",
                    ok = adapterEnabled && advSupported,
                    detail = if (adapterEnabled && advSupported) {
                        "anunciando servicio BlueBl y peer ID en scan response"
                    } else if (!adapterEnabled) {
                        "requiere activar Bluetooth"
                    } else {
                        "solo modo central (escaneo sin anuncio propio)"
                    }
                )
            )

            val notifOk = BackgroundPowerHelper.hasNotificationPermission(context)
            checks.add(
                RadioCheck(
                    name = "Notificaciones",
                    ok = notifOk,
                    detail = if (notifOk) {
                        "permitidas para mantener visible la malla activa"
                    } else {
                        "desactivadas — concédelas para evitar cortes del sistema"
                    }
                )
            )

            val ignoringBattery = BackgroundPowerHelper.isIgnoringBatteryOptimizations(context)
            val bgRestricted = BackgroundPowerHelper.isBackgroundRestricted(context)
            val bgReady = ignoringBattery && !bgRestricted
            checks.add(
                RadioCheck(
                    name = "Segundo plano y batería",
                    ok = bgReady,
                    detail = when {
                        bgRestricted -> "segundo plano restringido — permite uso sin restricciones"
                        !ignoringBattery -> "optimización de batería activa — permite segundo plano sin ahorro"
                        else -> "ejecución en segundo plano y batería sin restricciones"
                    }
                )
            )

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val locEnabled = lm?.isLocationEnabled == true
                checks.add(
                    RadioCheck(
                        name = "Servicios de ubicación (Android ≤ 11)",
                        ok = locEnabled,
                        detail = if (locEnabled) "activos para escaneo BLE" else "desactivados (necesarios en Android ≤ 11)"
                    )
                )
            }

            return checks
        }
    }
}
