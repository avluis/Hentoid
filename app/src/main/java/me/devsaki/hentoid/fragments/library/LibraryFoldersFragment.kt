package me.devsaki.hentoid.fragments.library

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.activities.ReaderActivity
import me.devsaki.hentoid.activities.bundles.FileItemBundle
import me.devsaki.hentoid.activities.bundles.ReaderActivityBundle
import me.devsaki.hentoid.databinding.FragmentLibraryFoldersBinding
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.AppUpdatedEvent
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.library.UpdateSuccessDialogFragment.Companion.invoke
import me.devsaki.hentoid.util.Debouncer
import me.devsaki.hentoid.util.PickFolderContract
import me.devsaki.hentoid.util.PickerResult
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.dpToPx
import me.devsaki.hentoid.util.file.DisplayFile
import me.devsaki.hentoid.util.file.DisplayFile.SubType
import me.devsaki.hentoid.util.file.DisplayFile.Type
import me.devsaki.hentoid.util.file.RQST_STORAGE_PERMISSION
import me.devsaki.hentoid.util.file.fileExists
import me.devsaki.hentoid.util.file.getDocumentFromTreeUri
import me.devsaki.hentoid.util.file.openUri
import me.devsaki.hentoid.util.file.requestExternalStorageReadWritePermission
import me.devsaki.hentoid.util.runExternalImport
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
    private var binding: FragmentLibraryFoldersBinding? = null

    // LayoutManager of the recyclerView
    private var llm: LinearLayoutManager? = null

    // === FASTADAPTER COMPONENTS AND HELPERS
    private var itemAdapter: ItemAdapter<FileItem> = ItemAdapter()
    private var fastAdapter = FastAdapter.with(itemAdapter)
    private var selectExtension: SelectExtension<FileItem>? = null
    private var touchHelper: ItemTouchHelper? = null
    private var mDragSelectTouchListener: DragSelectTouchListener? = null

    private val pickRootFolder =
        registerForActivityResult(PickFolderContract()) {
            onRootFolderPickerResult(it.first, it.second)
        }


    // ======== VARIABLES
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private var backButtonPressed: Long = 0

    private lateinit var pagingDebouncer: Debouncer<Unit>

    // Search and filtering criteria in the form of a Bundle (see FolderSearchManager.FolderSearchBundle)
    private var folderSearchBundle: Bundle? = null

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
                            && oldItem.doc.type == newItem.doc.type
                            && oldItem.doc.subType == newItem.doc.subType
                            && oldItem.doc.nbChildren == newItem.doc.nbChildren
                            && oldItem.doc.contentId == newItem.doc.contentId
                            && oldItem.doc.coverUri == newItem.doc.coverUri
                            && oldItem.refreshComplete == newItem.refreshComplete
                }

                override fun getChangePayload(
                    oldItem: FileItem,
                    oldItemPosition: Int,
                    newItem: FileItem,
                    newItemPosition: Int
                ): Any? {
                    val diffBundleBuilder = FileItemBundle()
                    if (oldItem.doc.type != newItem.doc.type) {
                        diffBundleBuilder.type = newItem.doc.type.ordinal
                    }
                    if (oldItem.doc.subType != newItem.doc.subType) {
                        diffBundleBuilder.subType = newItem.doc.subType.ordinal
                    }
                    if (newItem.doc.coverUri != Uri.EMPTY && oldItem.doc.coverUri != newItem.doc.coverUri) {
                        diffBundleBuilder.coverUri = newItem.doc.coverUri.toString()
                    }
                    if (oldItem.doc.contentId != newItem.doc.contentId) {
                        diffBundleBuilder.contentId = newItem.doc.contentId
                    }
                    if (oldItem.refreshComplete != newItem.refreshComplete) {
                        diffBundleBuilder.refreshed = newItem.refreshComplete
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
        binding = FragmentLibraryFoldersBinding.inflate(inflater, container, false)
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

        viewModel.folders.observe(viewLifecycleOwner) { onFoldersChanged(it) }
        viewModel.foldersDetail.observe(viewLifecycleOwner) { onFoldersDetail(it) }
        viewModel.folderRoot.observe(viewLifecycleOwner) {
            Settings.libraryFoldersRoot = it.toString()
        }
        viewModel.folderSearchBundle.observe(viewLifecycleOwner) { folderSearchBundle = it }

        // Trigger a blank search
        val currentRoot = Settings.libraryFoldersRoot.toUri()
        if (fileExists(requireContext(), currentRoot)) {
            viewModel.setFolderRoot(currentRoot)
        } else { // Display level 0 (roots)
            viewModel.setFolderRoot(Uri.EMPTY)
        }
    }

    private fun onEnable() {
        callback?.isEnabled = true
    }

    private fun onDisable() {
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

        binding?.apply {
            recyclerView.layoutManager = llm
            FastScrollerBuilder(recyclerView)
                .setPopupTextProvider(this@LibraryFoldersFragment)
                .useMd2Style()
                .build()
            swipeContainer.setOnRefreshListener { viewModel.searchFolder() }
            swipeContainer.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light
            )
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
            R.id.action_detach -> detachSelectedItems()
            R.id.action_delete -> deleteSelectedItems()
            R.id.action_refresh -> refreshSelectedItems()
            R.id.action_open_folder -> openItemFolder()
            R.id.action_select_all -> {
                // Make certain _everything_ is properly selected (selectExtension.select() as doesn't get everything the 1st time it's called)
                var count = 0
                selectExtension?.apply {
                    while (selections.size < itemAdapter.adapterItemCount && ++count < 5)
                        IntRange(0, itemAdapter.adapterItemCount - 1).forEach {
                            select(it, false, considerSelectableFlag = true)
                        }
                }
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
     * Callback for the "open containing folder" action button
     */
    private fun openItemFolder() {
        val selectedItems: Set<FileItem> = selectExtension!!.selectedItems
        val context = getActivity() ?: return
        if (1 == selectedItems.size) {
            val item = selectedItems.firstOrNull() ?: return
            if (item.doc.uri == Uri.EMPTY) {
                toast(R.string.folder_undefined)
                return
            }
            val folder = getDocumentFromTreeUri(context, item.doc.uri)
            if (folder != null) {
                selectExtension?.apply { deselect(selections.toMutableSet()) }
                activity.get()?.getSelectionToolbar()?.visibility = View.GONE

                val uri =
                    if (item.doc.type == Type.SUPPORTED_FILE) Settings.libraryFoldersRoot.toUri()
                    else folder.uri
                openUri(context, uri)
            }
        }
    }

    /**
     * Callback for the "delete item" action button
     */
    private fun deleteSelectedItems() {
        val selectedItems: Set<FileItem> = selectExtension!!.selectedItems
        if (selectedItems.isEmpty()) return

        // TODO make items blink while being deleted

        // Delete items mapped with a Content = delete content
        selectedItems.map { it.doc.contentId }.filter { it > 0 }.let {
            if (it.isNotEmpty()) viewModel.deleteItems(it, emptyList()) { viewModel.searchFolder() }
        }
        // Delete non-mapped items
        selectedItems.filter { 0L == it.doc.contentId }
            .let { item ->
                if (item.isNotEmpty())
                    viewModel.deleteOnStorage(item.map { it.doc.uri.toString() })
                    { viewModel.searchFolder() }
            }
    }

    /**
     * Callback for the "refresh item" action button
     */
    private fun refreshSelectedItems() {
        val selectedItems: Set<FileItem> = selectExtension!!.selectedItems
        if (selectedItems.isEmpty()) return

        runExternalImport(requireContext(), true, selectedItems.map { it.doc.uri.toString() })
        leaveSelectionMode()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onProcessStickyEvent(event: ProcessEvent) {
        // Filter on delete complete event
        if (R.id.delete_service_delete != event.processId) return
        if (ProcessEvent.Type.COMPLETE != event.eventType) return
        viewModel.refreshAvailableGroupings()
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
                } else if (Settings.libraryFoldersRoot.toUri() != Uri.EMPTY) {
                    viewModel.goUpOneFolder()
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
     * Initialize the paging method of the screen
     */
    private fun setPagingMethod(recreate: Boolean = false) {
        // Rebuild to be certain all layouts are recreated from scratch when switching to and from edit mode
        if (recreate) fastAdapter = FastAdapter.with(itemAdapter)
        if (!fastAdapter.hasObservers()) fastAdapter.setHasStableIds(true)

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.requireOrCreateExtension()
        selectExtension?.apply {
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
                    selectExtension?.let { se ->
                        if (isSelected) IntRange(start, end).forEach {
                            se.select(
                                it,
                                fireEvent = false,
                                considerSelectableFlag = true
                            )
                        }
                        else se.deselect(IntRange(start, end).toMutableList())
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
     * LiveData callback when the folders changes
     * Happens when navigating
     */
    private fun onFoldersChanged(result: List<DisplayFile>) {
        val enabled = activity.get()?.isFoldersDisplayed() == true
        callback?.isEnabled = enabled
        if (!enabled) return

        val resSize = result.size
        Timber.i(">> Folders changed [new] (folders) ! Size=$resSize)")

        val isEmpty = 0 == resSize
        binding?.emptyTxt?.isVisible = isEmpty
        activity.get()?.updateTitle(resSize, resSize)

        // Copy result to new list to avoid concurrency issues when processing updated list
        val files = result.toList().map { FileItem(it) }
        set(itemAdapter, files, FILEITEM_DIFF_CALLBACK)

        // Update visibility and content of advanced search bar
        // - After getting results from a search
        // - When switching between Group and Content view
        activity.get()?.updateSearchBarOnResults(!isEmpty)
    }

    /**
     * LiveData callback when receiving folder detail
     * => Update details of folders that are already on display
     */
    private fun onFoldersDetail(result: List<DisplayFile>) {
        binding?.swipeContainer?.isRefreshing = false
        // Copy result to new list to avoid concurrency issues when processing updated list
        lifecycleScope.launch {
            val updatedItems = withContext(Dispatchers.Default) {
                val inItems = result.toList()
                val updatedItems = itemAdapter.adapterItems.toList()
                // Merge detailed results data into existing items
                updatedItems.map { up ->
                    inItems.firstOrNull { it.id == up.identifier }?.let {
                        val newItem = FileItem(it, true)
                        newItem.isSelected = up.isSelected
                        newItem
                    } ?: up
                }
            }
            withContext(Dispatchers.Main) {
                set(itemAdapter, updatedItems, FILEITEM_DIFF_CALLBACK)
            }
        }
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
            val ctx = requireActivity()
            when (item.doc.type) {
                Type.ADD_BUTTON -> {
                    // Make sure permissions are set
                    if (ctx.requestExternalStorageReadWritePermission(RQST_STORAGE_PERMISSION)) {
                        // Run folder picker
                        pickRootFolder.launch(StorageLocation.NONE)
                    }
                }

                Type.UP_BUTTON -> {
                    viewModel.goUpOneFolder()
                }

                Type.BOOK_FOLDER, Type.SUPPORTED_FILE -> {
                    if (item.refreshComplete)
                        folderSearchBundle?.let {
                            openReaderForResource(requireContext(), item.doc, it)
                        }
                }

                else -> {
                    viewModel.setFolderRoot(item.doc.uri)
                }
            }
            return true
        }
        return false
    }

    fun openReaderForResource(
        context: Context,
        displayFile: DisplayFile,
        searchParams: Bundle
    ): Boolean {
        if (displayFile.uri == Uri.EMPTY) return false
        getDocumentFromTreeUri(context, displayFile.uri) ?: return false

        Timber.d("Opening: ${displayFile.uri}")

        val builder = ReaderActivityBundle()
        builder.docUri = displayFile.uri.toString()
        builder.folderSearchParams = searchParams
        builder.isOpenFolders = true

        val intent = Intent(context, ReaderActivity::class.java)
        intent.putExtras(builder.bundle)

        context.startActivity(intent)
        return true
    }

    private fun onRootFolderPickerResult(resultCode: PickerResult, uri: Uri) {
        when (resultCode) {
            PickerResult.OK -> {
                if (!viewModel.attachFolderRoot(uri)) activity.get()?.toast(R.string.add_root_fail)
            }

            else -> {
                activity.get()?.toast(R.string.add_root_fail)
            }
        }
    }

    /**
     * Callback for the "detach" action button
     */
    private fun detachSelectedItems() {
        val selectedItems: Set<FileItem> = selectExtension!!.selectedItems
        if (selectedItems.isEmpty()) return

        viewModel.detachFolderRoots(selectedItems.map { it.doc.uri })
        leaveSelectionMode()
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
            activity.get()?.apply {
                val nbRoots = selectedItems.count { it.doc.type == Type.ROOT_FOLDER }
                val nbRefreshable = selectedItems.count {
                    it.doc.type == Type.FOLDER || it.doc.type == Type.SUPPORTED_FILE || it.doc.type == Type.BOOK_FOLDER
                }
                val insideExtLib =
                    (Settings.libraryFoldersRoot.startsWith(Settings.externalLibraryUri) && nbRefreshable > 0)
                            || (selectedItems.count { it.doc.type == Type.ROOT_FOLDER && it.doc.subType == SubType.EXTERNAL_LIB } > 0)

                updateSelectionToolbar(selectedCount, 0, 0, 0, 0, 0, nbRoots, insideExtLib)
                getSelectionToolbar()?.visibility = View.VISIBLE
            }
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
        selectExtension?.apply {
            selectOnLongClick = true
            // Warning : next line makes FastAdapter cycle through all items,
            // which has a side effect of calling TiledPageList.onPagePlaceholderInserted,
            // flagging the end of the list as being the last displayed position
            val selection = selections
            if (selection.isNotEmpty()) deselect(selection.toMutableSet())
        }
        activity.get()?.getSelectionToolbar()?.visibility = View.GONE
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