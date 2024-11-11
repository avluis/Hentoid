package me.devsaki.hentoid.widget

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import java.lang.ref.WeakReference

/**
 * Zoned tap listener for the reader
 */
class OnZoneTapListener(view: View, private val tapZoneScale: Int) : OnTouchListener {
    /**
     * This view's dimensions are used to determine which zone a tap belongs to
     */
    private val gestureDetector: ViewZoomGestureListener

    private val view: WeakReference<View>

    private var onLeftZoneTapListener: Runnable? = null

    private var onRightZoneTapListener: Runnable? = null

    private var onMiddleZoneTapListener: Runnable? = null

    private var onLongTapListener: Runnable? = null

    init {
        gestureDetector = ViewZoomGestureListener(view.context, GestureListener())
        this.view = WeakReference(view)
    }

    private fun getPagerTapZoneWidth(parentWidth: Int): Int {
        return (parentWidth * 0.18 * tapZoneScale).toInt()
    }

    fun setOnLeftZoneTapListener(onLeftZoneTapListener: Runnable?): OnZoneTapListener {
        this.onLeftZoneTapListener = onLeftZoneTapListener
        return this
    }

    fun setOnRightZoneTapListener(onRightZoneTapListener: Runnable?): OnZoneTapListener {
        this.onRightZoneTapListener = onRightZoneTapListener
        return this
    }

    fun setOnMiddleZoneTapListener(onMiddleZoneTapListener: Runnable?): OnZoneTapListener {
        this.onMiddleZoneTapListener = onMiddleZoneTapListener
        return this
    }

    fun setOnLongTapListener(onLongTapListener: Runnable?): OnZoneTapListener {
        this.onLongTapListener = onLongTapListener
        return this
    }

    fun onSingleTapConfirmedAction(e: MotionEvent): Boolean {
        view.get()?.let {
            if (e.rawX < getPagerTapZoneWidth(it.width) && onLeftZoneTapListener != null) {
                onLeftZoneTapListener?.run()
            } else if (e.rawX > it.width - getPagerTapZoneWidth(it.width) && onRightZoneTapListener != null) {
                onRightZoneTapListener?.run()
            } else {
                if (onMiddleZoneTapListener != null) onMiddleZoneTapListener?.run()
                else return false
            }
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

    inner class GestureListener : ViewZoomGestureListener.Listener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            return onSingleTapConfirmedAction(e)
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            onLongPressAction()
        }
    }
}