package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ThemeCategory(val title: String) {
    Mensajeria("Mensajería · recomendados"),
    Base("Base y accesibilidad"),
    Terminales("Clásicos"),
    Juegos("Videojuegos"),
    Cine("Cine")
}

data class BlueBlPalette(
    val bg: Color,
    val fg: Color,
    val accent: Color,
    val accent2: Color,
    val dim: Color,
    val border: Color,
    val borderActive: Color,
    val inputBg: Color,
    val system: Color,
    val selfMsg: Color,
    val peerMsg: Color,
    val error: Color,
    val highlight: Color,
    val statusBg: Color,
    val isLight: Boolean,
    val headerBg: Color = statusBg,
    val headerFg: Color = fg,
    val headerSub: Color = dim,
    val chatBg: Color = bg,
    val ownBubbleBg: Color = highlight,
    val ownBubbleFg: Color = fg,
    val ownBubbleMeta: Color = dim,
    val peerBubbleBg: Color = inputBg,
    val peerBubbleFg: Color = fg,
    val peerBubbleMeta: Color = dim,
    val systemPillBg: Color = inputBg,
    val systemPillFg: Color = dim,
    val composerBg: Color = statusBg,
    val composerFieldBg: Color = inputBg,
    val onAccent: Color = bestOnGradient(accent, accent2),
    val accentGradientStart: Color = accent,
    val accentGradientEnd: Color = accent2,
    val ownBubbleGradientStart: Color = ownBubbleBg,
    val ownBubbleGradientEnd: Color = mixColor(ownBubbleBg, accent2, 0.14f),
    val headerGradientStart: Color = headerBg,
    val headerGradientEnd: Color = mixColor(headerBg, accent, if (isLight) 0.12f else 0.16f),
    val glassSurface: Color = if (isLight) Color.White.copy(alpha = 0.90f) else mixColor(peerBubbleBg, Color.White, 0.04f).copy(alpha = 0.84f),
    val glassBorder: Color = if (isLight) mixColor(border, accent, 0.25f).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.16f)
) {
    fun withHighContrast(): BlueBlPalette {
        return if (isLight) {
            val hcOwn = mixColor(accent, Color.White, 0.78f)
            val hcAccent = if (contrastRatio(Color.White, accent) >= 4.2f) accent else mixColor(accent, Color.Black, 0.28f)
            copy(
                bg = Color(0xFFFFFFFF),
                fg = Color(0xFF000000),
                accent = hcAccent,
                dim = Color(0xFF1F2937),
                border = Color(0xFF111827),
                borderActive = Color(0xFF000000),
                inputBg = Color(0xFFF3F4F6),
                headerBg = Color(0xFFFFFFFF),
                headerFg = Color(0xFF000000),
                headerSub = Color(0xFF111827),
                chatBg = Color(0xFFF5F5F5),
                ownBubbleBg = hcOwn,
                ownBubbleFg = Color(0xFF000000),
                ownBubbleMeta = Color(0xFF111827),
                peerBubbleBg = Color(0xFFFFFFFF),
                peerBubbleFg = Color(0xFF000000),
                peerBubbleMeta = Color(0xFF111827),
                systemPillBg = Color(0xFFE5E7EB),
                systemPillFg = Color(0xFF000000),
                composerBg = Color(0xFFFFFFFF),
                composerFieldBg = Color(0xFFFFFFFF),
                statusBg = Color(0xFFFFFFFF),
                onAccent = bestOnGradient(hcAccent, hcAccent),
                accentGradientStart = hcAccent,
                accentGradientEnd = hcAccent,
                ownBubbleGradientStart = hcOwn,
                ownBubbleGradientEnd = hcOwn,
                headerGradientStart = Color(0xFFFFFFFF),
                headerGradientEnd = Color(0xFFFFFFFF),
                glassSurface = Color(0xFFFFFFFF),
                glassBorder = Color(0xFF111827)
            )
        } else {
            val hcOwn = mixColor(accent, Color.Black, 0.65f)
            copy(
                bg = Color(0xFF000000),
                fg = Color(0xFFFFFFFF),
                dim = Color(0xFFE5E7EB),
                border = Color(0xFFFFFFFF),
                borderActive = accent,
                inputBg = Color(0xFF14181F),
                headerBg = Color(0xFF0A0D12),
                headerFg = Color(0xFFFFFFFF),
                headerSub = Color(0xFFE5E7EB),
                chatBg = Color(0xFF000000),
                ownBubbleBg = hcOwn,
                ownBubbleFg = Color(0xFFFFFFFF),
                ownBubbleMeta = Color(0xFFE5E7EB),
                peerBubbleBg = Color(0xFF161B22),
                peerBubbleFg = Color(0xFFFFFFFF),
                peerBubbleMeta = Color(0xFFE5E7EB),
                systemPillBg = Color(0xFF1F2937),
                systemPillFg = Color(0xFFFFFFFF),
                composerBg = Color(0xFF0A0D12),
                composerFieldBg = Color(0xFF161B22),
                statusBg = Color(0xFF0A0D12),
                onAccent = bestOnGradient(accent, accent),
                accentGradientStart = accent,
                accentGradientEnd = accent,
                ownBubbleGradientStart = hcOwn,
                ownBubbleGradientEnd = hcOwn,
                headerGradientStart = Color(0xFF0A0D12),
                headerGradientEnd = Color(0xFF0A0D12),
                glassSurface = Color(0xFF161B22),
                glassBorder = Color(0xFFFFFFFF)
            )
        }
    }
}

