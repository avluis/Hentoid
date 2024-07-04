package me.devsaki.hentoid.fragments.library

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.paging.PagedList
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.DiffCallback
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil.set
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.drag.SimpleDragCallback
import com.mikepenz.fastadapter.extensions.ExtensionsFactories.register
import com.mikepenz.fastadapter.listeners.ClickEventHook
import com.mikepenz.fastadapter.select.SelectExtension
import com.mikepenz.fastadapter.select.SelectExtensionFactory
import com.mikepenz.fastadapter.swipe.SimpleSwipeCallback
import com.mikepenz.fastadapter.swipe_drag.SimpleSwipeDragCallback
import com.mikepenz.fastadapter.utils.DragDropUtil.onMove
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.OnMenuItemClickListener
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.LibraryActivity
import me.devsaki.hentoid.activities.PrefsActivity
import me.devsaki.hentoid.activities.bundles.GroupItemBundle
import me.devsaki.hentoid.activities.bundles.PrefsBundle
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.GroupItem
import me.devsaki.hentoid.databinding.FragmentLibraryGroupsBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.AppUpdatedEvent
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.library.RatingDialogFragment.Companion.invoke
import me.devsaki.hentoid.fragments.library.UpdateSuccessDialogFragment.Companion.invoke
import me.devsaki.hentoid.ui.invokeInputDialog
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.dimensAsDp
import me.devsaki.hentoid.util.dimensAsPx
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.launchBrowserFor
import me.devsaki.hentoid.util.snack
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.viewholders.GroupDisplayItem
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
import java.util.Collections
import java.util.Locale
import kotlin.math.max

