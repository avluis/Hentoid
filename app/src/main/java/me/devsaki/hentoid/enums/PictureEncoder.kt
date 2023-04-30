package me.devsaki.hentoid.enums

import me.devsaki.hentoid.util.image.ImageHelper

enum class PictureEncoder(val value: Int, val mimeType: String) {
    WEBP_LOSSLESS(0, ImageHelper.MIME_IMAGE_WEBP),
    WEBP_LOSSY(1, ImageHelper.MIME_IMAGE_WEBP),
    PNG(2, ImageHelper.MIME_IMAGE_PNG),
    JPEG(3, ImageHelper.MIME_IMAGE_JPEG);

    companion object {
        fun fromValue(data: Int): PictureEncoder? {
            return values().firstOrNull { e -> data == e.value }
        }
    }
}