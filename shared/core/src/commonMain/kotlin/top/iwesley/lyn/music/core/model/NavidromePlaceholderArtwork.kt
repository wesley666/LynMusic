package top.iwesley.lyn.music.core.model

const val NAVIDROME_PLACEHOLDER_ARTWORK_MAX_BYTES = 20 * 1024
const val NAVIDROME_PLACEHOLDER_ARTWORK_SHA256 =
    "b502979c464ab7a61f79bfd820e23b8dc3eeb36034b0722f7588972fd9fa7392"
const val NAVIDROME_PLACEHOLDER_ARTWORK_DHASH_DISTANCE = 8

private const val NAVIDROME_PLACEHOLDER_ARTWORK_DHASH = 0x0c1377615911370cUL

fun isReplaceableNavidromePlaceholderArtwork(
    bytes: ByteArray,
    differenceHash: ULong? = null,
    sha256Hex: String = bytes.sha256Hex(),
): Boolean {
    if (bytes.size >= NAVIDROME_PLACEHOLDER_ARTWORK_MAX_BYTES) return false
    if (!isWebpArtworkPayload(bytes)) return false
    if (sha256Hex.equals(NAVIDROME_PLACEHOLDER_ARTWORK_SHA256, ignoreCase = true)) return true
    return differenceHash?.let { hash ->
        navidromePlaceholderArtworkDHashDistance(hash) <= NAVIDROME_PLACEHOLDER_ARTWORK_DHASH_DISTANCE
    } == true
}

fun navidromePlaceholderArtworkDHashDistance(differenceHash: ULong): Int {
    return hammingDistance(differenceHash, NAVIDROME_PLACEHOLDER_ARTWORK_DHASH)
}

fun navidromeArtworkDifferenceHash(luminance9x8: IntArray): ULong? {
    if (luminance9x8.size != 9 * 8) return null
    var hash = 0UL
    for (y in 0 until 8) {
        val rowOffset = y * 9
        for (x in 0 until 8) {
            hash = hash shl 1
            if (luminance9x8[rowOffset + x] > luminance9x8[rowOffset + x + 1]) {
                hash = hash or 1UL
            }
        }
    }
    return hash
}

fun hammingDistance(left: ULong, right: ULong): Int {
    var value = left xor right
    var count = 0
    while (value != 0UL) {
        count += (value and 1UL).toInt()
        value = value shr 1
    }
    return count
}

fun ByteArray.sha256Hex(): String {
    val padded = sha256PaddedMessage(this)
    val words = IntArray(64)
    var h0 = 0x6a09e667
    var h1 = 0xbb67ae85.toInt()
    var h2 = 0x3c6ef372
    var h3 = 0xa54ff53a.toInt()
    var h4 = 0x510e527f
    var h5 = 0x9b05688c.toInt()
    var h6 = 0x1f83d9ab
    var h7 = 0x5be0cd19

    for (chunkOffset in padded.indices step 64) {
        for (index in 0 until 16) {
            val offset = chunkOffset + index * 4
            words[index] =
                ((padded[offset].toInt() and 0xFF) shl 24) or
                    ((padded[offset + 1].toInt() and 0xFF) shl 16) or
                    ((padded[offset + 2].toInt() and 0xFF) shl 8) or
                    (padded[offset + 3].toInt() and 0xFF)
        }
        for (index in 16 until 64) {
            val s0 = words[index - 15].rotateRight(7) xor
                words[index - 15].rotateRight(18) xor
                (words[index - 15] ushr 3)
            val s1 = words[index - 2].rotateRight(17) xor
                words[index - 2].rotateRight(19) xor
                (words[index - 2] ushr 10)
            words[index] = words[index - 16] + s0 + words[index - 7] + s1
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        var f = h5
        var g = h6
        var h = h7

        for (index in 0 until 64) {
            val sum1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val choice = (e and f) xor (e.inv() and g)
            val temp1 = h + sum1 + choice + SHA256_K[index] + words[index]
            val sum0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = sum0 + majority
            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        h5 += f
        h6 += g
        h7 += h
    }

    return buildString(64) {
        appendSha256Word(h0)
        appendSha256Word(h1)
        appendSha256Word(h2)
        appendSha256Word(h3)
        appendSha256Word(h4)
        appendSha256Word(h5)
        appendSha256Word(h6)
        appendSha256Word(h7)
    }
}

private fun sha256PaddedMessage(bytes: ByteArray): ByteArray {
    val bitLength = bytes.size.toLong() * 8L
    var paddedLength = bytes.size + 1 + 8
    val remainder = paddedLength % 64
    if (remainder != 0) {
        paddedLength += 64 - remainder
    }
    val padded = ByteArray(paddedLength)
    bytes.copyInto(padded)
    padded[bytes.size] = 0x80.toByte()
    for (index in 0 until 8) {
        padded[padded.lastIndex - index] = ((bitLength ushr (index * 8)) and 0xFF).toByte()
    }
    return padded
}

private fun StringBuilder.appendSha256Word(value: Int) {
    for (shift in 28 downTo 0 step 4) {
        append(HEX_CHARS[(value ushr shift) and 0xF])
    }
}

private val HEX_CHARS = "0123456789abcdef".toCharArray()

private val SHA256_K = intArrayOf(
    0x428a2f98,
    0x71374491,
    0xb5c0fbcf.toInt(),
    0xe9b5dba5.toInt(),
    0x3956c25b,
    0x59f111f1,
    0x923f82a4.toInt(),
    0xab1c5ed5.toInt(),
    0xd807aa98.toInt(),
    0x12835b01,
    0x243185be,
    0x550c7dc3,
    0x72be5d74,
    0x80deb1fe.toInt(),
    0x9bdc06a7.toInt(),
    0xc19bf174.toInt(),
    0xe49b69c1.toInt(),
    0xefbe4786.toInt(),
    0x0fc19dc6,
    0x240ca1cc,
    0x2de92c6f,
    0x4a7484aa,
    0x5cb0a9dc,
    0x76f988da,
    0x983e5152.toInt(),
    0xa831c66d.toInt(),
    0xb00327c8.toInt(),
    0xbf597fc7.toInt(),
    0xc6e00bf3.toInt(),
    0xd5a79147.toInt(),
    0x06ca6351,
    0x14292967,
    0x27b70a85,
    0x2e1b2138,
    0x4d2c6dfc,
    0x53380d13,
    0x650a7354,
    0x766a0abb,
    0x81c2c92e.toInt(),
    0x92722c85.toInt(),
    0xa2bfe8a1.toInt(),
    0xa81a664b.toInt(),
    0xc24b8b70.toInt(),
    0xc76c51a3.toInt(),
    0xd192e819.toInt(),
    0xd6990624.toInt(),
    0xf40e3585.toInt(),
    0x106aa070,
    0x19a4c116,
    0x1e376c08,
    0x2748774c,
    0x34b0bcb5,
    0x391c0cb3,
    0x4ed8aa4a,
    0x5b9cca4f,
    0x682e6ff3,
    0x748f82ee,
    0x78a5636f,
    0x84c87814.toInt(),
    0x8cc70208.toInt(),
    0x90befffa.toInt(),
    0xa4506ceb.toInt(),
    0xbef9a3f7.toInt(),
    0xc67178f2.toInt(),
)