data class BlueBlThemeDef(
    val id: String,
    val name: String,
    val category: ThemeCategory,
    val palette: BlueBlPalette
)

private fun mixColor(a: Color, b: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * clamped,
        green = a.green + (b.green - a.green) * clamped,
        blue = a.blue + (b.blue - a.blue) * clamped,
        alpha = 1f
    )
}

private fun contrastRatio(fg: Color, bg: Color): Float {
    val l1 = fg.luminance()
    val l2 = bg.luminance()
    val bright = maxOf(l1, l2)
    val dark = minOf(l1, l2)
    return (bright + 0.05f) / (dark + 0.05f)
}

private fun bestOnGradient(c1: Color, c2: Color): Color {
    val whiteMin = minOf(contrastRatio(Color.White, c1), contrastRatio(Color.White, c2))
    val darkInk = Color(0xFF0D1418)
    val darkMin = minOf(contrastRatio(darkInk, c1), contrastRatio(darkInk, c2))
    return if (whiteMin >= 4.5f || whiteMin >= darkMin) Color.White else darkInk
}

object Themes {
    private val avatarColorsLight = listOf(
        Color(0xFF00897B),
        Color(0xFF1E88E5),
        Color(0xFF8E24AA),
        Color(0xFFE53935),
        Color(0xFFFB8C00),
        Color(0xFF43A047),
        Color(0xFF3949AB),
        Color(0xFFD81B60),
        Color(0xFF00ACC1),
        Color(0xFF6D4C41)
    )

    private val avatarColorsDark = listOf(
        Color(0xFF4DB6AC),
        Color(0xFF64B5F6),
        Color(0xFFBA68C8),
        Color(0xFFE57373),
        Color(0xFFFFB74D),
        Color(0xFF81C784),
        Color(0xFF7986CB),
        Color(0xFFF06292),
        Color(0xFF4DD0E1),
        Color(0xFFA1887F)
    )

    fun avatarColorFor(seed: String, isLightTheme: Boolean): Color {
        val list = if (isLightTheme) avatarColorsLight else avatarColorsDark
        val idx = abs(seed.lowercase().hashCode()) % list.size
        return list[idx]
    }

    fun avatarGradientFor(seed: String, isLightTheme: Boolean): Pair<Color, Color> {
        val list = if (isLightTheme) avatarColorsLight else avatarColorsDark
        val idx = abs(seed.lowercase().hashCode()) % list.size
        val c1 = list[idx]
        val c2 = list[(idx + 2) % list.size]
        return c1 to mixColor(c1, c2, 0.55f)
    }

