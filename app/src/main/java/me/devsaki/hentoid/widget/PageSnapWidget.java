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

    private float flingSensitivity;

    public PageSnapWidget(@NonNull RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
        setPageSnapEnabled(true);
    }

    public void setPageSnapEnabled(boolean pageSnapEnabled) {
        if (pageSnapEnabled) {
            snapHelper.attachToRecyclerView(recyclerView);
        } else {
            snapHelper.attachToRecyclerView(null);
        }
    }

    /**
     * Sets the sensitivity of a fling.
     *
     * @param sensitivity floating point sensitivity where 0 means never fling and 1 means always
     *                    fling. Values beyond this range will have undefined behavior.
     */
    public void setFlingSensitivity(float sensitivity) {
        flingSensitivity = sensitivity;
    }

    private final class SnapHelper extends PagerSnapHelper {
        @Override
        public boolean onFling(int velocityX, int velocityY) {
            int min = recyclerView.getMinFlingVelocity();
            int max = recyclerView.getMaxFlingVelocity();
            int threshold = (int) ((max * (1.0 - flingSensitivity)) + (min * flingSensitivity));

            if (abs(velocityX) > threshold) {
                return false;
            }
            return super.onFling(velocityX, velocityY);
        }
    }
}
