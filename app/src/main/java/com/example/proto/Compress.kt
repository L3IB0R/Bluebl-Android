package com.example.proto

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

object Compress {
    const val COMPRESSION_THRESHOLD = 100
    const val MAX_DEFLATE_RATIO = 1032L

    fun shouldCompress(data: ByteArray): Boolean {
        if (data.size < COMPRESSION_THRESHOLD) return false
        val seen = BooleanArray(256)
        var unique = 0
        for (b in data) {
            val idx = b.toInt() and 0xFF
            if (!seen[idx]) {
                seen[idx] = true
                unique++
            }
        }
        val denom = minOf(data.size, 256).toDouble()
        return (unique.toDouble() / denom) < 0.9
    }

    fun compress(data: ByteArray): ByteArray? {
        if (data.size < COMPRESSION_THRESHOLD) return null
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        return try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(data.size)
            val buf = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buf)
                if (count == 0 && deflater.needsInput()) break
                out.write(buf, 0, count)
            }
            val result = out.toByteArray()
            if (result.isNotEmpty() && result.size < data.size) result else null
        } finally {
            deflater.end()
        }
    }

    fun decompress(compressed: ByteArray, originalSize: Int): ByteArray {
        if (compressed.isEmpty()) throw ProtoException.BadCompression()
        if (originalSize.toLong() > compressed.size.toLong() * MAX_DEFLATE_RATIO) {
            throw ProtoException.CompressionBomb()
        }
        val inflater = Inflater(true)
        return try {
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream(originalSize)
            val buf = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    if (out.size() + count > originalSize) {
                        throw ProtoException.BadCompression()
                    }
                    out.write(buf, 0, count)
                }
            }
            if (!inflater.finished()) {
                throw ProtoException.BadCompression()
            }
            val result = out.toByteArray()
            if (result.size != originalSize) {
                throw ProtoException.BadCompression()
            }
            result
        } catch (_: DataFormatException) {
            throw ProtoException.BadCompression()
        } finally {
            inflater.end()
        }
    }
}
