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

private val SHA256_TABLE = intArrayOf(
    0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
    -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
    -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
    -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
)

/** Lower-case hex SHA-256 of the UTF-8 bytes of [value]. */
fun sha256Hex(value: String): String {
    val message = value.encodeToByteArray()
    val bitLength = message.size.toLong() * 8
    val paddedLength = ((message.size + 9 + 63) / 64) * 64
    val padded = ByteArray(paddedLength)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    for (index in 0 until 8) {
        padded[paddedLength - 1 - index] = (bitLength ushr (8 * index)).toByte()
    }
    val hash = intArrayOf(
        0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6,
        0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19,
    )
    val words = IntArray(64)
    var offset = 0
    while (offset < paddedLength) {
        for (index in 0 until 16) {
            val base = offset + index * 4
            words[index] = ((padded[base].toInt() and 0xff) shl 24) or
                ((padded[base + 1].toInt() and 0xff) shl 16) or
                ((padded[base + 2].toInt() and 0xff) shl 8) or
                (padded[base + 3].toInt() and 0xff)
        }
        for (index in 16 until 64) {
            val s0 = words[index - 15].rotateRight(7) xor words[index - 15].rotateRight(18) xor (words[index - 15] ushr 3)
            val s1 = words[index - 2].rotateRight(17) xor words[index - 2].rotateRight(19) xor (words[index - 2] ushr 10)
            words[index] = words[index - 16] + s0 + words[index - 7] + s1
        }
        var a = hash[0]
        var b = hash[1]
        var c = hash[2]
        var d = hash[3]
        var e = hash[4]
        var f = hash[5]
        var g = hash[6]
        var h = hash[7]
        for (index in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val choice = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + choice + SHA256_TABLE[index] + words[index]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + majority
            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }
        hash[0] += a
        hash[1] += b
        hash[2] += c
        hash[3] += d
        hash[4] += e
        hash[5] += f
        hash[6] += g
        hash[7] += h
        offset += 64
    }
    return hash.joinToString(separator = "") { word -> (word.toLong() and 0xffffffffL).toString(16).padStart(8, '0') }
}
