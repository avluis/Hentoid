package me.devsaki.hentoid.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;

import com.google.android.material.bottomappbar.BottomAppBar;

// https://stackoverflow.com/questions/42631542/show-hide-bottomnavigationview-on-scroll-in-coordinatorlayout-with-appbarlayout
public class BottomNavigationBehavior extends CoordinatorLayout.Behavior<BottomAppBar> {

    public BottomNavigationBehavior() {
        super();
    }

    public BottomNavigationBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean layoutDependsOn(@NonNull CoordinatorLayout parent, @NonNull BottomAppBar child, @NonNull View dependency) {
        return dependency instanceof FrameLayout;
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull BottomAppBar child, @NonNull View directTargetChild, @NonNull View target, int nestedScrollAxes, @ViewCompat.NestedScrollType int type) {
        return nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL;
    }

    @Override
    public void onNestedPreScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull BottomAppBar child, @NonNull View target, int dx, int dy, @NonNull int[] consumed, @ViewCompat.NestedScrollType int type) {
        if (dy < 0) {
            showBottomNavigationView(child);
        } else if (dy > 0) {
            hideBottomNavigationView(child);
        }
    }

    private void hideBottomNavigationView(BottomAppBar view) {
        view.animate().translationY(view.getHeight());
    }

    private void showBottomNavigationView(BottomAppBar view) {
        view.animate().translationY(0);
    }
}