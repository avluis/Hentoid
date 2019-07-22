package me.devsaki.hentoid.views;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.function.DoubleConsumer;

import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.widget.OnZoneTapListener;
import me.devsaki.hentoid.widget.ViewZoomGestureListener;
import me.devsaki.hentoid.widget.ViewZoomGestureListener.Listener;

/**
 * Zoomable RecyclerView that supports gestures
 * To be used inside a {@link ZoomableFrame}
 * <p>
 * Credits go to the Tachiyomi team
 */
public class ZoomableRecyclerView extends RecyclerView {

    private static final long ANIMATOR_DURATION_TIME = 200;
    private static final float DEFAULT_RATE = 1f;
    private static final float MAX_SCALE_RATE = 3f;

    private boolean isZooming = false;
    private boolean atLastPosition = false;

    private boolean atFirstPosition = false;
    private int halfWidth = 0;
    private int halfHeight = 0;
    private int firstVisibleItemPosition = 0;
    private int lastVisibleItemPosition = 0;
    private float currentScale = DEFAULT_RATE;

    private GestureListener listener = new GestureListener();
    private DoubleConsumer scaleListener = null;
    private Detector detector = new Detector(getContext(), listener);

    private OnZoneTapListener tapListener;
    private LongTapListener longTapListener;


    public ZoomableRecyclerView(Context context) {
        super(context);
    }

