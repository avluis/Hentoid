package me.devsaki.hentoid.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.SparseIntArray
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle.Companion.buildSearchUri
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle.Companion.parseSearchUri
import me.devsaki.hentoid.adapters.SelectedAttributeAdapter
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.databinding.ActivitySearchBinding
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.fragments.SearchBottomSheetFragment.Companion.invoke
import me.devsaki.hentoid.util.Location
import me.devsaki.hentoid.util.SearchCriteria
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.Type
import me.devsaki.hentoid.viewmodels.SearchViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import timber.log.Timber

/**
 * Activity for the advanced search screen
 */
class SearchActivity : BaseActivity() {

    private var binding: ActivitySearchBinding? = null

    // Container where selected attributed are displayed
    private lateinit var selectedAttributeAdapter: SelectedAttributeAdapter

    // ViewModel of this activity
    private lateinit var viewModel: SearchViewModel

    private var excludeClicked = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        val intent = intent
        var preSelectedCriteria: SearchCriteria? = null
        if (intent != null && intent.extras != null) {
            val parser = SearchActivityBundle(intent.extras!!)
            val searchUri = Uri.parse(parser.uri)
            excludeClicked = parser.excludeMode
            if (searchUri != null) preSelectedCriteria = parseSearchUri(searchUri)
        }
        binding?.apply {
            toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

            // Attribute type buttons
            textCategoryAny.setOnClickListener {
                onAttrButtonClick(
                    excludeClicked,
                    AttributeType.TAG,
                    AttributeType.ARTIST,
                    AttributeType.CIRCLE,
                    AttributeType.SERIE,
                    AttributeType.CHARACTER,
                    AttributeType.LANGUAGE
                )
            } // Everything but source !
            textCategoryAny.isEnabled = true
            textCategoryTag.setOnClickListener {
                onAttrButtonClick(
                    excludeClicked, AttributeType.TAG
                )
            }
            textCategoryArtist.setOnClickListener {
                onAttrButtonClick(
                    excludeClicked, AttributeType.ARTIST, AttributeType.CIRCLE
                )
            }
            textCategorySeries.setOnClickListener {
                onAttrButtonClick(
                    excludeClicked, AttributeType.SERIE
                )
            }
            textCategoryCharacter.setOnClickListener {
                onAttrButtonClick(
                    excludeClicked, AttributeType.CHARACTER
                )
            }
            textCategoryLanguage.setOnClickListener {
                onAttrButtonClick(
                    excludeClicked, AttributeType.LANGUAGE
                )
            }
            textCategorySource.setOnClickListener {
                onAttrButtonClick(
                    excludeClicked, AttributeType.SOURCE
                )
            }
            excludeCheckbox.setOnClickListener { view: View ->
                onExcludeClick(view)
            }
            excludeCheckbox.isChecked = excludeClicked
            val llm =
                LinearLayoutManager(this@SearchActivity, LinearLayoutManager.HORIZONTAL, false)
            searchTags.layoutManager = llm
            selectedAttributeAdapter = SelectedAttributeAdapter()
            selectedAttributeAdapter.setOnClickListener { button: View ->
                onSelectedAttributeClick(button)
            }
            selectedAttributeAdapter.registerAdapterDataObserver(object : AdapterDataObserver() {
                // Auto-Scroll to last added item
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    llm.smoothScrollToPosition(
                        searchTags, null, selectedAttributeAdapter.itemCount
                    )
                }
            })
            searchTags.adapter = selectedAttributeAdapter
            searchFab.setOnClickListener { searchBooks() }

