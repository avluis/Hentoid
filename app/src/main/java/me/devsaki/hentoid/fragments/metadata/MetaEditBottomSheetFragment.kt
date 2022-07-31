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
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
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
import me.devsaki.hentoid.util.Debouncer
import me.devsaki.hentoid.util.LanguageHelper
import me.devsaki.hentoid.util.SearchHelper.AttributeQueryResult
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.viewmodels.MetadataEditViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import timber.log.Timber

const val ATTRS_PER_PAGE = 40

class MetaEditBottomSheetFragment : BottomSheetDialogFragment() {

    // Communication
    private lateinit var viewModel: MetadataEditViewModel


    // UI
    private var _binding: IncludeSearchBottomPanelBinding? = null
    private val binding get() = _binding!!
    private lateinit var searchMasterDataDebouncer: Debouncer<String>

    // Container where all suggested attributes are loaded
    private lateinit var attributeAdapter: AvailableAttributeAdapter


    // Vars
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


    override fun onAttach(context: Context) {
        super.onAttach(context)

        val bundle = arguments
        if (bundle != null) {
            val parser = MetaEditActivityBundle(bundle)

            excludeAttr = parser.excludeMode
            currentPage = 1

            val vmFactory = ViewModelFactory(requireActivity().application)
            viewModel =
                ViewModelProvider(requireActivity(), vmFactory)[MetadataEditViewModel::class.java]
        }
        searchMasterDataDebouncer = Debouncer(context, 1000) { filter: String ->
            this.searchMasterData(filter)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = IncludeSearchBottomPanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        searchMasterDataDebouncer.clear()
        _binding = null
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
        binding.tagSuggestion.layoutManager = layoutManager
        attributeAdapter = AvailableAttributeAdapter()
        attributeAdapter.setOnScrollToEndListener { this.loadMore() }
        attributeAdapter.setOnClickListener { button: View ->
            this.onAttributeClicked(button)
        }
        binding.tagSuggestion.adapter = attributeAdapter
        binding.tagFilter.setSearchableInfo(getSearchableInfo(requireActivity())) // Associate searchable configuration with the SearchView

        binding.tagFilter.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                if (s.isNotEmpty()) searchMasterData(s)
                binding.tagFilter.clearFocus()
                return true
            }

            override fun onQueryTextChange(s: String): Boolean {
                searchMasterDataDebouncer.submit(s)
                return true
            }
        })

        viewModel.getAttributeTypes()
            .observe(viewLifecycleOwner) { results: List<AttributeType> ->
                onSelectedAttributeTypesReady(results)
            }
        viewModel.getContentAttributes()
            .observe(viewLifecycleOwner) { results: List<Attribute> ->
                onContentAttributesReady(results)
            }
        viewModel.getLibraryAttributes()
            .observe(viewLifecycleOwner) { results: AttributeQueryResult ->
                onLibraryAttributesReady(results)
            }
        searchMasterData("")
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
            binding.tagWaitDescription.startAnimation(BlinkAnimation(750, 20))
            binding.tagWaitDescription.setText(R.string.downloads_loading)
            binding.tagWaitPanel.visibility = View.VISIBLE
        }
        this.clearOnSuccess = clearOnSuccess
        viewModel.setAttributeQuery(filter, currentPage, ATTRS_PER_PAGE)
    }

    /**
     * Observer for changes in the available attributes (= query results)
     *
     * @param results Available attributes according to current search query
     */
    private fun onLibraryAttributesReady(results: AttributeQueryResult) {
        if (!isInitialized) return  // Hack to avoid double calls from LiveData
        binding.tagWaitDescription.clearAnimation()

        // Remove selected attributes from the result set
        val attrs = ArrayList(results.attributes)
        attrs.removeAll(contentAttributes
            .filter { a -> selectedAttributeTypes.contains(a.type) }
            .toSet())

        // Translate language names if present
        if (attrs.isNotEmpty() && attrs[0].type == AttributeType.LANGUAGE) {
            for (a in attrs) a.displayName = LanguageHelper.getLocalNameFromLanguage(
                requireContext(),
                a.name
            )
        }
        mTotalSelectedCount = results.totalSelectedAttributes.toInt()
        if (clearOnSuccess) attributeAdapter.clear()
        if (0 == mTotalSelectedCount) {
            val searchQuery: String = binding.tagFilter.query.toString()
            if (searchQuery.isEmpty()) dismiss() else binding.tagWaitDescription.setText(R.string.masterdata_no_result)
        } else {
            binding.tagWaitPanel.visibility = View.GONE
            attributeAdapter.setFormatWithNamespace(selectedAttributeTypes.size > 1)
            attributeAdapter.add(attrs)
        }
    }

    private fun onContentAttributesReady(data: List<Attribute>) {
        contentAttributes.clear()
        contentAttributes.addAll(data)
    }

    private fun onSelectedAttributeTypesReady(data: List<AttributeType>) {
        selectedAttributeTypes = ArrayList(data)
        val mainAttr = selectedAttributeTypes[0]

        // Image that displays current metadata type icon (e.g. face icon for character)
        binding.tagWaitImage.setImageResource(mainAttr.icon)

        // Image that displays current metadata type title (e.g. "Character search")
        binding.tagWaitTitle.text = getString(
            R.string.search_category,
            StringHelper.capitalizeString(getString(mainAttr.accusativeName))
        )

        val attrTypesNames =
            selectedAttributeTypes.map { a -> resources.getString(a.accusativeName) }

        binding.tagFilter.queryHint = resources.getString(
            R.string.search_prompt,
            TextUtils.join(", ", attrTypesNames)
        )
    }

    /**
     * Handler for Attribute button click
     *
     * @param button Button that has been clicked on
     */
    private fun onAttributeClicked(button: View) {
        val a = button.tag as Attribute
        if (!contentAttributes.contains(a)) { // Add selected tag
            button.isPressed = true
            a.isExcluded = excludeAttr
            viewModel.addContentAttribute(a)
            // Empty query and display all attributes again
            binding.tagFilter.setQuery("", false)
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
            searchMasterData(
                binding.tagFilter.query.toString(),
                displayLoadingImage = false,
                clearOnSuccess = false
            )
        }
    }

    companion object {
        fun invoke(
            context: Context,
            fragmentManager: FragmentManager,
            excludeSelected: Boolean
        ) {
            val builder = MetaEditActivityBundle()
            builder.excludeMode = excludeSelected

            val metaEditBottomSheetFragment = MetaEditBottomSheetFragment()
            metaEditBottomSheetFragment.arguments = builder.bundle
            ThemeHelper.setStyle(
                context,
                metaEditBottomSheetFragment,
                DialogFragment.STYLE_NORMAL,
                R.style.Theme_Light_BottomSheetDialog
            )
            metaEditBottomSheetFragment.show(fragmentManager, "metaEditBottomSheetFragment")
        }
    }
}