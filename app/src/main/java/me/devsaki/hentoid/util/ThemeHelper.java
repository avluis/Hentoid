package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;

public class ThemeHelper {

    private static final int PX_4_DP = Helper.dpToPixel(HentoidApp.getInstance(), 4);

    private ThemeHelper() {
        throw new IllegalStateException("Utility class");
    }


    public static void applyTheme(Context context) {
        String currentThemeName = getThemeName(context);
        boolean isAmoled = Preferences.isDarkModeAmoled();
    }

    private static String getThemeName(@NonNull Context context) {
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            int themeResId = packageInfo.applicationInfo.theme;
            return context.getResources().getResourceEntryName(themeResId);
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    public static int getColor(Context context, String resourceName, boolean isAmoled) {
        if (isAmoled) resourceName += "_amoled";
        int resourceId = context.getResources().getIdentifier(resourceName, "color", context.getPackageName()); // TODO cache ?
        return ContextCompat.getColor(context, resourceId);
    }

    public static StateListDrawable makeSelector(Context context) {
        boolean isAmoled = Preferences.isDarkModeAmoled();
        int colorBase = getColor(context, "card_surface", isAmoled);
        int colorPressed = getColor(context, "card_pressed", isAmoled);
        int colorSelected = getColor(context, "card_selected", isAmoled);
        int colorSecondary = ContextCompat.getColor(context, R.color.secondary);

        StateListDrawable res = new StateListDrawable();
        res.addState(new int[]{-android.R.attr.state_selected}, makeSelectorShape(colorBase, false, 0));
        res.addState(new int[]{android.R.attr.state_pressed}, makeSelectorShape(colorPressed, false, 0));
        res.addState(new int[]{-android.R.attr.state_pressed, android.R.attr.state_selected}, makeSelectorShape(colorSelected, true, colorSecondary));
        return res;
    }

    private static GradientDrawable makeSelectorShape(@ColorInt int bgColor, boolean stroke, @ColorInt int strokeColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(PX_4_DP);
        shape.setColor(bgColor);
        if (stroke) shape.setStroke(PX_4_DP, strokeColor);
        return shape;
    }
}
