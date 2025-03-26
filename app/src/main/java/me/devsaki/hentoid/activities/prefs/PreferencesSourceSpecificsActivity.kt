package me.devsaki.hentoid.activities.prefs

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.BaseActivity
import me.devsaki.hentoid.databinding.ActivityPrefsSourceSpecificsBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.fragments.SelectSiteDialogFragment
import me.devsaki.hentoid.util.PreferenceItem
import me.devsaki.hentoid.util.PreferencesParser
import me.devsaki.hentoid.util.applyTheme
import me.devsaki.hentoid.viewholders.ListPickerItem

/**
 * Activity to edit source-specific settings
 */
class PreferencesSourceSpecificsActivity : BaseActivity(), SelectSiteDialogFragment.Parent {
    // TODO check all onSharedPreferenceChanged
    private var binding: ActivityPrefsSourceSpecificsBinding? = null
    private lateinit var recyclerView: RecyclerView
    private var site = Site.ANY // TODO init upon calling
    private val preferenceItems: MutableList<PreferenceItem> = ArrayList()

    private val itemAdapter = ItemAdapter<ListPickerItem<PreferenceItem>>()
    private val fastAdapter: FastAdapter<ListPickerItem<PreferenceItem>> =
        FastAdapter.with(itemAdapter)

    @SuppressLint("NonConstantResourceId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()

        binding = ActivityPrefsSourceSpecificsBinding.inflate(layoutInflater)
        binding?.apply {
            setContentView(root)

            // Toolbar
            toolbar.title = site.name // TODO icon
            toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            toolbar.setOnClickListener {
                val codesToShow = Site.entries.filter { it.isVisible }.map { it.code }
                SelectSiteDialogFragment.invoke(
                    this@PreferencesSourceSpecificsActivity,
                    getString(R.string.bookmark_change_site),
                    codesToShow
                )
            }
            recyclerView.adapter = fastAdapter
            recyclerView.setHasFixedSize(true)
            this@PreferencesSourceSpecificsActivity.recyclerView = recyclerView
        }

        val prefsParser = PreferencesParser()
        prefsParser.addResourceFile(this, R.xml.preferences)

        preferenceItems.addAll(prefsParser.allEntries)

        refreshItems()
    }

    private fun refreshItems() {
        val items: MutableList<ListPickerItem<PreferenceItem>> = ArrayList()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        preferenceItems.forEach {
            if (it.sites.contains(Site.ANY) || it.sites.contains(site)) {
                val category = it.breadcrumbs?.split(">")[0]?.trim() ?: ""

                val key =
                    if (site == Site.NONE || site == Site.ANY) it.key else it.key + "." + site.name
                val value = if (it.dataType == PreferenceItem.DataType.BOOL)
                    sharedPreferences.getBoolean(key, it.defaultValue.toBoolean()).toString()
                else
                    sharedPreferences.getString(key, it.defaultValue)

                val entries = if (it.dataType == PreferenceItem.DataType.BOOL)
                    listOf(
                        getString(R.string.enabled_generic),
                        getString(R.string.disabled_generic)
                    )
                else
                    it.entries

                val values = if (it.dataType == PreferenceItem.DataType.BOOL)
                    listOf("true", "false")
                else
                    it.values

                items.add(
                    // Icons ?
                    ListPickerItem<PreferenceItem>(
                        category + " : " + (it.title ?: ""),
                        entries,
                        values,
                        value ?: "",
                        { s: String -> onChanged(sharedPreferences, it, site, s) },
                        it
                    )
                )
            }
        }

        itemAdapter.clear()
        itemAdapter.add(items)
    }

    private fun onChanged(
        prefs: SharedPreferences,
        item: PreferenceItem,
        site: Site,
        value: String
    ) {
        val key =
            if (site == Site.NONE || site == Site.ANY) item.key else item.key + "." + site.name
        if (item.dataType == PreferenceItem.DataType.BOOL)
            prefs.edit { putBoolean(key, value.toBoolean()) }
        else
            prefs.edit { putString(key, value) }
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