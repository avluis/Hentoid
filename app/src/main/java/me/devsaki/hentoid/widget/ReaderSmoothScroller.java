package me.devsaki.hentoid.widget;

import android.content.Context;
import android.graphics.PointF;
import android.util.DisplayMetrics;

import androidx.recyclerview.widget.LinearSmoothScroller;

import timber.log.Timber;

// See https://stackoverflow.com/questions/32459696/get-scroll-y-of-recyclerview-or-webview
public class ReaderSmoothScroller extends LinearSmoothScroller {
    private float speed = 25f;// LinearSmoothScroller.MILLISECONDS_PER_INCH

    private int currentScrollY = 0;
    private int itemHeight = 0;

    public ReaderSmoothScroller(Context context) {
        super(context);
    }

    @Override
    protected int getVerticalSnapPreference() {
        return LinearSmoothScroller.SNAP_TO_START;
    }

    @Override
    protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
        return speed / displayMetrics.densityDpi;
    }

    public void setSpeed(float speed) {
        Timber.i("SPEED : %s", speed);
        this.speed = speed;
    }

    public void setCurrentPositionY(int position) {
        currentScrollY = position;
    }

    public void setItemHeight(int height) {
        itemHeight = height;
    }

    @Override
    public PointF computeScrollVectorForPosition(int targetPosition) {
        int yDelta = calculateCurrentDistanceToPosition(targetPosition);
        return new PointF(0, yDelta);
    }

    private int calculateCurrentDistanceToPosition(int targetPosition) {
        int targetScrollY = targetPosition * itemHeight;
        return targetScrollY - currentScrollY;
    }

    // Hack to avoid the scrolling distance being capped when target item has not been laid out
    @Override
    protected void updateActionForInterimTarget(Action action) {
        // find an interim target position
        PointF scrollVector = computeScrollVectorForPosition(getTargetPosition());
        if (scrollVector == null || (scrollVector.x == 0 && scrollVector.y == 0)) {
            final int target = getTargetPosition();
            action.jumpTo(target);
            stop();
            return;
        }
        //normalize(scrollVector); we don't want that
        mTargetVector = scrollVector;

        mInterimTargetDx = (int) scrollVector.x;
        mInterimTargetDy = (int) scrollVector.y;
        final int time = calculateTimeForScrolling(mInterimTargetDy);
        // To avoid UI hiccups, trigger a smooth scroll to a distance little further than the
        // interim target. Since we track the distance travelled in onSeekTargetStep callback, it
        // won't actually scroll more than what we need.
        action.update(mInterimTargetDx, mInterimTargetDy, time, mLinearInterpolator);
    }
}
