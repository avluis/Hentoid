package me.devsaki.hentoid.widget

import android.annotation.SuppressLint
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import androidx.core.view.GestureDetectorCompat
import me.devsaki.hentoid.R

/**
 * Zoned tap listener for the reader
 */
class OnZoneTapListenerK(val view: View, tapZoneScale: Int) : OnTouchListener {
    /**
     * This view's dimensions are used to determine which zone a tap belongs to
     */
    private val gestureDetector: GestureDetectorCompat

    private var pagerTapZoneWidth = 0


    private var onLeftZoneTapListener: Runnable? = null

    private var onRightZoneTapListener: Runnable? = null

    private var onMiddleZoneTapListener: Runnable? = null

    private var onLongTapListener: Runnable? = null

    init {
        val context = view.context
        gestureDetector = GestureDetectorCompat(context, OnGestureListener())
        pagerTapZoneWidth =
            context.resources.getDimensionPixelSize(R.dimen.tap_zone_width) * tapZoneScale
    }

    fun setOnLeftZoneTapListener(onLeftZoneTapListener: Runnable?): OnZoneTapListenerK {
        this.onLeftZoneTapListener = onLeftZoneTapListener
        return this
    }

    fun setOnRightZoneTapListener(onRightZoneTapListener: Runnable?): OnZoneTapListenerK {
        this.onRightZoneTapListener = onRightZoneTapListener
        return this
    }

    fun setOnMiddleZoneTapListener(onMiddleZoneTapListener: Runnable?): OnZoneTapListenerK {
        this.onMiddleZoneTapListener = onMiddleZoneTapListener
        return this
    }

    fun setOnLongTapListener(onLongTapListener: Runnable?): OnZoneTapListenerK {
        this.onLongTapListener = onLongTapListener
        return this
    }

    fun onSingleTapConfirmedAction(e: MotionEvent): Boolean {
        if (e.x < pagerTapZoneWidth && onLeftZoneTapListener != null) {
            onLeftZoneTapListener!!.run()
        } else if (e.x > view.width - pagerTapZoneWidth && onRightZoneTapListener != null) {
            onRightZoneTapListener!!.run()
        } else {
            if (onMiddleZoneTapListener != null) onMiddleZoneTapListener!!.run() else return false
        }
        return true
    }

    fun onLongPressAction() {
        onLongTapListener?.run()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }

    inner class OnGestureListener : SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            return onSingleTapConfirmedAction(e)
        }

        override fun onLongPress(e: MotionEvent) {
            onLongPressAction()
        }
    }
}