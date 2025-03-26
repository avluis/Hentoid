package me.devsaki.hentoid.activities.prefs

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.BaseActivity
import me.devsaki.hentoid.activities.bundles.PrefsSourceSpecificsBundle
import me.devsaki.hentoid.databinding.ActivityPrefsSourceSpecificsBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.fragments.SelectSiteDialogFragment
import me.devsaki.hentoid.util.PreferenceItem
import me.devsaki.hentoid.util.PreferencesParser
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.applyTheme
import me.devsaki.hentoid.viewholders.ListPickerItem

/**
 * Activity to edit source-specific settings
 */
class PreferencesSourceSpecificsActivity : BaseActivity(), SelectSiteDialogFragment.Parent {
    private var binding: ActivityPrefsSourceSpecificsBinding? = null
    private lateinit var site: Site
    private val preferenceItems: MutableList<PreferenceItem> = ArrayList()

    private val itemAdapter = ItemAdapter<ListPickerItem<PreferenceItem>>()
    private val fastAdapter: FastAdapter<ListPickerItem<PreferenceItem>> =
        FastAdapter.with(itemAdapter)

    @SuppressLint("NonConstantResourceId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()

        if (null == intent || null == intent.extras) throw IllegalArgumentException("Required intent not found")

        val parser = PrefsSourceSpecificsBundle(intent.extras!!)
        val validSites = Site.entries.filter { it.isVisible }
        site = Site.searchByCode(parser.site.toLong())
        if (!site.isVisible) site = validSites.first()

        binding = ActivityPrefsSourceSpecificsBinding.inflate(layoutInflater)
        binding?.apply {
            setContentView(root)

            // Toolbar
            toolbar.title = site.name
            toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            toolbar.setOnClickListener {
                val codesToShow = validSites.map { it.code }
                SelectSiteDialogFragment.invoke(
                    this@PreferencesSourceSpecificsActivity,
                    getString(R.string.bookmark_change_site),
                    codesToShow
                )
            }
            recyclerView.adapter = fastAdapter
            recyclerView.setHasFixedSize(true)
        }

        val prefsParser = PreferencesParser()
        prefsParser.addResourceFile(this, R.xml.preferences)

        preferenceItems.addAll(prefsParser.allEntries)

        refreshItems()
    }

    private fun refreshItems() {
        val items: MutableList<ListPickerItem<PreferenceItem>> = ArrayList()
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        preferenceItems.forEach {
            if (it.sites.contains(Site.ANY) || it.sites.contains(site)) {
                val category = it.breadcrumbs?.split(">")[0]?.trim() ?: ""

                val appValue = if (it.dataType == PreferenceItem.DataType.BOOL)
                    sharedPrefs.getBoolean(it.key ?: "", it.defaultValue.toBoolean()).toString()
                else
                    sharedPrefs.getString(it.key ?: "", it.defaultValue) ?: ""

                val key = Settings.makeSiteKey(it.key ?: "", site)
                val value = if (it.dataType == PreferenceItem.DataType.BOOL)
                    sharedPrefs.getBoolean(key, appValue.toBoolean()).toString()
                else
                    sharedPrefs.getString(key, appValue)

                val values = if (it.dataType == PreferenceItem.DataType.BOOL)
                    listOf("true", "false")
                else
                    it.values

                val entries = if (it.dataType == PreferenceItem.DataType.BOOL)
                    listOf(
                        getString(R.string.enabled_generic),
                        getString(R.string.disabled_generic)
                    ).mapIndexed { i, s -> flagAppDefault(s, i, values, appValue) }
                else
                    it.entries.mapIndexed { i, s -> flagAppDefault(s, i, values, appValue) }

                items.add(
                    ListPickerItem<PreferenceItem>(
                        category + " : " + (it.title ?: ""),
                        site.ico,
                        entries,
                        values,
                        value ?: "",
                        { s: String -> onChanged(sharedPrefs, it, site, s) },
                        it
                    )
                )
            }
        }

        itemAdapter.clear()
        itemAdapter.add(items)
    }

    private fun flagAppDefault(
        value: String,
        index: Int,
        values: List<String>,
        appValue: String
    ): String {
        if (index > values.lastIndex) return value
        return if (values[index] == appValue) this.getString(R.string.use_app_prefs, value)
        else value
    }

    private fun onChanged(
        prefs: SharedPreferences,
        item: PreferenceItem,
        site: Site,
        value: String
    ) {
        val key = Settings.makeSiteKey(item.key ?: "", site)
        val appValue = if (item.dataType == PreferenceItem.DataType.BOOL)
            prefs.getBoolean(item.key ?: "", item.defaultValue.toBoolean()).toString()
        else
            prefs.getString(item.key ?: "", item.defaultValue)

        if (value == appValue) { // Select app value => Remove site entry
            prefs.edit { remove(key) }
        } else { // Edit site entry
            if (item.dataType == PreferenceItem.DataType.BOOL)
                prefs.edit { putBoolean(key, value.toBoolean()) }
            else
                prefs.edit { putString(key, value) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    override fun onSiteSelected(site: Site, altCode: Int) {
        this.site = site
        binding?.toolbar?.title = site.name
        refreshItems()
    }
}