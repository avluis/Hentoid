package me.devsaki.hentoid.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;


/**
 * WebView implementation which allows setting arbitrary thresholds long clicks
 */
public class VariableLongClickWebView extends WebView {
    // The minimum duration to hold down a click to register as a long click
    // (in ms). Default is 500ms (This is the same as the android system's
    // default).
    private int longClickThreshold = 500;

    private Handler handler;
    private static final int MESSAGE_LONG_CLICK = 1;

    private OnLongClickListener onLongClickListener;

    public VariableLongClickWebView(final Context context) {
        super(context);
        init();
    }

    public VariableLongClickWebView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VariableLongClickWebView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        handler = new Handler(Looper.getMainLooper(), message -> {
            if (message.what == MESSAGE_LONG_CLICK) {
                if (onLongClickListener != null) {
                    super.setOnLongClickListener(onLongClickListener);
                    performLongClick();
                    super.setOnLongClickListener(null);
                }
            }
            return true;
        });
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener onLongClickListener) {
        this.onLongClickListener = onLongClickListener;
    }

    public int getLongClickThreshold() {
        return longClickThreshold;
    }

    public void setLongClickThreshold(int threshold) {
        longClickThreshold = threshold;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                Message m = new Message();
                m.what = MESSAGE_LONG_CLICK;
                handler.sendMessageDelayed(m, longClickThreshold);
                break;

            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeMessages(MESSAGE_LONG_CLICK);
                break;

            default:
                // No default action
        }

        return super.onTouchEvent(event);
    }
}
