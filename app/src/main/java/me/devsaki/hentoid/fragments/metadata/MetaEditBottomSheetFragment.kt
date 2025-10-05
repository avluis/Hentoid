package me.devsaki.hentoid.fragments.metadata

import android.app.Activity
import android.app.SearchManager
import android.app.SearchableInfo
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.MetaEditActivityBundle
import me.devsaki.hentoid.adapters.AvailableAttributeAdapter
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.databinding.IncludeSearchBottomPanelBinding
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.ui.BlinkAnimation
import me.devsaki.hentoid.util.AttributeQueryResult
import me.devsaki.hentoid.util.Debouncer
import me.devsaki.hentoid.util.LanguageHelper
import me.devsaki.hentoid.util.capitalizeString
import me.devsaki.hentoid.util.setStyle
import me.devsaki.hentoid.viewmodels.MetadataEditViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import timber.log.Timber

private const val ATTRS_PER_PAGE = 40

class MetaEditBottomSheetFragment : BottomSheetDialogFragment() {

    // Communication
    private lateinit var viewModel: MetadataEditViewModel


    // UI
    private var binding: IncludeSearchBottomPanelBinding? = null
    private lateinit var searchMasterDataDebouncer: Debouncer<String>

    // Container where all suggested attributes are loaded
    private lateinit var attributeAdapter: AvailableAttributeAdapter


    // VARS

    // Flag to indicate is the fragment has been initialized, to avoid a double LiveData notification
    // See https://stackoverflow.com/a/50474911
    private var isInitialized: Boolean = false

    // Flag to clear the adapter on content reception
    private var clearOnSuccess: Boolean = false

    // Current page of paged content (used to display the attributes list as an endless list)
    private var currentPage = 0

    // Total count of current available attributes
    private var mTotalSelectedCount = 0

    // Selected attribute types (selection done in the activity view)
    private var selectedAttributeTypes = ArrayList<AttributeType>()

    private var contentAttributes = ArrayList<Attribute>()

    private var excludeAttr = false
    private var idToReplace: Long = -1

    // 0 = App; 1 = MAL
    private var source = 0


    companion object {
        fun invoke(
            context: Context,
            fragmentManager: FragmentManager,
            excludeSelected: Boolean,
            idToReplace: Long = -1
        ) {
            val builder = MetaEditActivityBundle()
            builder.excludeMode = excludeSelected
            builder.idToReplace = idToReplace

            val bottomSheetFragment = MetaEditBottomSheetFragment()
            bottomSheetFragment.arguments = builder.bundle
            context.setStyle(
                bottomSheetFragment,
                STYLE_NORMAL,
                R.style.Theme_Light_BottomSheetDialog
            )
            bottomSheetFragment.show(fragmentManager, "metaEditBottomSheetFragment")
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        val bundle = arguments
        if (bundle != null) {
            val parser = MetaEditActivityBundle(bundle)

            excludeAttr = parser.excludeMode
            idToReplace = parser.idToReplace
            currentPage = 1

            val vmFactory = ViewModelFactory(requireActivity().application)
            viewModel =
                ViewModelProvider(requireActivity(), vmFactory)[MetadataEditViewModel::class.java]
        }
        searchMasterDataDebouncer = Debouncer(this.lifecycleScope, 1000) { filter: String ->
            this.searchMasterData(filter, source)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = IncludeSearchBottomPanelBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        searchMasterDataDebouncer.clear()
        binding = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        isInitialized = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val layoutManager = FlexboxLayoutManager(this.context)
        layoutManager.alignItems = AlignItems.STRETCH
        layoutManager.flexWrap = FlexWrap.WRAP
        attributeAdapter = AvailableAttributeAdapter()
        attributeAdapter.setOnScrollToEndListener { this.loadMore() }
        attributeAdapter.setOnClickListener { this.onAttributeClicked(it) }
        binding?.apply {
            tagSuggestion.layoutManager = layoutManager
            tagSuggestion.adapter = attributeAdapter
            tagFilter.setSearchableInfo(getSearchableInfo(requireActivity())) // Associate searchable configuration with the SearchView

            tagFilter.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(s: String): Boolean {
                    if (s.isNotEmpty()) searchMasterData(s)
                    tagFilter.clearFocus()
                    return true
                }

                override fun onQueryTextChange(s: String): Boolean {
                    searchMasterDataDebouncer.submit(s)
                    return true
                }
            })

            source.addOnButtonCheckedListener { _, i, b ->
                if (!b) return@addOnButtonCheckedListener
                this@MetaEditBottomSheetFragment.source = i
                searchMasterDataDebouncer.submit(tagFilter.query.toString())
            }
        }

        viewModel.getAttributeTypes()
            .observe(viewLifecycleOwner) { onSelectedAttributeTypesReady(it) }
        viewModel.getContentAttributes()
            .observe(viewLifecycleOwner) { onContentAttributesReady(it) }
        viewModel.getLibraryAttributes()
            .observe(viewLifecycleOwner) { onLibraryAttributesReady(it) }
        viewModel.getResetSelectionFilter()
            .observe(viewLifecycleOwner) { binding?.tagFilter?.setQuery("", true) }
        searchMasterData("")
    }

