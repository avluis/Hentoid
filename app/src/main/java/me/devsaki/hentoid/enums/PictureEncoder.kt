package me.devsaki.hentoid.enums

import me.devsaki.hentoid.util.image.MIME_IMAGE_JPEG
import me.devsaki.hentoid.util.image.MIME_IMAGE_PNG
import me.devsaki.hentoid.util.image.MIME_IMAGE_WEBP

enum class PictureEncoder(val value: Int, val mimeType: String) {
    WEBP_LOSSLESS(0, MIME_IMAGE_WEBP),
    WEBP_LOSSY(1, MIME_IMAGE_WEBP),
    PNG(2, MIME_IMAGE_PNG),
    JPEG(3, MIME_IMAGE_JPEG);

    companion object {
        fun fromValue(data: Int): PictureEncoder? {
            return entries.firstOrNull { e -> data == e.value }
        }
    }
}