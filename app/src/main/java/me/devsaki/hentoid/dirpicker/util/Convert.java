package me.devsaki.hentoid.dirpicker.util;

import android.content.Context;
import android.util.DisplayMetrics;

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
}
