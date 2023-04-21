package me.devsaki.hentoid.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.ViewCompat.NestedScrollType
import com.google.android.material.bottomappbar.BottomAppBar

// Custom behaviour to auto-hide/scroll bottom bar at the first pixel scrolled
// Default behaviour when setting app:hideOnScroll="true" / app:layout_scrollFlags="scroll|enterAlways" has a too important deadzone
// https://stackoverflow.com/questions/42631542/show-hide-bottomnavigationview-on-scroll-in-coordinatorlayout-with-appbarlayout
class BottomNavigationBehaviour(context: Context, attrs: AttributeSet) :
    CoordinatorLayout.Behavior<BottomAppBar>(context, attrs) {

    override fun layoutDependsOn(
        parent: CoordinatorLayout,
        child: BottomAppBar,
        dependency: View
    ): Boolean {
        return dependency is FrameLayout
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: BottomAppBar,
        directTargetChild: View,
        target: View,
        nestedScrollAxes: Int,
        @NestedScrollType type: Int
    ): Boolean {
        return nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: BottomAppBar,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        @NestedScrollType type: Int
    ) {
        if (dy < 0) {
            showBottomBar(child)
        } else if (dy > 0) {
            hideBottomBar(child)
        }
    }

    private fun hideBottomBar(view: BottomAppBar) {
        view.animate().translationY(view.height.toFloat())
    }

    private fun showBottomBar(view: BottomAppBar) {
        view.animate().translationY(0f)
    }
}