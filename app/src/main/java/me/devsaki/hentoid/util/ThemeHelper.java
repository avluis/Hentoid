package me.devsaki.hentoid.util;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import java.util.HashMap;
import java.util.Map;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Theme;

public class ThemeHelper {

    private static final int PX_4_DP = Helper.dpToPixel(HentoidApp.getInstance(), 4);
    private static final Map<String, Integer> COLOR_CACHE = new HashMap<>();

    private ThemeHelper() {
        throw new IllegalStateException("Utility class");
    }


    public static void applyTheme(@NonNull AppCompatActivity activity) {
        Theme theme = Theme.searchById(Preferences.getColorTheme());
        applyTheme(activity, theme);
    }

    public static void applyTheme(@NonNull AppCompatActivity activity, Theme targetTheme) {
        String themeName = renameTheme(getThemeName(activity), targetTheme);
        if (themeName.equals(targetTheme.getName())) return; // Nothing to do

        activity.setTheme(getThemeId(activity, themeName));
    }

    public static void setStyle(@NonNull Context context, @NonNull DialogFragment dialog, int style, @StyleRes int themeResourceId) {
        String themeName = context.getResources().getResourceEntryName(themeResourceId);
        int themeId = getThemeId(context, renameThemeToCurrentTheme(themeName));

        dialog.setStyle(style, themeId);
    }

    public static int getIdForCurrentTheme(@NonNull Context context, @StyleRes int themeResourceId) {
        String themeName = getThemeName(context, themeResourceId);
        themeName = renameThemeToCurrentTheme(themeName);
        return getThemeId(context, themeName);
    }

    private static String getThemeName(@NonNull Activity activity) {
        try {
            int themeResourceId = activity.getPackageManager().getActivityInfo(activity.getComponentName(), 0).getThemeResource(); // PackageManager.GET_META_DATA instead of plain 0 ?
            return getThemeName(activity, themeResourceId);
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private static String getThemeName(@NonNull Context context, @StyleRes int themeResourceId) {
        return context.getResources().getResourceEntryName(themeResourceId);
    }

    private static String getColorName(@NonNull Context context, @ColorRes int colorResourceId) {
        return context.getResources().getResourceEntryName(colorResourceId);
    }

    private static int getThemeId(@NonNull Context context, @NonNull String themeName) {
        return context.getResources().getIdentifier(themeName, "style", context.getPackageName());
    }

    private static int getColorId(@NonNull Context context, @NonNull String colorName) {
        return context.getResources().getIdentifier(colorName, "color", context.getPackageName());
    }

    private static String renameThemeToCurrentTheme(@NonNull String themeName) {
        Theme targetTheme = Theme.searchById(Preferences.getColorTheme());
        return renameTheme(themeName, targetTheme);
    }

    private static String renameTheme(@NonNull String themeName, @NonNull Theme targetTheme) {
        for (Theme t : Theme.values())
            if (themeName.contains(t.getName())) {
                if (t.equals(targetTheme))
                    return themeName; // Nothing to do; target theme is already set
                themeName = themeName.replace(t.getName(), targetTheme.getName());
                break;
            }
        return themeName;
    }

    private static String renameColorToCurrentTheme(@NonNull String colorName) {
        Theme targetTheme = Theme.searchById(Preferences.getColorTheme());
        return renameColorToTheme(colorName, targetTheme);
    }

    private static String renameColorToTheme(@NonNull String colorName, @NonNull Theme targetTheme) {
        for (Theme t : Theme.values())
            if (colorName.contains("_" + t.getName().toLowerCase())) {
                if (t.equals(targetTheme))
                    return colorName; // Nothing to do; target theme is already set
                colorName = colorName.replace("_" + t.getName().toLowerCase(), "_" + targetTheme.getName().toLowerCase());
                break;
            }
        return colorName;
    }

    public static int getColor(Context context, @ColorRes int colorId) {
        String key = Preferences.getColorTheme() + "." + colorId;
        if (COLOR_CACHE.containsKey(key)) {
            Integer result = COLOR_CACHE.get(key);
            return (null == result) ? 0 : result;
        }

        String colorName = renameColorToCurrentTheme(getColorName(context, colorId));
        int result = ContextCompat.getColor(context, getColorId(context, colorName));
        COLOR_CACHE.put(key, result);
        return result;
    }

    /**
     * SPECIFIC HELPER METHODS FOR DRAWABLE RESOURCES THAT CONTAIN COLORS
     * <p>
     * NB : Replacing "absolute" colors (@color/...) by themed colors (?...) should be the correct solution, but it crashes on KitKat
     * => Universal solution is to create the drawable programmatically
     */

    public static GradientDrawable makeDrawerHeader(Context context) {
        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        getColor(context, R.color.primary_light),
                        getColor(context, R.color.drawer_header_diagonal_light),
                        getColor(context, R.color.primary_light)
                }
        );
        shape.setShape(GradientDrawable.RECTANGLE);

        return shape;
    }

    public static StateListDrawable makeQueueButtonSelector(Context context) {
        int colorPrimary = getColor(context, R.color.primary_light);
        int colorPrimaryVariant = getColor(context, R.color.primary_variant_light);

        StateListDrawable res = new StateListDrawable();
        res.addState(new int[]{android.R.attr.state_pressed}, makeCardSelectorShape(colorPrimaryVariant, false, 0));
        res.addState(new int[]{-android.R.attr.state_pressed}, makeCardSelectorShape(colorPrimary, false, 0));
        return res;
    }

    public static StateListDrawable makeCardSelector(Context context) {
        int colorBase = getColor(context, R.color.card_surface_light);
        int colorPressed = getColor(context, R.color.card_pressed_light);
        int colorSelected = getColor(context, R.color.card_selected_light);
        int colorSecondary = getColor(context, R.color.secondary_light);

        StateListDrawable res = new StateListDrawable();
        res.addState(new int[]{-android.R.attr.state_selected}, makeCardSelectorShape(colorBase, false, 0));
        res.addState(new int[]{android.R.attr.state_pressed}, makeCardSelectorShape(colorPressed, false, 0));
        res.addState(new int[]{-android.R.attr.state_pressed, android.R.attr.state_selected}, makeCardSelectorShape(colorSelected, true, colorSecondary));
        return res;
    }

    private static GradientDrawable makeCardSelectorShape(@ColorInt int bgColor, boolean stroke, @ColorInt int strokeColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(PX_4_DP);
        shape.setColor(bgColor);
        if (stroke) shape.setStroke(PX_4_DP, strokeColor);
        return shape;
    }
}
