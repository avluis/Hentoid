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

    public static void applyTheme(@NonNull AppCompatActivity activity, Theme targetTheme) {
        String themeName = renameThemeToCurrentTheme(getThemeName(activity), targetTheme);
        if (themeName.equals(targetTheme.getName())) return; // Nothing to do

        activity.setTheme(getThemeId(activity, themeName));
        if (activity.getLifecycle().getCurrentState().isAtLeast(CREATED))
            activity.recreate();
    }

    public static void setStyle(@NonNull Context context, @NonNull DialogFragment dialog, int style, @StyleRes int themeResourceId) {
        String themeName = context.getResources().getResourceEntryName(themeResourceId);
        int themeId = getThemeId(context, renameThemeToCurrentTheme(themeName));

        dialog.setStyle(style, themeId);
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
        return renameThemeToCurrentTheme(themeName, targetTheme);
    }

    private static String renameThemeToCurrentTheme(@NonNull String themeName, @NonNull Theme targetTheme) {
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
        return renameColorToCurrentTheme(colorName, targetTheme);
    }

    private static String renameColorToCurrentTheme(@NonNull String colorName, @NonNull Theme targetTheme) {
        for (Theme t : Theme.values())
            if (colorName.contains("_" + t.getName().toLowerCase())) {
                if (t.equals(targetTheme))
                    return colorName; // Nothing to do; target theme is already set
                colorName = colorName.replace("_" + t.getName().toLowerCase(), "_" + targetTheme.getName().toLowerCase());
                break;
            }
        return colorName;
    }

    public static int getColor(Context context, String resourceName) {
        int currentThemeId = Preferences.getColorTheme();
        resourceName += "_" + Theme.searchById(currentThemeId).getName().toLowerCase();
        int resourceId = getColorId(context, resourceName); // TODO cache ?
        return ContextCompat.getColor(context, resourceId);
    }

    public static int getColor(Context context, @ColorRes int colorId) {
        String colorName = renameColorToCurrentTheme(getColorName(context, colorId));
        int resourceId = getColorId(context, colorName); // TODO cache ?
        return ContextCompat.getColor(context, resourceId);
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
                        getColor(context, "primary"),
                        getColor(context, "drawer_header_diagonal"),
                        getColor(context, "primary")
                }
        );
        shape.setShape(GradientDrawable.RECTANGLE);

        return shape;
    }

    public static StateListDrawable makeCardSelector(Context context) {
        int colorBase = getColor(context, "card_surface");
        int colorPressed = getColor(context, "card_pressed");
        int colorSelected = getColor(context, "card_selected");
        int colorSecondary = getColor(context, "secondary");

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
