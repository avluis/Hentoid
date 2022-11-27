package me.devsaki.hentoid.views;

import static androidx.core.view.inputmethod.EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING;

import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebView;

import com.annimon.stream.function.BiConsumer;

import me.devsaki.hentoid.util.Debouncer;


/**
 * WebView implementation which allows setting arbitrary thresholds long clicks
 */
public class VariableLongClickWebView extends WebView {
    // The minimum duration to hold down a click to register as a long click (in ms).
    // Default is 500ms (This is the same as the android system's default).
    private int longClickThreshold = 500;

    private Debouncer<Point> longTapDebouncer;

    private BiConsumer<Integer, Integer> onLongClickListener;

    public VariableLongClickWebView(final Context context) {
        super(context);
        init(longClickThreshold);
    }

    public VariableLongClickWebView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init(longClickThreshold);
    }

    public VariableLongClickWebView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        init(longClickThreshold);
    }

    private void init(long longTapDebouncerThreshold) {
        longTapDebouncer = new Debouncer<>(getContext(), longTapDebouncerThreshold, point -> {
            if (onLongClickListener != null) {
                onLongClickListener.accept(point.x, point.y);
            }
        });
    }

    public void setOnLongTapListener(BiConsumer<Integer, Integer> onLongClickListener) {
        this.onLongClickListener = onLongClickListener;
    }

    public void setLongClickThreshold(int threshold) {
        longClickThreshold = threshold;
        init(threshold);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                longTapDebouncer.submit(new Point((int) event.getX(), (int) event.getY()));
                break;

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                longTapDebouncer.clear();
                break;

            default:
                // No default action
        }

        return super.onTouchEvent(event);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo info) {
        InputConnection connection = super.onCreateInputConnection(info);
        info.imeOptions |= IME_FLAG_NO_PERSONALIZED_LEARNING;
        return connection;
    }
}
