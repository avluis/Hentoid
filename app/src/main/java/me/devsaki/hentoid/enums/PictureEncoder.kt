package me.devsaki.hentoid.enums

import me.devsaki.hentoid.util.image.MIME_IMAGE_JPEG
import me.devsaki.hentoid.util.image.MIME_IMAGE_JXL
import me.devsaki.hentoid.util.image.MIME_IMAGE_PNG
import me.devsaki.hentoid.util.image.MIME_IMAGE_WEBP

enum class PictureEncoder(val value: Int, val mimeType: String, val isLossless : Boolean = false) {
    WEBP_LOSSLESS(0, MIME_IMAGE_WEBP, true),
    WEBP_LOSSY(1, MIME_IMAGE_WEBP),
    PNG(2, MIME_IMAGE_PNG, true),
    JPEG(3, MIME_IMAGE_JPEG),
    JXL_LOSSY(4, MIME_IMAGE_JXL),
    JXL_LOSSLESS(5, MIME_IMAGE_JXL, true);

    companion object {
        fun fromValue(data: Int): PictureEncoder? {
            return entries.firstOrNull { data == it.value }
        }
    }
}