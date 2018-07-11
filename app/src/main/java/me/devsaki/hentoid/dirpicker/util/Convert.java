package me.devsaki.hentoid.dirpicker.util;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.TypedValue;

/**
 * Created by avluis on 06/12/2016.
 * Conversion Utility
 */
public class Convert {

    public static int dpToPixel(Context context, int dp) {
        float scaleFactor =
                (1.0f / DisplayMetrics.DENSITY_DEFAULT)
                        * context.getResources().getDisplayMetrics().densityDpi;

        return (int) (dp * scaleFactor);
    }

    public static int spToPixel(Context context, float sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.getResources().getDisplayMetrics());
    }
}
