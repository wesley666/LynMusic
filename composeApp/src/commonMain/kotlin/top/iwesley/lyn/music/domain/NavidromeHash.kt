package top.iwesley.lyn.music.domain

import kotlin.math.abs
import kotlin.math.sin

private val MD5_SHIFT_AMOUNTS = intArrayOf(
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
)

private val MD5_TABLE = IntArray(64) { index ->
    (abs(sin(index + 1.0)) * 4294967296.0).toLong().toInt()
}

fun md5Hex(value: String): String {
    val message = value.encodeToByteArray()
    val padded = padMd5Message(message)

    var a0 = 0x67452301
    var b0 = 0xefcdab89.toInt()
    var c0 = 0x98badcfe.toInt()
    var d0 = 0x10325476

    val words = IntArray(16)
    var offset = 0
    while (offset < padded.size) {
        for (index in 0 until 16) {
            val base = offset + index * 4
            words[index] =
                (padded[base].toInt() and 0xff) or
                    ((padded[base + 1].toInt() and 0xff) shl 8) or
                    ((padded[base + 2].toInt() and 0xff) shl 16) or
                    ((padded[base + 3].toInt() and 0xff) shl 24)
        }

        var a = a0
        var b = b0
        var c = c0
        var d = d0

        for (index in 0 until 64) {
            val (f, g) = when (index) {
                in 0..15 -> ((b and c) or (b.inv() and d)) to index
                in 16..31 -> ((d and b) or (d.inv() and c)) to ((5 * index + 1) % 16)
                in 32..47 -> (b xor c xor d) to ((3 * index + 5) % 16)
                else -> (c xor (b or d.inv())) to ((7 * index) % 16)
            }
            val rotated = leftRotate(
                a + f + MD5_TABLE[index] + words[g],
                MD5_SHIFT_AMOUNTS[index],
            )
            val nextB = b + rotated
            a = d
            d = c
            c = b
            b = nextB
        }

        a0 += a
        b0 += b
        c0 += c
        d0 += d
        offset += 64
    }

    return buildString(32) {
        appendLittleEndianHex(a0)
        appendLittleEndianHex(b0)
        appendLittleEndianHex(c0)
        appendLittleEndianHex(d0)
    }
}

private fun padMd5Message(message: ByteArray): ByteArray {
    val bitLength = message.size.toLong() * 8L
    val totalSize = (((message.size + 8) / 64) + 1) * 64
    return ByteArray(totalSize).also { padded ->
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (index in 0 until 8) {
            padded[totalSize - 8 + index] = ((bitLength ushr (index * 8)) and 0xff).toByte()
        }
    }
}

private fun leftRotate(value: Int, bits: Int): Int {
    return (value shl bits) or (value ushr (32 - bits))
}

private fun StringBuilder.appendLittleEndianHex(value: Int) {
    repeat(4) { index ->
        val byte = (value ushr (index * 8)) and 0xff
        append(byte.toString(16).padStart(2, '0'))
    }
}
