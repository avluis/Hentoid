package me.devsaki.hentoid.fragments.library

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagedList
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.DiffCallback
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil.set
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.drag.SimpleDragCallback
import com.mikepenz.fastadapter.extensions.ExtensionsFactories.register
import com.mikepenz.fastadapter.select.SelectExtension
import com.mikepenz.fastadapter.select.SelectExtensionFactory
import com.mikepenz.fastadapter.swipe.SimpleSwipeCallback
import com.mikepenz.fastadapter.swipe_drag.SimpleSwipeDragCallback
import com.mikepenz.fastadapter.utils.DragDropUtil.onMove
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.activities.bundles.FileItemBundle
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.FragmentLibraryGroupsBinding
import me.devsaki.hentoid.events.AppUpdatedEvent
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.library.UpdateSuccessDialogFragment.Companion.invoke
import me.devsaki.hentoid.util.Debouncer
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.dpToPx
import me.devsaki.hentoid.util.file.DisplayFile
import me.devsaki.hentoid.util.file.fileExists
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.viewholders.FileItem
import me.devsaki.hentoid.viewholders.IDraggableViewHolder
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.AutofitGridLayoutManager
import me.devsaki.hentoid.widget.DragSelectTouchListener
import me.devsaki.hentoid.widget.DragSelectionProcessor
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.Locale

