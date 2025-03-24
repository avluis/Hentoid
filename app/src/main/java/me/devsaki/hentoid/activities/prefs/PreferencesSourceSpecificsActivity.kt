package me.devsaki.hentoid.activities.prefs

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.BaseActivity
import me.devsaki.hentoid.databinding.ActivityPrefsSourceSpecificsBinding
import me.devsaki.hentoid.util.PreferenceItem
import me.devsaki.hentoid.util.PreferencesParser
import me.devsaki.hentoid.util.applyTheme
import me.devsaki.hentoid.viewholders.ListPickerItem

/**
 * Activity to edit source-specific settings
 */
class PreferencesSourceSpecificsActivity : BaseActivity() {
    private var binding: ActivityPrefsSourceSpecificsBinding? = null
    private lateinit var recyclerView: RecyclerView
    private val itemAdapter = ItemAdapter<ListPickerItem<PreferenceItem>>()
    private val fastAdapter: FastAdapter<ListPickerItem<PreferenceItem>> =
        FastAdapter.with(itemAdapter)

    @SuppressLint("NonConstantResourceId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        binding = ActivityPrefsSourceSpecificsBinding.inflate(layoutInflater)
        binding?.let {
            setContentView(it.root)

            // Toolbar
            it.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            it.toolbar.setOnMenuItemClickListener { clickedMenuItem: MenuItem ->
                when (clickedMenuItem.itemId) {
                    // TODO
                    else -> {}
                }
                true
            }
        }

        // Recycler
        val items: MutableList<ListPickerItem<PreferenceItem>> = ArrayList()
        val prefsParser = PreferencesParser()
        prefsParser.addResourceFile(this, R.xml.preferences)

        prefsParser.allEntries.forEach {
            val category = it.breadcrumbs?.split(">")[0]?.trim() ?: ""

            val value = if (it.dataType == PreferenceItem.DataType.BOOL)
                sharedPreferences.getBoolean(it.key, it.defaultValue.toBoolean()).toString()
            else
                sharedPreferences.getString(it.key, it.defaultValue)

            val entries = if (it.dataType == PreferenceItem.DataType.BOOL)
                listOf(getString(R.string.enabled_generic), getString(R.string.disabled_generic))
            else
                it.entries

            val values = if (it.dataType == PreferenceItem.DataType.BOOL)
                listOf("true", "false")
            else
                it.values

            items.add(
                ListPickerItem<PreferenceItem>(
                    category + " : " + (it.title ?: ""),
                    entries,
                    values,
                    value ?: "",
                    { s: String -> onChanged(sharedPreferences, it, s) },
                    it
                )
            )
        }

        itemAdapter.add(items)
        recyclerView = findViewById(R.id.drawer_edit_list)
        recyclerView.adapter = fastAdapter
        recyclerView.setHasFixedSize(true)
    }

    private fun onChanged(prefs: SharedPreferences, item: PreferenceItem, value: String) {
        // TODO edit site (key.Site.Name)
        if (item.dataType == PreferenceItem.DataType.BOOL)
            prefs.edit { putBoolean(item.key, value.toBoolean()) }
        else
            prefs.edit { putString(item.key, value) }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }
}