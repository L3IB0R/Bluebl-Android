package com.example.proto

sealed class ProtoException(message: String) : Exception(message) {
    class Truncated : ProtoException("paquete demasiado corto")
    class BadVersion(val version: Int) : ProtoException("versión de paquete no soportada: $version")
    class PayloadTooLarge : ProtoException("payload excede el límite para el tipo")
    class Malformed : ProtoException("payload ilegible (firma o longitud inválidas)")
    class BadCompression : ProtoException("compresión inválida")
    class CompressionBomb : ProtoException("ratio de compresión imposible (bomba deflate)")
    class BadPadding : ProtoException("padding PKCS#7 inválido")
}

object Limits {
    const val MAX_PAYLOAD_LENGTH: Int = 10_485_760 // 10 MiB

    private const val FRAGMENT_BYTES = 1_280
    private const val CONTROL_BYTES = 4 * 1024
    private const val REQUEST_SYNC_BYTES = 8 * 1024
    private const val ENVELOPE_BYTES = 64 * 1024
    private const val TWICE_V1_FRAME_BYTES = 128 * 1024

    fun maxPayloadBytes(msgType: Int): Int {
        return when (msgType) {
            MsgType.FILE_TRANSFER, MsgType.NOISE_ENCRYPTED -> MAX_PAYLOAD_LENGTH
            MsgType.MESSAGE -> TWICE_V1_FRAME_BYTES
            MsgType.REQUEST_SYNC -> REQUEST_SYNC_BYTES
            MsgType.FRAGMENT -> FRAGMENT_BYTES
            MsgType.ANNOUNCE,
            MsgType.LEAVE,
            MsgType.NOISE_HANDSHAKE,
            MsgType.VOICE_FRAME -> CONTROL_BYTES
            0x04, 0x28 -> ENVELOPE_BYTES
            0x23, 0x24, 0x26, 0x27 -> CONTROL_BYTES
            0x25 -> TWICE_V1_FRAME_BYTES
            else -> TWICE_V1_FRAME_BYTES
        }
    }
}
