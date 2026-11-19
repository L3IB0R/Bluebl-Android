package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.example.crypto.Identity
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class AppConfig(
    val nick: String = defaultNick(),
    val theme: String = "whatsapp-dia",
    val density: String = "comodo",
    val wallpaperId: String = "patron-doodle",
    val wallpaperDim: Float = 0.08f,
    val fontScale: String = "m",
    val fontChoice: String = "sans",
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false
) {
    companion object {
        fun defaultNick(): String {
            val model = Build.MODEL?.trim()?.replace(" ", "-")?.take(14)
            return if (!model.isNullOrBlank()) model.lowercase() else "bluebl"
        }
    }
}

class ConfigRepository(private val filesDir: File) {
    constructor(context: Context) : this(context.filesDir)

    private val configFile = File(filesDir, "config")
    private val identityFile = File(filesDir, "identity")
    private val customWallpaperFile = File(filesDir, "custom_wallpaper.jpg")

    fun loadOrGenerateIdentity(): Identity = Identity.loadOrGenerate(identityFile)

    fun loadConfig(): AppConfig {
        var cfg = AppConfig()
        if (!configFile.exists()) return cfg
        val text = runCatching { configFile.readText() }.getOrNull() ?: return cfg
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#')) continue
            val idx = line.indexOf('=')
            if (idx < 0) continue
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            when (key) {
                "nick" -> if (value.isNotEmpty()) cfg = cfg.copy(nick = value)
                "theme" -> if (value.isNotEmpty()) cfg = cfg.copy(theme = value)
                "density" -> if (value.isNotEmpty()) cfg = cfg.copy(density = value)
                "wallpaper_id" -> if (value.isNotEmpty()) cfg = cfg.copy(wallpaperId = value)
                "wallpaper_dim" -> value.toFloatOrNull()?.let {
                    cfg = cfg.copy(wallpaperDim = it.coerceIn(0f, 0.75f))
                }
                "font_scale" -> if (value.isNotEmpty()) cfg = cfg.copy(fontScale = value)
                "font_choice" -> if (value.isNotEmpty()) cfg = cfg.copy(fontChoice = value)
                "high_contrast" -> cfg = cfg.copy(highContrast = value.equals("true", ignoreCase = true))
                "reduce_motion" -> cfg = cfg.copy(reduceMotion = value.equals("true", ignoreCase = true))
            }
        }
        return cfg
    }

    fun saveConfig(config: AppConfig) {
        runCatching {
            filesDir.mkdirs()
            val body = buildString {
                appendLine("# BlueBl — configuración")
                appendLine("nick=${config.nick}")
                appendLine("theme=${config.theme}")
                appendLine("density=${config.density}")
                appendLine("wallpaper_id=${config.wallpaperId}")
                appendLine("wallpaper_dim=${config.wallpaperDim}")
                appendLine("font_scale=${config.fontScale}")
                appendLine("font_choice=${config.fontChoice}")
                appendLine("high_contrast=${config.highContrast}")
                appendLine("reduce_motion=${config.reduceMotion}")
            }
            configFile.writeText(body)
        }
    }

    fun saveCustomWallpaperFromUri(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            filesDir.mkdirs()
            val maxDim = 1440
            val finalBitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val largest = max(info.size.width, info.size.height)
                    if (largest > maxDim && largest > 0) {
                        val scale = maxDim.toFloat() / largest.toFloat()
                        val w = (info.size.width * scale).toInt().coerceAtLeast(1)
                        val h = (info.size.height * scale).toInt().coerceAtLeast(1)
                        decoder.setTargetSize(w, h)
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val opened = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                    true
                } ?: false
                if (!opened || bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                var sampleSize = 1
                val largest = max(bounds.outWidth, bounds.outHeight)
                while (largest / sampleSize > maxDim * 2) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return null

                if (max(decoded.width, decoded.height) > maxDim) {
                    val scale = maxDim.toFloat() / max(decoded.width, decoded.height).toFloat()
                    val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
                    val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                        if (it !== decoded) decoded.recycle()
                    }
                } else {
                    decoded
                }
            }

            FileOutputStream(customWallpaperFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            finalBitmap
        }.getOrNull()
    }

    fun loadCustomWallpaperBitmap(): Bitmap? {
        if (!customWallpaperFile.exists()) return null
        return runCatching {
            BitmapFactory.decodeFile(customWallpaperFile.absolutePath)
        }.getOrNull()
    }

    fun deleteCustomWallpaper() {
        runCatching {
            if (customWallpaperFile.exists()) {
                customWallpaperFile.delete()
            }
        }
    }
}