    /**
     * Load the attributes corresponding to the given AttributeType, filtered with the given
     * string (applying "contains" filter)
     *
     * @param filter Filter to apply to the attributes name (only retrieve attributes with name like %s%)
     * @param source Source to search from
     *      0: App
     *      1: MyAnimeList
     */
    private fun searchMasterData(filter: String, source: Int = 0) {
        currentPage = 1
        clearOnSuccess = true
        displayLoadingImage()
        if (0 == source) {
            viewModel.setAttributeQuery(filter, currentPage, ATTRS_PER_PAGE)
        } else {
            viewModel.searchMalMasterData(filter)
        }
    }

    /**
     * Search the attributes master data according to the given parameters
     *
     * @param filter              Filter to apply to the attributes name (only retrieve attributes with name like %s%)
     */
    private fun searchMoreMasterData(filter: String) {
        this.clearOnSuccess = false
        if (0 == source)
            viewModel.setAttributeQuery(filter, currentPage, ATTRS_PER_PAGE)
    }

    private fun displayLoadingImage() {
        binding?.apply {
            tagWaitDescription.startAnimation(BlinkAnimation(750, 20))
            tagWaitDescription.setText(R.string.downloads_loading)
            tagWaitPanel.visibility = View.VISIBLE
        }
    }

    /**
     * Observer for changes in the available attributes (= query results)
     *
     * @param results Available attributes according to current search query
     */
    private fun onLibraryAttributesReady(results: AttributeQueryResult) {
        if (!isInitialized) return  // Hack to avoid double calls from LiveData
        binding?.tagWaitDescription?.clearAnimation()

        // Remove selected attributes from the result set
        val attrs = ArrayList<Attribute>()
        attrs.removeAll(
            contentAttributes
                .filter { selectedAttributeTypes.contains(it.type) }
                .toSet()
        )

        var isQueryPresent = false
        val query = binding?.tagFilter?.query?.toString() ?: ""
        val filteredContentAttr =
            contentAttributes.filter { a -> selectedAttributeTypes.contains(a.type) }.toSet()
        for (attr in results.attributes) {
            if (attr.displayName.equals(query, true)) isQueryPresent = true
            if (filteredContentAttr.contains(attr)) continue

            // Translate language names if present
            if (attr.type == AttributeType.LANGUAGE)
                attr.displayName = LanguageHelper.getLocalNameFromLanguage(
                    requireContext(),
                    attr.name
                )
            attrs.add(attr)
        }

        // If query is absent from collection, add the "new tag" attribute at the beginning of the list
        if (!isQueryPresent && query.isNotEmpty()) {
            val targetType =
                if (1 == selectedAttributeTypes.size) selectedAttributeTypes[0] else AttributeType.UNDEFINED
            val newAttr = Attribute(type = targetType, name = query.lowercase())
            newAttr.isNew = true
            attrs.add(0, newAttr)
        }

        mTotalSelectedCount = results.totalSelectedAttributes.toInt()
        if (clearOnSuccess) attributeAdapter.clear()
        binding?.tagWaitPanel?.visibility = View.GONE

        attributeAdapter.add(attrs)
    }

