package com.example.proto

import java.io.ByteArrayOutputStream
import java.util.UUID

object ChatMsgFlags {
    const val RELAY = 0x01
    const val PRIVATE = 0x02
    const val ORIGINAL_SENDER = 0x04
    const val RECIPIENT_NICKNAME = 0x08
    const val SENDER_PEER_ID = 0x10
    const val MENTIONS = 0x20
    const val CHANNEL = 0x40
    const val ENCRYPTED = 0x80
}

data class ChatMessage(
    val id: String = newMessageId(),
    val sender: String,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isRelay: Boolean = false,
    val isPrivate: Boolean = false,
    val isEncrypted: Boolean = false,
    val originalSender: String? = null,
    val recipientNickname: String? = null,
    val senderPeerId: String? = null,
    val mentions: List<String> = emptyList(),
    val channel: String? = null
) {
    fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        var f = 0
        if (isRelay) f = f or ChatMsgFlags.RELAY
        if (isPrivate) f = f or ChatMsgFlags.PRIVATE
        if (originalSender != null) f = f or ChatMsgFlags.ORIGINAL_SENDER
        if (recipientNickname != null) f = f or ChatMsgFlags.RECIPIENT_NICKNAME
        if (senderPeerId != null) f = f or ChatMsgFlags.SENDER_PEER_ID
        if (mentions.isNotEmpty()) f = f or ChatMsgFlags.MENTIONS
        if (channel != null) f = f or ChatMsgFlags.CHANNEL
        if (isEncrypted) f = f or ChatMsgFlags.ENCRYPTED
        out.write(f)

        for (shift in 56 downTo 0 step 8) {
            out.write(((timestampMs ushr shift) and 0xFF).toInt())
        }
        putStr(out, id)
        putStr(out, sender)

        val contentBytes = content.toByteArray(Charsets.UTF_8)
        val contentLen = minOf(contentBytes.size, 0xFFFF)
        out.write((contentLen ushr 8) and 0xFF)
        out.write(contentLen and 0xFF)
        out.write(contentBytes, 0, contentLen)

        originalSender?.let { putStr(out, it) }
        recipientNickname?.let { putStr(out, it) }
        senderPeerId?.let { putStr(out, it) }
        if (mentions.isNotEmpty()) {
            val count = minOf(mentions.size, 255)
            out.write(count)
            for (i in 0 until count) {
                putStr(out, mentions[i])
            }
        }
        channel?.let { putStr(out, it) }
        return out.toByteArray()
    }

    companion object {
        private fun putStr(out: ByteArrayOutputStream, s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            val len = minOf(bytes.size, 255)
            out.write(len)
            out.write(bytes, 0, len)
        }

        private fun takeStr(data: ByteArray, pos: IntArray): String {
            if (pos[0] >= data.size) throw ProtoException.Truncated()
            val len = data[pos[0]].toInt() and 0xFF
            pos[0]++
            if (pos[0] + len > data.size) throw ProtoException.Truncated()
            val s = decodeStrictUtf8(data.copyOfRange(pos[0], pos[0] + len))
            pos[0] += len
            return s
        }

        fun decode(data: ByteArray): ChatMessage {
            if (data.size < 13) throw ProtoException.Truncated()
            val pos = intArrayOf(0)
            val f = data[pos[0]++].toInt() and 0xFF
            var ts = 0L
            for (i in 0 until 8) {
                ts = (ts shl 8) or (data[pos[0]++].toLong() and 0xFF)
            }
            val id = takeStr(data, pos)
            val sender = takeStr(data, pos)
            if (pos[0] + 2 > data.size) throw ProtoException.Truncated()
            val contentLen = ((data[pos[0]].toInt() and 0xFF) shl 8) or (data[pos[0] + 1].toInt() and 0xFF)
            pos[0] += 2
            if (pos[0] + contentLen > data.size) throw ProtoException.Truncated()
            val content = decodeStrictUtf8(data.copyOfRange(pos[0], pos[0] + contentLen))
            pos[0] += contentLen

            val originalSender = if ((f and ChatMsgFlags.ORIGINAL_SENDER) != 0) takeStr(data, pos) else null
            val recipientNickname = if ((f and ChatMsgFlags.RECIPIENT_NICKNAME) != 0) takeStr(data, pos) else null
            val senderPeerId = if ((f and ChatMsgFlags.SENDER_PEER_ID) != 0) takeStr(data, pos) else null
            val mentions = if ((f and ChatMsgFlags.MENTIONS) != 0) {
                if (pos[0] >= data.size) throw ProtoException.Truncated()
                val count = data[pos[0]++].toInt() and 0xFF
                List(count) { takeStr(data, pos) }
            } else emptyList()
            val channel = if ((f and ChatMsgFlags.CHANNEL) != 0) takeStr(data, pos) else null

            return ChatMessage(
                id = id,
                sender = sender,
                content = content,
                timestampMs = ts,
                isRelay = (f and ChatMsgFlags.RELAY) != 0,
                isPrivate = (f and ChatMsgFlags.PRIVATE) != 0,
                isEncrypted = (f and ChatMsgFlags.ENCRYPTED) != 0,
                originalSender = originalSender,
                recipientNickname = recipientNickname,
                senderPeerId = senderPeerId,
                mentions = mentions,
                channel = channel
            )
        }

        private fun newMessageId(): String {
            return UUID.randomUUID().toString().replace("-", "").take(16)
        }
    }
}
