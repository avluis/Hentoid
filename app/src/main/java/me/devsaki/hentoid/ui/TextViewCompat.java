package me.devsaki.hentoid.ui;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Created by avluis on 09/08/2016.
 * Simple override for TextView widget to avoid android:ellipsize="start" bug"
 * Ref: https://code.google.com/p/android/issues/detail?id=33868
 */

public class TextViewCompat extends TextView {

    public TextViewCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    public TextViewCompat(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public TextViewCompat(Context context) {
        super(context);

        init();
    }

    private void init() {
        if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT <= 19) {
            if (getMaxLines() == 1) {
                setSingleLine(true);
            }
        }
    }
}
