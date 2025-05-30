package me.devsaki.hentoid.widget

import android.content.Context
import android.graphics.Typeface
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.OnDismissedListener
import com.skydoves.powermenu.OnMenuItemClickListener
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.dimensAsDp

fun showDownloadModeMenu(
    context: Context,
    anchor: View,
    lifecycle: LifecycleOwner,
    listener: OnMenuItemClickListener<PowerMenuItem?>,
    dismissListener: OnDismissedListener?
) {
    showDownloadModeMenu(
        buildDownloadModeMenu(context, lifecycle, null == dismissListener),
        anchor,
        listener,
        dismissListener
    )
}

fun showDownloadModeMenu(
    powerMenu: PowerMenu,
    anchor: View,
    listener: OnMenuItemClickListener<PowerMenuItem?>,
    dismissListener: OnDismissedListener?
) {
    powerMenu.onMenuItemClickListener = listener
    powerMenu.setOnDismissedListener(dismissListener)
    powerMenu.showAtCenter(anchor)
}

fun buildDownloadModeMenu(
    context: Context,
    lifecycle: LifecycleOwner,
    autoDismiss: Boolean = false
): PowerMenu {
    val res = context.resources
    val powerMenu = PowerMenu.Builder(context)
        .addItem(
            PowerMenuItem(
                res.getString(R.string.pref_viewer_dl_action_entries_1),
                false,
                R.drawable.ic_action_download,
            )
        )
        .addItem(
            PowerMenuItem(
                res.getString(R.string.pref_viewer_dl_action_entries_2),
                false,
                R.drawable.ic_action_download_stream
            )
        )
        .setAnimation(MenuAnimation.SHOWUP_TOP_LEFT)
        .setMenuRadius(10f)
        .setLifecycleOwner(lifecycle)
        .setTextColor(ContextCompat.getColor(context, R.color.white_opacity_87))
        .setTextTypeface(Typeface.DEFAULT)
        .setMenuColor(ContextCompat.getColor(context, R.color.dark_gray))
        .setTextSize(dimensAsDp(context, R.dimen.text_subtitle_1))
        .setWidth(res.getDimension(R.dimen.popup_menu_width).toInt())
        .setAutoDismiss(autoDismiss)
        .build()
    powerMenu.setIconColor(ContextCompat.getColor(context, R.color.white_opacity_87))
    return powerMenu
}