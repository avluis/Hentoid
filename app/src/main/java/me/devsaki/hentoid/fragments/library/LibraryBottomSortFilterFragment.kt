package me.devsaki.hentoid.fragments.library

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.select.SelectExtension
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.activities.bundles.LibraryBottomSortFilterBundle
import me.devsaki.hentoid.databinding.IncludeLibrarySortFilterBottomPanelBinding
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.setStyle
import me.devsaki.hentoid.viewholders.TextItem
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.ContentSearchManager.ContentSearchBundle
import me.devsaki.hentoid.widget.FolderSearchManager.FolderSearchBundle
import me.devsaki.hentoid.widget.GroupSearchManager.GroupSearchBundle

class LibraryBottomSortFilterFragment : BottomSheetDialogFragment() {
    private lateinit var viewModel: LibraryViewModel

    // UI
    private var binding: IncludeLibrarySortFilterBottomPanelBinding? = null
    private val stars = arrayOfNulls<ImageView>(6)

    // RecyclerView controls
    private val itemAdapter = ItemAdapter<TextItem<Int>>()
    private val fastAdapter = FastAdapter.with(itemAdapter)
    private var selectExtension: SelectExtension<TextItem<Int>>? = null

    // Variables
    private var isUngroupedGroupDisplayed = false
    private var isGroupsDisplayed = false
    private var isFoldersDisplayed = false
    private var favouriteFilter = false
    private var nonFavouriteFilter = false
    private var completedFilter = false
    private var notCompletedFilter = false
    private var ratingFilter = -1

    @ColorInt
    private var greyColor = 0

    @ColorInt
    private var selectedColor = 0


    companion object {
        @Synchronized
        fun invoke(
            context: Context,
            fragmentManager: FragmentManager,
            isGroupsDisplayed: Boolean,
            isUngroupedGroupDisplayed: Boolean,
            isFoldersDisplayed: Boolean
        ) {
            // Don't re-create it if already shown
            for (fragment in fragmentManager.fragments) if (fragment is LibraryBottomSortFilterFragment) return
            val builder = LibraryBottomSortFilterBundle()
            builder.isGroupsDisplayed = isGroupsDisplayed
            builder.isUngroupedGroupDisplayed = isUngroupedGroupDisplayed
            builder.isFoldersDisplayed = isFoldersDisplayed
            val libraryBottomSheetFragment = LibraryBottomSortFilterFragment()
            libraryBottomSheetFragment.arguments = builder.bundle
            context.setStyle(
                libraryBottomSheetFragment,
                STYLE_NORMAL,
                R.style.Theme_Light_BottomSheetDialog
            )
            libraryBottomSheetFragment.show(fragmentManager, "libraryBottomSheetFragment")
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val bundle = arguments
        if (bundle != null) {
            val parser = LibraryBottomSortFilterBundle(bundle)
            isGroupsDisplayed = parser.isGroupsDisplayed
            isFoldersDisplayed = parser.isFoldersDisplayed
            isUngroupedGroupDisplayed = parser.isUngroupedGroupDisplayed
        }
        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[LibraryViewModel::class.java]
        viewModel.contentSearchBundle.observe(this) { b: Bundle? ->
            if (isGroupsDisplayed || isFoldersDisplayed) return@observe
            val searchBundle = ContentSearchBundle(b!!)
            favouriteFilter = searchBundle.filterBookFavourites
            nonFavouriteFilter = searchBundle.filterBookNonFavourites
            completedFilter = searchBundle.filterBookCompleted
            notCompletedFilter = searchBundle.filterBookNotCompleted
            ratingFilter = searchBundle.filterRating
            updateFilters()
        }
        viewModel.groupSearchBundle.observe(this) { b: Bundle? ->
            if (!isGroupsDisplayed) return@observe
            val searchBundle = GroupSearchBundle(b!!)
            favouriteFilter = searchBundle.filterFavourites
            nonFavouriteFilter = searchBundle.filterNonFavourites
            ratingFilter = searchBundle.filterRating
            updateFilters()
        }
        viewModel.folderSearchBundle.observe(this) { b: Bundle? ->
            if (!isFoldersDisplayed) return@observe
            val searchBundle = FolderSearchBundle(b!!)
            // TODO
            updateFilters()
        }
        greyColor = ContextCompat.getColor(context, R.color.medium_gray)
        selectedColor = context.getThemedColor(R.color.secondary_light)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = IncludeLibrarySortFilterBottomPanelBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Hack to show the bottom sheet in expanded state by default (https://stackoverflow.com/a/45706484/8374722)
        view.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val dialog = dialog as BottomSheetDialog?
                if (dialog != null) {
                    val bottomSheet =
                        dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                    if (bottomSheet != null) {
                        val behavior = BottomSheetBehavior.from(bottomSheet)
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                        behavior.skipCollapsed = true
                    }
                }
            }
        })

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.requireOrCreateExtension()
        selectExtension?.apply {
            isSelectable = true
            multiSelect = false
            selectOnLongClick = false
            selectWithItemUpdate = true
            allowDeselection = false
            selectionListener =
                object : ISelectionListener<TextItem<Int>> {
                    override fun onSelectionChanged(item: TextItem<Int>, selected: Boolean) {
                        if (selected) onSelectionChanged()
                    }
                }
        }