class LibraryFoldersFragment : Fragment(),
    PopupTextProvider,
    ItemTouchCallback,
    SimpleSwipeCallback.ItemSwipeCallback {

    // ======== COMMUNICATION
    private var callback: OnBackPressedCallback? = null

    // Viewmodel
    private lateinit var viewModel: LibraryViewModel

    // Activity
    private lateinit var activity: WeakReference<LibraryActivity>


    // ======== UI
    private var binding: FragmentLibraryGroupsBinding? = null

    // LayoutManager of the recyclerView
    private var llm: LinearLayoutManager? = null

    // === FASTADAPTER COMPONENTS AND HELPERS
    private var itemAdapter: ItemAdapter<FileItem> = ItemAdapter()
    private var fastAdapter = FastAdapter.with(itemAdapter)
    private var selectExtension: SelectExtension<FileItem>? = null
    private var touchHelper: ItemTouchHelper? = null
    private var mDragSelectTouchListener: DragSelectTouchListener? = null


    // ======== VARIABLES
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private var backButtonPressed: Long = 0

    // Total number of books in the whole unfiltered library
    private var totalContentCount = 0

    // TODO doc
    private var enabled = false

    // TODO doc
    private var previousViewType = -1

    private lateinit var pagingDebouncer: Debouncer<Unit>

    companion object {

        val FILEITEM_DIFF_CALLBACK: DiffCallback<FileItem> =
            object : DiffCallback<FileItem> {
                override fun areItemsTheSame(
                    oldItem: FileItem,
                    newItem: FileItem
                ): Boolean {
                    return oldItem.identifier == newItem.identifier
                }

                override fun areContentsTheSame(
                    oldItem: FileItem,
                    newItem: FileItem
                ): Boolean {
                    return oldItem.doc.name == newItem.doc.name
                            && oldItem.doc.nbChildren == newItem.doc.nbChildren
                }

                override fun getChangePayload(
                    oldItem: FileItem,
                    oldItemPosition: Int,
                    newItem: FileItem,
                    newItemPosition: Int
                ): Any? {
                    val diffBundleBuilder = FileItemBundle()
                    if (newItem.doc.coverUri != null && oldItem.doc.coverUri != newItem.doc.coverUri) {
                        diffBundleBuilder.coverUri = newItem.doc.coverUri!!.toString()
                    }
                    return if (diffBundleBuilder.isEmpty) null else diffBundleBuilder.bundle
                }
            }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(requireActivity() is LibraryActivity) { "Parent activity has to be a LibraryActivity" }
        activity = WeakReference(requireActivity() as LibraryActivity)
        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[LibraryViewModel::class.java]
        pagingDebouncer = Debouncer(lifecycleScope, 100) { setPagingMethod() }
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
    ): View? {
        binding = FragmentLibraryGroupsBinding.inflate(inflater, container, false)
        initUI()
        activity.get()?.initFragmentToolbars(
            selectExtension!!,
            { onToolbarItemClicked(it) }
        ) { onSelectionToolbarItemClicked(it) }
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.libraryPaged.observe(viewLifecycleOwner) { onLibraryChanged(it) }
        viewModel.folders.observe(viewLifecycleOwner) { onFoldersChanged(it) }

        // Trigger a blank search
        val currentRoot = Settings.libraryFoldersRoot.toUri()
        if (fileExists(requireContext(), currentRoot)) {
            viewModel.setFolderRoot(currentRoot)
        } else { // Display level 0 (roots)
            Timber.d("Display level 0")
            // TODO
        }
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
     */
    private fun initUI() {
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

        binding?.recyclerView?.let {
            it.layoutManager = llm
            FastScrollerBuilder(it)
                .setPopupTextProvider(this)
                .useMd2Style()
                .build()
        }

        // Pager
        setPagingMethod()
        addCustomBackControl()
    }

    private fun addCustomBackControl() {
        callback?.remove()
        callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                customBackPress()
            }
        }
        activity.get()!!.onBackPressedDispatcher.addCallback(activity.get()!!, callback!!)
    }

    private fun onToolbarItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            // TODO
            //R.id.action_edit -> enterEditMode()
            else -> return activity.get()!!.toolbarOnItemClicked(menuItem)
        }
        return true
    }

    private fun onSelectionToolbarItemClicked(menuItem: MenuItem): Boolean {
        var keepToolbar = false
        when (menuItem.itemId) {
            R.id.action_edit -> editSelectedItemName()
            R.id.action_delete -> deleteSelectedItems()
            R.id.action_select_all -> {
                // Make certain _everything_ is properly selected (selectExtension.select() as doesn't get everything the 1st time it's called)
                var count = 0
                while (selectExtension!!.selections.size < itemAdapter.adapterItemCount && ++count < 5)
                    selectExtension!!.select(
                        IntRange(0, itemAdapter.adapterItemCount - 1)
                    )
                keepToolbar = true
            }

            else -> {
                activity.get()!!.getSelectionToolbar()?.visibility = View.GONE
                return false
            }
        }
        if (!keepToolbar) activity.get()!!.getSelectionToolbar()?.visibility = View.GONE
        return true
    }

    /**
     * Callback for the "delete item" action button
     */
    private fun deleteSelectedItems() {
        val selectedItems: Set<FileItem> = selectExtension!!.selectedItems
        // TODO do stuff
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onProcessStickyEvent(event: ProcessEvent) {
        // Filter on delete complete event
        if (R.id.delete_service_delete != event.processId) return
        if (ProcessEvent.Type.COMPLETE != event.eventType) return
        viewModel.refreshAvailableGroupings()
    }

    /**
     * Callback for the "edit item name" action button
     */
    private fun editSelectedItemName() {
        val selectedItems: Set<FileItem> = selectExtension!!.selectedItems
        // TODO do stuff
    }

    private fun onEditName(newName: String) {
        val selectedItems: Set<FileItem> = selectExtension!!.selectedItems
        // TODO do stuff
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.onSaveState(outState)
        fastAdapter.saveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (null == savedInstanceState) return
        viewModel.onRestoreState(savedInstanceState)
        fastAdapter.withSavedInstanceState(savedInstanceState)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onAppUpdated(event: AppUpdatedEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        // Display the "update success" dialog when an update is detected on a release version
        if (!BuildConfig.DEBUG) invoke(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onCommunicationEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.Recipient.FOLDERS && event.recipient != CommunicationEvent.Recipient.ALL) return
        when (event.type) {
            CommunicationEvent.Type.UPDATE_TOOLBAR -> {
                addCustomBackControl()
                selectExtension?.let { se ->
                    activity.get()?.initFragmentToolbars(se, { onToolbarItemClicked(it) })
                    { onSelectionToolbarItemClicked(it) }
                }
            }

            CommunicationEvent.Type.SEARCH -> onSubmitSearch(event.message)
            CommunicationEvent.Type.ENABLE -> onEnable()
            CommunicationEvent.Type.DISABLE -> onDisable()
            CommunicationEvent.Type.SCROLL_TOP -> llm?.scrollToPositionWithOffset(0, 0)
            else -> {}
        }
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        callback?.remove()
        super.onDestroy()
    }

    private fun customBackPress() {
        // If content is selected, deselect it
        if (selectExtension!!.selections.isNotEmpty()) {
            leaveSelectionMode()
            backButtonPressed = 0
            return
        }
        activity.get()?.apply {
            if (!collapseSearchMenu() && !closeLeftDrawer()) {
                // If none of the above and a search filter is on => clear search filter
                if (isFilterActive()) {
                    viewModel.clearFolderFilters()
                } else if (backButtonPressed + 2000 > SystemClock.elapsedRealtime()) {
                    callback!!.remove()
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
     * Initialize the paging method of the screen
     */
    private fun setPagingMethod(recreate: Boolean = false) {
        // Rebuild to be certain all layouts are recreated from scratch when switching to and from edit mode
        if (recreate) fastAdapter = FastAdapter.with(itemAdapter)
        if (!fastAdapter.hasObservers()) fastAdapter.setHasStableIds(true)

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.requireOrCreateExtension()
        selectExtension?.apply {
            isSelectable = true
            multiSelect = true
            selectOnLongClick = true
            selectWithItemUpdate = true
            selectionListener =
                object : ISelectionListener<FileItem> {
                    override fun onSelectionChanged(item: FileItem, selected: Boolean) {
                        onSelectionChanged()
                    }
                }
            val helper = FastAdapterPreClickSelectHelper(fastAdapter, this)
            fastAdapter.onPreClickListener =
                { _, _, _, position -> helper.onPreClickListener(position) }
            fastAdapter.onPreLongClickListener =
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
                    selectExtension?.let {
                        if (isSelected) it.select(IntRange(start, end))
                        else it.deselect(IntRange(start, end).toMutableList())
                    }
                }
            }).withMode(DragSelectionProcessor.Mode.Simple)

        DragSelectTouchListener().withSelectListener(onDragSelectionListener).let {
            mDragSelectTouchListener = it
            binding?.recyclerView?.addOnItemTouchListener(it)
        }

        // Drag, drop & swiping
        if (activity.get()!!.isEditMode()) {
            val dragSwipeCallback: SimpleDragCallback = SimpleSwipeDragCallback(
                this,
                this,
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_action_delete)
            ).withSensitivity(10f).withSurfaceThreshold(0.75f)
            dragSwipeCallback.notifyAllDrops = true
            dragSwipeCallback.setIsDragEnabled(false) // Despite its name, that's actually to disable drag on long tap
            touchHelper = ItemTouchHelper(dragSwipeCallback)
            binding?.recyclerView?.let {
                touchHelper?.attachToRecyclerView(it)
            }
        }

        // Item click listener
        fastAdapter.onClickListener = { _, _, i: FileItem, _ -> onItemClick(i) }

        fastAdapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        binding?.recyclerView?.apply {
            adapter = fastAdapter
            setHasFixedSize(true)
        }
    }

    /**
     * LiveData callback when the library changes
     * Happens when a book has been downloaded or deleted
     *
     * @param result Current library according to active filters
     */
    private fun onLibraryChanged(result: PagedList<Content>) {
        Timber.i(">> Library changed (folders) ! Size=%s enabled=%s", result.size, enabled)
        if (!enabled) return

        // TODO
    }

    /**
     * LiveData callback when the folders changes
     * Happens when navigating
     */
    private fun onFoldersChanged(result: List<DisplayFile>) {
        Timber.i(">> Folders changed (folders) ! Size=%s enabled=%s", result.size, enabled)
        if (!enabled) return

        val isEmpty = result.isEmpty()
        binding?.emptyTxt?.isVisible = isEmpty
        activity.get()?.updateTitle(result.size.toLong(), result.size.toLong())

        val files = result.map { FileItem(it) }.distinct()
        set(itemAdapter, files, FILEITEM_DIFF_CALLBACK)

        // Update visibility and content of advanced search bar
        // - After getting results from a search
        // - When switching between Group and Content view
        activity.get()?.updateSearchBarOnResults(result.isNotEmpty())
    }

    // TODO doc
    private fun onSubmitSearch(query: String) {
        viewModel.setFolderQuery(query)
    }

    /**
     * Callback for the item holder itself
     *
     * @param item item that has been clicked on
     */
    private fun onItemClick(item: FileItem): Boolean {
        if (selectExtension!!.selections.isEmpty()) {
            // TODO go down one level
            return true
        }
        return false
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private fun onSelectionChanged() {
        val selectedItems: Set<FileItem> = selectExtension!!.selectedItems
        val selectedCount = selectedItems.size
        if (0 == selectedCount) {
            activity.get()?.getSelectionToolbar()?.visibility = View.GONE
            selectExtension?.selectOnLongClick = true
        } else {
            // TODO
            /*
            activity.get()!!.updateSelectionToolbar(
                selectedCount.toLong(),
                selectedProcessedCount.toLong(),
                selectedLocalCount.toLong(),
                0,
                0,
                0
            )
            activity.get()!!.getSelectionToolbar()?.visibility = View.VISIBLE
             */
        }
    }

    /**
     * DRAG, DROP & SWIPE METHODS
     */
    override fun itemTouchOnMove(oldPosition: Int, newPosition: Int): Boolean {
        onMove(itemAdapter, oldPosition, newPosition) // change position
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

    override fun itemSwiped(position: Int, direction: Int) {
        // TODO
    }

    override fun itemTouchStopDrag(viewHolder: RecyclerView.ViewHolder) {
        // Nothing
    }

    /**
     * Callback for the rating dialog
     */
    fun leaveSelectionMode() {
        selectExtension!!.selectOnLongClick = true
        // Warning : next line makes FastAdapter cycle through all items,
        // which has a side effect of calling TiledPageList.onPagePlaceholderInserted,
        // flagging the end of the list as being the last displayed position
        val selection = selectExtension!!.selections
        if (selection.isNotEmpty()) selectExtension!!.deselect(selection.toMutableSet())
        activity.get()!!.getSelectionToolbar()!!.visibility = View.GONE
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        val doc = itemAdapter.getAdapterItem(position).doc
        return when (Settings.folderSortField) {

            Settings.Value.ORDER_FIELD_TITLE -> if (doc.name.isEmpty()) ""
            else (doc.name[0].toString() + "").uppercase(Locale.getDefault())

            Settings.Value.ORDER_FIELD_CHILDREN -> doc.nbChildren.toString()

            else -> ""
        }
    }
}