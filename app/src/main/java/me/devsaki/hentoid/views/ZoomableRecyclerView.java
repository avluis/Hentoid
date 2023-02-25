package me.devsaki.hentoid.views;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.function.DoubleConsumer;

import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.widget.OnZoneTapListener;
import me.devsaki.hentoid.widget.ViewZoomGestureListener;
import me.devsaki.hentoid.widget.ViewZoomGestureListener.Listener;
import timber.log.Timber;

/**
 * Zoomable RecyclerView that supports gestures
 * To be used inside a {@link ZoomableFrame}
 * <p>
 * Credits go to the Tachiyomi team
 */
public class ZoomableRecyclerView extends RecyclerView {

    private static final long ANIMATOR_DURATION_TIME = 200;
    private static final float DEFAULT_SCALE = 1f;
    private static final float MAX_SCALE = 3f;

    private boolean isZooming = false;
    private boolean isLongTapZooming = false;

    private boolean atFirstPosition = false;
    private boolean atLastPosition = false;

    private int halfWidth = 0;
    private int halfHeight = 0;
    private int firstVisibleItemPosition = 0;
    private int lastVisibleItemPosition = 0;
    private float scale = DEFAULT_SCALE;

    private final GestureListener listener = new GestureListener();
    private DoubleConsumer scaleListener = null;
    private final Detector detector = new Detector(getContext(), listener);
    private Consumer<Point> getMaxDimensionsListener = null;

    private OnZoneTapListener tapListener;
    private LongTapListener longTapListener;

    // Hack to access these values outside of a View
    private Point maxBitmapDimensions = null;

    private boolean longTapZoomEnabled = true;


    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);
        if (null == maxBitmapDimensions && getMaxDimensionsListener != null) {
            maxBitmapDimensions = new Point(c.getMaximumBitmapWidth(), c.getMaximumBitmapHeight());
            getMaxDimensionsListener.accept(maxBitmapDimensions);
        }
    }

    public void setOnGetMaxDimensionsListener(Consumer<Point> getMaxDimensionsListener) {
        this.getMaxDimensionsListener = getMaxDimensionsListener;
    }


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

    public void setLongTapZoomEnabled(boolean longTapZoomEnabled) {
        this.longTapZoomEnabled = longTapZoomEnabled;
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
        float maxPositionX = halfWidth * (scale - 1);
        return Helper.coerceIn(positionX, -maxPositionX, maxPositionX);
    }

    private float getPositionY(float positionY) {
        float maxPositionY = halfHeight * (scale - 1);
        return Helper.coerceIn(positionY, -maxPositionY, maxPositionY);
    }

    public float getScale() {
        return scale;
    }

    public void resetScale() {
        zoom(scale, DEFAULT_SCALE, getX(), 0f, getY(), 0f);
    }

    private void zoom(
            float fromScale,
            float toScale,
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

        ValueAnimator scaleAnimator = ValueAnimator.ofFloat(fromScale, toScale);
        scaleAnimator.addUpdateListener(animation -> setScaleRate((float) animation.getAnimatedValue()));

        animatorSet.playTogether(translationXAnimator, translationYAnimator, scaleAnimator);
        animatorSet.setDuration(Preferences.isReaderZoomTransitions() ? ANIMATOR_DURATION_TIME : 0);
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
                scale = toScale;
                if (scaleListener != null) scaleListener.accept(scale);
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
        if (getLayoutManager() != null)
            return (getLayoutManager().canScrollVertically() || (getLayoutManager().canScrollHorizontally() && (atFirstPosition || atLastPosition)));
        else return false;
    }

    private boolean canMoveVertically() {
        if (getLayoutManager() != null)
            return (getLayoutManager().canScrollHorizontally() || (getLayoutManager().canScrollVertically() && (atFirstPosition || atLastPosition)));
        else return false;
    }

    boolean zoomFling(int velocityX, int velocityY) {
        if (scale <= DEFAULT_SCALE) return false;

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

    private void setScaleRate(float rate) {
        setScaleX(rate);
        setScaleY(rate);
    }

    void onScale(float scaleFactor) {
        scale *= scaleFactor;
        scale = Helper.coerceIn(scale, DEFAULT_SCALE, MAX_SCALE);

        Timber.i(">> scale %s -> %s", scaleFactor, scale);

        setScaleRate(scale);

        if (scale != DEFAULT_SCALE) {
            setX(getPositionX(getX()));
            setY(getPositionY(getY()));
        } else {
            setX(0f);
            setY(0f);
        }

        if (scaleListener != null) scaleListener.accept(scale);
    }

    void onScaleBegin() {
        if (detector.isDoubleTapping) {
            detector.isQuickScaling = true;
        }
    }

    void onScaleEnd() {
        if (getScaleX() < DEFAULT_SCALE) {
            zoom(scale, DEFAULT_SCALE, getX(), 0f, getY(), 0f);
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
            LayoutManager llm = getLayoutManager();
            if (llm instanceof LinearLayoutManager) {
                int orientation = ((LinearLayoutManager) llm).getOrientation();
                if (tapListener != null && orientation == LinearLayoutManager.VERTICAL)
                    tapListener.onSingleTapConfirmedAction(e);
            }
            return false;
        }

        @Override
        public void onLongTapConfirmed(MotionEvent ev) {
            if (longTapListener != null && longTapListener.onListen(ev)) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            if (longTapZoomEnabled) longTapZoom(ev);
        }

        private void longTapZoom(MotionEvent ev) {
            if (isZooming) return;

            float toScale = 2f;
            float toX = (halfWidth - ev.getX()) * (toScale - 1);
            float toY = (halfHeight - ev.getY()) * (toScale - 1);
            isLongTapZooming = true;
            zoom(DEFAULT_SCALE, toScale, 0f, toX, 0f, toY);
        }

        @Override
        public void onDoubleTapConfirmed(MotionEvent ev) {
            if (!isZooming) {
                if (getScaleX() != DEFAULT_SCALE) {
                    zoom(scale, DEFAULT_SCALE, getX(), 0f, getY(), 0f);
                } else {
                    float toScale = 2f;
                    float toX = (halfWidth - ev.getX()) * (toScale - 1);
                    float toY = (halfHeight - ev.getY()) * (toScale - 1);
                    zoom(DEFAULT_SCALE, toScale, 0f, toX, 0f, toY);
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
                case MotionEvent.ACTION_DOWN:
                    motionActionDownLocal(ev);
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    motionActionPointerDown(ev, actionIndex);
                    break;
                case MotionEvent.ACTION_MOVE:
                    return motionActionMoveLocal(ev);
                case MotionEvent.ACTION_UP:
                    motionActionUpLocal(ev);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    motionActionCancel();
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

        private void motionActionPointerDown(MotionEvent ev, int actionIndex) {
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

            if (!isZoomDragging && scale > DEFAULT_SCALE) {
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
            if (isLongTapZooming) {
                zoom(scale, DEFAULT_SCALE, getX(), 0f, getY(), 0f);
                isLongTapZooming = false;
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

        private void zoomScrollBy(int dx, int dy) {
            if (dx != 0) {
                setX(getPositionX(getX() + dx));
            }
            if (dy != 0) {
                setY(getPositionY(getY() + dy));
            }
        }
    }
}
