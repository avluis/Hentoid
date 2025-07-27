package me.devsaki.hentoid.util

import androidx.annotation.XmlRes
import me.devsaki.hentoid.enums.Site

class PreferenceItem {
    enum class DataType { STRING, BOOL }

    @JvmField
    var dataType: DataType = DataType.STRING

    @JvmField
    var title: String? = null

    @JvmField
    var summary: String? = null

    @JvmField
    var key: String? = null

    @JvmField
    var entries: List<String> = emptyList()

    @JvmField
    var values: List<String> = emptyList()

    @JvmField
    var breadcrumbs: String? = null

    @JvmField
    var keywords: String? = null

    @JvmField
    var sites: MutableSet<Site> = HashSet()

    @JvmField
    var isGlobal: Boolean = false

    @JvmField
    var defaultValue: String? = null

    @JvmField
    var keyBreadcrumbs: ArrayList<String> = ArrayList<String>()

    @JvmField
    var resId: Int = 0

    internal constructor()

    fun hasData(): Boolean {
        return title != null || summary != null
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

    fun withEntries(entries: List<String>): PreferenceItem {
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