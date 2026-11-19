package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Density
import com.example.data.FontChoice
import com.example.data.FontScaleOption
import com.example.data.WallpaperCategory
import com.example.data.WallpaperOption
import com.example.data.Wallpapers
import com.example.ui.theme.BlueBlThemeDef
import com.example.ui.theme.LocalBlueBlPalette
import com.example.ui.theme.LocalHighContrast
import com.example.ui.theme.ThemeCategory
import com.example.ui.theme.Themes
import com.example.ui.theme.fontFamilyFor
import kotlin.math.roundToInt

@Composable
fun ThemesSettingsPane(
    state: BlueBlUiState,
    viewModel: BlueBlViewModel,
    modifier: Modifier = Modifier
) {
    val pal = LocalBlueBlPalette.current
    var showClassicThemes by rememberSaveable {
        mutableStateOf(state.themeDef.category != ThemeCategory.Mensajeria)
    }

    val messengerThemes = Themes.all.filter { it.category == ThemeCategory.Mensajeria }
    val extraCategories = ThemeCategory.entries.filter { it != ThemeCategory.Mensajeria }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BubblePreviewSample(
            pal = pal,
            wallpaper = state.wallpaper,
            wallpaperDim = state.wallpaperDim,
            customBitmap = state.customWallpaperBitmap
        )

        Text(
            text = "Recomendados para mensajería",
            style = MaterialTheme.typography.titleMedium,
            color = pal.fg
        )

        // 2-column visual theme cards
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            messengerThemes.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { theme ->
                        ThemePreviewCard(
                            theme = theme,
                            selected = state.themeDef.id == theme.id,
                            onClick = { viewModel.selectTheme(theme) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // Expandable classic & stylized themes
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable { showClassicThemes = !showClassicThemes }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("toggle_classic_themes"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Más estilos (clásicos y creativos)",
                            style = MaterialTheme.typography.titleSmall,
                            color = pal.fg
                        )
                        Text(
                            text = "${Themes.all.size - messengerThemes.size} paletas adicionales",
                            style = MaterialTheme.typography.bodySmall,
                            color = pal.dim
                        )
                    }
                    Icon(
                        imageVector = if (showClassicThemes) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = pal.dim
                    )
                }

                if (showClassicThemes) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        extraCategories.forEach { cat ->
                            Text(
                                text = cat.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = pal.accent,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            val catThemes = Themes.all.filter { it.category == cat }
                            catThemes.chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { theme ->
                                        ThemePreviewCard(
                                            theme = theme,
                                            selected = state.themeDef.id == theme.id,
                                            onClick = { viewModel.selectTheme(theme) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ThemePreviewCard(
    theme: BlueBlThemeDef,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePal = LocalBlueBlPalette.current
    val p = theme.palette

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = p.chatBg,
        border = BorderStroke(
            width = if (selected) 2.5.dp else 1.dp,
            color = if (selected) activePal.accent else p.glassBorder
        ),
        shadowElevation = 0.dp,
        modifier = modifier
            .testTag("theme_item_${theme.id}")
            .clickable(onClick = onClick)
    ) {
        Column {
            // Mini header + 2 sample mini bubbles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(p.headerGradientStart, p.headerGradientEnd)))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(p.accentGradientStart, p.accentGradientEnd)))
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Tema activo",
                            tint = activePal.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(p.glassSurface)
                        .border(0.75.dp, p.glassBorder, RoundedCornerShape(8.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .fillMaxWidth(0.65f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(listOf(p.ownBubbleGradientStart, p.ownBubbleGradientEnd))
                        )
                )
            }

            HorizontalDivider(color = p.border.copy(alpha = 0.35f))

            Surface(
                color = p.headerBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = p.headerFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallpapersSettingsPane(
    state: BlueBlUiState,
    viewModel: BlueBlViewModel,
    modifier: Modifier = Modifier
) {
    val pal = LocalBlueBlPalette.current
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importGalleryWallpaper(uri)
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BubblePreviewSample(
            pal = pal,
            wallpaper = state.wallpaper,
            wallpaperDim = state.wallpaperDim,
            customBitmap = state.customWallpaperBitmap
        )

        // Gallery photo picker card
        GlassCard(
            highlighted = state.wallpaper.id == "foto-galeria" && state.customWallpaperBitmap != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Foto personal",
                        style = MaterialTheme.typography.titleSmall,
                        color = pal.fg
                    )
                    if (state.wallpaper.id == "foto-galeria" && state.customWallpaperBitmap != null) {
                        Text(
                            text = "Activa en el chat",
                            style = MaterialTheme.typography.labelMedium,
                            color = pal.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GradientButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pick_gallery_wallpaper_button")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Elegir de galería", fontWeight = FontWeight.SemiBold)
                    }

                    if (state.customWallpaperBitmap != null) {
                        OutlinedButton(
                            onClick = { viewModel.clearGalleryWallpaper() },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, pal.error.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("clear_gallery_wallpaper_button")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Quitar foto", tint = pal.error)
                        }
                    }
                }
            }
        }

        // Contrast / veil slider
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Suavizado para leer mejor",
                        style = MaterialTheme.typography.titleSmall,
                        color = pal.fg
                    )
                    Text(
                        text = "${(state.wallpaperDim / 0.75f * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = pal.accent
                    )
                }
                Slider(
                    value = state.wallpaperDim,
                    onValueChange = { viewModel.setWallpaperDim(it) },
                    valueRange = 0f..0.75f,
                    colors = SliderDefaults.colors(
                        thumbColor = pal.accent,
                        activeTrackColor = pal.accent,
                        inactiveTrackColor = pal.border
                    ),
                    modifier = Modifier.testTag("wallpaper_dim_slider")
                )
            }
        }

        // Categorized wallpaper swatches
        WallpaperCategory.entries
            .filter { it != WallpaperCategory.Galeria || state.customWallpaperBitmap != null }
            .forEach { category ->
                val items = Wallpapers.all.filter { it.category == category }
                if (items.isNotEmpty()) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = pal.accent
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items.forEach { option ->
                            WallpaperSwatchCard(
                                option = option,
                                selected = state.wallpaper.id == option.id,
                                customBitmap = state.customWallpaperBitmap,
                                onClick = { viewModel.selectWallpaper(option) }
                            )
                        }
                    }
                }
            }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun WallpaperSwatchCard(
    option: WallpaperOption,
    selected: Boolean,
    customBitmap: android.graphics.Bitmap?,
    onClick: () -> Unit
) {
    val pal = LocalBlueBlPalette.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (selected) 2.5.dp else 1.dp,
            color = if (selected) pal.accent else pal.border.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .width(104.dp)
            .testTag("wallpaper_item_${option.id}")
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .background(pal.chatBg)
            ) {
                when (option.id) {
                    "degradado-menta" -> Box(
                        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFE5F6EE), Color(0xFFD9F2E8))))
                    )
                    "degradado-cielo" -> Box(
                        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFDCEEFB), Color(0xFFE2EEFA))))
                    )
                    "degradado-lavanda" -> Box(
                        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFECE6FB), Color(0xFFFBEBF4))))
                    )
                    "degradado-atardecer" -> Box(
                        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFFBE7DC), Color(0xFFF9E5E0))))
                    )
                    "degradado-noche" -> Box(
                        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0E1B2B), Color(0xFF152940))))
                    )
                    "arte-botanico" -> Image(
                        painter = painterResource(id = R.drawable.img_wallpaper_botanical),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    "arte-aurora" -> Image(
                        painter = painterResource(id = R.drawable.img_wallpaper_aurora),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    "foto-galeria" -> {
                        if (customBitmap != null) {
                            Image(
                                bitmap = customBitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    else -> {
                        ChatWallpaperLayer(
                            wallpaper = option,
                            dim = 0f,
                            customBitmap = null,
                            pal = pal,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(pal.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Activo",
                            tint = pal.onAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Surface(color = pal.composerFieldBg, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = pal.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
fun AccessibilitySettingsPane(
    state: BlueBlUiState,
    viewModel: BlueBlViewModel,
    modifier: Modifier = Modifier
) {
    val pal = LocalBlueBlPalette.current
    val highContrast = LocalHighContrast.current

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BubblePreviewSample(
            pal = pal,
            wallpaper = state.wallpaper,
            wallpaperDim = state.wallpaperDim,
            customBitmap = state.customWallpaperBitmap
        )

        // Font size selector S - XL
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tamaño de letra",
                        style = MaterialTheme.typography.titleSmall,
                        color = pal.fg
                    )
                    Text(
                        text = state.fontScale.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = pal.accent
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FontScaleOption.entries.forEach { option ->
                        val active = state.fontScale == option
                        val chipShape = RoundedCornerShape(12.dp)
                        val chipBg = when {
                            active && highContrast -> Brush.horizontalGradient(listOf(pal.accent, pal.accent))
                            active -> Brush.horizontalGradient(listOf(pal.accentGradientStart, pal.accentGradientEnd))
                            else -> Brush.horizontalGradient(listOf(pal.bg, pal.bg))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(chipShape)
                                .background(chipBg)
                                .border(
                                    1.dp,
                                    if (active) Color.Transparent else pal.glassBorder,
                                    chipShape
                                )
                                .testTag("font_scale_${option.id}")
                                .clickable { viewModel.setFontScale(option) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.shortLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = (13f + option.ordinal * 2.5f).sp,
                                color = if (active) pal.onAccent else pal.fg
                            )
                        }
                    }
                }
            }
        }

        // Message spacing / density
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Espaciado entre mensajes",
                    style = MaterialTheme.typography.titleSmall,
                    color = pal.fg
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Density.entries.forEach { d ->
                        val active = state.density == d
                        val chipShape = RoundedCornerShape(12.dp)
                        val chipBg = when {
                            active && highContrast -> Brush.horizontalGradient(listOf(pal.accent, pal.accent))
                            active -> Brush.horizontalGradient(listOf(pal.accentGradientStart, pal.accentGradientEnd))
                            else -> Brush.horizontalGradient(listOf(pal.bg, pal.bg))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(chipShape)
                                .background(chipBg)
                                .border(
                                    1.dp,
                                    if (active) Color.Transparent else pal.glassBorder,
                                    chipShape
                                )
                                .testTag("density_chip_${d.id}")
                                .clickable { viewModel.selectDensity(d) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = d.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) pal.onAccent else pal.fg
                            )
                        }
                    }
                }
            }
        }

        // Typography choice
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tipo de letra",
                    style = MaterialTheme.typography.titleSmall,
                    color = pal.fg
                )
                FontChoice.entries.forEach { choice ->
                    val selected = state.fontChoice == choice
                    val previewFamily = fontFamilyFor(choice)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) pal.accent.copy(alpha = 0.12f) else pal.bg,
                        border = BorderStroke(
                            if (selected) 1.8.dp else 1.dp,
                            if (selected) pal.accent else pal.border.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("font_choice_${choice.id}")
                            .clickable { viewModel.setFontChoice(choice) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { viewModel.setFontChoice(choice) },
                                colors = RadioButtonDefaults.colors(selectedColor = pal.accent)
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = choice.displayName,
                                    fontFamily = previewFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.5.sp,
                                    color = pal.fg
                                )
                                Text(
                                    text = choice.description,
                                    fontFamily = previewFamily,
                                    fontSize = 12.5.sp,
                                    color = pal.dim
                                )
                            }
                        }
                    }
                }
            }
        }

        // Toggles: High Contrast & Reduce Motion
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable { viewModel.setHighContrast(!state.highContrast) }
                        .padding(vertical = 8.dp)
                        .testTag("toggle_high_contrast"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modo alto contraste",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = pal.fg
                        )
                        Text(
                            text = "Bordes definidos y máximo contraste en burbujas y textos",
                            style = MaterialTheme.typography.bodySmall,
                            color = pal.dim
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.highContrast,
                        onCheckedChange = { viewModel.setHighContrast(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = pal.onAccent,
                            checkedTrackColor = pal.accent
                        )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clickable { viewModel.setReduceMotion(!state.reduceMotion) }
                        .padding(vertical = 8.dp)
                        .testTag("toggle_reduce_motion"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reducir animaciones",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = pal.fg
                        )
                        Text(
                            text = "Desactiva rebotes, pulsos y desplazamientos animados",
                            style = MaterialTheme.typography.bodySmall,
                            color = pal.dim
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.reduceMotion,
                        onCheckedChange = { viewModel.setReduceMotion(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = pal.onAccent,
                            checkedTrackColor = pal.accent
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}
