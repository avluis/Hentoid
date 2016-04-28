package me.devsaki.hentoid.util;

import android.annotation.SuppressLint;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.TextView;

/**
 * Created by avluis on 04/27/2016.
 * Animates our textView if it doesn't fit its parent.
 * <p/>
 * TODO: TextView does not cooperate on long text strings.
 */
public class ScrollTextHelper {
    private final static String TAG = LogHelper.makeLogTag(ScrollTextHelper.class);

    private int maxWidth;
    private TextView textView;
    private View view;

    public ScrollTextHelper(TextView textView, View view) {
        this.textView = textView;
        this.view = view;
        maxWidth = 0;

        animateTextView();
    }

    private void animateTextView() {
        ViewTreeObserver observer = textView.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.addOnGlobalLayoutListener(new ViewTreeObserver
                    .OnGlobalLayoutListener() {
                @SuppressLint("NewApi")
                @SuppressWarnings("deprecation")
                @Override
                public void onGlobalLayout() {
                    // Ensure you call it only once :
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        View parent = (View) view.getParent();
                        maxWidth = parent.getWidth();
                    } else {
                        view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        View parent = (View) view.getParent();
                        maxWidth = parent.getWidth();
                    }

                    LogHelper.d(TAG, "Max Width: " + maxWidth);

                    textView.measure(0, 0);
                    int textWidth = textView.getMeasuredWidth();
                    LogHelper.d(TAG, "Text Width: " + textWidth);

                    if (maxWidth < textWidth) {
                        Animation animation = new TranslateAnimation(0, maxWidth - textWidth, 0, 0);
                        animation.setDuration(3000);
                        animation.setStartOffset(500);
                        animation.setRepeatMode(Animation.REVERSE);
                        animation.setRepeatCount(Animation.INFINITE);

                        textView.startAnimation(animation);
                    }
                }
            });
        }
    }
}