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

private val colorCache: MutableMap<String, Int> = HashMap()

/**
 * Apply the app's current color theme to the given activity
 */
fun AppCompatActivity.applyTheme() {
    val theme = searchById(Preferences.getColorTheme())
    applyTheme(theme)
}

/**
 * Apply the given theme to the given activity
 *
 * @param targetTheme Theme to apply
 */
private fun AppCompatActivity.applyTheme(targetTheme: Theme) {
    val themeName = renameTheme(getThemeName(), targetTheme)
    if (themeName == targetTheme.resourceName) return  // Nothing to do
    setTheme(getThemeId(themeName))
}

/**
 * Set the given styles to the given dialog, using the app's current color theme
 *
 * @param dialog          Dialog to apply style and theme to
 * @param style           Standard style (see [DialogFragment.setStyle]
 * @param themeResourceId Resource ID of the custom style to apply
 * NB : its "Light" variant _must_ be used for it to be renamed properly
 */
fun Context.setStyle(
    dialog: DialogFragment,
    style: Int,
    @StyleRes themeResourceId: Int
) {
    val themeName = resources.getResourceEntryName(themeResourceId)
    val themeId = getThemeId(renameThemeToCurrentTheme(themeName))
    dialog.setStyle(style, themeId)
}

/**
 * Get the resource ID of the given style, using the app's current color theme
 *
 * @param themeResourceId Resource ID of the custom style to apply
 * NB : its "Light" variant _must_ be used for it to be renamed properly
 * @return Resurce ID of the given style adapter to the app's current color theme
 */
fun Context.getIdForCurrentTheme(@StyleRes themeResourceId: Int): Int {
    var themeName = getThemeName(themeResourceId)
    themeName = renameThemeToCurrentTheme(themeName)
    return getThemeId(themeName)
}

/**
 * Get the name of the given activity's current theme
 *
 * @return Name of the given activity's current theme
 */
private fun Activity.getThemeName(): String {
    return try {
        // PackageManager.GET_META_DATA instead of plain 0 ?
        val themeResourceId = packageManager.getActivityInfo(this.componentName, 0).themeResource
        getThemeName(themeResourceId)
    } catch (e: PackageManager.NameNotFoundException) {
        ""
    }
}

/**
 * Get the name of the given theme
 *
 * @param themeResourceId Resource ID of the theme to get the name for
 * @return Name of the given theme
 */
private fun Context.getThemeName(@StyleRes themeResourceId: Int): String {
    return resources.getResourceEntryName(themeResourceId)
}

/**
 * Get the name of the given color
 *
 * @param colorResourceId Resource ID of the color to get the name for
 * @return Name of the given color
 */
private fun Context.getColorName(@ColorRes colorResourceId: Int): String {
    return resources.getResourceEntryName(colorResourceId)
}

/**
 * Get the ID of the given theme
 *
 * @param themeName Name of the theme to get the ID for
 * @return ID of the given theme
 */
private fun Context.getThemeId(themeName: String): Int {
    return resources.getIdentifier(themeName, "style", packageName)
}

/**
 * Get the ID of the given color
 *
 * @param colorName Name of the color to get the ID for
 * @return ID of the given color
 */
private fun Context.getColorId(colorName: String): Int {
    return resources.getIdentifier(colorName, "color", packageName)
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
    for (t in Theme.entries) if (name.contains(t.resourceName)) {
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
    for (t in Theme.entries) if (name.contains("_" + t.resourceName.lowercase(Locale.getDefault()))) {
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
 * @param colorId Resource ID of the color to convert
 * NB : its "Light" variant _must_ be used for it to be renamed properly
 * @return Given color converted to @ColorInt, according to the app's current color theme
 */
fun Context.getThemedColor(@ColorRes colorId: Int): Int {
    val key = Preferences.getColorTheme().toString() + "." + colorId
    if (colorCache.containsKey(key)) {
        val result = colorCache[key]
        return result ?: 0
    }
    val colorName = renameColorToCurrentTheme(getColorName(colorId))
    val result = ContextCompat.getColor(this, getColorId(colorName))
    colorCache[key] = result
    return result
}