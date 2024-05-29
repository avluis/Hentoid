package me.devsaki.hentoid.util

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.view.View
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.request.RequestOptions
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.image.tintBitmap


val glideOptionCenterInside = RequestOptions().optionalTransform(CenterInside())
var glideOptionCenterImage: RequestOptions? = null

/**
 * Indicate whether the given View's context is usable by Glide
 *
 * @param view View whose Context to test
 * @return True if the given View's context is usable by Glide; false if not
 */
fun isValidContextForGlide(view: View): Boolean {
    return isValidContextForGlide(view.context)
}

/**
 * Indicate whether the given Context is usable by Glide
 *
 * @param context Context to test
 * @return True if the given Context is usable by Glide; false if not
 */
fun isValidContextForGlide(context: Context?): Boolean {
    if (context == null) {
        return false
    }
    if (context is Activity) {
        return !context.isDestroyed && !context.isFinishing
    }
    return true
}

fun getGlideOptionCenterImage(context: Context): RequestOptions {
    if (null == glideOptionCenterImage) {
        val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.ic_hentoid_trans)
        val tintColor = context.getThemedColor(R.color.light_gray)
        val d = BitmapDrawable(context.resources, tintBitmap(bmp, tintColor))
        glideOptionCenterImage = RequestOptions().optionalTransform(CenterInside()).error(d)
    }
    return glideOptionCenterImage!!
}