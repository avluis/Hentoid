package me.devsaki.hentoid.widget;

import android.support.annotation.NonNull;
import android.support.v7.widget.PagerSnapHelper;
import android.support.v7.widget.RecyclerView;

import static java.lang.Math.abs;

/**
 * Manages page snapping for a RecyclerView
 */
public final class PageSnapWidget {

    private final SnapHelper snapHelper = new SnapHelper();

    private final RecyclerView recyclerView;

    private int flingFactor;

    public PageSnapWidget(@NonNull RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    public PageSnapWidget setPageSnapEnabled(boolean pageSnapEnabled) {
        if (pageSnapEnabled) {
            snapHelper.attachToRecyclerView(recyclerView);
        } else {
            snapHelper.attachToRecyclerView(null);
        }
        return this;
    }

    public PageSnapWidget setFlingFactor(int flingFactor) {
        this.flingFactor = flingFactor;
        return this;
    }

    private final class SnapHelper extends PagerSnapHelper {
        @Override
        public boolean onFling(int velocityX, int velocityY) {
            int thresholdVelocity = recyclerView.getMinFlingVelocity() * flingFactor;
            if (abs(velocityX) >= thresholdVelocity) {
                return false;
            }
            return super.onFling(velocityX, velocityY);
        }
    }
}
