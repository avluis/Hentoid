package me.devsaki.hentoid.components;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;

import me.devsaki.hentoid.R;

/**
 * Created by neko on 07/06/2015.
 */
public class GifWebView extends WebView{

    public GifWebView(Context context) {
        super(context);

        loadDataWithBaseURL(null, getResources().getString(R.string.image_loading),
                "text/html", "utf-8", null);
    }

    public GifWebView(Context context, AttributeSet attrs) {
        super(context, attrs);

        loadDataWithBaseURL(null, getResources().getString(R.string.image_loading),
                "text/html", "utf-8", null);
    }

    public GifWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        loadDataWithBaseURL(null, getResources().getString(R.string.image_loading),
                "text/html", "utf-8", null);
    }
}