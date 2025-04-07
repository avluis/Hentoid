package me.devsaki.hentoid.fragments

import android.app.Activity
import android.app.Dialog
import android.app.SearchManager
import android.app.SearchableInfo
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle
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
import me.devsaki.hentoid.viewmodels.SearchViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import timber.log.Timber


/**
 * Bottom fragment that displays the available attributes in the advanced search screen
 * <p>
 * TODO 1 : look into recyclerview.extensions.ListAdapter for a RecyclerView.Adapter that can issue
 * appropriate notify commands based on list diff
 * <p>
 * TODO 2 : Use PagedList and FastAdapter to reduce boilerplate code used to display the endless list of available attributes
 * NB : only possible when ObjectBox implements SELECT Object.Field, COUNT(Object.Field) GROUP BY Object.Field to handle
 * source selection in a native ObjectBox query.
 */
class SearchBottomSheetFragment : BottomSheetDialogFragment() {
    // ViewModel of the current activity
    private lateinit var viewModel: SearchViewModel

    // UI
    private var binding: IncludeSearchBottomPanelBinding? = null

    /**
     * Strings submitted to this will be debounced to [.searchMasterData] after the given
     * delay.
     *
     * @see Debouncer
     */
    private lateinit var searchMasterDataDebouncer: Debouncer<String>

    // Container where all suggested attributes are loaded
    private lateinit var attributeAdapter: AvailableAttributeAdapter

    // Flag to clear the adapter on content reception
    private var clearOnSuccess = false

    // Current page of paged content (used to display the attributes list as an endless list)
    private var currentPage = 0

    // Total count of current available attributes
    private var mTotalSelectedCount = 0

    // Selected attribute types (selection done in the activity view)
    private lateinit var selectedAttributeTypes: List<AttributeType>

    // Flag to indicate is the fragment has been initialized, to avoid a double LiveData notification
    // See https://stackoverflow.com/a/50474911
    private var isInitialized = false

    private var excludeAttr = false


    override fun onAttach(context: Context) {
        super.onAttach(context)
        val bundle = arguments
        if (bundle != null) {
            val parser = SearchActivityBundle(bundle)
            var attributeTypeCodes: List<Int>? = parser.attributeTypes
            if (null == attributeTypeCodes) attributeTypeCodes = emptyList()
            selectedAttributeTypes =
                attributeTypeCodes.mapNotNull { c -> AttributeType.searchByCode(c) }
            excludeAttr = parser.excludeMode
            val groupId = parser.groupId
            currentPage = 1
            require(selectedAttributeTypes.isNotEmpty()) { "Initialization failed" }
            val vmFactory = ViewModelFactory(requireActivity().application)
            viewModel =
                ViewModelProvider(requireActivity(), vmFactory)[SearchViewModel::class.java]
            viewModel.setAttributeTypes(selectedAttributeTypes)
            viewModel.setGroup(groupId)
        }
        searchMasterDataDebouncer = Debouncer(this.lifecycleScope, 1000) { filter: String ->
            this.searchMasterData(filter)
        }
    }

    // https://stackoverflow.com/questions/46861306/how-to-disable-bottomsheetdialogfragment-dragging
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener {
            val bottomSheet = bottomSheetDialog
                .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)

