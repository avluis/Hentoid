package me.devsaki.hentoid.widget

import android.content.Context
import android.graphics.Typeface
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.skydoves.powermenu.*
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.Helper

class DownloadModeMenu {

    companion object {
        fun show(
            context: Context,
            anchor: View,
            lifecycle: LifecycleOwner,
            listener: OnMenuItemClickListener<PowerMenuItem?>,
            dismissListener: OnDismissedListener?
        ) {
            val res = context.resources
            val powerMenu = PowerMenu.Builder(context)
                .addItem(
                    PowerMenuItem(
                        res.getString(R.string.pref_viewer_dl_action_entries_1),
                        R.drawable.ic_action_download,
                        false
                    )
                )
                .addItem(
                    PowerMenuItem(
                        res.getString(R.string.pref_viewer_dl_action_entries_2),
                        R.drawable.ic_action_download_stream,
                        false
                    )
                )
                .setAnimation(MenuAnimation.SHOWUP_TOP_LEFT)
                .setMenuRadius(10f)
                .setLifecycleOwner(lifecycle)
                .setTextColor(ContextCompat.getColor(context, R.color.white_opacity_87))
                .setTextTypeface(Typeface.DEFAULT)
                .setMenuColor(ContextCompat.getColor(context, R.color.dark_gray))
                .setTextSize(Helper.dimensAsDp(context, R.dimen.text_subtitle_1))
                .setWidth(res.getDimension(R.dimen.popup_menu_width).toInt())
                .setAutoDismiss(true)
                .build()
            powerMenu.onMenuItemClickListener = listener
            powerMenu.setOnDismissedListener(dismissListener)
            powerMenu.setIconColor(ContextCompat.getColor(context, R.color.white_opacity_87))
            powerMenu.showAtCenter(anchor)
        }
    }
}