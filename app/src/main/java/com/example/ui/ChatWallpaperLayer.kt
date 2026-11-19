package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.WallpaperOption
import com.example.ui.theme.BlueBlPalette
import com.example.ui.theme.LocalHighContrast
import com.example.ui.theme.Themes
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun ChatWallpaperLayer(
    wallpaper: WallpaperOption,
    dim: Float,
    customBitmap: Bitmap?,
    pal: BlueBlPalette,
    modifier: Modifier = Modifier
) {
    val highContrast = LocalHighContrast.current
    val effectiveDim = if (highContrast) dim.coerceAtLeast(0.45f) else dim

    Box(modifier = modifier.background(pal.chatBg)) {
        when (wallpaper.id) {
            "degradado-menta" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFE5F6EE),
                                    Color(0xFFF3FAF6),
                                    Color(0xFFD9F2E8)
                                )
                            )
                        )
                )
            }
            "degradado-cielo" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFDCEEFB),
                                    Color(0xFFF2F8FD),
                                    Color(0xFFE2EEFA)
                                )
                            )
                        )
                )
            }
            "degradado-lavanda" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFECE6FB),
                                    Color(0xFFF8F4FC),
                                    Color(0xFFFBEBF4)
                                )
                            )
                        )
                )
            }
            "degradado-atardecer" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFFBE7DC),
                                    Color(0xFFFDF5EE),
                                    Color(0xFFF9E5E0)
                                )
                            )
                        )
                )
            }
            "degradado-noche" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF0E1B2B),
                                    Color(0xFF152940),
                                    Color(0xFF0A1421)
                                )
                            )
                        )
                )
            }
            "arte-botanico" -> {
                Image(
                    painter = painterResource(id = R.drawable.img_wallpaper_botanical),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            "arte-aurora" -> {
                Image(
                    painter = painterResource(id = R.drawable.img_wallpaper_aurora),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            "foto-galeria" -> {
                if (customBitmap != null) {
                    val imageBitmap = remember(customBitmap) { customBitmap.asImageBitmap() }
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            "patron-doodle" -> {
                val ink = if (pal.isLight) pal.dim.copy(alpha = 0.14f) else pal.fg.copy(alpha = 0.08f)
                val accentInk = pal.accent.copy(alpha = if (pal.isLight) 0.12f else 0.10f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stepX = 88.dp.toPx()
                    val stepY = 88.dp.toPx()
                    val strokeW = 1.6.dp.toPx()
                    var row = 0
                    var y = 24.dp.toPx()
                    while (y < size.height + stepY) {
                        val offsetX = if (row % 2 == 0) 0f else stepX * 0.5f
                        var col = 0
                        var x = -20.dp.toPx() + offsetX
                        while (x < size.width + stepX) {
                            when ((row + col) % 4) {
                                0 -> {
                                    drawRoundRect(
                                        color = ink,
                                        topLeft = Offset(x, y),
                                        size = Size(22.dp.toPx(), 16.dp.toPx()),
                                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                        style = Stroke(width = strokeW)
                                    )
                                }
                                1 -> {
                                    val c = Offset(x + 10.dp.toPx(), y + 12.dp.toPx())
                                    drawArc(
                                        color = accentInk,
                                        startAngle = -140f,
                                        sweepAngle = 100f,
                                        useCenter = false,
                                        topLeft = Offset(c.x - 7.dp.toPx(), c.y - 7.dp.toPx()),
                                        size = Size(14.dp.toPx(), 14.dp.toPx()),
                                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = accentInk,
                                        startAngle = -140f,
                                        sweepAngle = 100f,
                                        useCenter = false,
                                        topLeft = Offset(c.x - 12.dp.toPx(), c.y - 12.dp.toPx()),
                                        size = Size(24.dp.toPx(), 24.dp.toPx()),
                                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                    )
                                }
                                2 -> {
                                    val cx = x + 10.dp.toPx()
                                    val cy = y + 8.dp.toPx()
                                    val r = 7.dp.toPx()
                                    drawLine(ink, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = strokeW, cap = StrokeCap.Round)
                                    drawLine(ink, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = strokeW, cap = StrokeCap.Round)
                                }
                                else -> {
                                    val path = Path().apply {
                                        moveTo(x, y + 8.dp.toPx())
                                        lineTo(x + 18.dp.toPx(), y)
                                        lineTo(x + 12.dp.toPx(), y + 16.dp.toPx())
                                        lineTo(x + 8.dp.toPx(), y + 10.dp.toPx())
                                        close()
                                    }
                                    drawPath(path, color = ink, style = Stroke(width = strokeW, cap = StrokeCap.Round))
                                }
                            }
                            x += stepX
                            col++
                        }
                        y += stepY
                        row++
                    }
                }
            }
            "patron-burbujas" -> {
                val c1 = pal.accent.copy(alpha = if (pal.isLight) 0.10f else 0.12f)
                val c2 = pal.accent2.copy(alpha = if (pal.isLight) 0.08f else 0.10f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 110.dp.toPx()
                    var row = 0
                    var y = 20.dp.toPx()
                    while (y < size.height + step) {
                        var col = 0
                        var x = (if (row % 2 == 0) 20.dp.toPx() else 70.dp.toPx())
                        while (x < size.width + step) {
                            val radius = (14 + ((row * 3 + col * 5) % 18)).dp.toPx()
                            if ((row + col) % 2 == 0) {
                                drawCircle(color = c1, radius = radius, center = Offset(x, y))
                            } else {
                                drawCircle(
                                    color = c2,
                                    radius = radius,
                                    center = Offset(x, y),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                            x += step
                            col++
                        }
                        y += step
                        row++
                    }
                }
            }
            "patron-ondas" -> {
                val lineC = pal.accent.copy(alpha = if (pal.isLight) 0.11f else 0.13f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gap = 42.dp.toPx()
                    var baseY = 16.dp.toPx()
                    var idx = 0
                    while (baseY < size.height + gap) {
                        val path = Path()
                        val amp = 12.dp.toPx()
                        val freq = (2.0 * PI) / 160.dp.toPx()
                        val phase = idx * 0.7
                        var x = 0f
                        path.moveTo(0f, baseY + (sin(phase) * amp).toFloat())
                        while (x <= size.width + 20f) {
                            val wy = baseY + (sin(x * freq + phase) * amp).toFloat()
                            path.lineTo(x, wy)
                            x += 16f
                        }
                        drawPath(
                            path = path,
                            color = lineC,
                            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                        )
                        baseY += gap
                        idx++
                    }
                }
            }
        }

        if (effectiveDim > 0.005f) {
            val scrimColor = when {
                wallpaper.id == "foto-galeria" || wallpaper.id.startsWith("arte-") -> {
                    pal.chatBg.copy(alpha = effectiveDim)
                }
                else -> pal.chatBg.copy(alpha = (effectiveDim * 0.7f).coerceIn(0f, 0.75f))
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
            )
        }
    }
}

@Composable
fun BubblePreviewSample(
    pal: BlueBlPalette,
    wallpaper: WallpaperOption,
    wallpaperDim: Float,
    customBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    val highContrast = LocalHighContrast.current
    val peerBorder = if (highContrast) {
        androidx.compose.foundation.BorderStroke(1.2.dp, pal.border)
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, pal.glassBorder)
    }
    val ownBorder = if (highContrast) {
        androidx.compose.foundation.BorderStroke(1.2.dp, pal.border)
    } else {
        androidx.compose.foundation.BorderStroke(0.75.dp, pal.glassBorder.copy(alpha = 0.7f))
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, pal.border.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(18.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ChatWallpaperLayer(
                wallpaper = wallpaper,
                dim = wallpaperDim,
                customBitmap = customBitmap,
                pal = pal,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 16.dp,
                        bottomEnd = 16.dp,
                        bottomStart = 16.dp
                    ),
                    color = if (highContrast) pal.peerBubbleBg else pal.glassSurface,
                    shadowElevation = 0.dp,
                    border = peerBorder,
                    modifier = Modifier.widthIn(max = 240.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = "Lucía",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Themes.avatarColorFor("lucia-sample", pal.isLight)
                        )
                        Text(
                            text = "¿Cómo se lee el texto así?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = pal.peerBubbleFg
                        )
                        Text(
                            text = "12:04",
                            style = MaterialTheme.typography.labelSmall,
                            color = pal.peerBubbleMeta,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    val ownShape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 4.dp,
                        bottomEnd = 16.dp,
                        bottomStart = 16.dp
                    )
                    Surface(
                        shape = ownShape,
                        color = Color.Transparent,
                        shadowElevation = 0.dp,
                        border = ownBorder,
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .clip(ownShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(pal.ownBubbleGradientStart, pal.ownBubbleGradientEnd)
                                )
                            )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = "¡Súper estético y claro!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = pal.ownBubbleFg
                            )
                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "12:05",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = pal.ownBubbleMeta
                                )
                                Icon(
                                    imageVector = Icons.Rounded.DoneAll,
                                    contentDescription = null,
                                    tint = pal.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
