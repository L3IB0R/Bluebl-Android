package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Commands
import com.example.ui.theme.LocalBlueBlPalette
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlueBlOverlayHost(
    state: BlueBlUiState,
    viewModel: BlueBlViewModel,
    onRequestBlePermissions: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryAndBackground: () -> Unit,
    onDisconnectAndExit: () -> Unit
) {
    val overlay = state.overlay ?: return
    val pal = LocalBlueBlPalette.current

    when (overlay) {
        is ActiveOverlay.Settings -> {
            SettingsFullScreen(
                state = state,
                initialSection = overlay.section,
                viewModel = viewModel,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestBatteryAndBackground = onRequestBatteryAndBackground
            )
        }

        ActiveOverlay.Help -> {
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissOverlay() },
                containerColor = pal.bg,
                contentColor = pal.fg
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Atajos de teclado en el chat",
                        style = MaterialTheme.typography.titleLarge,
                        color = pal.fg
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Opcional: puedes escribir estos atajos directamente en la barra de mensajes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.dim
                    )
                    Spacer(Modifier.height(14.dp))
                    Commands.helpEntries().forEach { (cmd, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = cmd,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = pal.accent,
                                modifier = Modifier.width(142.dp)
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = pal.fg,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider(color = pal.border.copy(alpha = 0.4f))
                    }
                }
            }
        }

        ActiveOverlay.RadioDeps -> {
            RadioDepsSheet(
                state = state,
                onRefresh = { viewModel.refreshRadioDiagnostics() },
                onRestartDiscovery = {
                    viewModel.restartRadioDiscovery()
                    viewModel.refreshRadioDiagnostics()
                },
                onToggleMesh = { viewModel.setMeshEnabled(!state.meshEnabled) },
                onRequestPermissions = onRequestBlePermissions,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestBatteryAndBackground = onRequestBatteryAndBackground,
                onDisconnectAndExit = onDisconnectAndExit,
                onDismiss = { viewModel.dismissOverlay() }
            )
        }

        ActiveOverlay.NickEditor -> {
            var nickInput by remember(state.nickname) { mutableStateOf(state.nickname) }
            AlertDialog(
                onDismissRequest = { viewModel.dismissOverlay() },
                containerColor = pal.bg,
                titleContentColor = pal.fg,
                textContentColor = pal.fg,
                shape = RoundedCornerShape(24.dp),
                title = { Text("Tu nombre en BlueBl") },
                text = {
                    OutlinedTextField(
                        value = nickInput,
                        onValueChange = { nickInput = it },
                        singleLine = true,
                        label = { Text("Cómo te verán los dispositivos cerca") },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = pal.accent,
                            unfocusedBorderColor = pal.border,
                            focusedTextColor = pal.fg,
                            unfocusedTextColor = pal.fg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nick_input")
                    )
                },
                confirmButton = {
                    GradientButton(
                        onClick = {
                            viewModel.updateNickname(nickInput)
                            viewModel.dismissOverlay()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("nick_save_button")
                    ) {
                        Text("Guardar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissOverlay() }) {
                        Text("Cancelar", color = pal.dim)
                    }
                }
            )
        }
        ActiveOverlay.DisconnectConfirm -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissOverlay() },
                containerColor = pal.bg,
                titleContentColor = pal.fg,
                textContentColor = pal.fg,
                shape = RoundedCornerShape(24.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = pal.error,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = { Text("¿Desconectar BlueBl?") },
                text = {
                    Text(
                        text = "Se detendrá la búsqueda Bluetooth en segundo plano, se quitará la notificación activa y se cerrará la aplicación.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = pal.dim
                    )
                },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = onDisconnectAndExit,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = pal.error,
                            contentColor = androidx.compose.ui.graphics.Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("confirm_disconnect_exit_button")
                    ) {
                        Text("Desconectar y salir", fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissOverlay() }) {
                        Text("Cancelar", color = pal.dim)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadioDepsSheet(
    state: BlueBlUiState,
    onRefresh: () -> Unit,
    onRestartDiscovery: () -> Unit,
    onToggleMesh: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryAndBackground: () -> Unit,
    onDisconnectAndExit: () -> Unit,
    onDismiss: () -> Unit
) {
    val pal = LocalBlueBlPalette.current
    val context = LocalContext.current
    val diag = state.meshDiagnostics

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            onRefresh()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = pal.bg,
        contentColor = pal.fg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = pal.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Estado de radio Bluetooth",
                        style = MaterialTheme.typography.titleMedium,
                        color = pal.fg
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar diagnóstico", tint = pal.fg)
                }
            }

            state.radioChecks.forEach { check ->
                GlassCard(
                    shape = RoundedCornerShape(14.dp),
                    highlighted = check.ok
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (check.ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (check.ok) pal.accent2 else pal.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = check.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = pal.fg
                            )
                            Text(
                                text = check.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = pal.dim
                            )
                        }
                    }
                }
            }

            GlassCard(
                shape = RoundedCornerShape(14.dp),
                highlighted = state.meshEnabled
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Actividad BLE en vivo",
                        style = MaterialTheme.typography.labelLarge,
                        color = pal.accent
                    )
                    Text(
                        text = "Anuncio: ${diag.advertisingState} · Escaneo: ${diag.scanningState}",
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.fg
                    )
                    Text(
                        text = "Nodos detectados: ${diag.seenCandidates} · Enlaces GATT: ${diag.connectedGattLinks} · Paquetes: ${diag.packetsReceived}",
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.dim
                    )
                    Text(
                        text = "Último evento: ${diag.lastEvent}",
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.dim
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!state.hasBlePermissions) {
                    GradientButton(
                        onClick = onRequestPermissions,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("grant_ble_permissions_button")
                    ) {
                        Text("Conceder permisos")
                    }
                } else {
                    GradientButton(
                        onClick = onRestartDiscovery,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("restart_ble_discovery_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.meshEnabled) "Reiniciar búsqueda" else "Encender malla")
                    }
                }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, pal.border),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = pal.fg, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ajustes Bluetooth", color = pal.fg)
                }
            }

            if (!state.hasNotificationPermission) {
                OutlinedButton(
                    onClick = onRequestNotificationPermission,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, pal.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sheet_grant_notifications_button")
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = pal.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Permitir notificaciones", color = pal.accent, fontWeight = FontWeight.SemiBold)
                }
            }

            if (!state.isIgnoringBatteryOptimizations || state.isBackgroundRestricted) {
                OutlinedButton(
                    onClick = onRequestBatteryAndBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, pal.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sheet_grant_battery_button")
                ) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = pal.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Permitir segundo plano y batería", color = pal.accent, fontWeight = FontWeight.SemiBold)
                }
            }

            if (state.hasBlePermissions) {
                OutlinedButton(
                    onClick = onToggleMesh,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (state.meshEnabled) pal.accent.copy(alpha = 0.7f) else pal.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("toggle_mesh_service_button")
                ) {
                    Text(
                        text = if (state.meshEnabled) "Pausar búsqueda Bluetooth" else "Encender búsqueda Bluetooth",
                        color = pal.accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            OutlinedButton(
                onClick = onDisconnectAndExit,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, pal.error.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sheet_disconnect_exit_button")
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = pal.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Desconectar y cerrar app",
                    color = pal.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
