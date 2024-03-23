package me.devsaki.hentoid.util

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.skydoves.balloon.ArrowOrientation
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import me.devsaki.hentoid.R

fun Context.showTooltip(
    @StringRes message: Int,
    orientation: ArrowOrientation,
    anchor: View,
    lifecycleOwner: LifecycleOwner
) {
    this.showTooltip(message, orientation, anchor, lifecycleOwner, false)
}

fun Context.showTooltip(
    @StringRes message: Int,
    orientation: ArrowOrientation,
    anchor: View,
    lifecycleOwner: LifecycleOwner,
    always: Boolean
) {
    var prefName = "tooltip." + anchor.getName()
    if (this is Activity) prefName += "." + this.localClassName
    val balloonBuilder: Balloon.Builder = Balloon.Builder(this)
        .setArrowSize(10)
        .setArrowOrientation(orientation)
        .setIsVisibleArrow(true)
        .setPadding(4)
        .setTextSize(15f)
        .setArrowPosition(0.5f)
        .setCornerRadius(4f)
        .setAlpha(0.9f)
        .setTextResource(message)
        .setTextColor(ContextCompat.getColor(this, R.color.white_opacity_87))
        .setIconDrawable(ContextCompat.getDrawable(this, R.drawable.ic_help_outline))
        .setBackgroundColor(ContextCompat.getColor(this, R.color.dark_gray))
        .setDismissWhenClicked(true)
        .setDismissWhenTouchOutside(true)
        .setBalloonAnimation(BalloonAnimation.OVERSHOOT)
        .setLifecycleOwner(lifecycleOwner)
    if (!always) balloonBuilder.setPreferenceName(prefName)
    val balloon: Balloon = balloonBuilder.build()
    when (orientation) {
        ArrowOrientation.BOTTOM -> balloon.showAlignTop(anchor)
        ArrowOrientation.TOP -> balloon.showAlignBottom(anchor)
        ArrowOrientation.START -> balloon.showAlignEnd(anchor)
        ArrowOrientation.END -> balloon.showAlignStart(anchor)
    }
}

private fun View.getName(): String {
    return if (this.id == View.NO_ID) "no-id" else this.resources.getResourceName(this.id)
}