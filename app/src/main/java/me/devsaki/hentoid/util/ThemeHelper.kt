package me.devsaki.hentoid.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.ColorRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import me.devsaki.hentoid.enums.Theme
import me.devsaki.hentoid.enums.Theme.Companion.searchById
import java.util.Locale

object ThemeHelper {
    private val colorCache: MutableMap<String, Int> = HashMap()

    /**
     * Apply the app's current color theme to the given activity
     *
     * @param activity Activity to apply the theme to
     */
    fun applyTheme(activity: AppCompatActivity) {
        val theme = searchById(Preferences.getColorTheme())
        applyTheme(activity, theme)
    }

    /**
     * Apply the given theme to the given activity
     *
     * @param activity    Activity to apply the theme to
     * @param targetTheme Theme to apply
     */
    private fun applyTheme(activity: AppCompatActivity, targetTheme: Theme) {
        val themeName = renameTheme(getThemeName(activity), targetTheme)
        if (themeName == targetTheme.resourceName) return  // Nothing to do
        activity.setTheme(getThemeId(activity, themeName))
    }

    /**
     * Set the given styles to the given dialog, using the app's current color theme
     *
     * @param context         Context to use
     * @param dialog          Dialog to apply style and theme to
     * @param style           Standard style (see [DialogFragment.setStyle]
     * @param themeResourceId Resource ID of the custom style to apply
     * NB : its "Light" variant _must_ be used for it to be renamed properly
     */
    fun setStyle(
        context: Context,
        dialog: DialogFragment,
        style: Int,
        @StyleRes themeResourceId: Int
    ) {
        val themeName = context.resources.getResourceEntryName(themeResourceId)
        val themeId = getThemeId(context, renameThemeToCurrentTheme(themeName))
        dialog.setStyle(style, themeId)
    }

    /**
     * Get the resource ID of the given style, using the app's current color theme
     *
     * @param context         Context to use
     * @param themeResourceId Resource ID of the custom style to apply
     * NB : its "Light" variant _must_ be used for it to be renamed properly
     * @return Resurce ID of the given style adapter to the app's current color theme
     */
    fun getIdForCurrentTheme(context: Context, @StyleRes themeResourceId: Int): Int {
        var themeName = getThemeName(context, themeResourceId)
        themeName = renameThemeToCurrentTheme(themeName)
        return getThemeId(context, themeName)
    }

    /**
     * Get the name of the given activity's current theme
     *
     * @param activity Activity to get the theme from
     * @return Name of the given activity's current theme
     */
    private fun getThemeName(activity: Activity): String {
        return try {
            val themeResourceId = activity.packageManager.getActivityInfo(
                activity.componentName,
                0
            ).themeResource // PackageManager.GET_META_DATA instead of plain 0 ?
            getThemeName(activity, themeResourceId)
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }

    /**
     * Get the name of the given theme
     *
     * @param context         Context to use
     * @param themeResourceId Resource ID of the theme to get the name for
     * @return Name of the given theme
     */
    private fun getThemeName(context: Context, @StyleRes themeResourceId: Int): String {
        return context.resources.getResourceEntryName(themeResourceId)
    }

    /**
     * Get the name of the given color
     *
     * @param context         Context to use
     * @param colorResourceId Resource ID of the color to get the name for
     * @return Name of the given color
     */
    private fun getColorName(context: Context, @ColorRes colorResourceId: Int): String {
        return context.resources.getResourceEntryName(colorResourceId)
    }

    /**
     * Get the ID of the given theme
     *
     * @param context   Context to use
     * @param themeName Name of the theme to get the ID for
     * @return ID of the given theme
     */
    private fun getThemeId(context: Context, themeName: String): Int {
        return context.resources.getIdentifier(themeName, "style", context.packageName)
    }

    /**
     * Get the ID of the given color
     *
     * @param context   Context to use
     * @param colorName Name of the color to get the ID for
     * @return ID of the given color
     */
    private fun getColorId(context: Context, colorName: String): Int {
        return context.resources.getIdentifier(colorName, "color", context.packageName)
    }

    /**
     * Rename the given theme name according to the app's current color theme
     *
     * @param themeName Theme name to be renamed
     * NB : its "Light" variant _must_ be used for it to be renamed properly
     * @return Renamed theme according to the app's current color theme
     */
    private fun renameThemeToCurrentTheme(themeName: String): String {
        val targetTheme = searchById(Preferences.getColorTheme())
        return renameTheme(themeName, targetTheme)
    }

    /**
     * Rename the given theme name according to the given color theme
     *
     * @param themeName   Theme name to be renamed
     * NB : its "Light" variant _must_ be used for it to be renamed properly
     * @param targetTheme Target app color Theme
     * @return Renamed theme according to the given target Theme
     */
    private fun renameTheme(themeName: String, targetTheme: Theme): String {
        var name = themeName
        for (t in Theme.values()) if (name.contains(t.resourceName)) {
            if (t == targetTheme) return name // Nothing to do; target theme is already set
            name = name.replace(t.resourceName, targetTheme.resourceName)
            break
        }
        return name
    }

    /**
     * Rename the given color name according to the app's current color theme
     *
     * @param colorName Color name to be renamed
     * NB : its "Light" variant _must_ be used for it to be renamed properly
     * @return Renamed color according to the app's current color theme
     */
    private fun renameColorToCurrentTheme(colorName: String): String {
        val targetTheme = searchById(Preferences.getColorTheme())
        return renameColorToTheme(colorName, targetTheme)
    }

    /**
     * Rename the given color name according to the given color theme
     *
     * @param colorName   Color name to be renamed
     * NB : its "Light" variant _must_ be used for it to be renamed properly
     * @param targetTheme Target app color Theme
     * @return Renamed color according to the given target Theme
     */
    private fun renameColorToTheme(colorName: String, targetTheme: Theme): String {
        var name = colorName
        for (t in Theme.values()) if (name.contains("_" + t.resourceName.lowercase(Locale.getDefault()))) {
            if (t == targetTheme) return name // Nothing to do; target theme is already set
            name = name.replace(
                "_" + t.resourceName.lowercase(Locale.getDefault()),
                "_" + targetTheme.resourceName.lowercase(
                    Locale.getDefault()
                )
            )
            break
        }
        return name
    }

    /**
     * Convert a @ColorRes resource color to a @ColorInt reference color using the app's current color theme
     *
     * @param context Context to use
     * @param colorId Resource ID of the color to convert
     * NB : its "Light" variant _must_ be used for it to be renamed properly
     * @return Given color converted to @ColorInt, according to the app's current color theme
     */
    fun getColor(context: Context, @ColorRes colorId: Int): Int {
        val key = Preferences.getColorTheme().toString() + "." + colorId
        if (colorCache.containsKey(key)) {
            val result = colorCache[key]
            return result ?: 0
        }
        val colorName = renameColorToCurrentTheme(getColorName(context, colorId))
        val result = ContextCompat.getColor(context, getColorId(context, colorName))
        colorCache[key] = result
        return result
    }
}