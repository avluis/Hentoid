package me.devsaki.hentoid.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Credits go to the Tachiyomi team
 */
open class ViewZoomGestureListenerK(context: Context, private val listener: Listener) :
    GestureDetector(context, listener) {

    private val handler = Handler(Looper.getMainLooper())
    private var scaledTouchSlopslop = 0
    private val longTapTime = ViewConfiguration.getLongPressTimeout()
    private val doubleTapTime = ViewConfiguration.getDoubleTapTimeout()

    private var downX = 0f
    private var downY = 0f
    private var lastUp = 0L
    private var lastDownEvent: MotionEvent? = null

    init {
        scaledTouchSlopslop = ViewConfiguration.get(context).scaledTouchSlop
    }

    /**
     * Runnable to execute when a long tap is confirmed.
     */
    private val longTapFn = Runnable {
        lastDownEvent?.let {
            listener.onLongTapConfirmed(it)
        }
    }


    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> motionActionDown(ev)
            MotionEvent.ACTION_MOVE -> motionActionMove(ev)
            MotionEvent.ACTION_UP -> motionActionUp(ev)
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> motionActionCancelandPointerDown()
            else -> {}
        }
        return super.onTouchEvent(ev)
    }

    private fun motionActionDown(ev: MotionEvent) {
        lastDownEvent?.recycle()
        lastDownEvent = MotionEvent.obtain(ev)

        // This is the key difference with the built-in detector. We have to ignore the
        // event if the last up and current down are too close in time (double tap).
        if (ev.downTime - lastUp > doubleTapTime) {
            downX = ev.rawX
            downY = ev.rawY
            handler.postDelayed(longTapFn, longTapTime.toLong())
        }
    }

    private fun motionActionMove(ev: MotionEvent) {
        if (abs(ev.rawX - downX) > scaledTouchSlopslop || abs(ev.rawY - downY) > scaledTouchSlopslop) {
            handler.removeCallbacks(longTapFn)
        }
    }

    private fun motionActionUp(ev: MotionEvent) {
        lastUp = ev.eventTime
        handler.removeCallbacks(longTapFn)
    }

    private fun motionActionCancelandPointerDown() {
        handler.removeCallbacks(longTapFn)
    }

    /**
     * Custom listener to also include a long tap confirmed
     */
    open class Listener : SimpleOnGestureListener() {
        /**
         * Notified when a long tap occurs with the initial on down [ev] that triggered it.
         */
        open fun onLongTapConfirmed(ev: MotionEvent) {
            // Nothing to see here
        }

        open fun onDoubleTapConfirmed(ev: MotionEvent) {
            // Nothing to see here
        }
    }
}