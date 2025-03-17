package me.devsaki.hentoid.views

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.widget.FrameLayout
import androidx.core.view.isNotEmpty
import kotlin.math.roundToInt

/**
 * Frame layout which contains a [ZoomableRecyclerView]. It's needed to handle touch events,
 * because the recyclerview is scaled and its touch events are translated, which breaks the
 * detectors.
 * <p>
 * Credits for this go to the Tachiyomi team
 */
class ZoomableFrame : FrameLayout {

    var frameEnabled = true


    constructor (context: Context) : super(context)

    constructor (context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor (context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )


    /**
     * Scale detector, either with pinch or quick scale.
     */
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    /**
     * Fling detector.
     */
    private val flingDetector = GestureDetector(context, FlingListener())

    /**
     * Recycler view added in this frame.
     */
    private var recycler: ZoomableRecyclerView? = null

    private fun getRecycler(): ZoomableRecyclerView? {
        if (null == recycler && isNotEmpty()) recycler = getChildAt(0) as ZoomableRecyclerView
        return recycler
    }


    /**
     * Dispatches a touch event to the detectors.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (frameEnabled) {
            scaleDetector.onTouchEvent(ev)
            flingDetector.onTouchEvent(ev)
        }
        // Must return the default value; if not, events don't even reach the RecyclerView
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Scale listener used to delegate events to the recycler view.
     */
    inner class ScaleListener : SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (frameEnabled) getRecycler()?.onScaleBegin()
            return frameEnabled
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (frameEnabled) getRecycler()?.onScale(detector.scaleFactor)
            return frameEnabled
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (frameEnabled) getRecycler()?.onScaleEnd()
        }
    }

    /**
     * Fling listener used to delegate events to the recycler view.
     */
    inner class FlingListener : SimpleOnGestureListener() {

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (!frameEnabled) return false
            return getRecycler()?.zoomFling(velocityX.roundToInt(), velocityY.roundToInt()) == true
        }

        override fun onDown(e: MotionEvent): Boolean {
            return frameEnabled
        }
    }
}