    private fun onContentAttributesReady(data: List<Attribute>) {
        contentAttributes.clear()
        contentAttributes.addAll(data)
    }

    private fun onSelectedAttributeTypesReady(data: List<AttributeType>) {
        binding?.apply {
            selectedAttributeTypes = ArrayList(data)
            if (selectedAttributeTypes.count() < 3) {
                val mainAttr = selectedAttributeTypes[0]

                // Image that displays current metadata type icon (e.g. face icon for character)
                tagWaitImage.visibility = View.VISIBLE
                tagWaitImage.setImageResource(mainAttr.icon)

                // Image that displays current metadata type title (e.g. "Character search")
                tagWaitTitle.text = getString(
                    R.string.search_category,
                    capitalizeString(getString(mainAttr.accusativeName))
                )

                source.isVisible =
                    (mainAttr == AttributeType.CHARACTER || mainAttr == AttributeType.SERIE)
            } else {
                tagWaitImage.visibility = View.INVISIBLE
                tagWaitTitle.text = getString(R.string.searching_generic)
            }

            val attrTypesNames =
                selectedAttributeTypes.map { resources.getString(it.accusativeName) }

            tagFilter.queryHint = resources.getString(
                R.string.search_prompt,
                TextUtils.join(", ", attrTypesNames)
            )
        }
    }

    /**
     * Handler for Attribute button click
     *
     * @param button Button that has been clicked on
     */
    private fun onAttributeClicked(button: View) {
        val attr = button.tag as Attribute
        if (attr.isNew) { // Create new attribute
            if (attr.type == AttributeType.UNDEFINED)
                AttributeTypePickerDialogFragment.invoke(activity as FragmentActivity, attr.name)
            else // Type already known
                onNewAttributeSelected(attr.name, attr.type)
        } else if (!contentAttributes.contains(attr)) { // Add existing attribute
            button.isPressed = true
            attr.isExcluded = excludeAttr
            if (idToReplace > -1) viewModel.replaceContentAttribute(idToReplace, attr)
            else viewModel.addContentAttribute(attr)
            // Empty query and display all attributes again
            binding?.tagFilter?.setQuery("", false)
            attributeAdapter.remove(attr)
        }
    }

    /**
     * Utility method
     *
     * @param activity the activity to get the SearchableInfo from
     */
    private fun getSearchableInfo(activity: Activity): SearchableInfo? {
        val searchManager = activity.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        return searchManager.getSearchableInfo(activity.componentName)
    }

    /**
     * Indicates if the current "page" of loaded attributes is the last one
     */
    private fun isLastPage(): Boolean {
        return currentPage * ATTRS_PER_PAGE >= mTotalSelectedCount
    }

    /**
     * Callback when the recyclerview has reached the end of loaded items
     * Load the next set of items
     */
    private fun loadMore() {
        if (!isLastPage()) { // NB : A "page" is a group of loaded attributes. Last page is reached when scrolling reaches the very end of the list
            Timber.d("Load more data now~")
            currentPage++
            searchMoreMasterData(binding?.tagFilter?.query?.toString() ?: "")
        }
    }

    fun onNewAttributeSelected(name: String, type: AttributeType) {
        val attr = viewModel.createAssignNewAttribute(name, type)
        attributeAdapter.remove(attr)
        searchMasterData(name)
    }
}