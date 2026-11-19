package com.example.proto

object Padding {
    private val BLOCK_SIZES = intArrayOf(256, 512, 1024, 2048)

    fun optimalBlockSize(dataSize: Int): Int {
        val total = dataSize + 16
        for (block in BLOCK_SIZES) {
            if (total <= block) return block
        }
        return dataSize
    }

    fun pad(data: ByteArray, targetSize: Int): ByteArray {
        if (data.size >= targetSize) return data.copyOf()
        val needed = targetSize - data.size
        if (needed <= 0 || needed > 255) return data.copyOf()
        val out = ByteArray(targetSize)
        System.arraycopy(data, 0, out, 0, data.size)
        val padByte = needed.toByte()
        for (i in data.size until targetSize) {
            out[i] = padByte
        }
        return out
    }

    fun unpad(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data.copyOf()
        val last = data[data.size - 1].toInt() and 0xFF
        if (last == 0 || last > data.size) return data.copyOf()
        val start = data.size - last
        val lastByte = last.toByte()
        for (i in start until data.size) {
            if (data[i] != lastByte) return data.copyOf()
        }
        return data.copyOfRange(0, start)
    }
}
