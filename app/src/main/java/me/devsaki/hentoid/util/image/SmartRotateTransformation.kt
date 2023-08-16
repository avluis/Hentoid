package me.devsaki.hentoid.util.image

import android.graphics.Bitmap
import android.graphics.Matrix
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import me.devsaki.hentoid.util.image.ImageHelper.needsRotating
import java.security.MessageDigest

class SmartRotateTransformation(
    private val rotateRotationAngle: Float,
    private val screenWidth: Int,
    private val screenHeight: Int
) : BitmapTransformation() {

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap? {
        val matrix = Matrix()
        if (needsRotating(
                screenWidth,
                screenHeight,
                toTransform.width,
                toTransform.height
            )
        ) matrix.postRotate(rotateRotationAngle)
        return Bitmap.createBitmap(
            toTransform,
            0,
            0,
            toTransform.width,
            toTransform.height,
            matrix,
            true
        )
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("rotate$rotateRotationAngle".toByteArray())
    }
}