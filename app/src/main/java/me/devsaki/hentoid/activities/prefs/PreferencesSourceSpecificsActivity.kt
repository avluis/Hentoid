package me.devsaki.hentoid.activities.prefs

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.BaseActivity
import me.devsaki.hentoid.databinding.ActivityPrefsSourceSpecificsBinding
import me.devsaki.hentoid.util.PreferencesParser
import me.devsaki.hentoid.util.applyTheme
import me.devsaki.hentoid.viewholders.TextItem

/**
 * Activity to edit source-specific settings
 */
class PreferencesSourceSpecificsActivity : BaseActivity() {
    private var binding: ActivityPrefsSourceSpecificsBinding? = null
    private lateinit var recyclerView: RecyclerView
    private val itemAdapter = ItemAdapter<TextItem<String>>()
    private val fastAdapter: FastAdapter<TextItem<String>> = FastAdapter.with(itemAdapter)

    @SuppressLint("NonConstantResourceId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()

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
        val items: MutableList<TextItem<String>> = ArrayList()
        val prefsParser = PreferencesParser()
        prefsParser.addResourceFile(this, R.xml.preferences)

        prefsParser.allEntries.forEach {
            items.add(
                TextItem<String>(
                    it.breadcrumbs ?: "" + " : " + it.title ?: "",
                    it.key ?: "",
                    false
                )
            )
        }


        itemAdapter.add(items)
        recyclerView = findViewById(R.id.drawer_edit_list)
        recyclerView.adapter = fastAdapter
        recyclerView.setHasFixedSize(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }
}