            val vmFactory = ViewModelFactory(application)
            viewModel =
                ViewModelProvider(this@SearchActivity, vmFactory)[SearchViewModel::class.java]
            viewModel.nbAttributesPerType.observe(this@SearchActivity) { attrCount: SparseIntArray ->
                onQueryUpdated(attrCount)
            }
            viewModel.selectedAttributes.observe(this@SearchActivity) { selectedAttributes: List<Attribute> ->
                onSelectedAttributesChanged(selectedAttributes)
            }
            viewModel.selectedContentCount.observe(this@SearchActivity) { count: Int ->
                onBooksCounted(count)
            }
            if (preSelectedCriteria != null) {
                if (preSelectedCriteria.attributes.isNotEmpty())
                    viewModel.setSelectedAttributes(preSelectedCriteria.attributes.toList())
                if (preSelectedCriteria.location.value > 0) viewModel.setLocation(
                    preSelectedCriteria.location
                )
                locationPicker.index = preSelectedCriteria.location.value
                if (preSelectedCriteria.contentType.value > 0) viewModel.setContentType(
                    preSelectedCriteria.contentType
                )
                typePicker.index = preSelectedCriteria.contentType.value
            } else {
                locationPicker.index = 0
                typePicker.index = 0
                viewModel.update()
            }
            locationPicker.setOnIndexChangeListener { index -> viewModel.setLocation(Location.entries.first { it.value == index }) }
            typePicker.setOnIndexChangeListener { index -> viewModel.setContentType(Type.entries.first { it.value == index }) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding?.apply {
            val builder = SearchActivityBundle()
            builder.uri = buildSearchUri(
                selectedAttributeAdapter.currentList.toSet(),
                "",
                locationPicker.index,
                typePicker.index
            ).toString()
            outState.putAll(builder.bundle)
            outState.putBoolean("exclude", excludeClicked)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        excludeClicked = savedInstanceState.getBoolean("exclude")
        val searchUri = Uri.parse(SearchActivityBundle(savedInstanceState).uri)
        if (searchUri != null) {
            val (_, attributes, location, contentType) = parseSearchUri(searchUri)
            if (attributes.isNotEmpty()) viewModel.setSelectedAttributes(attributes.toList())
            binding?.apply {
                if (location.value > 0) {
                    viewModel.setLocation(location)
                    locationPicker.index = location.value
                }
                if (contentType.value > 0) {
                    viewModel.setContentType(contentType)
                    typePicker.index = contentType.value
                }
            }
        }
    }

    /**
     * Observer for changes in the entry count inside each attribute type
     *
     * @param attrCount Entry count in every attribute type (key = attribute type code; value = count)
     */
    private fun onQueryUpdated(attrCount: SparseIntArray) {
        binding?.apply {
            updateAttributeTypeButton(textCategoryTag, attrCount, AttributeType.TAG)
            updateAttributeTypeButton(
                textCategoryArtist, attrCount, AttributeType.ARTIST, AttributeType.CIRCLE
            )
            updateAttributeTypeButton(textCategorySeries, attrCount, AttributeType.SERIE)
            updateAttributeTypeButton(
                textCategoryCharacter, attrCount, AttributeType.CHARACTER
            )
            updateAttributeTypeButton(
                textCategoryLanguage, attrCount, AttributeType.LANGUAGE
            )
            updateAttributeTypeButton(textCategorySource, attrCount, AttributeType.SOURCE)
        }
    }

    private fun onExcludeClick(view: View) {
        excludeClicked = (view as CheckBox).isChecked
    }

    /**
     * Update the text of a given attribute type button based on the given SparseIntArray and relevant type(s)
     *
     * @param button    Button whose text to update
     * @param attrCount Entry count in every attribute type (key = attribute type code; value = count)
     * @param types     Type(s) to fetch the count for
     */
    private fun updateAttributeTypeButton(
        button: TextView, attrCount: SparseIntArray, vararg types: AttributeType
    ) {
        if (types.isEmpty()) return
        var count = 0
        for (type in types) count += attrCount[type.code, 0]
        button.text = String.format(
            "%s (%s)", StringHelper.capitalizeString(getString(types[0].displayName)), count
        )
        button.isEnabled = count > 0
    }

    /**
     * Handler for the click on a attribute type button
     * Opens the bottom dialog for a given attribute type
     *
     * @param attributeTypes Attribute type(s) to select
     */
    private fun onAttrButtonClick(excludeClicked: Boolean, vararg attributeTypes: AttributeType) {
        invoke(
            this, supportFragmentManager, attributeTypes.toList(), excludeClicked
        )
    }

    /**
     * Observer for changes in the selected attributes
     *
     * @param selectedAttributes list of currently selected attributes
     */
    private fun onSelectedAttributesChanged(selectedAttributes: List<Attribute>) {
        binding?.apply {
            if (selectedAttributes.isEmpty()) {
                searchTags.visibility = View.GONE
                startCaption.visibility = View.VISIBLE
            } else {
                searchTags.visibility = View.VISIBLE
                startCaption.visibility = View.GONE
                selectedAttributeAdapter.submitList(selectedAttributes)
            }
        }
    }

    /**
     * Handler for click on a selected attribute
     *
     * @param button Button that has been clicked on; contains the corresponding attribute as its tag
     */
    private fun onSelectedAttributeClick(button: View) {
        val a = button.tag as Attribute?
        if (a != null) viewModel.removeSelectedAttribute(a)
    }

    /**
     * Observer for changes in the entry count of selected content
     * i.e. count of all books of the library matching the currently selected attributes
     *
     * @param count Current book count matching the currently selected attributes
     */
    private fun onBooksCounted(count: Int) {
        binding?.apply {
            if (count >= 0) {
                searchFab.text = resources.getQuantityString(
                    R.plurals.search_button, count, count
                )
                searchFab.visibility = View.VISIBLE
            } else {
                searchFab.visibility = View.GONE
            }
        }
    }

    /**
     * Handler for the click on the "Search books" button
     * Transmit the search query to the library screen and close the advanced search screen
     */
    private fun searchBooks() {
        binding?.apply {
            val searchUri = buildSearchUri(
                selectedAttributeAdapter.currentList.toSet(),
                "",
                locationPicker.index,
                typePicker.index
            )
            Timber.d("URI :%s", searchUri)
            val builder = SearchActivityBundle()
            builder.uri = searchUri.toString()
            builder.excludeMode = excludeClicked
            val returnIntent = Intent()
            returnIntent.putExtras(builder.bundle)
            setResult(RESULT_OK, returnIntent)
            finish()
        }
    }
}