class LibraryGroupsFragment : Fragment(),
    RatingDialogFragment.Parent,
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
    private var itemAdapter: ItemAdapter<GroupDisplayItem> = ItemAdapter()
    private var fastAdapter = FastAdapter.with(itemAdapter)
    private var selectExtension: SelectExtension<GroupDisplayItem>? = null
    private var touchHelper: ItemTouchHelper? = null
    private var mDragSelectTouchListener: DragSelectTouchListener? = null


    // ======== VARIABLES
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private var backButtonPressed: Long = 0

    // Total number of books in the whole unfiltered library
    private var totalContentCount = 0

    // TODO doc
    private var firstLibraryLoad = true

    // TODO doc
    private var enabled = false

    companion object {

        val GROUPITEM_DIFF_CALLBACK: DiffCallback<GroupDisplayItem> =
            object : DiffCallback<GroupDisplayItem> {
                override fun areItemsTheSame(
                    oldItem: GroupDisplayItem,
                    newItem: GroupDisplayItem
                ): Boolean {
                    return oldItem.identifier == newItem.identifier
                }

                override fun areContentsTheSame(
                    oldItem: GroupDisplayItem,
                    newItem: GroupDisplayItem
                ): Boolean {
                    return oldItem.group.coverContent.targetId == newItem.group.coverContent.targetId
                            && oldItem.group.favourite == newItem.group.favourite
                            && oldItem.group.rating == newItem.group.rating
                            && oldItem.group.getItems().size == newItem.group.getItems().size
                }

                override fun getChangePayload(
                    oldItem: GroupDisplayItem,
                    oldItemPosition: Int,
                    newItem: GroupDisplayItem,
                    newItemPosition: Int
                ): Any? {
                    val diffBundleBuilder = GroupItemBundle()
                    if (!newItem.group.coverContent.isNull && oldItem.group.coverContent.targetId != newItem.group.coverContent.targetId) {
                        diffBundleBuilder.coverUri =
                            newItem.group.coverContent.target.cover.usableUri
                    }
                    if (oldItem.group.favourite != newItem.group.favourite) {
                        diffBundleBuilder.isFavourite = newItem.group.favourite
                    }
                    if (oldItem.group.rating != newItem.group.rating) {
                        diffBundleBuilder.rating = newItem.group.rating
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
        binding = FragmentLibraryGroupsBinding.inflate(inflater, container, false)
        initUI()
        activity.get()!!.initFragmentToolbars(
            selectExtension!!,
            { menuItem: MenuItem -> onToolbarItemClicked(menuItem) }
        ) { menuItem: MenuItem ->
            onSelectionToolbarItemClicked(menuItem)
        }
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firstLibraryLoad = true
        viewModel.groups.observe(viewLifecycleOwner) { result: List<Group> -> onGroupsChanged(result) }

        viewModel.getTotalGroup()
            .observe(viewLifecycleOwner) { count: Int -> onTotalGroupsChanged(count) }

        viewModel.libraryPaged.observe(viewLifecycleOwner) { result: PagedList<Content> ->
            onLibraryChanged(result)
        }

        // Trigger a blank search
        // TODO when group is reached from FLAT through the "group by" menu, this triggers a double-load and a screen blink
        viewModel.searchGroup()
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
                dimensAsPx(requireContext(), Settings.libraryGridCardWidthDP)
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
            R.id.action_edit -> enterEditMode()
            R.id.action_edit_confirm -> confirmEdit()
            R.id.action_edit_cancel -> cancelEdit()
            R.id.action_group_new -> newGroupPrompt()
            else -> return activity.get()!!.toolbarOnItemClicked(menuItem)
        }
        return true
    }

    private fun onSelectionToolbarItemClicked(menuItem: MenuItem): Boolean {
        var keepToolbar = false
        when (menuItem.itemId) {
            R.id.action_edit -> editSelectedItemName()
            R.id.action_delete -> deleteSelectedItems()
            R.id.action_rate -> onMassRateClick()
            R.id.action_archive -> archiveSelectedItems()
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

    private fun enterEditMode() {
        activity.get()?.setEditMode(true)
        setPagingMethod()
        viewModel.searchGroup()
    }

    private fun cancelEdit() {
        activity.get()?.setEditMode(false)
        setPagingMethod()
    }

    private fun confirmEdit() {
        activity.get()?.setEditMode(false)

        // == Save new item position
        // Set ordering field to custom
        Preferences.setGroupSortField(Preferences.Constant.ORDER_FIELD_CUSTOM)
        // Set ordering direction to ASC (we just manually ordered stuff; it has to be displayed as is)
        Preferences.setGroupSortDesc(false)
        viewModel.saveGroupPositions(itemAdapter.adapterItems.map { gi -> gi.group })
        setPagingMethod()
        viewModel.searchGroup()
    }

    private fun newGroupPrompt() {
        invokeInputDialog(
            requireActivity(), R.string.new_group_name,
            { groupName: String ->
                viewModel.newGroup(
                    Preferences.getGroupingDisplay(),
                    groupName, null
                ) { onNewGroupNameExists() }
            }
        )
    }

    private fun onNewGroupNameExists() {
        toast(R.string.group_name_exists)
        newGroupPrompt()
    }

    /**
     * Callback for the "favourite" button of the group holder
     *
     * @param group Group whose "favourite" button has been clicked on
     */
    private fun onGroupFavouriteClick(group: Group) {
        viewModel.toggleGroupFavourite(group)
    }

    /**
     * Callback for the "rating" button of the group holder
     *
     * @param group Group whose "rating" button has been clicked on
     */
    private fun onGroupRatingClick(group: Group) {
        invoke(this, longArrayOf(group.id), group.rating)
    }

    /**
     * Callback for the "delete item" action button
     */
    private fun deleteSelectedItems() {
        val selectedItems: Set<GroupDisplayItem> = selectExtension!!.selectedItems
        if (selectedItems.isNotEmpty()) {
            var selectedGroups = selectedItems.map { gi -> gi.group }.toMutableList()
            val selectedContentLists = selectedGroups.map { g -> viewModel.getGroupContents(g) }
            var selectedContent: MutableList<Content> = ArrayList()
            for (list in selectedContentLists) selectedContent.addAll(list)

            // Remove external items if they can't be deleted
            if (!Preferences.isDeleteExternalLibrary()) {
                val contentToDelete =
                    selectedContent.filterNot { c -> c.status == StatusContent.EXTERNAL }
                val diff = selectedContent.size - contentToDelete.size
                // Remove undeletable books from the list
                if (diff > 0) {
                    snack(
                        resources.getQuantityString(R.plurals.external_not_removed, diff, diff)
                    )
                    selectedContent = contentToDelete.toMutableList()
                    // Rebuild the groups list from the remaining contents if needed
                    if (Preferences.getGroupingDisplay().canDeleteGroups) selectedGroups =
                        selectedContent.flatMap { c -> c.groupItems }
                            .map { gi: GroupItem -> gi.group.target }
                            .toMutableList()
                }
            }
            // Don't remove non-deletable groups
            if (!Preferences.getGroupingDisplay().canDeleteGroups) selectedGroups.clear()
            if (selectedContent.isNotEmpty() || selectedGroups.isNotEmpty()) {
                val powerMenuBuilder = PowerMenu.Builder(requireContext())
                    .setOnDismissListener { leaveSelectionMode() }
                    .setWidth(resources.getDimensionPixelSize(R.dimen.dialog_width))
                    .setAnimation(MenuAnimation.SHOW_UP_CENTER)
                    .setMenuRadius(10f)
                    .setIsMaterial(true)
                    .setLifecycleOwner(requireActivity())
                    .setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.white_opacity_87
                        )
                    )
                    .setTextTypeface(Typeface.DEFAULT)
                    .setMenuColor(
                        requireContext().getThemedColor(R.color.window_background_light)
                    )
                    .setTextSize(dimensAsDp(requireContext(), R.dimen.text_subtitle_1))
                    .setAutoDismiss(true)
                if (!Preferences.getGroupingDisplay().canDeleteGroups) {
                    // Delete books only
                    powerMenuBuilder.addItem(
                        PowerMenuItem(
                            resources.getQuantityString(
                                R.plurals.group_delete_selected_book,
                                selectedContent.size,
                                selectedContent.size
                            ),
                            false,
                            R.drawable.ic_action_delete,
                            null,
                            null,
                            0
                        )
                    )
                } else {
                    // Delete group only
                    if (Preferences.getGroupingDisplay().canReorderGroups)
                        powerMenuBuilder.addItem(
                            PowerMenuItem(
                                resources.getQuantityString(
                                    R.plurals.group_delete_selected_group,
                                    selectedGroups.size
                                ), false, R.drawable.ic_folder_delete, null, null, 1
                            )
                        )
                    if (selectedContent.isNotEmpty()) // Delete groups and books
                        powerMenuBuilder.addItem(
                            PowerMenuItem(
                                resources.getQuantityString(
                                    R.plurals.group_delete_selected_group_books,
                                    selectedGroups.size
                                ), false, R.drawable.ic_action_delete, null, null, 2
                            )
                        )
                }
                powerMenuBuilder.addItem(
                    PowerMenuItem(
                        resources.getString(R.string.cancel),
                        false,
                        R.drawable.ic_close,
                        null,
                        null,
                        99
                    )
                )
                val powerMenu = powerMenuBuilder.build()
                val finalGroups = Collections.unmodifiableList(selectedGroups)
                val finalContent = Collections.unmodifiableList(selectedContent)
                powerMenu.onMenuItemClickListener =
                    OnMenuItemClickListener { _: Int, (_, _, _, _, _, tag1): PowerMenuItem ->
                        if (tag1 != null) {
                            when (tag1 as Int) {
                                0 -> { // Delete books only
                                    viewModel.deleteItems(
                                        finalContent,
                                        emptyList(),
                                        false,
                                        null
                                    )
                                }

                                1 -> { // Delete group only
                                    viewModel.deleteItems(
                                        emptyList(),
                                        finalGroups,
                                        true,
                                        null
                                    )
                                }

                                2 -> { // Delete groups and books
                                    viewModel.deleteItems(finalContent, finalGroups, false, null)
                                }

                                else -> {
                                    leaveSelectionMode() // Cancel button
                                }
                            }
                        }
                    }
                powerMenu.setIconColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.white_opacity_87
                    )
                )
                binding?.recyclerView?.let {
                    powerMenu.showAtCenter(it)
                }
            } else {
                binding?.recyclerView?.let {
                    val snackbar = Snackbar.make(
                        it,
                        resources.getString(R.string.group_delete_nothing),
                        BaseTransientBottomBar.LENGTH_LONG
                    )
                    snackbar.setAction(R.string.app_settings) {
                        // Open prefs on the "storage" category
                        val intent = Intent(requireActivity(), PrefsActivity::class.java)
                        val prefsBundle = PrefsBundle()
                        prefsBundle.isStoragePrefs = true
                        intent.putExtras(prefsBundle.bundle)
                        requireContext().startActivity(intent)
                    }
                    snackbar.show()
                }
                leaveSelectionMode()
            }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onProcessStickyEvent(event: ProcessEvent) {
        // Filter on delete complete event
        if (R.id.delete_service_delete != event.processId) return
        if (ProcessEvent.Type.COMPLETE != event.eventType) return
        viewModel.refreshAvailableGroupings()
    }

    /**
     * Callback for the "archive item" action button
     */
    private fun archiveSelectedItems() {
        val selectedItems: Set<GroupDisplayItem> = selectExtension!!.selectedItems
        val selectedContent = selectedItems
            .map { obj -> obj.group }
            .flatMap { g -> viewModel.getGroupContents(g) }
            .filterNot { c -> c.storageUri.isEmpty() }
            .toList()
        if (selectedContent.isNotEmpty()) activity.get()!!
            .askArchiveItems(selectedContent, selectExtension!!)
    }

    /**
     * Callback for the "rate items" action button
     */
    private fun onMassRateClick() {
        val selectedItems: Set<GroupDisplayItem> = selectExtension!!.selectedItems
        val selectedIds = selectedItems.map { gi -> gi.group }.map { g -> g.id }
        if (selectedIds.isNotEmpty()) {
            invoke(this, selectedIds.toLongArray(), 0)
        }
    }

    /**
     * Callback for the "edit item name" action button
     */
    private fun editSelectedItemName() {
        val selectedItems: Set<GroupDisplayItem> = selectExtension!!.selectedItems
        val g = selectedItems.firstNotNullOfOrNull { gi -> gi.group }
        if (g != null) {
            invokeInputDialog(
                requireActivity(),
                R.string.group_edit_name,
                { newName: String -> onEditName(newName) },
                g.name
            ) { leaveSelectionMode() }
        }
    }

    private fun onEditName(newName: String) {
        val selectedItems: Set<GroupDisplayItem> = selectExtension!!.selectedItems
        val g = selectedItems.firstNotNullOfOrNull { gi -> gi.group }
        if (g != null) {
            viewModel.renameGroup(g, newName, { stringIntRes ->
                toast(stringIntRes)
                editSelectedItemName()
            }) { selectExtension!!.selectOnLongClick = true }
        }
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
        if (event.recipient != CommunicationEvent.Recipient.GROUPS && event.recipient != CommunicationEvent.Recipient.ALL) return
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
                    viewModel.clearGroupFilters()
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
    private fun setPagingMethod() {
        if (!fastAdapter.hasObservers()) fastAdapter.setHasStableIds(true)

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.requireOrCreateExtension()
        selectExtension?.apply {
            isSelectable = true
            multiSelect = true
            selectOnLongClick = true
            selectWithItemUpdate = true
            selectionListener =
                object : ISelectionListener<GroupDisplayItem> {
                    override fun onSelectionChanged(item: GroupDisplayItem, selected: Boolean) {
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
                touchHelper!!.attachToRecyclerView(it)
            }
        }

        // Item click listener
        fastAdapter.onClickListener = { _, _, i: GroupDisplayItem, _ -> onItemClick(i) }

        // Favourite button click listener
        fastAdapter.addEventHook(object : ClickEventHook<GroupDisplayItem>() {
            override fun onClick(
                v: View,
                position: Int,
                fastAdapter: FastAdapter<GroupDisplayItem>,
                item: GroupDisplayItem
            ) {
                onGroupFavouriteClick(item.group)
            }

            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return if (viewHolder is GroupDisplayItem.ViewHolder) {
                    viewHolder.favouriteButton
                } else super.onBind(viewHolder)
            }
        })

        // Rating button click listener
        fastAdapter.addEventHook(object : ClickEventHook<GroupDisplayItem>() {
            override fun onClick(
                v: View,
                position: Int,
                fastAdapter: FastAdapter<GroupDisplayItem>,
                item: GroupDisplayItem
            ) {
                onGroupRatingClick(item.group)
            }

            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return if (viewHolder is GroupDisplayItem.ViewHolder) {
                    viewHolder.ratingButton
                } else super.onBind(viewHolder)
            }
        })
        fastAdapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        binding?.recyclerView?.apply {
            adapter = fastAdapter
            setHasFixedSize(true)
        }
    }

    private fun onGroupsChanged(result: List<Group>) {
        Timber.i(">> Groups changed ! Size=%s enabled=%s", result.size, enabled)
        if (!enabled) return
        val isEmpty = result.isEmpty()
        binding?.emptyTxt?.isVisible = isEmpty
        activity.get()!!.updateTitle(result.size.toLong(), totalContentCount.toLong())
        val viewType =
            if (activity.get()!!.isEditMode()) GroupDisplayItem.ViewType.LIBRARY_EDIT
            else if (Settings.Value.LIBRARY_DISPLAY_LIST == Settings.libraryDisplay) GroupDisplayItem.ViewType.LIBRARY
            else GroupDisplayItem.ViewType.LIBRARY_GRID

        val groups = result.map { g -> GroupDisplayItem(g, touchHelper, viewType) }.distinct()
        set(itemAdapter, groups, GROUPITEM_DIFF_CALLBACK)

        // Update visibility and content of advanced search bar
        // - After getting results from a search
        // - When switching between Group and Content view
        activity.get()?.updateSearchBarOnResults(result.isNotEmpty())

        // Reset library load indicator
        firstLibraryLoad = true
    }

    /**
     * LiveData callback when the total number of groups changes (because of book download of removal)
     *
     * @param count Current group count for the currently selected grouping
     */
    private fun onTotalGroupsChanged(count: Int) {
        if (!enabled) return
        totalContentCount = count
        activity.get()!!
            .updateTitle(itemAdapter.itemList.size().toLong(), totalContentCount.toLong())
    }

    /**
     * LiveData callback when the library changes
     * Happens when a book has been downloaded or deleted
     *
     * @param result Current library according to active filters
     */
    private fun onLibraryChanged(result: PagedList<Content>) {
        Timber.i(">> Library changed (groups) ! Size=%s enabled=%s", result.size, enabled)
        if (!enabled) return

        // Refresh groups (new content -> updated book count or new groups)
        // TODO do we really want to do that, especially when deleting content ?
        if (!firstLibraryLoad) viewModel.searchGroup() else {
            Timber.i(">> Library changed (groups) : ignored")
            firstLibraryLoad = false
        }
    }

    // TODO doc
    private fun onSubmitSearch(query: String) {
        if (query.startsWith("http")) { // Quick-open a page
            when (Site.searchByUrl(query)) {
                null -> snack(R.string.malformed_url)
                Site.NONE -> snack(R.string.unsupported_site)
                else -> launchBrowserFor(requireContext(), query)
            }
        } else {
            viewModel.setGroupQuery(query)
        }
    }

    /**
     * Callback for the group holder itself
     *
     * @param item GroupDisplayItem that has been clicked on
     */
    private fun onItemClick(item: GroupDisplayItem): Boolean {
        if (selectExtension!!.selections.isEmpty()) {
            if (!item.group.isBeingProcessed) {
                activity.get()?.showBooksInGroup(item.group)
            }
            return true
        }
        return false
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private fun onSelectionChanged() {
        val selectedItems: Set<GroupDisplayItem> = selectExtension!!.selectedItems
        val selectedCount = selectedItems.size
        if (0 == selectedCount) {
            activity.get()!!.getSelectionToolbar()?.visibility = View.GONE
            selectExtension!!.selectOnLongClick = true
        } else {
            val selectedProcessedCount =
                selectedItems.map { gi -> gi.group }.count { obj -> obj.isBeingProcessed }
            val selectedLocalCount = selectedItems.map { gi -> gi.group }.count()
            activity.get()!!.updateSelectionToolbar(
                selectedCount.toLong(),
                selectedProcessedCount.toLong(),
                selectedLocalCount.toLong(),
                0,
                0,
                0
            )
            activity.get()!!.getSelectionToolbar()?.visibility = View.VISIBLE
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
    override fun rateItems(itemIds: LongArray, newRating: Int) {
        viewModel.rateGroups(itemIds.asList(), newRating)
    }

    override fun leaveSelectionMode() {
        selectExtension!!.selectOnLongClick = true
        // Warning : next line makes FastAdapter cycle through all items,
        // which has a side effect of calling TiledPageList.onPagePlaceholderInserted,
        // flagging the end of the list as being the last displayed position
        val selection = selectExtension!!.selections
        if (selection.isNotEmpty()) selectExtension!!.deselect(selection.toMutableSet())
        activity.get()!!.getSelectionToolbar()!!.visibility = View.GONE
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        val g = itemAdapter.getAdapterItem(position).group
        return when (Preferences.getGroupSortField()) {

            Preferences.Constant.ORDER_FIELD_TITLE -> if (g.name.isEmpty()) ""
            else (g.name[0].toString() + "").uppercase(Locale.getDefault())

            Preferences.Constant.ORDER_FIELD_CHILDREN -> g.contentIds.size.toString()

            Preferences.Constant.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE, Preferences.Constant.ORDER_FIELD_NONE, Preferences.Constant.ORDER_FIELD_CUSTOM -> ""

            else -> ""
        }
    }
}