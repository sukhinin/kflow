package com.github.sukhinin.kflow.utils

object InetUtils {
    fun ntoa(n: Int): String {
        val sb = StringBuilder()
        sb.append(n ushr 24 and 0xff)
        sb.append('.')
        sb.append(n ushr 16 and 0xff)
        sb.append('.')
        sb.append(n ushr 8 and 0xff)
        sb.append('.')
        sb.append(n and 0xff)
        return sb.toString()
    }

    fun bton(b: ByteArray): Int {
        require(b.size == 4) { "Invalid IPv4 byte array, expected 4 bytes" }
        return (uint(b[0]) shl 24) or (uint(b[1]) shl 16) or (uint(b[2]) shl 8) or uint(b[3])
    }

    private fun uint(b: Byte) = b.toInt() and 0xff
}