    private fun mix(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>, t: Float): Color {
        fun m(x: Int, y: Int): Int = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
        return Color(m(a.first, b.first), m(a.second, b.second), m(a.third, b.third))
    }

    private fun rgb(c: Triple<Int, Int, Int>): Color = Color(c.first, c.second, c.third)

    private fun messengerTheme(
        id: String,
        name: String,
        bg: Color,
        headerBg: Color,
        headerGradEnd: Color,
        chatBg: Color,
        fg: Color,
        dim: Color,
        accent: Color,
        accent2: Color,
        ownBubbleBg: Color,
        ownBubbleGradEnd: Color,
        ownBubbleFg: Color,
        peerBubbleBg: Color,
        peerBubbleFg: Color,
        composerBg: Color,
        composerFieldBg: Color,
        border: Color,
        error: Color,
        isLight: Boolean
    ): BlueBlThemeDef {
        val metaOwn = mixColor(ownBubbleFg, ownBubbleBg, 0.42f)
        val metaPeer = mixColor(peerBubbleFg, peerBubbleBg, 0.45f)
        val sysBg = if (isLight) Color.White.copy(alpha = 0.88f) else mixColor(chatBg, fg, 0.14f).copy(alpha = 0.84f)
        val glass = if (isLight) Color.White.copy(alpha = 0.90f) else mixColor(peerBubbleBg, Color.White, 0.04f).copy(alpha = 0.84f)
        val glassBorder = if (isLight) mixColor(border, accent, 0.25f).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.16f)
        return BlueBlThemeDef(
            id = id,
            name = name,
            category = ThemeCategory.Mensajeria,
            palette = BlueBlPalette(
                bg = bg,
                fg = fg,
                accent = accent,
                accent2 = accent2,
                dim = dim,
                border = border,
                borderActive = accent,
                inputBg = composerFieldBg,
                system = dim,
                selfMsg = accent,
                peerMsg = peerBubbleFg,
                error = error,
                highlight = ownBubbleBg,
                statusBg = headerBg,
                isLight = isLight,
                headerBg = headerBg,
                headerFg = fg,
                headerSub = dim,
                chatBg = chatBg,
                ownBubbleBg = ownBubbleBg,
                ownBubbleFg = ownBubbleFg,
                ownBubbleMeta = metaOwn,
                peerBubbleBg = peerBubbleBg,
                peerBubbleFg = peerBubbleFg,
                peerBubbleMeta = metaPeer,
                systemPillBg = sysBg,
                systemPillFg = dim,
                composerBg = composerBg,
                composerFieldBg = composerFieldBg,
                onAccent = bestOnGradient(accent, accent2),
                accentGradientStart = accent,
                accentGradientEnd = accent2,
                ownBubbleGradientStart = ownBubbleBg,
                ownBubbleGradientEnd = ownBubbleGradEnd,
                headerGradientStart = headerBg,
                headerGradientEnd = headerGradEnd,
                glassSurface = glass,
                glassBorder = glassBorder
            )
        )
    }

    private fun theme(
        id: String,
        name: String,
        category: ThemeCategory,
        bg: Triple<Int, Int, Int>,
        fg: Triple<Int, Int, Int>,
        accent: Triple<Int, Int, Int>,
        accent2: Triple<Int, Int, Int>,
        error: Triple<Int, Int, Int>
    ): BlueBlThemeDef {
        val luminance = (0.299 * bg.first + 0.587 * bg.second + 0.114 * bg.third) / 255.0
        val isLight = luminance > 0.5
        val rawBg = rgb(bg)
        val fgC = rgb(fg)
        val accC = rgb(accent)
        val acc2C = rgb(accent2)
        val bgC = if (isLight) mixColor(rawBg, accC, 0.04f) else rawBg
        val dimC = mix(fg, bg, 0.44f)
        val borderC = mix(fg, bg, 0.72f)
        val ownBg = if (isLight) mixColor(accC, Color.White, 0.76f) else mixColor(accC, bgC, 0.68f)
        val ownEnd = if (isLight) mixColor(acc2C, Color.White, 0.70f) else mixColor(acc2C, bgC, 0.62f)
        val ownFgC = bestOnGradient(ownBg, ownEnd)
        val peerBg = if (isLight) Color.White else mixColor(bgC, fgC, 0.10f)
        val headerBgC = if (isLight) mixColor(bgC, Color.White, 0.45f) else mixColor(bgC, fgC, 0.06f)
        val headerEndC = mixColor(headerBgC, accC, if (isLight) 0.14f else 0.18f)
        val chatBgC = if (isLight) mixColor(bgC, Color(0xFFECE6DA), 0.16f) else bgC
        val fieldBg = if (isLight) Color.White else mixColor(bgC, fgC, 0.12f)
        val glass = if (isLight) Color.White.copy(alpha = 0.90f) else mixColor(peerBg, Color.White, 0.04f).copy(alpha = 0.84f)
        val glassBorder = if (isLight) mixColor(borderC, accC, 0.25f).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.16f)

        return BlueBlThemeDef(
            id = id,
            name = name,
            category = category,
            palette = BlueBlPalette(
                bg = bgC,
                fg = fgC,
                accent = accC,
                accent2 = acc2C,
                dim = dimC,
                border = borderC,
                borderActive = accC,
                inputBg = fieldBg,
                system = acc2C,
                selfMsg = accC,
                peerMsg = fgC,
                error = rgb(error),
                highlight = ownBg,
                statusBg = headerBgC,
                isLight = isLight,
                headerBg = headerBgC,
                headerFg = fgC,
                headerSub = dimC,
                chatBg = chatBgC,
                ownBubbleBg = ownBg,
                ownBubbleFg = ownFgC,
                ownBubbleMeta = mixColor(ownFgC, ownBg, 0.40f),
                peerBubbleBg = peerBg,
                peerBubbleFg = fgC,
                peerBubbleMeta = mixColor(fgC, peerBg, 0.45f),
                systemPillBg = if (isLight) Color.White.copy(alpha = 0.88f) else mixColor(bgC, fgC, 0.12f).copy(alpha = 0.86f),
                systemPillFg = dimC,
                composerBg = headerBgC,
                composerFieldBg = fieldBg,
                onAccent = bestOnGradient(accC, acc2C),
                accentGradientStart = accC,
                accentGradientEnd = acc2C,
                ownBubbleGradientStart = ownBg,
                ownBubbleGradientEnd = ownEnd,
                headerGradientStart = headerBgC,
                headerGradientEnd = headerEndC,
                glassSurface = glass,
                glassBorder = glassBorder
            )
        )
    }

    private fun t(r: Int, g: Int, b: Int) = Triple(r, g, b)

    val all: List<BlueBlThemeDef> = listOf(
        // ── Mensajería (recomendados) ───────────────────────────────────────
        messengerTheme(
            id = "whatsapp-dia",
            name = "WhatsApp Día",
            bg = Color(0xFFF0F7F4),
            headerBg = Color(0xFFF6FBF9),
            headerGradEnd = Color(0xFFDDF3EC),
            chatBg = Color(0xFFEFEAE2),
            fg = Color(0xFF111B21),
            dim = Color(0xFF54656F),
            accent = Color(0xFF007A65),
            accent2 = Color(0xFF05967B),
            ownBubbleBg = Color(0xFFD8FCE3),
            ownBubbleGradEnd = Color(0xFFB5F2D6),
            ownBubbleFg = Color(0xFF111B21),
            peerBubbleBg = Color(0xFFFFFFFF),
            peerBubbleFg = Color(0xFF111B21),
            composerBg = Color(0xFFEEF6F3),
            composerFieldBg = Color(0xFFFFFFFF),
            border = Color(0xFFCDE0D9),
            error = Color(0xFFD93025),
            isLight = true
        ),
        messengerTheme(
            id = "telegram-dia",
            name = "Telegram Día",
            bg = Color(0xFFEEF5FB),
            headerBg = Color(0xFFF4F9FE),
            headerGradEnd = Color(0xFFD9ECFC),
            chatBg = Color(0xFFE4EEF8),
            fg = Color(0xFF18222C),
            dim = Color(0xFF546778),
            accent = Color(0xFF0284C7),
            accent2 = Color(0xFF0D9488),
            ownBubbleBg = Color(0xFFD9EEFE),
            ownBubbleGradEnd = Color(0xFFC5F4E6),
            ownBubbleFg = Color(0xFF0F212E),
            peerBubbleBg = Color(0xFFFFFFFF),
            peerBubbleFg = Color(0xFF18222C),
            composerBg = Color(0xFFECF4FB),
            composerFieldBg = Color(0xFFFFFFFF),
            border = Color(0xFFC8DCEE),
            error = Color(0xFFD93025),
            isLight = true
        ),
        messengerTheme(
            id = "telegram-noche",
            name = "Telegram Noche",
            bg = Color(0xFF141E29),
            headerBg = Color(0xFF17222E),
            headerGradEnd = Color(0xFF1B3046),
            chatBg = Color(0xFF0E1621),
            fg = Color(0xFFF5F7FA),
            dim = Color(0xFF93A8BC),
            accent = Color(0xFF38BDF8),
            accent2 = Color(0xFF34D399),
            ownBubbleBg = Color(0xFF254C73),
            ownBubbleGradEnd = Color(0xFF1E5D6E),
            ownBubbleFg = Color(0xFFFFFFFF),
            peerBubbleBg = Color(0xFF1A2838),
            peerBubbleFg = Color(0xFFF5F7FA),
            composerBg = Color(0xFF16212E),
            composerFieldBg = Color(0xFF203042),
            border = Color(0xFF2A3D52),
            error = Color(0xFFFF6B6B),
            isLight = false
        ),
        messengerTheme(
            id = "whatsapp-noche",
            name = "WhatsApp Noche",
            bg = Color(0xFF101A20),
            headerBg = Color(0xFF1B2830),
            headerGradEnd = Color(0xFF173336),
            chatBg = Color(0xFF0B141A),
            fg = Color(0xFFE9EDEF),
            dim = Color(0xFF8FA1AC),
            accent = Color(0xFF10B981),
            accent2 = Color(0xFF38BDF8),
            ownBubbleBg = Color(0xFF065F46),
            ownBubbleGradEnd = Color(0xFF0F5A66),
            ownBubbleFg = Color(0xFFE9EDEF),
            peerBubbleBg = Color(0xFF1E2B33),
            peerBubbleFg = Color(0xFFE9EDEF),
            composerBg = Color(0xFF1B2830),
            composerFieldBg = Color(0xFF263640),
            border = Color(0xFF2B3D47),
            error = Color(0xFFFF6B6B),
            isLight = false
        ),
        messengerTheme(
            id = "menta-fresca",
            name = "Menta Fresca",
            bg = Color(0xFFEDF8F3),
            headerBg = Color(0xFFF4FBF8),
            headerGradEnd = Color(0xFFD4F1E5),
            chatBg = Color(0xFFE4F3EC),
            fg = Color(0xFF12231E),
            dim = Color(0xFF4B665D),
            accent = Color(0xFF0F766E),
            accent2 = Color(0xFF10B981),
            ownBubbleBg = Color(0xFFCCFBF1),
            ownBubbleGradEnd = Color(0xFFBAE6FD),
            ownBubbleFg = Color(0xFF0F241D),
            peerBubbleBg = Color(0xFFFFFFFF),
            peerBubbleFg = Color(0xFF12231E),
            composerBg = Color(0xFFEBF7F2),
            composerFieldBg = Color(0xFFFFFFFF),
            border = Color(0xFFC3DFD3),
            error = Color(0xFFC93B3B),
            isLight = true
        ),
        messengerTheme(
            id = "lavanda-suave",
            name = "Lavanda Suave",
            bg = Color(0xFFF5F1FD),
            headerBg = Color(0xFFF9F6FF),
            headerGradEnd = Color(0xFFE7DCFC),
            chatBg = Color(0xFFEEE8FA),
            fg = Color(0xFF1E1930),
            dim = Color(0xFF605878),
            accent = Color(0xFF6D28D9),
            accent2 = Color(0xFFDB2777),
            ownBubbleBg = Color(0xFFEDE9FE),
            ownBubbleGradEnd = Color(0xFFFCE7F3),
            ownBubbleFg = Color(0xFF1C1633),
            peerBubbleBg = Color(0xFFFFFFFF),
            peerBubbleFg = Color(0xFF1E1930),
            composerBg = Color(0xFFF2ECFC),
            composerFieldBg = Color(0xFFFFFFFF),
            border = Color(0xFFD8CDF0),
            error = Color(0xFFD63854),
            isLight = true
        ),
        messengerTheme(
            id = "atardecer-calido",
            name = "Atardecer Cálido",
            bg = Color(0xFFFDF3EC),
            headerBg = Color(0xFFFFF8F4),
            headerGradEnd = Color(0xFFFCE2D4),
            chatBg = Color(0xFFF7EBE2),
            fg = Color(0xFF261914),
            dim = Color(0xFF70584E),
            accent = Color(0xFFEA580C),
            accent2 = Color(0xFFE11D48),
            ownBubbleBg = Color(0xFFFFEDD5),
            ownBubbleGradEnd = Color(0xFFFFE4E6),
            ownBubbleFg = Color(0xFF2B1811),
            peerBubbleBg = Color(0xFFFFFFFF),
            peerBubbleFg = Color(0xFF261914),
            composerBg = Color(0xFFFBF0E7),
            composerFieldBg = Color(0xFFFFFFFF),
            border = Color(0xFFE8CFC2),
            error = Color(0xFFC93030),
            isLight = true
        ),
        messengerTheme(
            id = "oceano-profundo",
            name = "Océano Profundo",
            bg = Color(0xFF0A1724),
            headerBg = Color(0xFF0F2132),
            headerGradEnd = Color(0xFF132E47),
            chatBg = Color(0xFF08141F),
            fg = Color(0xFFEBF4FC),
            dim = Color(0xFF8AA7BF),
            accent = Color(0xFF38BDF8),
            accent2 = Color(0xFF2DD4BF),
            ownBubbleBg = Color(0xFF15456B),
            ownBubbleGradEnd = Color(0xFF115E59),
            ownBubbleFg = Color(0xFFF0F8FF),
            peerBubbleBg = Color(0xFF142738),
            peerBubbleFg = Color(0xFFEBF4FC),
            composerBg = Color(0xFF0F2132),
            composerFieldBg = Color(0xFF193146),
            border = Color(0xFF203A52),
            error = Color(0xFFF87171),
            isLight = false
        ),

        // ── Base y accesibilidad ────────────────────────────────────────────
        theme("oscuro", "Oscuro", ThemeCategory.Base, t(18, 20, 28), t(220, 224, 232), t(110, 168, 254), t(120, 200, 160), t(230, 90, 90)),
        theme("claro", "Claro", ThemeCategory.Base, t(246, 246, 242), t(36, 38, 44), t(30, 90, 200), t(20, 130, 90), t(190, 40, 40)),
        theme("contraste-alto", "Alto contraste", ThemeCategory.Base, t(0, 0, 0), t(255, 255, 255), t(255, 255, 0), t(0, 255, 255), t(255, 70, 70)),
        theme("contraste-alto-2", "Alto contraste (ámbar)", ThemeCategory.Base, t(0, 0, 0), t(255, 220, 150), t(255, 200, 60), t(120, 230, 255), t(255, 90, 90)),
        theme("neutro-suave", "Neutro suave", ThemeCategory.Base, t(32, 33, 36), t(210, 210, 208), t(150, 160, 175), t(140, 175, 155), t(220, 110, 110)),
        theme("monocromo", "Monocromo", ThemeCategory.Base, t(12, 12, 12), t(205, 205, 205), t(250, 250, 250), t(150, 150, 150), t(230, 230, 230)),

        // ── Terminales clásicos ─────────────────────────────────────────────
        theme("fosforo-verde", "Fósforo verde", ThemeCategory.Terminales, t(4, 20, 8), t(80, 255, 120), t(140, 255, 160), t(60, 220, 140), t(255, 90, 90)),
        theme("fosforo-ambar", "Fósforo ámbar", ThemeCategory.Terminales, t(24, 14, 4), t(255, 190, 80), t(255, 215, 120), t(255, 160, 60), t(255, 90, 90)),
        theme("solarized-dark", "Solarized Dark", ThemeCategory.Terminales, t(0, 43, 54), t(147, 161, 161), t(38, 139, 210), t(133, 153, 0), t(220, 50, 47)),
        theme("solarized-light", "Solarized Light", ThemeCategory.Terminales, t(253, 246, 227), t(72, 92, 99), t(38, 139, 210), t(133, 153, 0), t(220, 50, 47)),
        theme("dracula", "Dracula", ThemeCategory.Terminales, t(40, 42, 54), t(248, 248, 242), t(255, 121, 198), t(80, 250, 123), t(255, 85, 85)),
        theme("gruvbox", "Gruvbox", ThemeCategory.Terminales, t(40, 40, 40), t(235, 219, 178), t(250, 189, 47), t(142, 192, 124), t(251, 73, 52)),
        theme("nord", "Nord", ThemeCategory.Terminales, t(46, 52, 64), t(216, 222, 233), t(136, 192, 208), t(163, 190, 140), t(191, 97, 106)),
        theme("monokai", "Monokai", ThemeCategory.Terminales, t(39, 40, 34), t(248, 248, 242), t(166, 226, 46), t(174, 129, 255), t(249, 38, 114)),
        theme("tokyo-night", "Tokyo Night", ThemeCategory.Terminales, t(26, 27, 38), t(169, 177, 214), t(130, 170, 255), t(158, 206, 106), t(247, 118, 142)),
        theme("catppuccin", "Catppuccin", ThemeCategory.Terminales, t(30, 30, 46), t(205, 214, 244), t(203, 166, 247), t(166, 227, 161), t(243, 139, 168)),
        theme("one-dark", "One Dark", ThemeCategory.Terminales, t(40, 44, 52), t(171, 178, 191), t(97, 175, 239), t(152, 195, 121), t(224, 108, 117)),
        theme("rose-pine", "Rosé Pine", ThemeCategory.Terminales, t(25, 23, 36), t(224, 220, 233), t(235, 188, 215), t(156, 231, 191), t(235, 111, 133)),

        // ── Videojuegos ─────────────────────────────────────────────────────
        theme("night-city", "Night City", ThemeCategory.Juegos, t(8, 6, 20), t(230, 225, 255), t(255, 40, 180), t(60, 230, 255), t(255, 70, 70)),
        theme("pip-boy-verde", "Pip-Boy verde", ThemeCategory.Juegos, t(6, 16, 6), t(120, 255, 130), t(170, 255, 175), t(90, 230, 120), t(255, 100, 100)),
        theme("pip-boy-ambar", "Pip-Boy ámbar", ThemeCategory.Juegos, t(20, 13, 4), t(255, 195, 90), t(255, 220, 140), t(255, 170, 70), t(255, 95, 95)),
        theme("mojave", "Mojave NCR", ThemeCategory.Juegos, t(26, 20, 12), t(235, 215, 175), t(90, 160, 220), t(200, 70, 60), t(240, 90, 80)),
        theme("black-mesa", "Black Mesa", ThemeCategory.Juegos, t(14, 16, 18), t(210, 215, 220), t(240, 130, 40), t(90, 190, 220), t(235, 80, 70)),
        theme("aperture", "Aperture", ThemeCategory.Juegos, t(242, 242, 242), t(40, 40, 40), t(0, 120, 210), t(240, 150, 30), t(200, 40, 40)),
        theme("normandia", "Normandía", ThemeCategory.Juegos, t(24, 26, 22), t(205, 210, 190), t(180, 175, 140), t(120, 150, 100), t(210, 90, 70)),
        theme("rapture", "Rapture", ThemeCategory.Juegos, t(6, 14, 22), t(200, 225, 235), t(60, 190, 210), t(240, 180, 90), t(235, 90, 80)),
        theme("yharnam", "Yharnam", ThemeCategory.Juegos, t(16, 12, 14), t(215, 205, 200), t(170, 60, 70), t(140, 130, 160), t(220, 70, 70)),
        theme("hyrule", "Hyrule", ThemeCategory.Juegos, t(10, 18, 14), t(225, 235, 215), t(70, 190, 120), t(230, 190, 80), t(225, 85, 75)),
        theme("midgar", "Taller de Midgar", ThemeCategory.Juegos, t(18, 16, 20), t(220, 215, 210), t(240, 170, 60), t(90, 170, 230), t(235, 90, 80)),
        theme("deus-ex", "DEUS EX", ThemeCategory.Juegos, t(10, 12, 14), t(200, 215, 205), t(60, 200, 150), t(240, 190, 70), t(235, 85, 75)),
        theme("neon-drift", "Neon Drift", ThemeCategory.Juegos, t(12, 8, 24), t(235, 230, 255), t(255, 90, 220), t(80, 255, 220), t(255, 80, 110)),
        theme("island", "Island", ThemeCategory.Juegos, t(10, 22, 30), t(230, 240, 240), t(80, 210, 200), t(250, 200, 110), t(240, 100, 90)),

        // ── Cine ────────────────────────────────────────────────────────────
        theme("lluvia-neon", "Lluvia de neón", ThemeCategory.Cine, t(10, 8, 18), t(225, 222, 240), t(255, 60, 150), t(70, 200, 255), t(255, 80, 90)),
        theme("lluvia-fria", "Lluvia fría", ThemeCategory.Cine, t(14, 18, 24), t(205, 215, 225), t(110, 160, 210), t(150, 190, 180), t(220, 95, 95)),
        theme("spinner", "Spinner", ThemeCategory.Cine, t(6, 8, 12), t(200, 215, 230), t(90, 220, 255), t(255, 170, 60), t(255, 90, 90)),
        theme("matrix", "Matrix", ThemeCategory.Cine, t(2, 8, 4), t(120, 255, 140), t(60, 255, 110), t(170, 255, 180), t(255, 100, 100)),
        theme("la-red", "La Red", ThemeCategory.Cine, t(16, 6, 6), t(235, 215, 210), t(220, 70, 60), t(250, 150, 90), t(255, 90, 80)),
        theme("nostromo", "Nostromo", ThemeCategory.Cine, t(10, 12, 10), t(205, 215, 195), t(235, 190, 80), t(120, 200, 140), t(235, 100, 80)),
        theme("neo-tokyo", "Neo-Tokyo", ThemeCategory.Cine, t(14, 6, 16), t(235, 225, 240), t(255, 95, 180), t(95, 210, 255), t(255, 85, 100)),
        theme("shell", "Shell", ThemeCategory.Cine, t(8, 12, 16), t(215, 225, 235), t(70, 190, 220), t(160, 210, 160), t(230, 100, 90)),
        theme("arrakis", "Arrakis", ThemeCategory.Cine, t(24, 18, 10), t(240, 225, 195), t(235, 170, 80), t(120, 190, 160), t(230, 90, 70)),
        theme("nebulosa", "Nebulosa", ThemeCategory.Cine, t(12, 10, 24), t(225, 222, 245), t(150, 120, 255), t(90, 210, 235), t(240, 95, 130))
    )

    fun default(): BlueBlThemeDef = all.first()

    fun find(id: String): BlueBlThemeDef? = all.firstOrNull { it.id == id.trim().lowercase() }

    fun indexOf(id: String): Int = all.indexOfFirst { it.id == id.trim().lowercase() }.takeIf { it >= 0 } ?: 0
}
