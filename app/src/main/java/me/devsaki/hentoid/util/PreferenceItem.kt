package me.devsaki.hentoid.util

import android.text.TextUtils
import androidx.annotation.XmlRes
import me.devsaki.hentoid.enums.Site

class PreferenceItem {
    @JvmField
    var title: String? = null

    @JvmField
    var summary: String? = null

    @JvmField
    var key: String? = null

    @JvmField
    var entries: String? = null

    @JvmField
    var breadcrumbs: String? = null

    @JvmField
    var keywords: String? = null

    @JvmField
    var sites: MutableSet<Site> = HashSet()

    @JvmField
    var keyBreadcrumbs: ArrayList<String> = ArrayList<String>()

    @JvmField
    var resId: Int = 0

    private val lastScore = 0f
    private var lastKeyword: String? = null

    internal constructor()

    fun hasData(): Boolean {
        return title != null || summary != null
    }

    private val info: String
        get() {
            val infoBuilder = StringBuilder()
            if (!TextUtils.isEmpty(title)) {
                infoBuilder.append("ø").append(title)
            }
            if (!TextUtils.isEmpty(summary)) {
                infoBuilder.append("ø").append(summary)
            }
            if (!TextUtils.isEmpty(entries)) {
                infoBuilder.append("ø").append(entries)
            }
            if (!TextUtils.isEmpty(breadcrumbs)) {
                infoBuilder.append("ø").append(breadcrumbs)
            }
            if (!TextUtils.isEmpty(keywords)) {
                infoBuilder.append("ø").append(keywords)
            }
            return infoBuilder.toString()
        }

    fun withKey(key: String?): PreferenceItem {
        this.key = key
        return this
    }

    fun withSummary(summary: String?): PreferenceItem {
        this.summary = summary
        return this
    }

    fun withTitle(title: String?): PreferenceItem {
        this.title = title
        return this
    }

    fun withEntries(entries: String?): PreferenceItem {
        this.entries = entries
        return this
    }

    fun withKeywords(keywords: String?): PreferenceItem {
        this.keywords = keywords
        return this
    }

    fun withResId(@XmlRes resId: Int): PreferenceItem {
        this.resId = resId
        return this
    }

    /**
     * @param breadcrumb The breadcrumb to add
     * @return For chaining
     */
    fun addBreadcrumb(breadcrumb: String?): PreferenceItem {
        this.breadcrumbs = PreferencesParser.Breadcrumb.concat(this.breadcrumbs, breadcrumb)
        return this
    }

    override fun toString(): String {
        return "PreferenceItem: $title $summary $key"
    }
}