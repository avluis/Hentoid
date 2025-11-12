package me.devsaki.hentoid.core

import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.core.view.ViewCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

fun <T : View, U : View> T.requireById(@IdRes resId: Int): U {
    return ViewCompat.requireViewById(this, resId)
}

fun View.fixBottomSheetLanscape(dialogFragment: DialogFragment) {
    this.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            this@fixBottomSheetLanscape.viewTreeObserver.removeOnGlobalLayoutListener(this)
            val dialog = dialogFragment.dialog as BottomSheetDialog?
            if (dialog != null) {
                val bottomSheet =
                    dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                if (bottomSheet != null) {
                    val behavior = BottomSheetBehavior.from(bottomSheet)
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                }
            }
        }
    })
}