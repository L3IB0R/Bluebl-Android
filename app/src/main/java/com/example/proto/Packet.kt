package com.example.proto

import com.example.crypto.Identity
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.ByteArrayOutputStream

object MsgType {
    const val ANNOUNCE = 0x01
    const val MESSAGE = 0x02
    const val LEAVE = 0x03
    const val NOISE_HANDSHAKE = 0x10
    const val NOISE_ENCRYPTED = 0x11
    const val FRAGMENT = 0x20
    const val REQUEST_SYNC = 0x21
    const val FILE_TRANSFER = 0x22
    const val VOICE_FRAME = 0x29
}

object PacketFlags {
    const val HAS_RECIPIENT = 0x01
    const val HAS_SIGNATURE = 0x02
    const val IS_COMPRESSED = 0x04
    const val HAS_ROUTE = 0x08
}

data class WirePayload(
    val bytes: ByteArray,
    val compressed: Boolean,
    val forPayload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WirePayload) return false
        return compressed == other.compressed &&
            bytes.contentEquals(other.bytes) &&
            forPayload.contentEquals(other.forPayload)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + compressed.hashCode()
        result = 31 * result + forPayload.contentHashCode()
        return result
    }
}

data class Packet(
    var version: Int = 1,
    var msgType: Int,
    var ttl: Int = MESSAGE_TTL_HOPS,
    var timestamp: Long = System.currentTimeMillis(),
    var sender: ByteArray,
    var recipient: ByteArray? = null,
    var route: List<ByteArray>? = null,
    var payload: ByteArray,
    var signature: ByteArray? = null,
    var wire: WirePayload? = null
) {
    fun isBroadcast(): Boolean {
        val r = recipient ?: return true
        return r.contentEquals(BROADCAST)
    }

    fun encode(pad: Boolean): ByteArray {
        if (payload.size > Limits.MAX_PAYLOAD_LENGTH) {
            throw ProtoException.PayloadTooLarge()
        }

        var encodedPayload = payload
        var originalSize: Int? = null
        var compressed = false

        val w = wire
        if (w != null && w.forPayload.contentEquals(payload)) {
            if (w.compressed) {
                encodedPayload = w.bytes
                originalSize = payload.size
                compressed = true
            }
        } else if (Compress.shouldCompress(payload)) {
            val c = Compress.compress(payload)
            if (c != null) {
                originalSize = payload.size
                encodedPayload = c
                compressed = true
            }
        }

        var flags = 0
        if (recipient != null) flags = flags or PacketFlags.HAS_RECIPIENT
        if (signature != null) flags = flags or PacketFlags.HAS_SIGNATURE
        if (compressed) flags = flags or PacketFlags.IS_COMPRESSED

        val activeRoute = if (version >= 2) route?.takeIf { it.isNotEmpty() } else null
        if (activeRoute != null) flags = flags or PacketFlags.HAS_ROUTE

        val sizeField = if (compressed) (if (version >= 2) 4 else 2) else 0
        val payloadLen = encodedPayload.size + sizeField

        val out = ByteArrayOutputStream(64 + payloadLen + SIGNATURE_SIZE)

        // Header
        out.write(version and 0xFF)
        out.write(msgType and 0xFF)
        out.write(ttl and 0xFF)
        writeLongBE(out, timestamp)
        out.write(flags and 0xFF)

        if (version >= 2) {
            writeIntBE(out, payloadLen)
        } else {
            if (payloadLen > 0xFFFF || (originalSize ?: 0) > 0xFFFF) {
                throw ProtoException.PayloadTooLarge()
            }
            writeShortBE(out, payloadLen)
        }

        // Sender & Recipient
        require(sender.size == SENDER_ID_SIZE)
        out.write(sender)
        recipient?.let {
            require(it.size == RECIPIENT_ID_SIZE)
            out.write(it)
        }

        // Route (v2+)
        if (activeRoute != null) {
            val count = minOf(activeRoute.size, 255)
            out.write(count)
            for (i in 0 until count) {
                out.write(activeRoute[i])
            }
        }

        // Payload (with original size prefix if compressed)
        if (originalSize != null) {
            if (version >= 2) {
                writeIntBE(out, originalSize)
            } else {
                if (originalSize > 0xFFFF) throw ProtoException.PayloadTooLarge()
                writeShortBE(out, originalSize)
            }
        }
        out.write(encodedPayload)

        // Signature
        signature?.let {
            require(it.size == SIGNATURE_SIZE)
            out.write(it)
        }

        val raw = out.toByteArray()
        return if (pad) {
            val target = Padding.optimalBlockSize(raw.size)
            Padding.pad(raw, target)
        } else {
            raw
        }
    }

    fun forSigning(): ByteArray {
        val clone = copy(
            ttl = SYNC_TTL_HOPS,
            signature = null,
            sender = sender.copyOf(),
            recipient = recipient?.copyOf(),
            route = route?.map { it.copyOf() },
            payload = payload.copyOf()
        )
        return clone.encode(true)
    }

    fun sign(signingKey: Ed25519PrivateKeyParameters) {
        val preimage = forSigning()
        val signer = Ed25519Signer()
        signer.init(true, signingKey)
        signer.update(preimage, 0, preimage.size)
        signature = signer.generateSignature()
    }

    fun sign(identity: Identity) {
        sign(identity.signingPrivateParams())
    }

    fun verify(publicKey: Ed25519PublicKeyParameters): Boolean {
        val sig = signature ?: throw ProtoException.Malformed()
        if (sig.size != SIGNATURE_SIZE) return false
        val preimage = forSigning()
        val verifier = Ed25519Signer()
        verifier.init(false, publicKey)
        verifier.update(preimage, 0, preimage.size)
        return verifier.verifySignature(sig)
    }

    fun verify(publicKeyBytes: ByteArray): Boolean {
        if (publicKeyBytes.size != 32) return false
        return verify(Ed25519PublicKeyParameters(publicKeyBytes, 0))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Packet) return false
        return version == other.version &&
            msgType == other.msgType &&
            ttl == other.ttl &&
            timestamp == other.timestamp &&
            sender.contentEquals(other.sender) &&
            ((recipient == null && other.recipient == null) ||
                (recipient != null && other.recipient != null && recipient.contentEquals(other.recipient))) &&
            payload.contentEquals(other.payload) &&
            ((signature == null && other.signature == null) ||
                (signature != null && other.signature != null && signature.contentEquals(other.signature)))
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + msgType
        result = 31 * result + ttl
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + sender.contentHashCode()
        result = 31 * result + (recipient?.contentHashCode() ?: 0)
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (signature?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        const val HEADER_SIZE_V1 = 14
        const val HEADER_SIZE_V2 = 16
        const val SENDER_ID_SIZE = 8
        const val RECIPIENT_ID_SIZE = 8
        const val SIGNATURE_SIZE = 64
        const val MESSAGE_TTL_HOPS = 7
        const val SYNC_TTL_HOPS = 0

        val BROADCAST = ByteArray(8) { 0xFF.toByte() }

        fun decode(data: ByteArray): Packet {
            try {
                return decodeCore(data)
            } catch (_: ProtoException) {
                val unpadded = Padding.unpad(data)
                if (unpadded.contentEquals(data)) {
                    throw ProtoException.Truncated()
                }
                return decodeCore(unpadded)
            }
        }

        private fun decodeCore(raw: ByteArray): Packet {
            if (raw.size < HEADER_SIZE_V1 + SENDER_ID_SIZE) {
                throw ProtoException.Truncated()
            }
            val version = raw[0].toInt() and 0xFF
            if (version != 1 && version != 2) {
                throw ProtoException.BadVersion(version)
            }
            val headerSize = if (version >= 2) HEADER_SIZE_V2 else HEADER_SIZE_V1
            if (raw.size < headerSize + SENDER_ID_SIZE) {
                throw ProtoException.Truncated()
            }
            val msgType = raw[1].toInt() and 0xFF
            val ttl = raw[2].toInt() and 0xFF
            val timestamp = readLongBE(raw, 3)
            val flag = raw[11].toInt() and 0xFF
            val hasRecipient = (flag and PacketFlags.HAS_RECIPIENT) != 0
            val hasSignature = (flag and PacketFlags.HAS_SIGNATURE) != 0
            val isCompressed = (flag and PacketFlags.IS_COMPRESSED) != 0
            val hasRoute = version >= 2 && (flag and PacketFlags.HAS_ROUTE) != 0

            val payloadLength = if (version >= 2) {
                readUIntBE(raw, 12).toInt()
            } else {
                readUShortBE(raw, 12)
            }
            if (payloadLength < 0 || payloadLength > Limits.MAX_PAYLOAD_LENGTH) {
                throw ProtoException.PayloadTooLarge()
            }
            val maxPayload = Limits.maxPayloadBytes(msgType)

            var expected = headerSize + SENDER_ID_SIZE + payloadLength
            if (hasRecipient) expected += RECIPIENT_ID_SIZE

            var routeCount = 0
            if (hasRoute) {
                var offset = headerSize + SENDER_ID_SIZE
                if (hasRecipient) offset += RECIPIENT_ID_SIZE
                if (raw.size > offset) {
                    routeCount = raw[offset].toInt() and 0xFF
                }
                expected += 1 + routeCount * SENDER_ID_SIZE
            }
            if (hasSignature) expected += SIGNATURE_SIZE
            if (raw.size < expected) throw ProtoException.Truncated()

            var pos = headerSize
            val sender = raw.copyOfRange(pos, pos + SENDER_ID_SIZE)
            pos += SENDER_ID_SIZE

            val recipient = if (hasRecipient) {
                val r = raw.copyOfRange(pos, pos + RECIPIENT_ID_SIZE)
                pos += RECIPIENT_ID_SIZE
                r
            } else null

            val route = if (hasRoute) {
                val count = raw[pos].toInt() and 0xFF
                pos += 1
                if (count == 0) {
                    null
                } else {
                    val hops = ArrayList<ByteArray>(count)
                    repeat(count) {
                        hops.add(raw.copyOfRange(pos, pos + SENDER_ID_SIZE))
                        pos += SENDER_ID_SIZE
                    }
                    hops
                }
            } else null

            val payload: ByteArray
            val wireBytes: ByteArray
            val wireCompressed: Boolean

            if (isCompressed) {
                val field = if (version >= 2) 4 else 2
                if (payloadLength < field) throw ProtoException.Truncated()
                val originalSize = if (version >= 2) {
                    val v = readUIntBE(raw, pos)
                    pos += 4
                    if (v > Int.MAX_VALUE.toLong()) throw ProtoException.PayloadTooLarge()
                    v.toInt()
                } else {
                    val v = readUShortBE(raw, pos)
                    pos += 2
                    v
                }
                if (originalSize <= 0 || originalSize > maxPayload) {
                    throw ProtoException.PayloadTooLarge()
                }
                val compressedSize = payloadLength - field
                if (compressedSize <= 0) throw ProtoException.BadCompression()
                val comp = raw.copyOfRange(pos, pos + compressedSize)
                pos += compressedSize
                payload = Compress.decompress(comp, originalSize)
                wireBytes = comp
                wireCompressed = true
            } else {
                if (payloadLength > maxPayload) throw ProtoException.PayloadTooLarge()
                payload = raw.copyOfRange(pos, pos + payloadLength)
                pos += payloadLength
                wireBytes = payload.copyOf()
                wireCompressed = false
            }

            val signature = if (hasSignature) {
                raw.copyOfRange(pos, pos + SIGNATURE_SIZE)
            } else null

            return Packet(
                version = version,
                msgType = msgType,
                ttl = ttl,
                timestamp = timestamp,
                sender = sender,
                recipient = recipient,
                route = route,
                payload = payload,
                signature = signature,
                wire = WirePayload(
                    bytes = wireBytes,
                    compressed = wireCompressed,
                    forPayload = payload.copyOf()
                )
            )
        }

        private fun writeShortBE(out: ByteArrayOutputStream, v: Int) {
            out.write((v ushr 8) and 0xFF)
            out.write(v and 0xFF)
        }

        private fun writeIntBE(out: ByteArrayOutputStream, v: Int) {
            out.write((v ushr 24) and 0xFF)
            out.write((v ushr 16) and 0xFF)
            out.write((v ushr 8) and 0xFF)
            out.write(v and 0xFF)
        }

        private fun writeLongBE(out: ByteArrayOutputStream, v: Long) {
            for (shift in 56 downTo 0 step 8) {
                out.write(((v ushr shift) and 0xFF).toInt())
            }
        }

        private fun readUShortBE(b: ByteArray, off: Int): Int {
            return ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)
        }

        private fun readUIntBE(b: ByteArray, off: Int): Long {
            return ((b[off].toLong() and 0xFF) shl 24) or
                ((b[off + 1].toLong() and 0xFF) shl 16) or
                ((b[off + 2].toLong() and 0xFF) shl 8) or
                (b[off + 3].toLong() and 0xFF)
        }

        private fun readLongBE(b: ByteArray, off: Int): Long {
            var v = 0L
            for (i in 0 until 8) {
                v = (v shl 8) or (b[off + i].toLong() and 0xFF)
            }
            return v
        }
    }
}
