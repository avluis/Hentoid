package me.devsaki.hentoid.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.AttributeSet;
import android.webkit.WebView;

/**
 * WebView implementation with scroll listener
 *
 * Ref: http://stackoverflow.com/questions/14752523/
 */
public class ObservableWebView extends WebView {
    private OnScrollChangedCallback mOnScrollChangedCallback;

    public ObservableWebView(final Context context) {
        super(getFixedContext(context));
    }

    public ObservableWebView(final Context context, final AttributeSet attrs) {
        super(getFixedContext(context), attrs);
    }

    public ObservableWebView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(getFixedContext(context), attrs, defStyle);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ObservableWebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(getFixedContext(context), attrs, defStyleAttr, defStyleRes);
    }

    @Deprecated
    public ObservableWebView(Context context, AttributeSet attrs, int defStyleAttr, boolean privateBrowsing) {
        super(getFixedContext(context), attrs, defStyleAttr, privateBrowsing);
    }

    // Fix for inflating on Android 5.1.1
    // https://stackoverflow.com/questions/41025200/android-view-inflateexception-error-inflating-class-android-webkit-webview
    private static Context getFixedContext(Context context) {
        return context.createConfigurationContext(new Configuration());
    }

    @Override
    protected void onScrollChanged(final int l, final int t, final int oldl, final int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        int deltaX = l - oldl;
        int deltaY = t - oldt;
        if (mOnScrollChangedCallback != null) mOnScrollChangedCallback.onScroll(deltaX, deltaY);
    }

    public OnScrollChangedCallback getOnScrollChangedCallback() {
        return mOnScrollChangedCallback;
    }

    public void setOnScrollChangedCallback(final OnScrollChangedCallback onScrollChangedCallback) {
        mOnScrollChangedCallback = onScrollChangedCallback;
    }

    /**
     * Implement in the activity/fragment/view that you want to listen to the WebView
     */
    public interface OnScrollChangedCallback {
        void onScroll(int deltaX, int deltaY);
    }
}
