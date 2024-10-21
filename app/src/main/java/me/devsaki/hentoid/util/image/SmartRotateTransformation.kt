package me.devsaki.hentoid.util.image

import android.graphics.Bitmap
import android.graphics.Matrix
import coil3.size.Size
import coil3.transform.Transformation

class SmartRotateTransformation(
    private val rotateRotationAngle: Float,
    private val screenWidth: Int,
    private val screenHeight: Int
) : Transformation() {

    override val cacheKey = "${this::class.qualifiedName}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val matrix = Matrix()
        if (needsRotating(
                screenWidth,
                screenHeight,
                input.width,
                input.height
            )
        ) matrix.postRotate(rotateRotationAngle)
        return Bitmap.createBitmap(
            input,
            0,
            0,
            input.width,
            input.height,
            matrix,
            true
        )
    }
}