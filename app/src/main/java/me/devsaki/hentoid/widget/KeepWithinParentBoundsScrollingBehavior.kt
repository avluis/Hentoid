package me.devsaki.hentoid.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout

// https://stackoverflow.com/questions/39739882/viewpagers-height-in-coordinator-layout-is-more-than-available
class KeepWithinParentBoundsScrollingBehavior(context: Context, attrs: AttributeSet) :
    AppBarLayout.ScrollingViewBehavior(context, attrs) {

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: View,
        dependency: View
    ): Boolean {
        if (dependency !is AppBarLayout) {
            return super.onDependentViewChanged(parent, child, dependency)
        }

        val layoutParams = child.layoutParams as CoordinatorLayout.LayoutParams
        layoutParams.height = parent.height - dependency.bottom
        child.layoutParams = layoutParams
        return super.onDependentViewChanged(parent, child, dependency)
    }
}