        binding?.apply {
            list.adapter = fastAdapter
            itemAdapter.set(getSortFields())
            updateSortDirection()
            sortRandom.setOnClickListener {
                viewModel.shuffleContent()
                viewModel.searchContent()
            }
            sortAscDesc.addOnButtonCheckedListener { _, i, b ->
                if (!b) return@addOnButtonCheckedListener
                if (isGroupsDisplayed) {
                    Settings.isGroupSortDesc = (i == R.id.sort_descending)
                    viewModel.searchGroup()
                } else if (isFoldersDisplayed) {
                    Settings.isFolderSortDesc = (i == R.id.sort_descending)
                    viewModel.searchFolder()
                } else {
                    Settings.isContentSortDesc = (i == R.id.sort_descending)
                    viewModel.searchContent()
                }
            }
            filtersPanel.isVisible = !isFoldersDisplayed
            filterFavsBtn.setOnClickListener {
                favouriteFilter = !favouriteFilter
                updateFilters()
                if (isGroupsDisplayed) viewModel.setGroupFavouriteFilter(favouriteFilter)
                else viewModel.setContentFavouriteFilter(favouriteFilter)
            }
            filterNonFavsBtn.setOnClickListener {
                nonFavouriteFilter = !nonFavouriteFilter
                updateFilters()
                if (isGroupsDisplayed) viewModel.setGroupNonFavouriteFilter(nonFavouriteFilter)
                else viewModel.setContentNonFavouriteFilter(nonFavouriteFilter)
            }
            filterCompletedBtn.setOnClickListener {
                completedFilter = !completedFilter
                updateFilters()
                viewModel.setCompletedFilter(completedFilter)
            }
            filterNotCompletedBtn.setOnClickListener {
                notCompletedFilter = !notCompletedFilter
                updateFilters()
                viewModel.setNotCompletedFilter(notCompletedFilter)
            }
            stars[0] = filterRatingNone
            stars[1] = filterRating1
            stars[2] = filterRating2
            stars[3] = filterRating3
            stars[4] = filterRating4
            stars[5] = filterRating5
            for (i in 0..5) {
                stars[i]?.setOnClickListener { setRating(i, false) }
            }
        }
    }

    private fun updateSortDirection() {
        val isRandom =
            (if (isGroupsDisplayed) Settings.groupSortField else if (isFoldersDisplayed) Settings.folderSortField else Settings.contentSortField) == Settings.Value.ORDER_FIELD_RANDOM
        binding?.apply {
            if (isRandom) {
                sortAscending.visibility = View.GONE
                sortDescending.visibility = View.GONE
                sortRandom.visibility = View.VISIBLE
                sortRandom.isChecked = true
            } else {
                sortRandom.visibility = View.GONE
                sortAscending.visibility = View.VISIBLE
                sortDescending.visibility = View.VISIBLE
                val currentPrefSortDesc =
                    if (isGroupsDisplayed) Settings.isGroupSortDesc else if (isFoldersDisplayed) Settings.isFolderSortDesc else Settings.isContentSortDesc
                sortAscDesc.check(if (currentPrefSortDesc) R.id.sort_descending else R.id.sort_ascending)
            }
        }
    }

    private fun updateFilters() {
        binding?.apply {
            filterFavsBtn.setColorFilter(if (favouriteFilter) selectedColor else greyColor)
            filterNonFavsBtn.setColorFilter(if (nonFavouriteFilter) selectedColor else greyColor)
            val completeFiltersVisibility = if (isGroupsDisplayed) View.GONE else View.VISIBLE
            filterCompletedBtn.visibility = completeFiltersVisibility
            filterNotCompletedBtn.visibility = completeFiltersVisibility
            filterCompletedBtn.setColorFilter(if (completedFilter) selectedColor else greyColor)
            filterNotCompletedBtn.setColorFilter(if (notCompletedFilter) selectedColor else greyColor)
        }
        setRating(ratingFilter, true)
    }

    private fun getSortFields(): List<TextItem<Int>> {
        val result: MutableList<TextItem<Int>> = ArrayList()
        if (isGroupsDisplayed) {
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_TITLE))
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_CHILDREN))
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE))
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_CUSTOM))
        } else if (isFoldersDisplayed) {
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_TITLE))
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_DOWNLOAD_COMPLETION_DATE))
        } else {
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_TITLE))
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_ARTIST))
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_NB_PAGES))
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE))
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_DOWNLOAD_COMPLETION_DATE))
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_UPLOAD_DATE))
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_READ_DATE))
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_READS))
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_SIZE))
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_READ_PROGRESS))
            if (Settings.getGroupingDisplayG().canReorderBooks && !isUngroupedGroupDisplayed
            ) result.add(
                createFromFieldCode(
                    Settings.Value.ORDER_FIELD_CUSTOM
                )
            )
            result.add(createFromFieldCode(Settings.Value.ORDER_FIELD_RANDOM))
        }
        return result
    }

    private fun createFromFieldCode(sortFieldCode: Int): TextItem<Int> {
        val currentPrefFieldCode =
            if (isGroupsDisplayed) Settings.groupSortField else if (isFoldersDisplayed) Settings.folderSortField else Settings.contentSortField
        return TextItem(
            resources.getString(LibraryActivity.getNameFromFieldCode(sortFieldCode)),
            sortFieldCode,
            true,
            currentPrefFieldCode == sortFieldCode
        )
    }

    private fun setRating(rating: Int, init: Boolean) {
        // Tap current rating -> clear
        val clear = !init && rating == ratingFilter
        for (i in 5 downTo 0) {
            val activated = i <= rating && !clear
            if (i > 0) stars[i]!!.setImageResource(if (activated) R.drawable.ic_star_full else R.drawable.ic_star_empty)
            var color = if (activated) selectedColor else greyColor
            // Don't colour the 1st icon if we're choosing at least 1 star
            if (activated && rating > 0 && 0 == i) color = greyColor
            stars[i]!!.setColorFilter(color)
        }
        ratingFilter = if (clear) -1 else rating
        if (!init) {
            if (isGroupsDisplayed) viewModel.setGroupRatingFilter(ratingFilter)
            else viewModel.setContentRatingFilter(ratingFilter)
        }
    }

    /**
     * Callback for any selection change (i.e. another sort field has been selected)
     */
    private fun onSelectionChanged() {
        val item = selectExtension!!.selectedItems.firstOrNull() ?: return
        val code = item.getObject()
        if (code != null) {
            if (isGroupsDisplayed) {
                Settings.groupSortField = code
                viewModel.searchGroup()
            } else if (isFoldersDisplayed) {
                Settings.folderSortField = code
                viewModel.searchFolder()
            } else {
                Settings.contentSortField = code
                viewModel.searchContent()
            }
        }
        updateSortDirection()
    }
}