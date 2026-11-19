package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalBlueBlPalette
import com.example.ui.theme.LocalHighContrast
import com.example.ui.theme.LocalReduceMotion

@Composable
fun SettingsFullScreen(
    state: BlueBlUiState,
    initialSection: SettingsSection,
    viewModel: BlueBlViewModel,
    onRequestNotificationPermission: () -> Unit = {},
    onRequestBatteryAndBackground: () -> Unit = {}
) {
    val pal = LocalBlueBlPalette.current
    val reduceMotion = LocalReduceMotion.current
    val highContrast = LocalHighContrast.current

    BackHandler {
        viewModel.navigateSettingsBack()
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
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(pal.headerGradientStart, pal.headerGradientEnd)
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateSettingsBack() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = pal.headerFg
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = initialSection.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = pal.headerFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HorizontalDivider(color = pal.border.copy(alpha = 0.5f))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        if (highContrast) {
                            Brush.verticalGradient(listOf(pal.bg, pal.bg))
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    pal.headerGradientEnd.copy(alpha = 0.28f),
                                    pal.bg,
                                    pal.accent2.copy(alpha = if (pal.isLight) 0.08f else 0.05f)
                                )
                            )
                        }
                    )
            ) {
                AnimatedContent(
                    targetState = initialSection,
                    transitionSpec = {
                        if (reduceMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else if (targetState == SettingsSection.Root) {
                            (slideInHorizontally(tween(220)) { -it / 4 } + fadeIn(tween(180))) togetherWith
                                (slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(150)))
                        } else {
                            (slideInHorizontally(tween(220)) { it / 4 } + fadeIn(tween(180))) togetherWith
                                (slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(150)))
                        }
                    },
                    label = "settings_section_transition",
                    modifier = Modifier.fillMaxSize()
                ) { section ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val contentMod = Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxSize()

                        when (section) {
                            SettingsSection.Root -> SettingsRootPane(
                                state = state,
                                viewModel = viewModel,
                                onRequestNotificationPermission = onRequestNotificationPermission,
                                onRequestBatteryAndBackground = onRequestBatteryAndBackground,
                                modifier = contentMod
                            )
                            SettingsSection.Themes -> ThemesSettingsPane(
                                state = state,
                                viewModel = viewModel,
                                modifier = contentMod
                            )
                            SettingsSection.Wallpapers -> WallpapersSettingsPane(
                                state = state,
                                viewModel = viewModel,
                                modifier = contentMod
                            )
                            SettingsSection.Accessibility -> AccessibilitySettingsPane(
                                state = state,
                                viewModel = viewModel,
                                modifier = contentMod
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRootPane(
    state: BlueBlUiState,
    viewModel: BlueBlViewModel,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryAndBackground: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = LocalBlueBlPalette.current
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Nickname card (clean, no QR or initial avatar)
        GlassCard(
            highlighted = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.openNickEditor() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(pal.accentGradientStart, pal.accentGradientEnd)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = pal.onAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.nickname,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = pal.fg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Cambiar nombre",
                            tint = pal.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Toca para cambiar tu nombre visible en el chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.dim
                    )
                }
            }
        }

        Text(
            text = "Personalización y lectura",
            style = MaterialTheme.typography.labelLarge,
            color = pal.accent,
            modifier = Modifier.padding(start = 6.dp, top = 4.dp)
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                SettingsRowItem(
                    icon = Icons.Default.Palette,
                    iconBg = pal.accent,
                    title = "Apariencia y temas",
                    subtitle = "${state.themeDef.name} · ${if (state.themeDef.palette.isLight) "Claro" else "Oscuro"}",
                    onClick = { viewModel.openSettings(SettingsSection.Themes) },
                    testTag = "settings_themes_row"
                )
                HorizontalDivider(
                    color = pal.border.copy(alpha = 0.45f),
                    modifier = Modifier.padding(start = 68.dp)
                )
                SettingsRowItem(
                    icon = Icons.Default.Wallpaper,
                    iconBg = pal.accent2,
                    title = "Fondo del chat",
                    subtitle = state.wallpaper.name,
                    onClick = { viewModel.openSettings(SettingsSection.Wallpapers) },
                    testTag = "settings_wallpapers_row"
                )
                HorizontalDivider(
                    color = pal.border.copy(alpha = 0.45f),
                    modifier = Modifier.padding(start = 68.dp)
                )
                SettingsRowItem(
                    icon = Icons.Default.AccessibilityNew,
                    iconBg = Color(0xFF5C6BC0),
                    title = "Accesibilidad y lectura",
                    subtitle = "Texto ${state.fontScale.shortLabel} · ${state.fontChoice.displayName}",
                    onClick = { viewModel.openSettings(SettingsSection.Accessibility) },
                    testTag = "settings_accessibility_row"
                )
            }
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("settings_advanced_toggle"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Diagnóstico y atajos",
                            style = MaterialTheme.typography.titleSmall,
                            color = pal.fg
                        )
                        Text(
                            text = "Estado de radio Bluetooth y atajos del chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = pal.dim
                        )
                    }
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showAdvanced) "Ocultar opciones" else "Mostrar opciones",
                        tint = pal.dim
                    )
                }

                if (showAdvanced) {
                    HorizontalDivider(color = pal.border.copy(alpha = 0.45f))
                    SettingsRowItem(
                        icon = Icons.Default.Bluetooth,
                        iconBg = if (state.radioReady) pal.accent2 else pal.error,
                        title = "Estado de radio Bluetooth",
                        subtitle = if (state.radioReady) "Todo listo para buscar dispositivos" else "Revisar permisos o adaptador",
                        onClick = { viewModel.openRadioDeps() },
                        testTag = "settings_radio_deps_row"
                    )

                    HorizontalDivider(
                        color = pal.border.copy(alpha = 0.45f),
                        modifier = Modifier.padding(start = 68.dp)
                    )
                    SettingsRowItem(
                        icon = Icons.Default.Notifications,
                        iconBg = if (state.hasNotificationPermission) pal.accent2 else pal.error,
                        title = "Notificaciones de la malla",
                        subtitle = if (state.hasNotificationPermission) "Activadas" else "Toca para activar notificaciones",
                        onClick = onRequestNotificationPermission,
                        testTag = "settings_notifications_row"
                    )

                    HorizontalDivider(
                        color = pal.border.copy(alpha = 0.45f),
                        modifier = Modifier.padding(start = 68.dp)
                    )
                    val bgReady = state.isIgnoringBatteryOptimizations && !state.isBackgroundRestricted
                    SettingsRowItem(
                        icon = Icons.Default.BatteryChargingFull,
                        iconBg = if (bgReady) pal.accent2 else pal.error,
                        title = "Segundo plano y batería",
                        subtitle = when {
                            state.isBackgroundRestricted -> "Restringido — toca para permitir segundo plano"
                            !state.isIgnoringBatteryOptimizations -> "Optimizado — toca para quitar ahorro de batería"
                            else -> "Sin restricciones de batería"
                        },
                        onClick = onRequestBatteryAndBackground,
                        testTag = "settings_battery_background_row"
                    )

                    HorizontalDivider(
                        color = pal.border.copy(alpha = 0.45f),
                        modifier = Modifier.padding(start = 68.dp)
                    )
                    SettingsRowItem(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        iconBg = pal.dim,
                        title = "Atajos de teclado",
                        subtitle = "Comandos opcionales escribiendo / en el chat",
                        onClick = { viewModel.openHelp() },
                        testTag = "settings_help_row"
                    )
                }
            }
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsRowItem(
                icon = Icons.Default.PowerSettingsNew,
                iconBg = pal.error,
                title = "Desconectar y cerrar app",
                subtitle = "Detiene el segundo plano Bluetooth y cierra BlueBl",
                onClick = { viewModel.openDisconnectConfirm() },
                testTag = "settings_disconnect_row"
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    val pal = LocalBlueBlPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            iconBg.copy(alpha = 0.24f),
                            iconBg.copy(alpha = 0.12f)
                        )
                    )
                )
                .border(0.75.dp, iconBg.copy(alpha = 0.28f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconBg,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = pal.fg
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = pal.dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = pal.dim
        )
    }
}
