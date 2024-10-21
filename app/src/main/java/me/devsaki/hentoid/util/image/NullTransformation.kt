package me.devsaki.hentoid.util.image

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

class NullTransformation : Transformation() {
    override val cacheKey = "${this::class.qualifiedName}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        return input
    }
}