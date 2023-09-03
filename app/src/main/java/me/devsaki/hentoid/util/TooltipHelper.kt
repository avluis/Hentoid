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

object TooltipHelper {
    fun showTooltip(
        context: Context,
        @StringRes message: Int,
        orientation: ArrowOrientation,
        anchor: View,
        lifecycleOwner: LifecycleOwner
    ) {
        showTooltip(context, message, orientation, anchor, lifecycleOwner, false)
    }

    fun showTooltip(
        context: Context,
        @StringRes message: Int,
        orientation: ArrowOrientation,
        anchor: View,
        lifecycleOwner: LifecycleOwner,
        always: Boolean
    ) {
        var prefName = "tooltip." + getViewName(anchor)
        if (context is Activity) prefName += "." + context.localClassName
        val balloonBuilder: Balloon.Builder = Balloon.Builder(context)
            .setArrowSize(10)
            .setArrowOrientation(orientation)
            .setIsVisibleArrow(true)
            .setPadding(4)
            .setTextSize(15f)
            .setArrowPosition(0.5f)
            .setCornerRadius(4f)
            .setAlpha(0.9f)
            .setTextResource(message)
            .setTextColor(ContextCompat.getColor(context, R.color.white_opacity_87))
            .setIconDrawable(ContextCompat.getDrawable(context, R.drawable.ic_help_outline))
            .setBackgroundColor(ContextCompat.getColor(context, R.color.dark_gray))
            .setDismissWhenClicked(true)
            .setDismissWhenTouchOutside(true)
            .setBalloonAnimation(BalloonAnimation.OVERSHOOT)
            .setLifecycleOwner(lifecycleOwner)
        if (!always) balloonBuilder.setPreferenceName(prefName)
        val balloon: Balloon = balloonBuilder.build()
        if (orientation == ArrowOrientation.BOTTOM) balloon.showAlignTop(anchor) else if (orientation == ArrowOrientation.TOP) balloon.showAlignBottom(
            anchor
        ) else if (orientation == ArrowOrientation.START) balloon.showAlignRight(anchor) else if (orientation == ArrowOrientation.END) balloon.showAlignLeft(
            anchor
        )
    }

    private fun getViewName(view: View): String {
        return if (view.id == View.NO_ID) "no-id" else view.resources.getResourceName(view.id)
    }
}