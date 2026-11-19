package com.example.data

const val GENERAL_CHANNEL = "#general"

enum class Density(val id: String, val displayName: String) {
    Compacto("compacto", "Compacto"),
    Comodo("comodo", "Cómodo"),
    Grande("grande", "Amplio");

    companion object {
        fun parse(s: String): Density? {
            return when (s.trim().lowercase()) {
                "compacto", "compact" -> Compacto
                "comodo", "cómodo", "comfortable" -> Comodo
                "grande", "amplio", "large" -> Grande
                else -> null
            }
        }
    }
}

enum class FontScaleOption(
    val id: String,
    val shortLabel: String,
    val displayName: String,
    val scaleFactor: Float
) {
    Small("s", "S", "Pequeño", 0.88f),
    Medium("m", "M", "Normal", 1.0f),
    Large("l", "L", "Grande", 1.15f),
    ExtraLarge("xl", "XL", "Muy grande", 1.32f);

    companion object {
        fun parse(id: String): FontScaleOption {
            return entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) } ?: Medium
        }
    }
}

enum class FontChoice(
    val id: String,
    val displayName: String,
    val description: String
) {
    Sans("sans", "Sans moderna", "Clara, limpia y equilibrada para uso diario"),
    Rounded("rounded", "Redondeada", "Trazos suaves y cercanos tipo mensajería"),
    Dyslexia("dyslexia", "Lectura fácil", "Mayor apertura y ritmo pensada para reducir fatiga visual");

    companion object {
        fun parse(id: String): FontChoice {
            return entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) } ?: Sans
        }
    }
}

enum class WallpaperCategory(val title: String) {
    Patrones("Patrones"),
    Degradados("Degradados"),
    Artisticos("Ilustraciones"),
    Solidos("Minimalista"),
    Galeria("Tu galería")
}

data class WallpaperOption(
    val id: String,
    val name: String,
    val category: WallpaperCategory
)

object Wallpapers {
    val all: List<WallpaperOption> = listOf(
        WallpaperOption("patron-doodle", "Mensajería clásico", WallpaperCategory.Patrones),
        WallpaperOption("patron-burbujas", "Burbujas suaves", WallpaperCategory.Patrones),
        WallpaperOption("patron-ondas", "Ondas cercanas", WallpaperCategory.Patrones),
        WallpaperOption("degradado-menta", "Brisa menta", WallpaperCategory.Degradados),
        WallpaperOption("degradado-cielo", "Cielo claro", WallpaperCategory.Degradados),
        WallpaperOption("degradado-lavanda", "Lavanda suave", WallpaperCategory.Degradados),
        WallpaperOption("degradado-atardecer", "Atardecer cálido", WallpaperCategory.Degradados),
        WallpaperOption("degradado-noche", "Noche profunda", WallpaperCategory.Degradados),
        WallpaperOption("arte-botanico", "Hojas suaves", WallpaperCategory.Artisticos),
        WallpaperOption("arte-aurora", "Seda pastel", WallpaperCategory.Artisticos),
        WallpaperOption("solido-tema", "Fondo liso del tema", WallpaperCategory.Solidos),
        WallpaperOption("foto-galeria", "Foto de tu galería", WallpaperCategory.Galeria)
    )

    fun default(): WallpaperOption = all.first()

    fun find(id: String): WallpaperOption =
        all.firstOrNull { it.id == id.trim().lowercase() } ?: default()
}

enum class Panel(val title: String) {
    Pares("Personas cerca"),
    Chat("Chat")
}

enum class LineKind {
    Own,
    Peer,
    System,
    Error,
    Action
}

data class ChatLine(
    val id: Long = nextLineId(),
    val time: String,
    val kind: LineKind,
    val sender: String,
    val text: String,
    val senderId: String = ""
) {
    companion object {
        private var seq = 0L

        @Synchronized
        fun nextLineId(): Long = ++seq
    }
}

data class Peer(
    val id: String,
    val nickname: String,
    val noisePublic: String,
    val signingPublic: String,
    val lastSeenMs: Long = System.currentTimeMillis()
)

data class RadioCheck(
    val name: String,
    val ok: Boolean,
    val detail: String
)

sealed interface MeshEvent {
    data class PeerJoined(
        val id: String,
        val nickname: String,
        val noisePublic: String,
        val signingPublic: String
    ) : MeshEvent

    data class PeerLeft(val id: String) : MeshEvent

    data class Message(
        val from: String,
        val nick: String?,
        val channel: String?,
        val text: String
    ) : MeshEvent

    data class Status(val text: String) : MeshEvent
}
