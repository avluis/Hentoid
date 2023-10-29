package me.devsaki.hentoid.util;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import java.util.HashMap;
import java.util.Map;

import me.devsaki.hentoid.enums.Theme;

public class ThemeHelper {

    private static final Map<String, Integer> COLOR_CACHE = new HashMap<>();

    private ThemeHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Apply the app's current color theme to the given activity
     *
     * @param activity Activity to apply the theme to
     */
    public static void applyTheme(@NonNull AppCompatActivity activity) {
        Theme theme = Theme.Companion.searchById(Preferences.getColorTheme());
        applyTheme(activity, theme);
    }

    /**
     * Apply the given theme to the given activity
     *
     * @param activity    Activity to apply the theme to
     * @param targetTheme Theme to apply
     */
    private static void applyTheme(@NonNull AppCompatActivity activity, Theme targetTheme) {
        String themeName = renameTheme(getThemeName(activity), targetTheme);
        if (themeName.equals(targetTheme.getResourceName())) return; // Nothing to do

        activity.setTheme(getThemeId(activity, themeName));
    }

    /**
     * Set the given styles to the given dialog, using the app's current color theme
     *
     * @param context         Context to use
     * @param dialog          Dialog to apply style and theme to
     * @param style           Standard style (see {@link DialogFragment#setStyle(int, int)}
     * @param themeResourceId Resource ID of the custom style to apply
     *                        NB : its "Light" variant _must_ be used for it to be renamed properly
     */
    public static void setStyle(@NonNull Context context, @NonNull DialogFragment dialog, int style, @StyleRes int themeResourceId) {
        String themeName = context.getResources().getResourceEntryName(themeResourceId);
        int themeId = getThemeId(context, renameThemeToCurrentTheme(themeName));

        dialog.setStyle(style, themeId);
    }

    /**
     * Get the resource ID of the given style, using the app's current color theme
     *
     * @param context         Context to use
     * @param themeResourceId Resource ID of the custom style to apply
     *                        NB : its "Light" variant _must_ be used for it to be renamed properly
     * @return Resurce ID of the given style adapter to the app's current color theme
     */
    public static int getIdForCurrentTheme(@NonNull Context context, @StyleRes int themeResourceId) {
        String themeName = getThemeName(context, themeResourceId);
        themeName = renameThemeToCurrentTheme(themeName);
        return getThemeId(context, themeName);
    }

    /**
     * Get the name of the given activity's current theme
     *
     * @param activity Activity to get the theme from
     * @return Name of the given activity's current theme
     */
    private static String getThemeName(@NonNull Activity activity) {
        try {
            int themeResourceId = activity.getPackageManager().getActivityInfo(activity.getComponentName(), 0).getThemeResource(); // PackageManager.GET_META_DATA instead of plain 0 ?
            return getThemeName(activity, themeResourceId);
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /**
     * Get the name of the given theme
     *
     * @param context         Context to use
     * @param themeResourceId Resource ID of the theme to get the name for
     * @return Name of the given theme
     */
    private static String getThemeName(@NonNull Context context, @StyleRes int themeResourceId) {
        return context.getResources().getResourceEntryName(themeResourceId);
    }

    /**
     * Get the name of the given color
     *
     * @param context         Context to use
     * @param colorResourceId Resource ID of the color to get the name for
     * @return Name of the given color
     */
    private static String getColorName(@NonNull Context context, @ColorRes int colorResourceId) {
        return context.getResources().getResourceEntryName(colorResourceId);
    }

    /**
     * Get the ID of the given theme
     *
     * @param context   Context to use
     * @param themeName Name of the theme to get the ID for
     * @return ID of the given theme
     */
    private static int getThemeId(@NonNull Context context, @NonNull String themeName) {
        return context.getResources().getIdentifier(themeName, "style", context.getPackageName());
    }

    /**
     * Get the ID of the given color
     *
     * @param context   Context to use
     * @param colorName Name of the color to get the ID for
     * @return ID of the given color
     */
    private static int getColorId(@NonNull Context context, @NonNull String colorName) {
        return context.getResources().getIdentifier(colorName, "color", context.getPackageName());
    }

    /**
     * Rename the given theme name according to the app's current color theme
     *
     * @param themeName Theme name to be renamed
     *                  NB : its "Light" variant _must_ be used for it to be renamed properly
     * @return Renamed theme according to the app's current color theme
     */
    private static String renameThemeToCurrentTheme(@NonNull String themeName) {
        Theme targetTheme = Theme.Companion.searchById(Preferences.getColorTheme());
        return renameTheme(themeName, targetTheme);
    }

    /**
     * Rename the given theme name according to the given color theme
     *
     * @param themeName   Theme name to be renamed
     *                    NB : its "Light" variant _must_ be used for it to be renamed properly
     * @param targetTheme Target app color Theme
     * @return Renamed theme according to the given target Theme
     */
    private static String renameTheme(@NonNull String themeName, @NonNull Theme targetTheme) {
        for (Theme t : Theme.values())
            if (themeName.contains(t.getResourceName())) {
                if (t.equals(targetTheme))
                    return themeName; // Nothing to do; target theme is already set
                themeName = themeName.replace(t.getResourceName(), targetTheme.getResourceName());
                break;
            }
        return themeName;
    }

    /**
     * Rename the given color name according to the app's current color theme
     *
     * @param colorName Color name to be renamed
     *                  NB : its "Light" variant _must_ be used for it to be renamed properly
     * @return Renamed color according to the app's current color theme
     */
    private static String renameColorToCurrentTheme(@NonNull String colorName) {
        Theme targetTheme = Theme.Companion.searchById(Preferences.getColorTheme());
        return renameColorToTheme(colorName, targetTheme);
    }

    /**
     * Rename the given color name according to the given color theme
     *
     * @param colorName   Color name to be renamed
     *                    NB : its "Light" variant _must_ be used for it to be renamed properly
     * @param targetTheme Target app color Theme
     * @return Renamed color according to the given target Theme
     */
    private static String renameColorToTheme(@NonNull String colorName, @NonNull Theme targetTheme) {
        for (Theme t : Theme.values())
            if (colorName.contains("_" + t.getResourceName().toLowerCase())) {
                if (t.equals(targetTheme))
                    return colorName; // Nothing to do; target theme is already set
                colorName = colorName.replace("_" + t.getResourceName().toLowerCase(), "_" + targetTheme.getResourceName().toLowerCase());
                break;
            }
        return colorName;
    }

    /**
     * Convert a @ColorRes resource color to a @ColorInt reference color using the app's current color theme
     *
     * @param context Context to use
     * @param colorId Resource ID of the color to convert
     *                NB : its "Light" variant _must_ be used for it to be renamed properly
     * @return Given color converted to @ColorInt, according to the app's current color theme
     */
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
}
