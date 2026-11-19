package com.example.proto

import java.io.ByteArrayOutputStream

object TlvType {
    const val NICKNAME = 0x01
    const val NOISE_PUBLIC_KEY = 0x02
    const val SIGNING_PUBLIC_KEY = 0x03
    const val CAPABILITIES = 0x05
}

data class UnknownTlv(val tlvType: Int, val value: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UnknownTlv) return false
        return tlvType == other.tlvType && value.contentEquals(other.value)
    }

    override fun hashCode(): Int = 31 * tlvType + value.contentHashCode()
}

data class Capabilities(val raw: Long) {
    fun contains(cap: Long): Boolean = (raw and cap) == cap

    fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        var rem = raw
        while (true) {
            out.write((rem and 0xFF).toInt())
            rem = rem ushr 8
            if (rem == 0L) break
        }
        return out.toByteArray()
    }

    companion object {
        fun decode(bytes: ByteArray): Capabilities {
            var raw = 0L
            val take = minOf(bytes.size, 8)
            for (i in 0 until take) {
                raw = raw or ((bytes[i].toLong() and 0xFF) shl (8 * i))
            }
            return Capabilities(raw)
        }
    }
}

data class Announcement(
    val nickname: String,
    val noisePublic: ByteArray,
    val signingPublic: ByteArray,
    val capabilities: Capabilities? = null,
    val unknown: List<UnknownTlv> = emptyList()
) {
    fun encode(): ByteArray {
        val nickBytes = nickname.toByteArray(Charsets.UTF_8)
        if (nickBytes.size > 255 || unknown.any { it.value.size > 255 }) {
            throw ProtoException.PayloadTooLarge()
        }
        val out = ByteArrayOutputStream()
        fun put(t: Int, v: ByteArray) {
            out.write(t and 0xFF)
            out.write(v.size and 0xFF)
            out.write(v)
        }
        put(TlvType.NICKNAME, nickBytes)
        put(TlvType.NOISE_PUBLIC_KEY, noisePublic)
        put(TlvType.SIGNING_PUBLIC_KEY, signingPublic)
        capabilities?.let { put(TlvType.CAPABILITIES, it.encode()) }
        for (u in unknown) {
            put(u.tlvType, u.value)
        }
        return out.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Announcement) return false
        return nickname == other.nickname &&
            noisePublic.contentEquals(other.noisePublic) &&
            signingPublic.contentEquals(other.signingPublic) &&
            capabilities == other.capabilities &&
            unknown == other.unknown
    }

    override fun hashCode(): Int {
        var result = nickname.hashCode()
        result = 31 * result + noisePublic.contentHashCode()
        result = 31 * result + signingPublic.contentHashCode()
        result = 31 * result + (capabilities?.hashCode() ?: 0)
        result = 31 * result + unknown.hashCode()
        return result
    }

    companion object {
        fun decode(data: ByteArray): Announcement {
            var pos = 0
            var nickname: String? = null
            var noisePublic: ByteArray? = null
            var signingPublic: ByteArray? = null
            var capabilities: Capabilities? = null
            val unknown = mutableListOf<UnknownTlv>()

            while (pos + 2 <= data.size) {
                val t = data[pos].toInt() and 0xFF
                val len = data[pos + 1].toInt() and 0xFF
                pos += 2
                if (pos + len > data.size) throw ProtoException.Truncated()
                val value = data.copyOfRange(pos, pos + len)
                pos += len
                when (t) {
                    TlvType.NICKNAME -> {
                        nickname = decodeStrictUtf8(value)
                    }
                    TlvType.NOISE_PUBLIC_KEY -> {
                        if (value.size != 32) throw ProtoException.Malformed()
                        noisePublic = value
                    }
                    TlvType.SIGNING_PUBLIC_KEY -> {
                        if (value.size != 32) throw ProtoException.Malformed()
                        signingPublic = value
                    }
                    TlvType.CAPABILITIES -> {
                        capabilities = Capabilities.decode(value)
                    }
                    else -> unknown.add(UnknownTlv(t, value))
                }
            }
            return Announcement(
                nickname = nickname ?: throw ProtoException.Malformed(),
                noisePublic = noisePublic ?: throw ProtoException.Malformed(),
                signingPublic = signingPublic ?: throw ProtoException.Malformed(),
                capabilities = capabilities,
                unknown = unknown
            )
        }
    }
}

internal fun decodeStrictUtf8(bytes: ByteArray): String {
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
    return try {
        decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) {
        throw ProtoException.Malformed()
    }
}