            if (bottomSheet != null) {
                val behavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(bottomSheet)
                behavior.isDraggable = false
            }
        }
        return bottomSheetDialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = IncludeSearchBottomPanelBinding.inflate(inflater, container, false)
        val mainAttr = selectedAttributeTypes[0]

        binding?.apply {
            // Image that displays current metadata type icon (e.g. face icon for character)
            tagWaitImage.setImageResource(mainAttr.icon)

            // Image that displays current metadata type title (e.g. "Character search")
            tagWaitTitle.text = getString(
                R.string.search_category, capitalizeString(
                    getString(mainAttr.accusativeName)
                )
            )
            val layoutManager = FlexboxLayoutManager(requireContext())
            //        layoutManager.setAlignContent(AlignContent.FLEX_START); <-- not supported
            layoutManager.alignItems = AlignItems.STRETCH
            layoutManager.flexWrap = FlexWrap.WRAP
            tagSuggestion.layoutManager = layoutManager
            attributeAdapter = AvailableAttributeAdapter()
            attributeAdapter.setOnScrollToEndListener { loadMore() }
            attributeAdapter.setOnClickListener { onAttributeChosen(it) }
            tagSuggestion.adapter = attributeAdapter

            tagFilter.setSearchableInfo(getSearchableInfo(requireActivity())) // Associate searchable configuration with the SearchView
            val attrTypesNames = selectedAttributeTypes
                .map { it.accusativeName }
                .map { getString(it) }
            tagFilter.queryHint =
                resources.getString(R.string.search_prompt, TextUtils.join(", ", attrTypesNames))
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
        }
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.availableAttributes.observe(viewLifecycleOwner) { results: AttributeQueryResult ->
            onAttributesReady(results)
        }
        searchMasterData("")
    }

    override fun onResume() {
        super.onResume()
        isInitialized = true
    }

    override fun onDestroyView() {
        binding = null
        searchMasterDataDebouncer.clear()
        super.onDestroyView()
    }

    /**
     * Load the attributes corresponding to the given AttributeType, filtered with the given
     * string (applying "contains" filter)
     *
     * @param filter Filter to apply to the attributes name (only retrieve attributes with name like
     * %s%)
     */
    private fun searchMasterData(filter: String) {
        currentPage = 1
        searchMasterData(filter, displayLoadingImage = true, clearOnSuccess = true)
    }

    /**
     * Search the attributes master data according to the given parameters
     *
     * @param filter              Filter to apply to the attributes name (only retrieve attributes with name like %s%)
     * @param displayLoadingImage True if a "loading..." image has to be displayed
     * @param clearOnSuccess      True if the currently displayed list should be clear when this call succeeds
     * (should be true for new searches; false for "load more" queries)
     */
    private fun searchMasterData(
        filter: String,
        displayLoadingImage: Boolean,
        clearOnSuccess: Boolean
    ) {
        if (displayLoadingImage) {
            binding?.apply {
                tagWaitDescription.startAnimation(BlinkAnimation(750, 20))
                tagWaitDescription.setText(R.string.downloads_loading)
                tagWaitPanel.visibility = View.VISIBLE
            }
        }
        this.clearOnSuccess = clearOnSuccess
        viewModel.setAttributeQuery(filter, currentPage, ATTRS_PER_PAGE)
    }

    /**
     * Observer for changes in the available attributes (= query results)
     *
     * @param results Available attributes according to current search query
     */
    private fun onAttributesReady(results: AttributeQueryResult) {
        if (!isInitialized) return  // Hack to avoid double calls from LiveData
        binding?.tagWaitDescription?.clearAnimation()

        var selectedAttributes = viewModel.selectedAttributes.value
        selectedAttributes = selectedAttributes
            ?.filter { a -> selectedAttributeTypes.contains(a.type) }
            ?: emptyList()

        // Remove selected attributes from the result set
        val attrs = results.attributes.toMutableList()
        attrs.removeAll(selectedAttributes)

        // Translate language names if present
        if (attrs.isNotEmpty() && attrs[0].type == AttributeType.LANGUAGE) {
            for (a in attrs) a.displayName =
                LanguageHelper.getLocalNameFromLanguage(requireContext(), a.name)
        }
        mTotalSelectedCount = results.totalSelectedAttributes.toInt()
        if (clearOnSuccess) attributeAdapter.clear()
        binding?.apply {
            if (0 == mTotalSelectedCount) {
                val searchQuery = tagFilter.query.toString()
                if (searchQuery.isEmpty()) dismiss() else tagWaitDescription.setText(R.string.masterdata_no_result)
            } else {
                tagWaitPanel.visibility = View.GONE
                attributeAdapter.add(attrs)
            }
        }
    }

    /**
     * Handler for Attribute button click
     *
     * @param button Button that has been clicked on
     */
    private fun onAttributeChosen(button: View) {
        val a = button.tag as Attribute
        if (null == viewModel.selectedAttributes.value
            || !viewModel.selectedAttributes.value!!.contains(a)
        ) { // Add selected tag
            button.isPressed = true
            a.isExcluded = excludeAttr
            viewModel.addSelectedAttribute(a)
            // Empty query and display all attributes again
            binding?.tagFilter?.setQuery("", false)
            searchMasterData("")
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
            binding?.apply {
                searchMasterData(tagFilter.query.toString(), false, clearOnSuccess = false)
            }
        }
    }

    companion object {
        const val ATTRS_PER_PAGE = 40

        fun invoke(
            context: Context,
            fragmentManager: FragmentManager,
            types: List<AttributeType>,
            excludeClicked: Boolean
        ) {
            val builder = SearchActivityBundle()
            val attrTypes = types.map { at -> at.code }
            builder.attributeTypes = ArrayList(attrTypes)
            builder.excludeMode = excludeClicked
            val searchBottomSheetFragment = SearchBottomSheetFragment()
            searchBottomSheetFragment.arguments = builder.bundle
            context.setStyle(
                searchBottomSheetFragment,
                STYLE_NORMAL,
                R.style.Theme_Light_BottomSheetDialog
            )
            searchBottomSheetFragment.show(fragmentManager, "searchBottomSheetFragment")
        }
    }
}