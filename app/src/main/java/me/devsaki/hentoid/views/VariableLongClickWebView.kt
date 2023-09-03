package me.devsaki.hentoid.views

import android.content.Context
import android.graphics.Point
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.annimon.stream.function.BiConsumer
import me.devsaki.hentoid.util.Debouncer

/**
 * WebView implementation which allows setting arbitrary thresholds long clicks
 */
open class VariableLongClickWebView : WebView {
    // The minimum duration to hold down a click to register as a long click (in ms).
    // Default is 500ms (This is the same as the android system's default).
    private var longClickThreshold = 500

    private var onLongClickListener: BiConsumer<Int, Int>? = null

    private lateinit var longTapDebouncer: Debouncer<Point>

    constructor(context: Context) : super(context) {
        init(longClickThreshold.toLong())
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(longClickThreshold.toLong())
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(longClickThreshold.toLong())
    }

    private fun init(longTapDebouncerThreshold: Long) {
        longTapDebouncer =
            Debouncer(findViewTreeLifecycleOwner()!!.lifecycleScope, longTapDebouncerThreshold)
            { point: Point -> onLongClickListener?.accept(point.x, point.y) }
    }

    fun setOnLongTapListener(onLongClickListener: BiConsumer<Int, Int>?) {
        this.onLongClickListener = onLongClickListener
    }

    fun setLongClickThreshold(threshold: Int) {
        longClickThreshold = threshold
        init(threshold.toLong())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> longTapDebouncer.submit(
                Point(event.x.toInt(), event.y.toInt())
            )

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> longTapDebouncer.clear()
            else -> {}
        }
        return super.onTouchEvent(event)
    }

    override fun onCreateInputConnection(info: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(info)
        info.imeOptions = info.imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
        return connection
    }
}