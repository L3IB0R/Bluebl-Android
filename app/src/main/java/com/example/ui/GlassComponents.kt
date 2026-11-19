package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalBlueBlPalette
import com.example.ui.theme.LocalHighContrast

@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val pal = LocalBlueBlPalette.current
    val highContrast = LocalHighContrast.current
    val fgColor = if (enabled) pal.onAccent else pal.dim

    val brush = when {
        !enabled -> Brush.horizontalGradient(
            listOf(pal.border.copy(alpha = 0.35f), pal.border.copy(alpha = 0.35f))
        )
        highContrast -> Brush.horizontalGradient(listOf(pal.accent, pal.accent))
        else -> Brush.horizontalGradient(listOf(pal.accentGradientStart, pal.accentGradientEnd))
    }

    val borderStroke = when {
        !enabled -> BorderStroke(1.dp, pal.border.copy(alpha = 0.5f))
        highContrast -> BorderStroke(1.dp, pal.border)
        pal.isLight -> BorderStroke(0.75.dp, Color.White.copy(alpha = 0.35f))
        else -> BorderStroke(0.75.dp, Color.White.copy(alpha = 0.18f))
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(brush)
            .border(borderStroke, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides fgColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    highlighted: Boolean = false,
    content: @Composable () -> Unit
) {
    val pal = LocalBlueBlPalette.current
    val highContrast = LocalHighContrast.current

    val container = when {
        highContrast -> pal.peerBubbleBg
        else -> pal.glassSurface
    }
    val border = when {
        highContrast -> BorderStroke(1.2.dp, pal.border)
        highlighted -> BorderStroke(1.2.dp, pal.accent.copy(alpha = 0.55f))
        else -> BorderStroke(1.dp, pal.glassBorder)
    }

    Surface(
        shape = shape,
        color = container,
        border = border,
        shadowElevation = 0.dp,
        modifier = modifier,
        content = content
    )
}
