package me.devsaki.hentoid.views

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Point
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.util.Consumer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.widget.OnZoneTapListener
import me.devsaki.hentoid.widget.ViewZoomGestureListenerK
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Zoomable RecyclerView that supports gestures
 * To be used inside a {@link ZoomableFrame}
 * <p>
 * Credits go to the Tachiyomi team
 */
class ZoomableRecyclerView : RecyclerView {

    private var isZooming = false
    private var isLongTapZooming = false

    private var atFirstPosition = false
    private var atLastPosition = false

    private var halfWidth = 0
    private var halfHeight = 0
    private var firstVisibleItemPosition = 0
    private var lastVisibleItemPosition = 0
    var scale = DEFAULT_SCALE
        private set

    private val listener = GestureListener()
    private var scaleListener: Consumer<Double>? = null
    private val detector = Detector(context, listener)
    private var getMaxDimensionsListener: Consumer<Point>? = null

    private var tapListener: OnZoneTapListener? = null
    private var longTapListener: LongTapListener? = null

    // Hack to access these values outside of a View
    private var maxBitmapDimensions: Point? = null

    private var longTapZoomEnabled = true


    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )


    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (null == maxBitmapDimensions && getMaxDimensionsListener != null) {
            maxBitmapDimensions = Point(c.maximumBitmapWidth, c.maximumBitmapHeight)
            getMaxDimensionsListener!!.accept(maxBitmapDimensions)
        }
    }

    fun setOnGetMaxDimensionsListener(getMaxDimensionsListener: Consumer<Point>?) {
        this.getMaxDimensionsListener = getMaxDimensionsListener
    }

    fun setTapListener(tapListener: OnZoneTapListener?) {
        this.tapListener = tapListener
    }

    fun setLongTapListener(longTapListener: LongTapListener?) {
        this.longTapListener = longTapListener
    }

    fun setOnScaleListener(scaleListener: Consumer<Double>?) {
        this.scaleListener = scaleListener
    }

    fun setLongTapZoomEnabled(longTapZoomEnabled: Boolean) {
        this.longTapZoomEnabled = longTapZoomEnabled
    }

    interface LongTapListener {
        fun onListen(ev: MotionEvent?): Boolean
    }


    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        halfWidth = MeasureSpec.getSize(widthSpec) / 2
        halfHeight = MeasureSpec.getSize(heightSpec) / 2
        super.onMeasure(widthSpec, heightSpec)
    }


    override fun onTouchEvent(e: MotionEvent): Boolean {
        detector.onTouchEvent(e)
        return super.onTouchEvent(e)
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        val layoutManager = layoutManager as LinearLayoutManager?
        if (layoutManager != null) {
            lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
            firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        }
    }


    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        val layoutManager = layoutManager
        if (layoutManager != null) {
            val visibleItemCount = layoutManager.childCount
            val totalItemCount = layoutManager.itemCount
            atLastPosition = visibleItemCount > 0 && lastVisibleItemPosition == totalItemCount - 1
            atFirstPosition = firstVisibleItemPosition == 0
        }
    }

    private fun getPositionX(positionX: Float): Float {
        val maxPositionX = halfWidth * (scale - 1)
        return Helper.coerceIn(positionX, -maxPositionX, maxPositionX)
    }

    private fun getPositionY(positionY: Float): Float {
        val maxPositionY = halfHeight * (scale - 1)
        return Helper.coerceIn(positionY, -maxPositionY, maxPositionY)
    }

    fun resetScale() {
        zoom(scale, DEFAULT_SCALE, x, 0f, y, 0f)
    }

    private fun zoom(
        fromScale: Float,
        toScale: Float,
        fromX: Float,
        toX: Float,
        fromY: Float,
        toY: Float
    ) {
        isZooming = true
        val animatorSet = AnimatorSet()
        val translationXAnimator = ValueAnimator.ofFloat(fromX, toX)
        translationXAnimator.addUpdateListener { animation: ValueAnimator ->
            x = animation.animatedValue as Float
        }
        val translationYAnimator = ValueAnimator.ofFloat(fromY, toY)
        translationYAnimator.addUpdateListener { animation: ValueAnimator ->
            y = animation.animatedValue as Float
        }
        val scaleAnimator = ValueAnimator.ofFloat(fromScale, toScale)
        scaleAnimator.addUpdateListener { animation: ValueAnimator ->
            setScaleRate(animation.animatedValue as Float)
        }
        animatorSet.playTogether(translationXAnimator, translationYAnimator, scaleAnimator)
        animatorSet.duration =
            if (Preferences.isReaderZoomTransitions()) ANIMATOR_DURATION_TIME else 0
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
        animatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                // No need to define any behaviour here
            }

            override fun onAnimationEnd(animation: Animator) {
                isZooming = false
                scale = toScale
                if (scaleListener != null) scaleListener!!.accept(scale.toDouble())
            }

            override fun onAnimationCancel(animation: Animator) {
                // No need to define any behaviour here
            }

            override fun onAnimationRepeat(animation: Animator) {
                // No need to define any behaviour here
            }
        })
    }

    private fun canMoveHorizontally(): Boolean {
        return if (layoutManager != null) layoutManager!!.canScrollVertically() || layoutManager!!.canScrollHorizontally() && (atFirstPosition || atLastPosition) else false
    }

    private fun canMoveVertically(): Boolean {
        return if (layoutManager != null) layoutManager!!.canScrollHorizontally() || layoutManager!!.canScrollVertically() && (atFirstPosition || atLastPosition) else false
    }

    fun zoomFling(velocityX: Int, velocityY: Int): Boolean {
        if (scale <= DEFAULT_SCALE) return false
        val distanceTimeFactor = 0.4f
        var newX: Float? = null
        var newY: Float? = null
        if (velocityX != 0 && canMoveHorizontally()) {
            val dx = distanceTimeFactor * velocityX / 2
            newX = getPositionX(x + dx)
        }
        if (velocityY != 0 && canMoveVertically()) {
            val dy = distanceTimeFactor * velocityY / 2
            newY = getPositionY(y + dy)
        }
        val animation = animate()
        if (newX != null) animation.x(newX)
        if (newY != null) animation.y(newY)
        animation.setInterpolator(DecelerateInterpolator())
            .setDuration(400)
            .start()
        return true
    }

    private fun setScaleRate(rate: Float) {
        scaleX = rate
        scaleY = rate
    }

    fun onScale(scaleFactor: Float) {
        scale *= scaleFactor
        scale = Helper.coerceIn(scale, DEFAULT_SCALE, MAX_SCALE)
        Timber.i(">> scale %s -> %s", scaleFactor, scale)
        setScaleRate(scale)
        if (scale != DEFAULT_SCALE) {
            x = getPositionX(x)
            y = getPositionY(y)
        } else {
            x = 0f
            y = 0f
        }
        if (scaleListener != null) scaleListener!!.accept(scale.toDouble())
    }

    fun onScaleBegin() {
        if (detector.isDoubleTapping) {
            detector.isQuickScaling = true
        }
    }

    fun onScaleEnd() {
        if (scaleX < DEFAULT_SCALE) {
            zoom(scale, DEFAULT_SCALE, x, 0f, y, 0f)
        }
    }

    inner class GestureListener : ViewZoomGestureListenerK.Listener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            detector.isDoubleTapping = true
            return false
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val llm: LayoutManager? = layoutManager
            if (llm is LinearLayoutManager) {
                val orientation = llm.orientation
                if (orientation == LinearLayoutManager.VERTICAL)
                    tapListener?.onSingleTapConfirmedAction(e)
            }
            return false
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            longTapListener?.let {
                if (it.onListen(ev)) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            if (longTapZoomEnabled) longTapZoom(ev)
        }

        private fun longTapZoom(ev: MotionEvent) {
            if (isZooming) return
            val toScale = 2f
            val toX: Float = (halfWidth - ev.x) * (toScale - 1)
            val toY: Float = (halfHeight - ev.y) * (toScale - 1)
            isLongTapZooming = true
            zoom(DEFAULT_SCALE, toScale, 0f, toX, 0f, toY)
        }

        override fun onDoubleTapConfirmed(ev: MotionEvent) {
            if (!isZooming) {
                if (scaleX != DEFAULT_SCALE) {
                    zoom(scale, DEFAULT_SCALE, x, 0f, y, 0f)
                } else {
                    val toScale = 2f
                    val toX: Float = (halfWidth - ev.x) * (toScale - 1)
                    val toY: Float = (halfHeight - ev.y) * (toScale - 1)
                    zoom(DEFAULT_SCALE, toScale, 0f, toX, 0f, toY)
                }
            }
        }
    }

    inner class Detector(context: Context, listener: Listener) :
        ViewZoomGestureListenerK(context, listener) {
        private var scrollPointerId = 0
        private var downX = 0
        private var downY = 0
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var isZoomDragging = false
        var isDoubleTapping = false
        var isQuickScaling = false
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val action = ev.actionMasked
            val actionIndex = ev.actionIndex
            when (action) {
                MotionEvent.ACTION_DOWN -> motionActionDownLocal(ev)
                MotionEvent.ACTION_POINTER_DOWN -> motionActionPointerDown(ev, actionIndex)
                MotionEvent.ACTION_MOVE -> return motionActionMoveLocal(ev)
                MotionEvent.ACTION_UP -> motionActionUpLocal(ev)
                MotionEvent.ACTION_CANCEL -> motionActionCancel()
                else -> {}
            }
            return super.onTouchEvent(ev)
        }

        private fun motionActionDownLocal(ev: MotionEvent) {
            scrollPointerId = ev.getPointerId(0)
            downX = (ev.x + 0.5f).roundToInt()
            downY = (ev.y + 0.5f).roundToInt()
        }

        private fun motionActionPointerDown(ev: MotionEvent, actionIndex: Int) {
            scrollPointerId = ev.getPointerId(actionIndex)
            downX = (ev.getX(actionIndex) + 0.5f).roundToInt()
            downY = (ev.getY(actionIndex) + 0.5f).roundToInt()
        }

        private fun motionActionMoveLocal(ev: MotionEvent): Boolean {
            if (isDoubleTapping && isQuickScaling) {
                return true
            }
            val index = ev.findPointerIndex(scrollPointerId)
            if (index < 0) {
                return false
            }
            val x = (ev.getX(index) + 0.5f).roundToInt()
            val y = (ev.getY(index) + 0.5f).roundToInt()
            var dx = if (canMoveHorizontally()) x - downX else 0
            var dy = if (canMoveVertically()) y - downY else 0
            if (!isZoomDragging && scale > DEFAULT_SCALE) {
                var startScroll = false
                if (abs(dx) > touchSlop) {
                    if (dx < 0) {
                        dx += touchSlop
                    } else {
                        dx -= touchSlop
                    }
                    startScroll = true
                }
                if (abs(dy) > touchSlop) {
                    if (dy < 0) {
                        dy += touchSlop
                    } else {
                        dy -= touchSlop
                    }
                    startScroll = true
                }
                if (startScroll) {
                    isZoomDragging = true
                }
            }
            if (isZoomDragging) {
                zoomScrollBy(dx, dy)
            }
            return super.onTouchEvent(ev)
        }

        private fun motionActionUpLocal(ev: MotionEvent) {
            if (isDoubleTapping && !isQuickScaling) {
                listener.onDoubleTapConfirmed(ev)
            }
            if (isLongTapZooming) {
                zoom(scale, DEFAULT_SCALE, x, 0f, y, 0f)
                isLongTapZooming = false
            }
            isZoomDragging = false
            isDoubleTapping = false
            isQuickScaling = false
        }

        private fun motionActionCancel() {
            isZoomDragging = false
            isDoubleTapping = false
            isQuickScaling = false
        }

        private fun zoomScrollBy(dx: Int, dy: Int) {
            if (dx != 0) {
                x = getPositionX(x + dx)
            }
            if (dy != 0) {
                y = getPositionY(y + dy)
            }
        }
    }

    companion object {
        const val ANIMATOR_DURATION_TIME: Long = 200
        const val DEFAULT_SCALE = 1f
        const val MAX_SCALE = 3f
    }
}