package me.devsaki.hentoid.util

import android.content.Context
import android.text.TextUtils
import androidx.annotation.XmlRes
import me.devsaki.hentoid.enums.Site
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber

// Inspired by com.bytehamster.lib.preferencesearch.PreferenceParser
private val CONTAINERS = mutableListOf<String?>("PreferenceCategory", "PreferenceScreen")
private const val NS_ANDROID = "http://schemas.android.com/apk/res/android"
private const val NS_APP = "http://schemas.android.com/apk/res-auto"

class PreferencesParser internal constructor() {
    val allEntries: MutableList<PreferenceItem> = ArrayList()

    fun addResourceFile(context: Context, @XmlRes resId: Int) {
        val item = SearchIndexItem(resId)
        allEntries.addAll(parseFile(context, item))
    }

    fun addPreferenceItems(preferenceItems: ArrayList<PreferenceItem>) {
        allEntries.addAll(preferenceItems)
    }

    private fun parseFile(context: Context, item: SearchIndexItem): ArrayList<PreferenceItem> {
        val results = ArrayList<PreferenceItem>()
        val xpp: XmlPullParser = context.resources.getXml(item.resId)

        //        List<String> bannedKeys = item.getSearchConfiguration().getBannedKeys();
        try {
            xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            xpp.setFeature(XmlPullParser.FEATURE_REPORT_NAMESPACE_ATTRIBUTES, true)
            val breadcrumbs = ArrayList<String?>()
            val keyBreadcrumbs = ArrayList<String?>()
            if (!TextUtils.isEmpty(item.breadcrumb)) breadcrumbs.add(item.breadcrumb)

            while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
                if (xpp.eventType == XmlPullParser.START_TAG) {
                    val result = parseSearchResult(context, xpp)
                    result.resId = item.resId

                    if (result.site != Site.NONE) {
                        result.breadcrumbs = joinBreadcrumbs(breadcrumbs)
                        result.keyBreadcrumbs = cleanupKeyBreadcrumbs(keyBreadcrumbs)
                        results.add(result)
                    }

                    if (CONTAINERS.contains(xpp.name)) {
                        breadcrumbs.add(if (result.title == null) "" else result.title)
                    }
                    if (xpp.name == "PreferenceScreen") {
                        keyBreadcrumbs.add(getAttribute(xpp, "key"))
                    }
                } else if (xpp.eventType == XmlPullParser.END_TAG && CONTAINERS.contains(xpp.name)) {
                    breadcrumbs.removeAt(breadcrumbs.size - 1)
                    if (xpp.name == "PreferenceScreen") {
                        keyBreadcrumbs.removeAt(keyBreadcrumbs.size - 1)
                    }
                }

                xpp.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun cleanupKeyBreadcrumbs(keyBreadcrumbs: ArrayList<String?>): ArrayList<String?> {
        val result = ArrayList<String?>()
        for (keyBreadcrumb in keyBreadcrumbs) {
            if (keyBreadcrumb != null) {
                result.add(keyBreadcrumb)
            }
        }
        return result
    }

    private fun joinBreadcrumbs(breadcrumbs: ArrayList<String?>): String? {
        var result: String? = ""
        for (crumb in breadcrumbs) {
            if (!TextUtils.isEmpty(crumb)) {
                result = Breadcrumb.concat(result, crumb)
            }
        }
        return result
    }

    private fun getAttribute(xpp: XmlPullParser, namespace: String?, attribute: String): String? {
        for (i in 0..<xpp.attributeCount) {
            Timber.tag("ns").d(xpp.getAttributeNamespace(i))
            if (attribute == xpp.getAttributeName(i) &&
                (namespace == null || namespace == xpp.getAttributeNamespace(i))
            ) {
                return xpp.getAttributeValue(i)
            }
        }
        return null
    }

    private fun getAttribute(xpp: XmlPullParser, attribute: String): String? {
        if (hasAttribute(xpp, NS_APP, attribute)) {
            return getAttribute(xpp, NS_APP, attribute)
        } else {
            return getAttribute(xpp, NS_ANDROID, attribute)
        }
    }

    private fun hasAttribute(xpp: XmlPullParser, namespace: String?, attribute: String): Boolean {
        return getAttribute(xpp, namespace, attribute) != null
    }

    private fun parseSearchResult(context: Context, xpp: XmlPullParser): PreferenceItem {
        val result = PreferenceItem()
        result.title = readString(context, getAttribute(xpp, "title"))
        result.summary = readString(context, getAttribute(xpp, "summary"))
        result.key = readString(context, getAttribute(xpp, "key"))
        result.entries = readStringArray(context, getAttribute(xpp, "entries"))
        result.site = Site.searchByName(readString(context, getAttribute(xpp, "tag")))

        Timber.tag("PreferenceParser").d("Found: ${xpp.name}/$result")
        return result
    }

    private fun readStringArray(context: Context, s: String?): String? {
        if (s == null) {
            return null
        }
        if (s.startsWith("@")) {
            try {
                val id = s.substring(1).toInt()
                val elements = context.resources.getStringArray(id)
                return TextUtils.join(",", elements)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return s
    }

    private fun readString(context: Context, s: String?): String? {
        if (s == null) {
            return null
        }
        if (s.startsWith("@")) {
            try {
                val id = s.substring(1).toInt()
                return context.getString(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return s
    }

    internal class SearchIndexItem {
        var breadcrumb: String? = ""
            private set

        @get:XmlRes
        @XmlRes
        val resId: Int

        /**
         * Includes the given R.xml resource in the index
         * @param resId The resource to index
         */
        constructor(@XmlRes resId: Int) {
            this.resId = resId
        }

        /**
         * Adds a breadcrumb
         * @param breadcrumb The breadcrumb to add
         * @return For chaining
         */
        /*

        public SearchIndexItem addBreadcrumb(@StringRes int breadcrumb) {
            assertNotParcel();
            return addBreadcrumb(searchConfiguration.activity.getString(breadcrumb));
        }
*/
        /**
         * Adds a breadcrumb
         * @param breadcrumb The breadcrumb to add
         * @return For chaining
         */
        fun addBreadcrumb(breadcrumb: String?): SearchIndexItem {
            this.breadcrumb = Breadcrumb.concat(this.breadcrumb, breadcrumb)
            return this
        }
    }

    object Breadcrumb {
        /**
         * Joins two breadcrumbs
         * @param s1 First breadcrumb, might be null
         * @param s2 Second breadcrumb
         * @return Both breadcrumbs joined
         */
        fun concat(s1: String?, s2: String?): String? {
            if (TextUtils.isEmpty(s1)) {
                return s2
            }
            return "$s1 > $s2"
        }
    }
}
