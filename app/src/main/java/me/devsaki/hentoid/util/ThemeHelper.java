package me.devsaki.hentoid.util;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.enums.Theme;

import static androidx.lifecycle.Lifecycle.State.CREATED;

public class ThemeHelper {

    private static final int PX_4_DP = Helper.dpToPixel(HentoidApp.getInstance(), 4);

    private ThemeHelper() {
        throw new IllegalStateException("Utility class");
    }


    public static void applyTheme(@NonNull AppCompatActivity activity) {
        Theme theme = Theme.searchById(Preferences.getColorTheme());
        applyTheme(activity, theme);
    }

    public static void applyTheme(@NonNull AppCompatActivity activity, Theme theme) {
        String themeName = getThemeName(activity);

        for (Theme t : Theme.values())
            if (themeName.contains(t.getName())) {
                if (t.equals(theme)) return; // Nothing to do; target theme is already set
                themeName = themeName.replace(t.getName(), theme.getName());
                break;
            }

        activity.setTheme(activity.getResources().getIdentifier(themeName, "style", activity.getPackageName()));
        if (activity.getLifecycle().getCurrentState().isAtLeast(CREATED))
            activity.recreate();
    }

    private static String getThemeName(@NonNull Activity activity) {
        try {
            int themeResourceId = activity.getPackageManager().getActivityInfo(activity.getComponentName(), 0).getThemeResource(); // PackageManager.GET_META_DATA instead of plain 0 ?
            return activity.getResources().getResourceEntryName(themeResourceId);
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    public static int getColor(Context context, String resourceName) { // TODO alternate API with an actual color resource ID ?
        int currentThemeId = Preferences.getColorTheme();
        resourceName += "_" + Theme.searchById(currentThemeId).getName().toLowerCase();
        int resourceId = context.getResources().getIdentifier(resourceName, "color", context.getPackageName()); // TODO cache ?
        return ContextCompat.getColor(context, resourceId);
    }

    public static StateListDrawable makeSelector(Context context) {
        int colorBase = getColor(context, "card_surface");
        int colorPressed = getColor(context, "card_pressed");
        int colorSelected = getColor(context, "card_selected");
        int colorSecondary = getColor(context, "secondary");

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