    public ZoomableRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ZoomableRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }


    public void setTapListener(OnZoneTapListener tapListener) {
        this.tapListener = tapListener;
    }

    public void setLongTapListener(LongTapListener longTapListener) {
        this.longTapListener = longTapListener;
    }

    public void setOnScaleListener(DoubleConsumer scaleListener) {
        this.scaleListener = scaleListener;
    }

    public interface LongTapListener {
        boolean onListen(MotionEvent ev);
    }


    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        halfWidth = MeasureSpec.getSize(widthSpec) / 2;
        halfHeight = MeasureSpec.getSize(heightSpec) / 2;
        super.onMeasure(widthSpec, heightSpec);
    }


    @Override
    public boolean onTouchEvent(MotionEvent e) {
        detector.onTouchEvent(e);
        return super.onTouchEvent(e);
    }

    @Override
    public void onScrolled(int dx, int dy) {
        super.onScrolled(dx, dy);
        LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
        if (layoutManager != null) {
            lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();
            firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
        }
    }


    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onScrollStateChanged(int state) {
        super.onScrollStateChanged(state);
        LayoutManager layoutManager = getLayoutManager();
        if (layoutManager != null) {
            int visibleItemCount = layoutManager.getChildCount();
            int totalItemCount = layoutManager.getItemCount();
            atLastPosition = visibleItemCount > 0 && lastVisibleItemPosition == totalItemCount - 1;
            atFirstPosition = firstVisibleItemPosition == 0;
        }
    }

    private float getPositionX(float positionX) {
        float maxPositionX = halfWidth * (currentScale - 1);
        return Helper.coerceIn(positionX, -maxPositionX, maxPositionX);
    }

    private float getPositionY(float positionY) {
        float maxPositionY = halfHeight * (currentScale - 1);
        return Helper.coerceIn(positionY, -maxPositionY, maxPositionY);
    }

    public float getCurrentScale() {
        return currentScale;
    }

    public void resetScale() {
        zoom(currentScale, DEFAULT_RATE, getX(), 0f, getY(), 0f);
    }

    private void zoom(
            float fromRate,
            float toRate,
            float fromX,
            float toX,
            float fromY,
            float toY
    ) {
        isZooming = true;
        AnimatorSet animatorSet = new AnimatorSet();

        ValueAnimator translationXAnimator = ValueAnimator.ofFloat(fromX, toX);
        translationXAnimator.addUpdateListener(animation -> setX((float) animation.getAnimatedValue()));

        ValueAnimator translationYAnimator = ValueAnimator.ofFloat(fromY, toY);
        translationYAnimator.addUpdateListener(animation -> setY((float) animation.getAnimatedValue()));

        ValueAnimator scaleAnimator = ValueAnimator.ofFloat(fromRate, toRate);
        scaleAnimator.addUpdateListener(animation -> setScaleRate((float) animation.getAnimatedValue()));

        animatorSet.playTogether(translationXAnimator, translationYAnimator, scaleAnimator);
        animatorSet.setDuration(ANIMATOR_DURATION_TIME);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.start();
        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                // No need to define any behaviour here
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                isZooming = false;
                currentScale = toRate;
                if (scaleListener != null) scaleListener.accept(currentScale);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // No need to define any behaviour here
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                // No need to define any behaviour here
            }
        });
    }

    private boolean canMoveHorizontally() {
        return (getLayoutManager().canScrollVertically() || (getLayoutManager().canScrollHorizontally() && (atFirstPosition || atLastPosition)));
    }

    private boolean canMoveVertically() {
        return (getLayoutManager().canScrollHorizontally() || (getLayoutManager().canScrollVertically() && (atFirstPosition || atLastPosition)));
    }

    boolean zoomFling(int velocityX, int velocityY) {
        if (currentScale <= 1f) return false;

        float distanceTimeFactor = 0.4f;
        Float newX = null;
        Float newY = null;

        if (velocityX != 0 && canMoveHorizontally()) {
            float dx = (distanceTimeFactor * velocityX / 2);
            newX = getPositionX(getX() + dx);
        }
        if (velocityY != 0 && canMoveVertically()) {
            float dy = (distanceTimeFactor * velocityY / 2);
            newY = getPositionY(getY() + dy);
        }

        ViewPropertyAnimator animation = animate();
        if (newX != null) animation.x(newX);
        if (newY != null) animation.y(newY);
        animation.setInterpolator(new DecelerateInterpolator())
                .setDuration(400)
                .start();

        return true;
    }

    private void zoomScrollBy(int dx, int dy) {
        if (dx != 0) {
            setX(getPositionX(getX() + dx));
        }
        if (dy != 0) {
            setY(getPositionY(getY() + dy));
        }
    }

    private void setScaleRate(float rate) {
        setScaleX(rate);
        setScaleY(rate);
    }

    void onScale(float scaleFactor) {
        currentScale *= scaleFactor;
        currentScale = Helper.coerceIn(currentScale, DEFAULT_RATE, MAX_SCALE_RATE);

        setScaleRate(currentScale);

        if (currentScale != DEFAULT_RATE) {
            setX(getPositionX(getX()));
            setY(getPositionY(getY()));
        } else {
            setX(0f);
            setY(0f);
        }

        if (scaleListener != null) scaleListener.accept(currentScale);
    }

    void onScaleBegin() {
        if (detector.isDoubleTapping) {
            detector.isQuickScaling = true;
        }
    }

    void onScaleEnd() {
        if (getScaleX() < DEFAULT_RATE) {
            zoom(currentScale, DEFAULT_RATE, getX(), 0f, getY(), 0f);
        }
    }

    class GestureListener extends Listener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            detector.isDoubleTapping = true;
            return false;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (tapListener != null) tapListener.onSingleTapConfirmedAction(e);
            return false;
        }

        @Override
        public void onLongTapConfirmed(MotionEvent ev) {
            if (longTapListener != null && longTapListener.onListen(ev)) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        }

        @Override
        public void onDoubleTapConfirmed(MotionEvent ev) {
            if (!isZooming) {
                if (getScaleX() != DEFAULT_RATE) {
                    zoom(currentScale, DEFAULT_RATE, getX(), 0f, getY(), 0f);
                } else {
                    float toScale = 2f;
                    float toX = (halfWidth - ev.getX()) * (toScale - 1);
                    float toY = (halfHeight - ev.getY()) * (toScale - 1);
                    zoom(DEFAULT_RATE, toScale, 0f, toX, 0f, toY);
                }
            }
        }
    }

    class Detector extends ViewZoomGestureListener {

        private int scrollPointerId = 0;
        private int downX = 0;
        private int downY = 0;
        private final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        private boolean isZoomDragging = false;
        boolean isDoubleTapping = false;
        boolean isQuickScaling = false;

        Detector(Context context, Listener listener) {
            super(context, listener);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            int action = ev.getActionMasked();
            int actionIndex = ev.getActionIndex();

            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    motionActionDownLocal(ev);
                }
                break;
                case MotionEvent.ACTION_POINTER_DOWN: {
                    motionActionPointerDown(ev, actionIndex);
                }
                break;
                case MotionEvent.ACTION_MOVE: {
                    return motionActionMoveLocal(ev);
                }
                case MotionEvent.ACTION_UP: {
                    motionActionUpLocal(ev);
                    break;
                }
                case MotionEvent.ACTION_CANCEL: {
                    motionActionCancel();
                }
                break;
                default:
                    // Nothing to process as default
            }
            return super.onTouchEvent(ev);
        }

        private void motionActionDownLocal(MotionEvent ev) {
            scrollPointerId = ev.getPointerId(0);
            downX = Math.round(ev.getX() + 0.5f);
            downY = Math.round(ev.getY() + 0.5f);
        }

        private void motionActionPointerDown(MotionEvent ev, int actionIndex){
            scrollPointerId = ev.getPointerId(actionIndex);
            downX = Math.round(ev.getX(actionIndex) + 0.5f);
            downY = Math.round(ev.getY(actionIndex) + 0.5f);
        }

        private boolean motionActionMoveLocal(MotionEvent ev) {
            if (isDoubleTapping && isQuickScaling) {
                return true;
            }

            int index = ev.findPointerIndex(scrollPointerId);
            if (index < 0) {
                return false;
            }

            int x = Math.round(ev.getX(index) + 0.5f);
            int y = Math.round(ev.getY(index) + 0.5f);
            int dx = (canMoveHorizontally()) ? x - downX : 0;
            int dy = (canMoveVertically()) ? y - downY : 0;

            if (!isZoomDragging && currentScale > 1f) {
                boolean startScroll = false;

                if (Math.abs(dx) > touchSlop) {
                    if (dx < 0) {
                        dx += touchSlop;
                    } else {
                        dx -= touchSlop;
                    }
                    startScroll = true;
                }
                if (Math.abs(dy) > touchSlop) {
                    if (dy < 0) {
                        dy += touchSlop;
                    } else {
                        dy -= touchSlop;
                    }
                    startScroll = true;
                }

                if (startScroll) {
                    isZoomDragging = true;
                }
            }

            if (isZoomDragging) {
                zoomScrollBy(dx, dy);
            }
            return super.onTouchEvent(ev);
        }

        private void motionActionUpLocal(MotionEvent ev) {
            if (isDoubleTapping && !isQuickScaling) {
                listener.onDoubleTapConfirmed(ev);
            }
            isZoomDragging = false;
            isDoubleTapping = false;
            isQuickScaling = false;
        }

        private void motionActionCancel() {
            isZoomDragging = false;
            isDoubleTapping = false;
            isQuickScaling = false;
        }
    }
}
