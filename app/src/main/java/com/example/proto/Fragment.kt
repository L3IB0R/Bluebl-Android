package com.example.proto

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

object MeshFrag {
    const val FRAGMENT_THRESHOLD = 512
    const val FRAGMENT_CHUNK = 469
    const val FRAGMENT_HEADER = 13
    const val FRAGMENT_TIMEOUT_MS = 30_000L

    fun fragmentPacket(packet: Packet): List<Packet>? {
        val wire = packet.encode(true)
        if (wire.size <= FRAGMENT_THRESHOLD) return null

        val totalChunks = (wire.size + FRAGMENT_CHUNK - 1) / FRAGMENT_CHUNK
        if (totalChunks > 0xFFFF) throw ProtoException.PayloadTooLarge()

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(wire)
        val tsBytes = ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array()
        digest.update(tsBytes)
        val id = digest.digest().copyOfRange(0, 8)

        val out = ArrayList<Packet>(totalChunks)
        for (i in 0 until totalChunks) {
            val start = i * FRAGMENT_CHUNK
            val end = minOf(start + FRAGMENT_CHUNK, wire.size)
            val chunk = wire.copyOfRange(start, end)
            val frag = Fragment(
                id = id,
                index = i,
                total = totalChunks,
                originalType = packet.msgType,
                data = chunk
            )
            out.add(
                Packet(
                    msgType = MsgType.FRAGMENT,
                    sender = packet.sender.copyOf(),
                    timestamp = packet.timestamp,
                    ttl = packet.ttl,
                    recipient = packet.recipient?.copyOf(),
                    payload = frag.encode()
                )
            )
        }
        return out
    }

    fun fragmentForWireLimit(packet: Packet, maxWireBytes: Int): List<ByteArray> {
        val padded = packet.encode(true)
        if (padded.size <= maxWireBytes) return listOf(padded)

        val unpadded = packet.encode(false)
        if (unpadded.size <= maxWireBytes || maxWireBytes < 64) {
            return listOf(unpadded)
        }

        val recipientOverhead = if (packet.recipient != null) Packet.RECIPIENT_ID_SIZE else 0
        val fragOverhead = Packet.HEADER_SIZE_V1 + Packet.SENDER_ID_SIZE + recipientOverhead + FRAGMENT_HEADER
        val chunkCap = (maxWireBytes - fragOverhead).coerceIn(16, FRAGMENT_CHUNK)
        val totalChunks = (unpadded.size + chunkCap - 1) / chunkCap
        if (totalChunks <= 1) return listOf(unpadded)
        if (totalChunks > 0xFFFF) throw ProtoException.PayloadTooLarge()

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(unpadded)
        val tsBytes = ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array()
        digest.update(tsBytes)
        val id = digest.digest().copyOfRange(0, 8)

        val frames = ArrayList<ByteArray>(totalChunks)
        for (i in 0 until totalChunks) {
            val start = i * chunkCap
            val end = minOf(start + chunkCap, unpadded.size)
            val chunk = unpadded.copyOfRange(start, end)
            val frag = Fragment(
                id = id,
                index = i,
                total = totalChunks,
                originalType = packet.msgType,
                data = chunk
            )
            val fragPacket = Packet(
                msgType = MsgType.FRAGMENT,
                sender = packet.sender.copyOf(),
                timestamp = packet.timestamp,
                ttl = packet.ttl,
                recipient = packet.recipient?.copyOf(),
                payload = frag.encode()
            )
            frames.add(fragPacket.encode(false))
        }
        return frames
    }

    fun dedupKey(packet: Packet): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(packet.sender)
        digest.update(ByteBuffer.allocate(8).putLong(packet.timestamp).array())
        digest.update(packet.payload)
        return digest.digest().copyOfRange(0, 8).joinToString("") { "%02x".format(it) }
    }
}

data class Fragment(
    val id: ByteArray,
    val index: Int,
    val total: Int,
    val originalType: Int,
    val data: ByteArray
) {
    fun encode(): ByteArray {
        val out = ByteArrayOutputStream(MeshFrag.FRAGMENT_HEADER + data.size)
        require(id.size == 8)
        out.write(id)
        out.write((index ushr 8) and 0xFF)
        out.write(index and 0xFF)
        out.write((total ushr 8) and 0xFF)
        out.write(total and 0xFF)
        out.write(originalType and 0xFF)
        out.write(data)
        return out.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Fragment) return false
        return id.contentEquals(other.id) &&
            index == other.index &&
            total == other.total &&
            originalType == other.originalType &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = id.contentHashCode()
        result = 31 * result + index
        result = 31 * result + total
        result = 31 * result + originalType
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object {
        fun decode(data: ByteArray): Fragment {
            if (data.size < MeshFrag.FRAGMENT_HEADER) throw ProtoException.Truncated()
            val id = data.copyOfRange(0, 8)
            val index = ((data[8].toInt() and 0xFF) shl 8) or (data[9].toInt() and 0xFF)
            val total = ((data[10].toInt() and 0xFF) shl 8) or (data[11].toInt() and 0xFF)
            val originalType = data[12].toInt() and 0xFF
            val body = data.copyOfRange(13, data.size)
            return Fragment(id, index, total, originalType, body)
        }
    }
}

class Reassembler {
    private class Pending(
        val originalType: Int,
        val total: Int,
        val receivedAt: Long,
        val parts: MutableMap<Int, ByteArray> = mutableMapOf()
    )

    private val pending = HashMap<String, Pending>()

    fun push(frag: Fragment): Pair<Int, ByteArray>? {
        cleanup()
        val key = frag.id.joinToString("") { "%02x".format(it) }
        val entry = pending.getOrPut(key) {
            Pending(
                originalType = frag.originalType,
                total = frag.total,
                receivedAt = System.currentTimeMillis()
            )
        }
        entry.parts[frag.index] = frag.data
        return if (entry.parts.size == entry.total && entry.total > 0) {
            pending.remove(key)
            val out = ByteArrayOutputStream()
            for (i in 0 until entry.total) {
                val part = entry.parts[i] ?: return null
                out.write(part)
            }
            Pair(entry.originalType, out.toByteArray())
        } else {
            null
        }
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        val it = pending.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (now - e.value.receivedAt >= MeshFrag.FRAGMENT_TIMEOUT_MS) {
                it.remove()
            }
        }
    }
}
