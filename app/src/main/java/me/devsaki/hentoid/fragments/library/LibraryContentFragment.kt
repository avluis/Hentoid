package me.devsaki.hentoid.fragments.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DimenRes
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagedList
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.DiffCallback
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil.set
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.extensions.ExtensionsFactories.register
import com.mikepenz.fastadapter.listeners.ClickEventHook
import com.mikepenz.fastadapter.paged.ExperimentalPagedSupport
import com.mikepenz.fastadapter.paged.PagedModelAdapter
import com.mikepenz.fastadapter.select.SelectExtension
import com.mikepenz.fastadapter.select.SelectExtensionFactory
import com.mikepenz.fastadapter.swipe.SimpleSwipeDrawerCallback
import com.mikepenz.fastadapter.swipe_drag.SimpleSwipeDrawerDragCallback
import com.mikepenz.fastadapter.utils.DragDropUtil.onMove
import com.skydoves.powermenu.PowerMenuItem
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.activities.MetadataEditActivity
import me.devsaki.hentoid.activities.QueueActivity
import me.devsaki.hentoid.activities.SearchActivity
import me.devsaki.hentoid.activities.bundles.ContentItemBundle
import me.devsaki.hentoid.activities.bundles.MetaEditActivityBundle
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle.Companion.buildSearchUri
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle.Companion.parseSearchUri
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.databinding.FragmentLibraryContentBinding
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.AppUpdatedEvent
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.SelectSiteDialogFragment
import me.devsaki.hentoid.fragments.library.LibraryTransformDialogFragment.Companion.invoke
import me.devsaki.hentoid.fragments.library.MergeDialogFragment.Companion.invoke
import me.devsaki.hentoid.fragments.library.RatingDialogFragment.Companion.invoke
import me.devsaki.hentoid.fragments.library.SplitDialogFragment.Companion.invoke
import me.devsaki.hentoid.fragments.library.UpdateSuccessDialogFragment.Companion.invoke
import me.devsaki.hentoid.util.AchievementsManager
import me.devsaki.hentoid.util.Debouncer
import me.devsaki.hentoid.util.QueuePosition
import me.devsaki.hentoid.util.SearchCriteria
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.dimensAsDp
import me.devsaki.hentoid.util.dpToPx
import me.devsaki.hentoid.util.file.formatHumanReadableSizeInt
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getParent
import me.devsaki.hentoid.util.file.openUri
import me.devsaki.hentoid.util.formatEpochToDate
import me.devsaki.hentoid.util.getIdForCurrentTheme
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.util.launchBrowserFor
import me.devsaki.hentoid.util.openReader
import me.devsaki.hentoid.util.shareContent
import me.devsaki.hentoid.util.snack
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.util.viewContentGalleryPage
import me.devsaki.hentoid.viewholders.ContentItem
import me.devsaki.hentoid.viewholders.IDraggableViewHolder
import me.devsaki.hentoid.viewholders.ISwipeableViewHolder
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.AddQueueMenu
import me.devsaki.hentoid.widget.AutofitGridLayoutManager
import me.devsaki.hentoid.widget.DragSelectTouchListener
import me.devsaki.hentoid.widget.DragSelectionProcessor
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper
import me.devsaki.hentoid.widget.LibraryPager
import me.devsaki.hentoid.widget.RedownloadMenu
import me.devsaki.hentoid.widget.ScrollPositionListener
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.lang.ref.WeakReference
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalPagedSupport::class)
class LibraryContentFragment : Fragment(), ChangeGroupDialogFragment.Parent,
    MergeDialogFragment.Parent,
    SplitDialogFragment.Parent,
    RatingDialogFragment.Parent,
    LibraryTransformDialogFragment.Parent,
    SelectSiteDialogFragment.Parent,
    PopupTextProvider,
    ItemTouchCallback,
    SimpleSwipeDrawerCallback.ItemSwipeCallback {

    companion object {
        private const val KEY_LAST_LIST_POSITION = "last_list_position"

        // The one for "legacy" List (paged mode)
        val CONTENT_ITEM_DIFF_CALLBACK: DiffCallback<ContentItem> =
            object : DiffCallback<ContentItem> {
                override fun areItemsTheSame(
                    oldItem: ContentItem,
                    newItem: ContentItem
                ): Boolean {
                    return oldItem.identifier == newItem.identifier
                }

                override fun areContentsTheSame(
                    oldItem: ContentItem,
                    newItem: ContentItem
                ): Boolean {
                    var result = oldItem.content == newItem.content
                    if (oldItem.queueRecord != null && newItem.queueRecord != null) {
                        result =
                            result and (oldItem.queueRecord.frozen == newItem.queueRecord.frozen)
                    }
                    return result
                }

                override fun getChangePayload(
                    oldItem: ContentItem,
                    oldItemPosition: Int,
                    newItem: ContentItem,
                    newItemPosition: Int
                ): Any? {
                    val oldContent = oldItem.content
                    val newContent = newItem.content
                    if (null == oldContent || null == newContent) return false
                    val diffBundleBuilder = ContentItemBundle()
                    if (oldContent.favourite != newContent.favourite) {
                        diffBundleBuilder.isFavourite = newContent.favourite
                    }
                    if (oldContent.rating != newContent.rating) {
                        diffBundleBuilder.rating = newContent.rating
                    }
                    if (oldContent.completed != newContent.completed) {
                        diffBundleBuilder.isCompleted = newContent.completed
                    }
                    if (oldContent.reads != newContent.reads) {
                        diffBundleBuilder.reads = newContent.reads
                    }
                    if (oldContent.readPagesCount != newContent.readPagesCount) {
                        diffBundleBuilder.readPagesCount = newContent.readPagesCount
                    }
                    if (oldContent.coverImageUrl != newContent.coverImageUrl) {
                        diffBundleBuilder.coverUri = newContent.cover.fileUri
                    }
                    if (oldContent.title != newContent.title) {
                        diffBundleBuilder.title = newContent.title
                    }
                    if (oldContent.downloadMode != newContent.downloadMode) {
                        diffBundleBuilder.downloadMode = newContent.downloadMode.value
                    }
                    if (oldItem.queueRecord != null && newItem.queueRecord != null && oldItem.queueRecord.frozen != newItem.queueRecord.frozen) {
                        diffBundleBuilder.frozen = newItem.queueRecord.frozen
                    }
                    return if (diffBundleBuilder.isEmpty) null else diffBundleBuilder.bundle
                }
            }
    }


    // ======== COMMUNICATION
    private var callback: OnBackPressedCallback? = null

    // Viewmodel
    private lateinit var viewModel: LibraryViewModel

    // Settings listener
    private val prefsListener =
        OnSharedPreferenceChangeListener { _, k: String? ->
            onSharedPreferenceChanged(k)
        }

    // Activity
    private lateinit var activity: WeakReference<LibraryActivity>


    // ======== UI
    private var binding: FragmentLibraryContentBinding? = null

    // Wrapper for the bottom pager
    private val pager = LibraryPager { handleNewPage() }

    // LayoutManager of the recyclerView
    private var llm: LinearLayoutManager? = null

    // Scroll listener for the top FAB
    private val scrollListener = ScrollPositionListener { i -> onScrollPositionChange(i) }

    // === FASTADAPTER COMPONENTS AND HELPERS
    private var itemAdapter: ItemAdapter<ContentItem>? = null
    private var pagedItemAdapter: PagedModelAdapter<Content, ContentItem>? = null
    private var fastAdapter: FastAdapter<ContentItem>? = null
    private var selectExtension: SelectExtension<ContentItem>? = null
    private var touchHelper: ItemTouchHelper? = null
    private var mDragSelectTouchListener: DragSelectTouchListener? = null


    // ======== VARIABLES
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private var backButtonPressed: Long = 0

    // Total number of books in the whole unfiltered library
    private var totalContentCount = 0

    // True when a new search has been performed and its results have not been handled yet
    // False when the refresh is passive (i.e. not from a direct user action)
    private var newSearch = false

    // Collection of books according to current filters
    private var library: PagedList<Content>? = null

    // Position of top item to memorize or restore (used when activity is destroyed and recreated)
    private var topItemPosition = -1

    // TODO doc
    private var group: Group? = null

    // Indicate whether this tab is enabled (active on screen) or not
    private var enabled = false

    // Search and filtering criteria in the form of a Bundle (see ContentSearchManager.ContentSearchBundle)
    private var contentSearchBundle: Bundle? = null

    // Used to start processing when the recyclerView has finished updating
    private lateinit var listRefreshDebouncer: Debouncer<Int>

    // Used to check back the "exclude" checkbox when re-entering advanced search
    private var excludeClicked = false

    // Launches the search activity according to the returned result
    private val advancedSearchReturnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult -> advancedSearchReturnResult(result) }

    /**
     * Diff calculation rules for contents
     *
     * Created once and for all to be used by FastAdapter in endless mode (=using Android PagedList)
     */
    // The one for the PagedList (endless mode)
    private val asyncDifferConfig =
        AsyncDifferConfig.Builder(object : DiffUtil.ItemCallback<Content>() {
            override fun areItemsTheSame(oldItem: Content, newItem: Content): Boolean {
                return oldItem.uniqueHash() == newItem.uniqueHash()
            }

            override fun areContentsTheSame(oldItem: Content, newItem: Content): Boolean {
                if (oldItem.lastEditDate != newItem.lastEditDate) return false
                if (oldItem.favourite != newItem.favourite) return false
                if (oldItem.rating != newItem.rating) return false
                if (oldItem.completed != newItem.completed) return false
                if (oldItem.reads != newItem.reads) return false
                if (oldItem.readPagesCount != newItem.readPagesCount) return false
                if (oldItem.coverImageUrl != newItem.coverImageUrl) return false
                if (oldItem.isBeingProcessed != newItem.isBeingProcessed) return false
                if (oldItem.title != newItem.title) return false
                return true
            }

            override fun getChangePayload(oldItem: Content, newItem: Content): Any? {
                val diffBundleBuilder = ContentItemBundle()
                if (oldItem.favourite != newItem.favourite) {
                    diffBundleBuilder.isFavourite = newItem.favourite
                }
                if (oldItem.rating != newItem.rating) {
                    diffBundleBuilder.rating = newItem.rating
                }
                if (oldItem.completed != newItem.completed) {
                    diffBundleBuilder.isCompleted = newItem.completed
                }
                if (oldItem.reads != newItem.reads) {
                    diffBundleBuilder.reads = newItem.reads
                }
                if (oldItem.readPagesCount != newItem.readPagesCount) {
                    diffBundleBuilder.readPagesCount = newItem.readPagesCount
                }
                if (oldItem.coverImageUrl != newItem.coverImageUrl) {
                    diffBundleBuilder.coverUri = newItem.cover.fileUri
                }
                if (oldItem.isBeingProcessed != newItem.isBeingProcessed) {
                    diffBundleBuilder.processed = newItem.isBeingProcessed
                }
                if (oldItem.title != newItem.title) {
                    diffBundleBuilder.title = newItem.title
                }
                return if (diffBundleBuilder.isEmpty) null else diffBundleBuilder.bundle
            }
        }).build()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(requireActivity() is LibraryActivity) { "Parent activity has to be a LibraryActivity" }
        activity = WeakReference(requireActivity() as LibraryActivity)
        listRefreshDebouncer = Debouncer(lifecycleScope, 75) { onRecyclerUpdated(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        register(SelectExtensionFactory())
        EventBus.getDefault().register(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLibraryContentBinding.inflate(inflater, container, false)

        Settings.registerPrefsChangedListener(prefsListener)
        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[LibraryViewModel::class.java]

        initUI(binding!!.root)

        activity.get()?.initFragmentToolbars(
            selectExtension!!,
            { menuItem: MenuItem -> onToolbarItemClicked(menuItem) }
        ) { menuItem: MenuItem -> onSelectionToolbarItemClicked(menuItem) }

        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.newContentSearch.observe(viewLifecycleOwner) { newSearch = it }

        viewModel.libraryPaged.observe(viewLifecycleOwner) { onLibraryChanged(it) }

        viewModel.totalContent.observe(viewLifecycleOwner) { onTotalContentChanged(it) }

        viewModel.group.observe(viewLifecycleOwner) { group = it }

        viewModel.contentSearchBundle.observe(viewLifecycleOwner) { contentSearchBundle = it }

        // Display pager tooltip
        if (pager.isVisible()) pager.showTooltip(viewLifecycleOwner)
    }

    private fun onEnable() {
        enabled = true
        callback?.isEnabled = true
    }

    private fun onDisable() {
        enabled = false
        callback?.isEnabled = false
    }

    /**
     * Initialize the UI components
     *
     * @param rootView Root view of the library screen
     */
    private fun initUI(rootView: View) {
        // RecyclerView
        llm =
            if (Settings.Value.LIBRARY_DISPLAY_LIST == Settings.libraryDisplay) LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.VERTICAL,
                false
            ) else AutofitGridLayoutManager(
                requireContext(),
                dpToPx(requireContext(), Settings.libraryGridCardWidthDP)
            )
        binding?.recyclerView?.apply {
            layoutManager = llm
            addOnScrollListener(scrollListener)
            FastScrollerBuilder(this)
                .setPopupTextProvider(this@LibraryContentFragment)
                .useMd2Style()
                .build()
        }

        // Hide FAB when scrolling up
        binding?.topFab?.apply {
            scrollListener.setDeltaYListener(lifecycleScope) { i: Int ->
                isVisible = (Settings.topFabEnabled && i > 0)
            }

            // Top FAB
            setOnClickListener {
                llm?.scrollToPositionWithOffset(0, 0)
            }
            setOnLongClickListener {
                Settings.topFabEnabled = false
                visibility = View.GONE
                true
            }
        }

        // Swipe to shuffle
        binding?.swipeContainer?.apply {
            setOnRefreshListener {
                if (this.isRefreshing && Settings.contentSortField == Settings.Value.ORDER_FIELD_RANDOM) {
                    viewModel.shuffleContent()
                    viewModel.searchContent()
                }
                isRefreshing = false
            }
            setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light
            )
        }

        // Pager
        pager.initUI(rootView)
        setPagingMethod(Settings.endlessScroll, false)
        addCustomBackControl()
    }

    private fun addCustomBackControl() {
        callback?.remove()
        callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                customBackPress()
            }
        }
        callback?.let {
            activity.get()?.onBackPressedDispatcher?.addCallback(activity.get()!!, it)
        }
    }

    private fun getQuery(): String {
        return activity.get()!!.getQuery()
    }

    private fun getMetadata(): SearchCriteria {
        return activity.get()!!.getSearchCriteria()
    }

    private fun setMetadata(criteria: SearchCriteria) {
        activity.get()?.setAdvancedSearchCriteria(criteria)
    }

    private fun enterEditMode() {
        if (group!!.hasCustomBookOrder) { // Warn if a custom order already exists
            MaterialAlertDialogBuilder(
                requireContext(),
                requireContext().getIdForCurrentTheme(R.style.Theme_Light_Dialog)
            )
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.app_name)
                .setMessage(R.string.menu_edit_warning_custom)
                .setPositiveButton(
                    R.string.yes
                ) { dialog1, _ -> dialog1.dismiss() }
                .setNegativeButton(
                    R.string.no
                ) { dialog2, _ ->
                    dialog2.dismiss()
                    cancelEdit()
                }
                .create()
                .show()
        }
        activity.get()?.setEditMode(true)
    }

    private fun cancelEdit() {
        activity.get()?.setEditMode(false)
    }

    private fun confirmEdit() {
        // == Save new item position
        // Set ordering field to custom
        Settings.contentSortField = Settings.Value.ORDER_FIELD_CUSTOM
        // Set ordering direction to ASC (we just manually ordered stuff; it has to be displayed as is)
        Settings.isContentSortDesc = false
        viewModel.saveContentPositions(itemAdapter!!.adapterItems.mapNotNull { it.content }) { refreshIfNeeded() }
        group?.hasCustomBookOrder = true
        activity.get()?.setEditMode(false)
    }

    private fun onToolbarItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_edit -> enterEditMode()
            R.id.action_edit_confirm -> confirmEdit()
            R.id.action_edit_cancel -> cancelEdit()
            else -> return activity.get()!!.toolbarOnItemClicked(menuItem)
        }
        return true
    }

    private fun onSelectionToolbarItemClicked(menuItem: MenuItem): Boolean {
        var keepToolbar = false
        var keepSelection = true
        val selectedContent: ContentItem?
        when (menuItem.itemId) {
            R.id.action_share -> shareSelectedItems()
            R.id.action_delete -> deleteSelectedItems()
            R.id.action_completed -> markSelectedAsCompleted()
            R.id.action_reset_read -> resetSelectedReadStats()
            R.id.action_rate -> onMassRateClick()
            R.id.action_archive -> archiveSelectedItems()
            R.id.action_change_group -> moveSelectedItems()
            R.id.action_open_folder -> openItemFolder()
            R.id.action_redownload -> {
                askRedownloadSelectedItemsScratch()
                keepToolbar = true
            }

            R.id.action_download -> {
                askDownloadSelectedItems()
                keepToolbar = true
            }

            R.id.action_stream -> {
                askStreamSelectedItems()
                keepToolbar = true
            }

            R.id.action_select_all -> {
                // Make certain _everything_ is properly selected (selectExtension.select() as doesn't get everything the 1st time it's called)
                var count = 0
                selectExtension?.apply {
                    while (selections.size < getItemAdapter()!!.adapterItemCount && ++count < 5)
                        IntRange(0, getItemAdapter()!!.adapterItemCount - 1).forEach {
                            select(it, false, true)
                        }
                }
                keepToolbar = true
            }

            R.id.action_set_group_cover -> askSetGroupCover()
            R.id.action_merge -> {
                invoke(
                    this,
                    selectExtension!!.selectedItems.mapNotNull { ci -> ci.content },
                    false
                )
                keepToolbar = true
            }

            R.id.action_split -> {
                selectedContent = selectExtension!!.selectedItems.firstOrNull()
                selectedContent?.let {
                    val c = it.content
                    if (c != null) invoke(this, c)
                }
                keepToolbar = true
            }

            R.id.action_transform -> {
                val contents = selectExtension!!.selectedItems.mapNotNull { ci -> ci.content }
                if (contents.size > 1000) {
                    snack(R.string.transform_limit)
                    return false
                }
                if (contents.isEmpty()) {
                    snack(R.string.invalid_selection_generic)
                    return false
                }
                invoke(this, contents)
                keepToolbar = true
            }

            R.id.action_edit -> {
                val selectedIds = selectExtension!!.selectedItems
                    .mapNotNull { ci -> ci.content }
                    .map { c -> c.id }
                if (selectedIds.isNotEmpty()) {
                    val editMetaIntent = Intent(this.context, MetadataEditActivity::class.java)
                    val builder = MetaEditActivityBundle()
                    builder.contentIds = selectedIds.toLongArray()
                    editMetaIntent.putExtras(builder.bundle)
                    requireContext().startActivity(editMetaIntent)
                }
                keepSelection = false
            }

            else -> {
                activity.get()!!.getSelectionToolbar()?.visibility = View.GONE
                return false
            }
        }
        if (!keepSelection) {
            selectExtension!!.selectOnLongClick = true
            selectExtension!!.deselect(selectExtension!!.selections.toMutableSet())
        }
        if (!keepToolbar) activity.get()!!.getSelectionToolbar()!!.visibility = View.GONE
        return true
    }

    // TODO doc
    override fun leaveSelectionMode() {
        selectExtension!!.selectOnLongClick = true
        // Warning : next line makes FastAdapter cycle through all items,
        // which has a side effect of calling TiledPageList.onPagePlaceholderInserted,
        // flagging the end of the list as being the last displayed position
        val selection = selectExtension!!.selections
        if (selection.isNotEmpty()) selectExtension!!.deselect(selection.toMutableSet())
        activity.get()!!.getSelectionToolbar()!!.visibility = View.GONE
    }

    /**
     * Callback for the "share item" action button
     */
    private fun shareSelectedItems() {
        val selectedItems: Set<ContentItem> = selectExtension!!.selectedItems
        if (selectedItems.isNotEmpty()) {
            val c = selectedItems.mapNotNull { ci -> ci.content }
            leaveSelectionMode()
            shareContent(requireContext(), c)
        }
    }

    /**
     * Callback for the "delete item" action button
     */
    private fun deleteSelectedItems() {
        val selectedItems: Set<ContentItem> = selectExtension!!.selectedItems
        if (selectedItems.isNotEmpty()) {
            var selectedContent = selectedItems.mapNotNull { it.content }
            // Remove external items if they can't be deleted
            if (!Settings.isDeleteExternalLibrary) selectedContent = selectedContent
                .filterNot { it.status == StatusContent.EXTERNAL }
            if (selectedContent.isNotEmpty()) activity.get()!!.askDeleteItems(
                selectedContent.map { it.id }, emptyList(),
                { refreshIfNeeded() }, selectExtension!!
            )
        }
    }

    /**
     * Callback for the "rate items" action button
     */
    private fun onMassRateClick() {
        val selectedItems = selectExtension!!.selectedItems
        val selectedIds = selectedItems.mapNotNull { ci -> ci.content }.map { c -> c.id }
        if (selectedIds.isNotEmpty()) {
            invoke(this, selectedIds.toLongArray(), 0)
        }
    }

    /**
     * Callback for "book completed" action button
     */
    private fun markSelectedAsCompleted() {
        selectExtension?.apply {
            val selectedItems: Set<ContentItem> = selectedItems
            if (selectedItems.isNotEmpty()) {
                val selectedContent = selectedItems.mapNotNull { ci -> ci.content }
                if (selectedContent.isNotEmpty()) {
                    viewModel.toggleContentCompleted(selectedContent) { refreshIfNeeded() }
                    deselect(selections.toMutableSet())
                }
            }
        }
    }

    /**
     * Callback for "reset read stats" action button
     */
    private fun resetSelectedReadStats() {
        selectExtension?.apply {
            val selectedItems: Set<ContentItem> = selectedItems
            if (selectedItems.isNotEmpty()) {
                val selectedContent = selectedItems.mapNotNull { ci -> ci.content }
                if (selectedContent.isNotEmpty()) {
                    viewModel.resetReadStats(selectedContent) { refreshIfNeeded() }
                    deselect(selections.toMutableSet())
                }
            }
        }
    }

    /**
     * Callback for the "archive item" action button
     */
    private fun archiveSelectedItems() {
        selectExtension?.apply {
            val selectedItems: Set<ContentItem> = selectedItems
            val contents = selectedItems.mapNotNull { ci -> ci.content }
                .filterNot { c -> c.storageUri.isEmpty() }
            activity.get()?.askArchiveItems(contents, this)
        }
    }

    /**
     * Callback for the "change group" action button
     */
    private fun moveSelectedItems() {
        selectExtension?.apply {
            val selectedItems: Set<ContentItem> = selectedItems
            deselect(selections.toMutableSet())
            val bookIds = selectedItems.mapNotNull { ci -> ci.content }.map { c -> c.id }
            ChangeGroupDialogFragment.invoke(
                this@LibraryContentFragment,
                bookIds.toLongArray()
            )
        }
    }

    /**
     * Callback for the "open containing folder" action button
     */
    private fun openItemFolder() {
        val selectedItems: Set<ContentItem> = selectExtension!!.selectedItems
        val context = getActivity() ?: return
        if (1 == selectedItems.size) {
            val item = selectedItems.firstOrNull() ?: return
            val c = item.content ?: return
            if (c.storageUri.isEmpty()) {
                toast(R.string.folder_undefined)
                return
            }
            val folder = getDocumentFromTreeUriString(context, c.storageUri)
            if (folder != null) {
                selectExtension?.apply { deselect(selections.toMutableSet()) }
                activity.get()?.getSelectionToolbar()?.visibility = View.GONE

                val uri = if (c.isArchive || c.isPdf)
                    getParent(context, Settings.externalLibraryUri.toUri(), folder.uri)
                else folder.uri
                uri?.let { openUri(context, it) }
            }
        }
    }

    /**
     * Callback for the "redownload from scratch" action button
     */
    private fun askRedownloadSelectedItemsScratch() {
        val selectedItems: Set<ContentItem> = selectExtension!!.selectedItems
        var externalContent = 0
        val contents: MutableList<Content> = java.util.ArrayList()
        for (ci in selectedItems) {
            val c = ci.content ?: continue
            if (c.status == StatusContent.EXTERNAL) {
                externalContent++
            } else {
                contents.add(c)
            }
        }
        if (contents.size > 1000) {
            snack(R.string.redownload_limit)
            return
        }
        binding?.recyclerView?.let {
            RedownloadMenu.show(
                requireContext(),
                it,
                this
            ) { position: Int, _ ->
                if (0 == position) redownloadFromScratch(contents) // Redownload images
                else viewModel.downloadContent(
                    contents, // Update metadata only
                    reparseContent = true,
                    reparseImages = false,
                    position = QueuePosition.TOP,
                    onSuccess = { nbSuccess: Int? ->
                        val message = resources.getQuantityString(
                            R.plurals.add_to_queue,
                            contents.size,
                            nbSuccess,
                            contents.size
                        )
                        val snackbar =
                            Snackbar.make(
                                it,
                                message,
                                BaseTransientBottomBar.LENGTH_LONG
                            )
                        snackbar.setAction(R.string.view_queue) { viewQueue() }
                        snackbar.show()
                    }
                ) { t: Throwable? ->
                    Timber.w(t)
                    snack(R.string.redownloaded_error)
                }
                leaveSelectionMode()
            }
        }
    }

    /**
     * Callback for the "Download" action button (for streamed books)
     */
    private fun askDownloadSelectedItems() {
        val selectedItems: Set<ContentItem> = selectExtension!!.selectedItems
        var nonOnlineContent = 0
        val contents: MutableList<Content> = java.util.ArrayList()
        for (ci in selectedItems) {
            val c = ci.content ?: continue
            if (DownloadMode.STREAM != c.downloadMode) {
                nonOnlineContent++
            } else {
                contents.add(c)
            }
        }
        var message = resources.getQuantityString(R.plurals.download_confirm, contents.size)
        if (nonOnlineContent > 0) message = resources.getQuantityString(
            R.plurals.download_non_streamed_content,
            nonOnlineContent,
            nonOnlineContent
        )
        MaterialAlertDialogBuilder(
            requireContext(),
            requireContext().getIdForCurrentTheme(R.style.Theme_Light_Dialog)
        )
            .setIcon(R.drawable.ic_warning)
            .setCancelable(false)
            .setTitle(R.string.app_name)
            .setMessage(message)
            .setPositiveButton(R.string.yes)
            { dialog1, _ ->
                dialog1.dismiss()
                download(contents) { t -> onDownloadError(t) }
                leaveSelectionMode()
            }
            .setNegativeButton(R.string.no)
            { dialog12, _ -> dialog12.dismiss() }
            .create()
            .show()
    }

    private fun onDownloadError(t: Throwable) {
        Timber.w(t)
        snack(t.message ?: "")
    }

    /**
     * Callback for the "Switch to streaming" action button
     */
    private fun askStreamSelectedItems() {
        val selectedItems: Set<ContentItem> = selectExtension!!.selectedItems
        var streamedOrExternalContent = 0
        val contents: MutableList<Content> = java.util.ArrayList()
        for (ci in selectedItems) {
            val c = ci.content ?: continue
            if (c.downloadMode == DownloadMode.STREAM || c.status == StatusContent.EXTERNAL) {
                streamedOrExternalContent++
            } else {
                contents.add(c)
            }
        }
        if (contents.size > 1000) {
            snack(R.string.stream_limit)
            return
        }
        var message = resources.getQuantityString(R.plurals.stream_confirm, contents.size)
        if (streamedOrExternalContent > 0) message = resources.getQuantityString(
            R.plurals.stream_external_streamed_content,
            streamedOrExternalContent,
            streamedOrExternalContent
        )
        MaterialAlertDialogBuilder(
            requireContext(),
            requireContext().getIdForCurrentTheme(R.style.Theme_Light_Dialog)
        )
            .setIcon(R.drawable.ic_warning)
            .setCancelable(false)
            .setTitle(R.string.app_name)
            .setMessage(message)
            .setPositiveButton(
                R.string.yes
            ) { dialog1, _ ->
                dialog1.dismiss()
                leaveSelectionMode()
                viewModel.streamContent(contents) { t: Throwable -> onStreamError(t) }
            }
            .setNegativeButton(
                R.string.no
            ) { dialog12, _ -> dialog12.dismiss() }
            .create()
            .show()
    }

    private fun onStreamError(t: Throwable) {
        Timber.w(t)
        snack(t.message ?: "")
    }

    /**
     * Callback for the "set as group cover" action button
     */
    private fun askSetGroupCover() {
        val selectedItems: Set<ContentItem> = selectExtension!!.selectedItems
        if (selectedItems.isEmpty()) return
        val item = selectedItems.firstOrNull() ?: return
        val content = item.content ?: return

        MaterialAlertDialogBuilder(
            requireContext(),
            requireContext().getIdForCurrentTheme(R.style.Theme_Light_Dialog)
        )
            .setCancelable(false)
            .setTitle(R.string.app_name)
            .setMessage(resources.getString(R.string.group_make_cover_ask))
            .setPositiveButton(
                R.string.yes
            ) { dialog1, _ ->
                dialog1.dismiss()
                viewModel.setGroupCoverContent(group!!.id, content)
                leaveSelectionMode()
            }
            .setNegativeButton(
                R.string.no
            ) { dialog12, _ -> dialog12.dismiss() }
            .create()
            .show()
    }

    /**
     * Indicates whether a search query is active (using universal search or advanced search) or not
     *
     * @return True if a search query is active (using universal search or advanced search); false if not (=whole unfiltered library selected)
     */
    private fun isSearchQueryActive(): Boolean {
        return activity.get()!!.isSearchQueryActive()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.onSaveState(outState)
        fastAdapter?.saveInstanceState(outState)

        // Remember current position in the sorted list
        val currentPosition = getTopItemPosition()
        if (currentPosition > 0 || -1 == topItemPosition) topItemPosition = currentPosition
        outState.putInt(KEY_LAST_LIST_POSITION, topItemPosition)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        topItemPosition = 0
        if (null == savedInstanceState) return
        viewModel.onRestoreState(savedInstanceState)
        fastAdapter?.withSavedInstanceState(savedInstanceState)
        // Mark last position in the list to be the one it will come back to
        topItemPosition = savedInstanceState.getInt(KEY_LAST_LIST_POSITION, 0)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onAppUpdated(event: AppUpdatedEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        // Display the "update success" dialog when an update is detected on a release version
        if (!BuildConfig.DEBUG) invoke(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onCommunicationEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.Recipient.CONTENTS && event.recipient != CommunicationEvent.Recipient.ALL) return
        when (event.type) {
            CommunicationEvent.Type.UPDATE_TOOLBAR -> {
                addCustomBackControl()
                selectExtension?.let { se ->
                    activity.get()?.initFragmentToolbars(se, { onToolbarItemClicked(it) })
                    { onSelectionToolbarItemClicked(it) }
                }
            }

            CommunicationEvent.Type.SEARCH -> onSubmitSearch(event.message)
            CommunicationEvent.Type.ADVANCED_SEARCH -> onAdvancedSearchButtonClick()
            CommunicationEvent.Type.ENABLE -> onEnable()
            CommunicationEvent.Type.DISABLE -> onDisable()
            CommunicationEvent.Type.UNSELECT -> leaveSelectionMode()
            CommunicationEvent.Type.UPDATE_EDIT_MODE -> setPagingMethod(
                Settings.endlessScroll, activity.get()!!.isEditMode()
            )

            CommunicationEvent.Type.SCROLL_TOP -> {
                topItemPosition = 0
                llm?.scrollToPositionWithOffset(0, 0)
            }

            else -> {}
        }
    }

    override fun onDestroy() {
        Settings.unregisterPrefsChangedListener(prefsListener)
        EventBus.getDefault().unregister(this)
        binding = null
        callback?.remove()
        super.onDestroy()
    }

    private fun customBackPress() {
        // If content is selected, deselect it
        if (selectExtension!!.selections.isNotEmpty()) {
            selectExtension!!.deselect(selectExtension!!.selections.toMutableSet())
            activity.get()!!.getSelectionToolbar()!!.visibility = View.GONE
            backButtonPressed = 0
            return
        }
        activity.get()?.apply {
            if (!collapseSearchMenu() && !closeLeftDrawer()) {
                // If none of the above and we're into a grouping, go back to the groups view
                if (Grouping.FLAT != Settings.getGroupingDisplayG()) {
                    // Load an empty list to avoid having the image of the current list appear
                    // on screen next time the activity's ViewPager2 switches back to LibraryContentFragment
                    viewModel.clearContent()
                    // Let the list become visually empty before going back to the groups fragment
                    Handler(Looper.getMainLooper()).postDelayed({
                        goBackToGroups()
                    }, 100)
                } else if (isFilterActive()) {
                    viewModel.clearContentFilters()
                } else if (backButtonPressed + 2000 > SystemClock.elapsedRealtime()) {
                    callback?.remove()
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    backButtonPressed = SystemClock.elapsedRealtime()
                    toast(R.string.press_back_again)
                    llm!!.scrollToPositionWithOffset(0, 0)
                }
            }
        }
    }

    /**
     * Callback for any change in Preferences
     */
    private fun onSharedPreferenceChanged(key: String?) {
        if (null == key) return

        Timber.v("Prefs change detected : %s", key)
        when (key) {
            Settings.Key.TOP_FAB ->
                binding?.topFab?.isVisible = Settings.topFabEnabled

            Settings.Key.ENDLESS_SCROLL -> {
                setPagingMethod(
                    Settings.endlessScroll, activity.get()!!.isEditMode()
                )
                FirebaseCrashlytics.getInstance().setCustomKey(
                    "Library display mode",
                    if (Settings.endlessScroll) "endless" else "paged"
                )
                viewModel.searchContent() // Trigger a blank search
            }

            else -> {}
        }
    }

    private fun onSubmitSearch(query: String) {
        if (query.startsWith("http")) { // Quick-open a page
            when (Site.searchByUrl(query)) {
                null -> snack(R.string.malformed_url)
                Site.NONE -> snack(R.string.unsupported_site)
                else -> launchBrowserFor(requireContext(), query)
            }
        } else {
            viewModel.searchContentUniversal(query)
        }
    }

    /**
     * Handler for the "Advanced search" button
     */
    private fun onAdvancedSearchButtonClick() {
        val search = Intent(this.context, SearchActivity::class.java)
        val builder = SearchActivityBundle()
        val advancedSearchCriteria = getMetadata()
        if (!advancedSearchCriteria.isEmpty()) {
            builder.uri = buildSearchUri(advancedSearchCriteria, "").toString()
        }
        if (group != null) builder.groupId = group!!.id
        builder.excludeMode = excludeClicked
        search.putExtras(builder.bundle)
        advancedSearchReturnLauncher.launch(search)
        activity.get()!!.collapseSearchMenu()
    }

    /**
     * Called when returning from the Advanced Search screen
     */
    private fun advancedSearchReturnResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK && result.data != null && result.data!!.extras != null) {
            val parser = SearchActivityBundle(result.data!!.extras!!)
            val searchUri = parser.uri.toUri()
            excludeClicked = parser.excludeMode
            setMetadata(parseSearchUri(searchUri))
            viewModel.searchContent(getQuery(), getMetadata(), searchUri)
        }
    }

    /**
     * Initialize the paging method of the screen
     *
     * @param isEndless True if endless mode has to be set; false if paged mode has to be set
     */
    @OptIn(ExperimentalPagedSupport::class)
    private fun setPagingMethod(isEndless: Boolean, isEditMode: Boolean) {
        // Editing will always be done in Endless mode
        viewModel.setContentPagingMethod(isEndless && !isEditMode)

        // RecyclerView horizontal centering
        binding?.recyclerView?.let {
            val layoutParams = it.layoutParams
            it.layoutParams = layoutParams
        }

        // Pager appearance
        if (!isEndless && !isEditMode) {
            pager.setCurrentPage(1)
            pager.show()
        } else pager.hide()

        // Adapter initialization
        if (isEndless && !isEditMode) {
            val viewType =
                if (Settings.Value.LIBRARY_DISPLAY_LIST == Settings.libraryDisplay) ContentItem.ViewType.LIBRARY else ContentItem.ViewType.LIBRARY_GRID
            pagedItemAdapter = PagedModelAdapter(
                asyncDifferConfig,
                { ContentItem(viewType) }) { c: Content ->
                ContentItem(c, touchHelper, viewType) { item -> onDeleteSwipedBook(item) }
            }
            fastAdapter = FastAdapter.with(pagedItemAdapter!!)
            val item = ContentItem(viewType)
            fastAdapter?.registerItemFactory(item.type, item)
            itemAdapter = null
        } else { // Paged mode or edit mode
            itemAdapter = ItemAdapter()
            fastAdapter = FastAdapter.with(itemAdapter!!)
            pagedItemAdapter = null
        }
        if (!fastAdapter!!.hasObservers()) fastAdapter?.setHasStableIds(true)


        // == CLICK LISTENERS

        // Item click listener
        fastAdapter?.onClickListener = { _, _, i: ContentItem, _ -> onItemClick(i) }

        // Favourite button click listener
        fastAdapter?.addEventHook(object : ClickEventHook<ContentItem>() {
            override fun onClick(
                v: View,
                position: Int,
                fastAdapter: FastAdapter<ContentItem>,
                item: ContentItem
            ) {
                if (item.content != null) onBookFavouriteClick(item.content)
            }

            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return if (viewHolder is ContentItem.ViewHolder) {
                    viewHolder.favouriteButton
                } else super.onBind(viewHolder)
            }
        })

        // Rating button click listener
        fastAdapter?.addEventHook(object : ClickEventHook<ContentItem>() {
            override fun onClick(
                v: View,
                position: Int,
                fastAdapter: FastAdapter<ContentItem>,
                item: ContentItem
            ) {
                if (item.content != null) onBookRatingClick(item.content)
            }

            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return if (viewHolder is ContentItem.ViewHolder) {
                    viewHolder.ratingButton
                } else super.onBind(viewHolder)
            }
        })

        // Site button click listener
        fastAdapter?.addEventHook(object : ClickEventHook<ContentItem>() {
            override fun onClick(
                v: View,
                position: Int,
                fastAdapter: FastAdapter<ContentItem>,
                item: ContentItem
            ) {
                if (item.content != null) onBookSourceClick(item.content)
            }

            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return if (viewHolder is ContentItem.ViewHolder) {
                    viewHolder.siteButton
                } else super.onBind(viewHolder)
            }
        })

        // "To top" button click listener (groups view only)
        fastAdapter?.addEventHook(object : ClickEventHook<ContentItem>() {
            override fun onClick(
                v: View,
                position: Int,
                fastAdapter: FastAdapter<ContentItem>,
                item: ContentItem
            ) {
                itemTouchOnMove(position, 0)
            }

            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return if (viewHolder is ContentItem.ViewHolder) {
                    viewHolder.topButton
                } else super.onBind(viewHolder)
            }
        })

        // "To bottom" button click listener (groups view only)
        fastAdapter?.addEventHook(object : ClickEventHook<ContentItem>() {
            override fun onClick(
                v: View,
                position: Int,
                fastAdapter: FastAdapter<ContentItem>,
                item: ContentItem
            ) {
                itemTouchOnMove(position, fastAdapter.itemCount - 1)
            }

            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return if (viewHolder is ContentItem.ViewHolder) {
                    viewHolder.bottomButton
                } else super.onBind(viewHolder)
            }
        })

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter!!.requireOrCreateExtension()
        selectExtension?.apply {
            isSelectable = true
            multiSelect = true
            selectOnLongClick = true
            selectWithItemUpdate = true
            selectionListener =
                object : ISelectionListener<ContentItem> {
                    override fun onSelectionChanged(item: ContentItem, selected: Boolean) {
                        onSelectionChanged()
                    }
                }
            val helper = FastAdapterPreClickSelectHelper(fastAdapter!!, this)
            fastAdapter?.onPreClickListener =
                { _, _, _, position -> helper.onPreClickListener(position) }
            fastAdapter?.onPreLongClickListener =
                { _, _, _, p ->
                    // Warning : specific code for drag selection
                    mDragSelectTouchListener?.startDragSelection(p)
                    helper.onPreLongClickListener(p)
                }
        }

        // Select / deselect on swipe
        val onDragSelectionListener: DragSelectTouchListener.OnDragSelectListener =
            DragSelectionProcessor(object : DragSelectionProcessor.ISelectionHandler {
                override val selection: Set<Int>
                    get() = selectExtension!!.selections

                override fun isSelected(index: Int): Boolean {
                    return selectExtension!!.selections.contains(index)
                }

                override fun updateSelection(
                    start: Int,
                    end: Int,
                    isSelected: Boolean,
                    calledFromOnStart: Boolean
                ) {
                    selectExtension?.let { se ->
                        if (isSelected) IntRange(start, end).forEach { se.select(it, false, true) }
                        else se.deselect(IntRange(start, end).toMutableList())
                    }
                }
            }).withMode(DragSelectionProcessor.Mode.Simple)

        DragSelectTouchListener().withSelectListener(onDragSelectionListener).let {
            mDragSelectTouchListener = it
            binding?.recyclerView?.addOnItemTouchListener(it)
        }

        // Drag, drop & swiping
        @DimenRes val dimen: Int =
            if (Settings.Value.LIBRARY_DISPLAY_LIST == Settings.libraryDisplay) R.dimen.delete_drawer_width_list else R.dimen.delete_drawer_width_grid
        val dragSwipeCallback = SimpleSwipeDrawerDragCallback(this, ItemTouchHelper.LEFT, this)
            .withSwipeLeft(dimensAsDp(requireContext(), dimen))
            .withSensitivity(1.5f)
            .withSurfaceThreshold(0.3f)
            .withNotifyAllDrops(true)
        dragSwipeCallback.setIsDragEnabled(false) // Despite its name, that's actually to disable drag on long tap
        touchHelper = ItemTouchHelper(dragSwipeCallback)

        binding?.recyclerView?.let {
            touchHelper?.attachToRecyclerView(it)
            it.adapter = fastAdapter
            it.setHasFixedSize(true)
        }
    }

    /**
     * Returns the index bounds of the list to be displayed according to the given shelf number
     * Used for paged mode only
     *
     * @param shelfNumber Number of the shelf to display
     * @param librarySize Size of the library
     * @return Min and max index of the books to display on the given page
     */
    private fun getShelfBound(shelfNumber: Int, librarySize: Int): Pair<Int, Int> {
        val minIndex = (shelfNumber - 1) * Settings.contentPageQuantity
        val maxIndex = min(minIndex + Settings.contentPageQuantity, librarySize)
        return Pair(minIndex, maxIndex)
    }

    /**
     * Loads current shelf of books to into the paged mode adapter
     * NB : A bookshelf is the portion of the collection that is displayed on screen by the paged mode
     * The width of the shelf is determined by the "Quantity per page" setting
     *
     * @param iLibrary Library to extract the shelf from
     */
    private fun loadBookshelf(iLibrary: PagedList<Content>) {
        if (iLibrary.isEmpty()) {
            itemAdapter?.set(emptyList())
            fastAdapter?.notifyDataSetChanged()
        } else {
            val bounds = getShelfBound(pager.getCurrentPageNumber(), iLibrary.size)
            val minIndex = bounds.first
            val maxIndex = bounds.second
            if (minIndex >= maxIndex) { // We just deleted the last item of the last page => Go back one page
                pager.setCurrentPage(pager.getCurrentPageNumber() - 1)
                loadBookshelf(iLibrary)
                return
            }
            populateBookshelf(iLibrary, pager.getCurrentPageNumber())
        }
    }

    /**
     * Displays the current "bookshelf" (section of the list corresponding to the selected page)
     * A shelf contains as many books as the user has set in Preferences
     *
     *
     * Used in paged mode only
     *
     * @param iLibrary    Library to display books from
     * @param shelfNumber Number of the shelf to display
     */
    private fun populateBookshelf(iLibrary: PagedList<Content>, shelfNumber: Int) {
        if (Settings.endlessScroll) return

        val bounds = getShelfBound(shelfNumber, iLibrary.size)
        val minIndex = bounds.first
        val maxIndex = bounds.second
        // Paged mode won't be used in edit mode
        val viewType =
            if (Settings.Value.LIBRARY_DISPLAY_LIST == Settings.libraryDisplay) ContentItem.ViewType.LIBRARY
            else ContentItem.ViewType.LIBRARY_GRID // Paged mode won't be used in edit mode
        val contentItems =
            iLibrary.subList(minIndex, maxIndex).filterNotNull().map { c ->
                ContentItem(c, null, viewType) { item -> onDeleteSwipedBook(item) }
            }.distinct()

        itemAdapter?.let {
            set(it, contentItems, CONTENT_ITEM_DIFF_CALLBACK)
        }
        Handler(Looper.getMainLooper()).postDelayed({ differEndCallback() }, 150)
    }

    private fun populateAllResults(iLibrary: PagedList<Content>) {
        if (null == itemAdapter) return
        val contentItems: List<ContentItem>
        if (iLibrary.isEmpty()) {
            contentItems = emptyList()
        } else {
            // Grid won't be used in edit mode
            val viewType =
                if (Settings.Value.LIBRARY_DISPLAY_LIST == Settings.libraryDisplay
                    || activity.get()!!.isEditMode()
                )
                    if (activity.get()!!.isEditMode()) ContentItem.ViewType.LIBRARY_EDIT
                    else ContentItem.ViewType.LIBRARY
                else ContentItem.ViewType.LIBRARY_GRID

            contentItems = iLibrary.subList(0, iLibrary.size).filterNotNull()
                .map { c ->
                    ContentItem(c, touchHelper, viewType) { item -> onDeleteSwipedBook(item) }
                }
                .distinct()
        }
        itemAdapter?.let {
            set(it, contentItems, CONTENT_ITEM_DIFF_CALLBACK)
        }
        Handler(Looper.getMainLooper()).postDelayed({ differEndCallback() }, 150)
    }

    /**
     * LiveData callback when the library changes
     * - Either because a new search has been performed
     * - Or because a book has been downloaded, deleted, updated
     *
     * @param result Current library according to active filters
     */
    @OptIn(ExperimentalPagedSupport::class)
    private fun onLibraryChanged(result: PagedList<Content>) {
        Timber.i(">> Library changed ! Size=%s enabled=%s", result.size, enabled)
        if (!enabled && Settings.getGroupingDisplayG() != Grouping.FLAT) return
        activity.get()?.updateTitle(result.size.toLong(), totalContentCount.toLong())

        // Reshuffle on swipe is only enabled when sort order is random
        binding?.swipeContainer?.isEnabled =
            (Settings.contentSortField == Settings.Value.ORDER_FIELD_RANDOM)

        // Update background text
        @StringRes var backgroundText = -1
        if (result.isEmpty()) {
            if (isSearchQueryActive()) backgroundText =
                R.string.search_entry_not_found else if (0 == totalContentCount) backgroundText =
                R.string.downloads_empty_library
        }
        binding?.emptyTxt?.apply {
            if (backgroundText != -1) {
                visibility = View.VISIBLE
                setText(backgroundText)
            } else visibility = View.GONE
        }

        // Update visibility and content of advanced search bar
        // - After getting results from a search
        // - When switching between Group and Content view
        // Shouldn't trigger for a new download
        if (newSearch) activity.get()?.updateSearchBarOnResults(!result.isEmpty())
        val query = getQuery()

        // User searches a book ID
        // => Suggests searching through all sources except those where the selected book ID is already in the collection
        if (newSearch && isNumeric(query)) {
            val dialogTitle = getString(R.string.search_bookid_label, query)
            val excludedSiteCodes = result.toList().filterNotNull()
                .filter { query == it.uniqueSiteId }
                .map { it.site.code }
            val siteCodes = Site.entries
                .map { it.code }
                .filterNot { excludedSiteCodes.contains(it) }
            if (!result.isEmpty()) {
                binding?.recyclerView?.let {
                    val snackbar: Snackbar = Snackbar.make(
                        it,
                        R.string.launchcode_present,
                        BaseTransientBottomBar.LENGTH_LONG
                    )
                    snackbar.setAction(R.string.menu_search) {
                        SelectSiteDialogFragment.invoke(
                            this,
                            dialogTitle,
                            siteCodes,
                            uniqueIdOnly = true,
                            showAltSites = true
                        )
                    }
                    snackbar.show()
                }
            } else SelectSiteDialogFragment.invoke(
                this,
                dialogTitle,
                siteCodes,
                uniqueIdOnly = true,
                showAltSites = true
            )
        }

        if (newSearch && query.trim().equals(
                resources.getString(R.string.ach_63),
                true
            ) && result.isNotEmpty()
        ) AchievementsManager.trigger(63)

        // If the update is the result of a new search, get back on top of the list
        if (newSearch) topItemPosition = 0

        // Update displayed books
        if (Settings.endlessScroll && !activity.get()!!
                .isEditMode() && pagedItemAdapter != null
        ) {
            pagedItemAdapter?.submitList(result) { differEndCallback() }
        } else if (activity.get()!!.isEditMode()) {
            populateAllResults(result)
        } else { // Paged mode
            if (newSearch) pager.setCurrentPage(1)
            pager.setPageCount(
                ceil(result.size * 1.0 / Settings.contentPageQuantity).toInt()
            )
            loadBookshelf(result)
        }

        newSearch = false
        library = result
    }

    /**
     * LiveData callback when the total number of books changes (because of book download of removal)
     *
     * @param count Current book count in the whole, unfiltered library
     */
    private fun onTotalContentChanged(count: Int) {
        totalContentCount = count
        if (enabled) {
            library?.let {
                activity.get()!!.updateTitle(it.size.toLong(), totalContentCount.toLong())
            }
        }
    }

    /**
     * Callback for the book holder itself
     *
     * @param item ContentItem that has been clicked on
     */
    private fun onItemClick(item: ContentItem): Boolean {
        if (selectExtension!!.selectOnLongClick) {
            if (item.content != null && !item.content.isBeingProcessed) {
                readBook(item.content, false)
            }
            return true
        }
        return false
    }

    // TODO doc
    override fun readBook(content: Content, forceShowGallery: Boolean) {
        topItemPosition = getTopItemPosition()
        openReader(
            requireContext(),
            content,
            -1,
            contentSearchBundle,
            forceShowGallery,
            false
        )
    }

    /**
     * Callback for the "source" button of the book holder
     *
     * @param content Content whose "source" button has been clicked on
     */
    private fun onBookSourceClick(content: Content) {
        viewContentGalleryPage(requireContext(), content)
    }

    /**
     * Callback for the "favourite" button of the book holder
     *
     * @param content Content whose "favourite" button has been clicked on
     */
    private fun onBookFavouriteClick(content: Content) {
        viewModel.toggleContentFavourite(content) { refreshIfNeeded() }
    }

    /**
     * Callback for the rating dialog
     */
    override fun rateItems(itemIds: LongArray, newRating: Int) {
        viewModel.rateContents(itemIds.asList(), newRating) { refreshIfNeeded() }
    }

    /**
     * Callback for the "rating" button of the book holder
     *
     * @param content Content whose "rating" button has been clicked on
     */
    private fun onBookRatingClick(content: Content) {
        invoke(this, longArrayOf(content.id), content.rating)
    }

    private fun redownloadFromScratch(contentList: List<Content>) {
        if (Settings.queueNewDownloadPosition == Settings.Value.QUEUE_NEW_DOWNLOADS_POSITION_ASK) {
            binding?.recyclerView?.let {
                AddQueueMenu.show(requireActivity(), it, this) { position, _ ->
                    redownloadFromScratch(
                        contentList,
                        if (0 == position) QueuePosition.TOP
                        else QueuePosition.BOTTOM
                    )
                }
            }
        } else redownloadFromScratch(
            contentList,
            QueuePosition.fromValue(Settings.queueNewDownloadPosition)
        )
    }

    private fun redownloadFromScratch(contentList: List<Content>, addMode: QueuePosition) {
        topItemPosition = getTopItemPosition()
        binding?.recyclerView?.let {
            viewModel.downloadContent(
                contentList,
                reparseContent = true,
                reparseImages = true,
                position = addMode,
                onSuccess = { nbSuccess ->
                    val message = resources.getQuantityString(
                        R.plurals.add_to_queue,
                        contentList.size,
                        nbSuccess,
                        contentList.size
                    )
                    val snackbar = Snackbar.make(it, message, BaseTransientBottomBar.LENGTH_LONG)
                    snackbar.setAction(R.string.view_queue) { viewQueue() }
                    snackbar.show()
                }
            ) { t ->
                Timber.w(t)
                snack(R.string.redownloaded_error)
            }
        }
    }

    private fun download(contentList: List<Content>, onError: Consumer<Throwable>) {
        if (Settings.queueNewDownloadPosition == Settings.Value.QUEUE_NEW_DOWNLOADS_POSITION_ASK) {
            binding?.recyclerView?.let {
                AddQueueMenu.show(activity.get()!!, it, this) { position: Int, _: PowerMenuItem? ->
                    download(
                        contentList,
                        if (0 == position) QueuePosition.TOP else QueuePosition.BOTTOM,
                        onError
                    )
                }
            }
        } else download(
            contentList,
            QueuePosition.fromValue(Settings.queueNewDownloadPosition),
            onError
        )
    }

    private fun download(
        contentList: List<Content>,
        addMode: QueuePosition,
        onError: Consumer<Throwable>
    ) {
        topItemPosition = getTopItemPosition()
        binding?.recyclerView?.let {
            viewModel.downloadContent(
                contentList,
                false, reparseImages = true,
                position = addMode,
                onSuccess = { nbSuccess ->
                    val message = resources.getQuantityString(
                        R.plurals.add_to_queue,
                        nbSuccess, nbSuccess, contentList.size
                    )
                    val snackbar =
                        Snackbar.make(it, message, BaseTransientBottomBar.LENGTH_LONG)
                    snackbar.setAction(R.string.view_queue) { viewQueue() }
                    snackbar.show()
                },
                onError = onError
            )
        }
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private fun onSelectionChanged() {
        val selectedItems: Set<ContentItem> = selectExtension!!.selectedItems
        val selectedCount = selectedItems.size
        if (0 == selectedCount) {
            activity.get()?.getSelectionToolbar()?.visibility = View.GONE
            selectExtension?.selectOnLongClick = true
        } else {
            val contentList = selectedItems.mapNotNull { it.content }
            val selectedProcessedCount = contentList.count { it.isBeingProcessed }
            val selectedLocalCount = contentList
                .filterNot { it.status == StatusContent.EXTERNAL }
                .filterNot { it.downloadMode == DownloadMode.STREAM }
                .count()
            val selectedStreamedCount = contentList.map { it.downloadMode }
                .count { it == DownloadMode.STREAM }
            val selectedNonArchivePdfExternalCount =
                contentList.count { it.status == StatusContent.EXTERNAL && !it.isArchive && !it.isPdf }
            val selectedArchivePdfExternalCount =
                contentList.count { it.status == StatusContent.EXTERNAL && (it.isArchive || it.isPdf) }
            activity.get()?.updateSelectionToolbar(
                selectedCount.toLong(),
                selectedProcessedCount.toLong(),
                selectedLocalCount.toLong(),
                selectedStreamedCount.toLong(),
                selectedNonArchivePdfExternalCount.toLong(),
                selectedArchivePdfExternalCount.toLong()
            )
            activity.get()?.getSelectionToolbar()?.visibility = View.VISIBLE
        }
    }

    /**
     * Handler for any page change
     */
    private fun handleNewPage() {
        loadBookshelf(library!!)
        binding?.recyclerView?.scrollToPosition(0)
    }

    /**
     * Navigate to the queue screen
     */
    private fun viewQueue() {
        val intent = Intent(requireContext(), QueueActivity::class.java)
        requireContext().startActivity(intent)
    }

    override fun mergeContents(
        contentList: List<Content>,
        newTitle: String,
        appendBookTitle: Boolean,
        deleteAfterMerging: Boolean
    ) {
        leaveSelectionMode()
        //invoke(this, resources.getString(R.string.merge_progress), R.plurals.page)
        viewModel.mergeContents(
            contentList, newTitle, appendBookTitle, deleteAfterMerging
        )
    }

    override fun splitContent(content: Content, chapters: List<Chapter>, deleteAfter: Boolean) {
        leaveSelectionMode()
        viewModel.splitContent(content, chapters, deleteAfter)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onProcessStickyEvent(event: ProcessEvent) {
        // Filter on delete, merge & split complete events
        if (R.id.delete_service_delete != event.processId && R.id.split_service != event.processId && R.id.merge_service != event.processId) return
        if (ProcessEvent.Type.COMPLETE != event.eventType) return
        refreshIfNeeded()
    }

    override fun onChangeGroupSuccess(nbProcessed: Int, nbTotal: Int) {
        if (nbProcessed == nbTotal) {
            snack(
                resources.getQuantityString(
                    R.plurals.notif_process_complete_details,
                    nbProcessed,
                    nbProcessed
                )
            )
        } else {
            val nbErrors = nbTotal - nbProcessed
            snack(
                resources.getQuantityString(
                    R.plurals.notif_delete_fail_details,
                    nbErrors,
                    nbErrors
                )
            )
        }
        refreshIfNeeded()
    }

    /**
     * Force a new search :
     * - when the book sort order is custom (in that case, LiveData can't do its job because of https://github.com/objectbox/objectbox-java/issues/141)
     * - when the current grouping is not flat (because the app needs to refresh the display when moving books out of/into the currently displayed group)
     */
    private fun refreshIfNeeded() {
        if (Grouping.FLAT != Settings.getGroupingDisplayG() || Settings.contentSortField == Settings.Value.ORDER_FIELD_CUSTOM)
            viewModel.searchContent(false)
    }

    /**
     * Callback for the end of item diff calculations
     * Activated when all _adapter_ items are placed on their definitive position
     */
    private fun differEndCallback() {
        if (topItemPosition > -1) {
            val targetPos = topItemPosition
            listRefreshDebouncer.submit(targetPos)
            topItemPosition = -1
        }
    }

    /**
     * Callback for the end of recycler updates
     * Activated when all _displayed_ items are placed on their definitive position
     */
    private fun onRecyclerUpdated(topItemPosition: Int) {
        val currentPosition = getTopItemPosition()
        // Used to restore position after activity has been stopped and recreated
        if (currentPosition != topItemPosition) {
            llm!!.scrollToPositionWithOffset(topItemPosition, 0)
        }
    }

    /**
     * Calculate the position of the top visible item of the book list
     *
     * @return position of the top visible item of the book list
     */
    private fun getTopItemPosition(): Int {
        return max(
            llm!!.findFirstVisibleItemPosition(),
            llm!!.findFirstCompletelyVisibleItemPosition()
        )
    }

    @OptIn(ExperimentalPagedSupport::class)
    private fun getItemAdapter(): IAdapter<ContentItem>? {
        return if (itemAdapter != null) itemAdapter else pagedItemAdapter
    }

    /**
     * DRAG, DROP & SWIPE METHODS
     */
    override fun itemTouchOnMove(oldPosition: Int, newPosition: Int): Boolean {
        onMove(itemAdapter!!, oldPosition, newPosition) // change position
        return true
    }

    override fun itemTouchDropped(oldPosition: Int, newPosition: Int) {
        // Nothing; final position will be saved once the "save" button is hit
    }

    override fun itemTouchStartDrag(viewHolder: RecyclerView.ViewHolder) {
        if (viewHolder is IDraggableViewHolder) {
            (viewHolder as IDraggableViewHolder).onDragged()
        }
    }

    override fun itemTouchStopDrag(viewHolder: RecyclerView.ViewHolder) {
        // Nothing
    }

    override fun itemSwiped(position: Int, direction: Int) {
        binding?.recyclerView?.let {
            val vh = it.findViewHolderForAdapterPosition(position)
            if (vh is ISwipeableViewHolder) {
                (vh as ISwipeableViewHolder).onSwiped()
            }
        }
    }

    override fun itemUnswiped(position: Int) {
        binding?.recyclerView?.let {
            val vh = it.findViewHolderForAdapterPosition(position)
            if (vh is ISwipeableViewHolder) {
                (vh as ISwipeableViewHolder).onUnswiped()
            }
        }
    }

    private fun onDeleteSwipedBook(item: ContentItem) {
        // Deleted book is the last selected books => disable selection mode
        if (item.isSelected) {
            selectExtension!!.deselect(item)
            if (selectExtension!!.selections.isEmpty()) activity.get()!!
                .getSelectionToolbar()!!.visibility =
                View.GONE
        }
        val content = item.content
        if (content != null) viewModel.deleteItems(
            listOf(content.id), emptyList(), false
        ) { refreshIfNeeded() }
    }

    /**
     * Scroll / page change listener
     *
     * @param scrollPosition New 0-based scroll position
     */
    private fun onScrollPositionChange(scrollPosition: Int) {
        if (Settings.topFabEnabled) {
            binding?.topFab?.isVisible = (scrollPosition > 2)
        }
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        val adapter = getItemAdapter() ?: return ""
        val c = adapter.getAdapterItem(position).content ?: return ""
        val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ENGLISH)
        return when (Settings.contentSortField) {
            Settings.Value.ORDER_FIELD_TITLE ->
                if (c.title.isEmpty()) ""
                else (c.title[0].toString() + "").uppercase(Locale.getDefault())

            Settings.Value.ORDER_FIELD_ARTIST ->
                if (c.author.isEmpty()) ""
                else (c.author[0].toString() + "").uppercase(Locale.getDefault())

            Settings.Value.ORDER_FIELD_NB_PAGES -> c.qtyPages.toLong().toString()
            Settings.Value.ORDER_FIELD_READS -> c.reads.toString()
            Settings.Value.ORDER_FIELD_SIZE -> formatHumanReadableSizeInt(
                c.size,
                resources
            )

            Settings.Value.ORDER_FIELD_READ_PROGRESS -> String.format(
                Locale.ENGLISH,
                "%d %%",
                (c.readProgress * 100).roundToInt()
            )

            Settings.Value.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE -> formatEpochToDate(
                c.downloadDate,
                formatter
            )

            Settings.Value.ORDER_FIELD_UPLOAD_DATE -> formatEpochToDate(
                c.uploadDate,
                formatter
            )

            Settings.Value.ORDER_FIELD_READ_DATE -> formatEpochToDate(
                c.lastReadDate,
                formatter
            )

            Settings.Value.ORDER_FIELD_DOWNLOAD_COMPLETION_DATE -> formatEpochToDate(
                c.downloadCompletionDate,
                formatter
            )

            Settings.Value.ORDER_FIELD_NONE, Settings.Value.ORDER_FIELD_CUSTOM, Settings.Value.ORDER_FIELD_RANDOM -> ""
            else -> ""
        }
    }

    override fun onSiteSelected(site: Site, altCode: Int) {
        launchBrowserFor(
            requireContext(), Content.getGalleryUrlFromId(site, getQuery(), altCode)
        )
    }
}