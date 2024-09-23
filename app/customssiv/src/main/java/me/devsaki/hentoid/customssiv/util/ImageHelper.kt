package me.devsaki.hentoid.customssiv.util

import java.nio.charset.StandardCharsets
import kotlin.math.min


private val CHARSET_LATIN_1 = StandardCharsets.ISO_8859_1

const val MIME_IMAGE_GENERIC = "image/*"
const val MIME_IMAGE_WEBP = "image/webp"
const val MIME_IMAGE_JPEG = "image/jpeg"
const val MIME_IMAGE_GIF = "image/gif"
private const val MIME_IMAGE_BMP = "image/bmp"
const val MIME_IMAGE_PNG = "image/png"
private const val MIME_IMAGE_APNG = "image/apng"

// In Java and Kotlin, byte type is signed !
// => Converting all raw values to byte to be sure they are evaluated as expected
private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
private val WEBP_SIGNATURE =
    byteArrayOf(0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte())
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte())
private val GIF_SIGNATURE = byteArrayOf(0x47.toByte(), 0x49.toByte(), 0x46.toByte())
private val BMP_SIGNATURE = byteArrayOf(0x42.toByte(), 0x4D.toByte())

private val GIF_NETSCAPE = "NETSCAPE".toByteArray(CHARSET_LATIN_1)

private val PNG_ACTL = "acTL".toByteArray(CHARSET_LATIN_1)
private val PNG_IDAT = "IDAT".toByteArray(CHARSET_LATIN_1)

private val WEBP_VP8L = "VP8L".toByteArray(CHARSET_LATIN_1)
private val WEBP_ANIM = "ANIM".toByteArray(CHARSET_LATIN_1)


fun ByteArray.startsWith(data: ByteArray): Boolean {
    if (this.size < data.size) return false
    data.forEachIndexed { index, byte -> if (byte != this[index]) return false }
    return true
}

/**
 * Determine the MIME-type of the given binary data if it's a picture
 *
 * @param binary Picture binary data to determine the MIME-type for
 * @return MIME-type of the given binary data; empty string if not supported
 */
fun getMimeTypeFromPictureBinary(binary: ByteArray): String {
    if (binary.size < 12) return ""

    return if (binary.startsWith(JPEG_SIGNATURE)) MIME_IMAGE_JPEG
    // WEBP : byte comparison is non-contiguous
    else if (binary.startsWith(WEBP_SIGNATURE) && 0x57.toByte() == binary[8] && 0x45.toByte() == binary[9] && 0x42.toByte() == binary[10] && 0x50.toByte() == binary[11]
    ) MIME_IMAGE_WEBP
    else if (binary.startsWith(PNG_SIGNATURE)) {
        // Detect animated PNG : To be recognized as APNG an 'acTL' chunk must appear in the stream before any 'IDAT' chunks
        val acTlPos = findSequencePosition(
            binary,
            0,
            PNG_ACTL,
            (binary.size * 0.2).toInt()
        )
        if (acTlPos > -1) {
            val idatPos = findSequencePosition(
                binary,
                acTlPos,
                PNG_IDAT,
                (binary.size * 0.1).toInt()
            ).toLong()
            if (idatPos > -1) return MIME_IMAGE_APNG
        }
        MIME_IMAGE_PNG
    } else if (binary.startsWith(GIF_SIGNATURE)) MIME_IMAGE_GIF
    else if (binary.startsWith(BMP_SIGNATURE)) MIME_IMAGE_BMP
    else MIME_IMAGE_GENERIC
}

/**
 * Analyze the given binary picture header to try and detect if the picture is animated.
 * If the format is supported by the app, returns true if animated (animated GIF, APNG, animated WEBP); false if not
 *
 * @param data Binary picture file header (400 bytes minimum)
 * @return True if the format is animated and supported by the app
 */
fun isImageAnimated(data: ByteArray): Boolean {
    return if (data.size < 400) false
    else {
        val limit = min(data.size, 1000)
        when (getMimeTypeFromPictureBinary(data)) {
            MIME_IMAGE_APNG -> true
            MIME_IMAGE_GIF -> findSequencePosition(
                data,
                0,
                GIF_NETSCAPE,
                limit
            ) > -1

            MIME_IMAGE_WEBP -> findSequencePosition(
                data,
                0,
                WEBP_ANIM,
                limit
            ) > -1

            else -> false
        }
    }
}