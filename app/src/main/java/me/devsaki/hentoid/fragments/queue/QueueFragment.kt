package me.devsaki.hentoid.fragments.queue

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil.set
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.listeners.ClickEventHook
import com.mikepenz.fastadapter.select.getSelectExtension
import com.mikepenz.fastadapter.swipe.SimpleSwipeDrawerCallback
import com.mikepenz.fastadapter.swipe_drag.SimpleSwipeDrawerDragCallback
import com.mikepenz.fastadapter.utils.DragDropUtil.onMove
import com.skydoves.balloon.ArrowOrientation
import com.skydoves.powermenu.PowerMenuItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.PrefsActivity
import me.devsaki.hentoid.activities.QueueActivity
import me.devsaki.hentoid.activities.bundles.PrefsBundle
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.QueueRecord
import me.devsaki.hentoid.databinding.FragmentQueueBinding
import me.devsaki.hentoid.databinding.IncludeQueueBottomBarBinding
import me.devsaki.hentoid.events.DownloadCommandEvent
import me.devsaki.hentoid.events.DownloadEvent
import me.devsaki.hentoid.events.DownloadPreparationEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.events.ServiceDestroyedEvent
import me.devsaki.hentoid.fragments.ProgressDialogFragment
import me.devsaki.hentoid.fragments.library.LibraryContentFragment
import me.devsaki.hentoid.fragments.tools.DownloadsImportDialogFragment.Companion.invoke
import me.devsaki.hentoid.ui.BlinkAnimation
import me.devsaki.hentoid.util.*
import me.devsaki.hentoid.util.download.ContentQueueManager
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.file.PermissionHelper
import me.devsaki.hentoid.util.network.DownloadSpeedCalculator.getAvgSpeedKbps
import me.devsaki.hentoid.viewholders.ContentItem
import me.devsaki.hentoid.viewholders.IDraggableViewHolder
import me.devsaki.hentoid.viewholders.ISwipeableViewHolder
import me.devsaki.hentoid.viewmodels.QueueViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.DownloadModeMenu.Companion.build
import me.devsaki.hentoid.widget.DownloadModeMenu.Companion.show
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.max

/**
 * Downloads queue screen
 */
class QueueFragment : Fragment(R.layout.fragment_queue), ItemTouchCallback,
    SimpleSwipeDrawerCallback.ItemSwipeCallback {

    // == UI
    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!
    private var _bottomBarBinding: IncludeQueueBottomBarBinding? = null
    private val bottomBarBinding get() = _bottomBarBinding!!

    private lateinit var llm: LinearLayoutManager

    // === COMMUNICATION
    private var callback: OnBackPressedCallback? = null
    private lateinit var viewModel: QueueViewModel

    // Activity
    private lateinit var activity: WeakReference<QueueActivity>


    // == FASTADAPTER COMPONENTS AND HELPERS
    // Use a non-paged model adapter; drag & drop doesn't work with paged content, as Adapter.move is not supported and move from DB refreshes the whole list
    private val itemAdapter = ItemAdapter<ContentItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)
    private val selectExtension = fastAdapter.getSelectExtension()

    // Helper for swiping items
    private lateinit var touchHelper: ItemTouchHelper


    // State
    private var isPreparingDownload = false

    // Indicate if the fragment is currently canceling all items
    private var isCancelingAll = false


    // === VARIABLES
    // Currenty content ID
    private var contentId = -1L

    // Current text search query
    private var query = ""

    // Used to ignore native calls to onQueryTextChange
    private var invalidateNextQueryTextChange = false

    // Used to show a given item at first display
    private var contentHashToDisplayFirst: Long = 0

    // Used to start processing when the recyclerView has finished updating
    private lateinit var listRefreshDebouncer: Debouncer<Int>
    private var itemToRefreshIndex = -1

    // Used to avoid closing search panel immediately when user uses backspace to correct what he typed
    private lateinit var searchClearDebouncer: Debouncer<Int>

    // Used to keep scroll position when moving items
    // https://stackoverflow.com/questions/27992427/recyclerview-adapter-notifyitemmoved0-1-scrolls-screen
    private var topItemPosition = -1
    private var offsetTop = 0

    // True when a new search has been performed and its results have not been handled yet
    // False when the refresh is passive (i.e. not from a direct user action)
    private var newSearch = false


    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(requireActivity() is QueueActivity) { "Parent activity has to be a QueueActivity" }
        activity = WeakReference(requireActivity() as QueueActivity)

        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[QueueViewModel::class.java]

        listRefreshDebouncer = Debouncer(lifecycleScope, 75)
        { topItemPosition: Int -> this.onRecyclerUpdated(topItemPosition) }

        searchClearDebouncer = Debouncer(lifecycleScope, 1500)
        {
            query = ""
            viewModel.searchQueueUniversal(query)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onResume() {
        super.onResume()
        selectExtension.apply {
            deselect(selections.toMutableSet())
        }
        initSelectionToolbar()
        updateControlBar()
    }

    override fun onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQueueBinding.inflate(inflater, container, false)
        // We need to manually bind the merged view - it won't work at runtime with the main view alone
        _bottomBarBinding = IncludeQueueBottomBarBinding.bind(binding.root)

        // Both queue control buttons actually just need to send a signal that will be processed accordingly by whom it may concern
        bottomBarBinding.actionButton.setOnClickListener {
            if (isPaused()) viewModel.unpauseQueue()
            else EventBus.getDefault()
                .post(DownloadCommandEvent(DownloadCommandEvent.Type.EV_PAUSE))
        }

        // Book list
        val initItem = ContentItem(ContentItem.ViewType.QUEUE)
        fastAdapter.registerItemFactory(initItem.type, initItem)

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension.apply {
            isSelectable = true
            multiSelect = true
            selectOnLongClick = true
            selectWithItemUpdate = true
            selectionListener = object : ISelectionListener<ContentItem> {
                override fun onSelectionChanged(item: ContentItem, selected: Boolean) {
                    onSelectionChanged2()
                }
            }
        }
        val helper = FastAdapterPreClickSelectHelper(fastAdapter, selectExtension)
        fastAdapter.onPreClickListener = { _, _, _, pos -> helper.onPreClickListener(pos) }
        fastAdapter.onPreLongClickListener = { _, _, _, pos -> helper.onPreLongClickListener(pos) }

        binding.queueList.adapter = fastAdapter
        binding.queueList.setHasFixedSize(true)
        llm = binding.queueList.layoutManager as LinearLayoutManager

        // Fast scroller
        FastScrollerBuilder(binding.queueList).build()

        // Drag, drop & swiping
        val dragSwipeCallback = SimpleSwipeDrawerDragCallback(this, ItemTouchHelper.LEFT, this)
            .withSwipeLeft(Helper.dimensAsDp(requireContext(), R.dimen.delete_drawer_width_list))
            .withSensitivity(1.5f)
            .withSurfaceThreshold(0.3f)
            .withNotifyAllDrops(true)
        dragSwipeCallback.setIsDragEnabled(false) // Despite its name, that's actually to disable drag on long tap

        touchHelper = ItemTouchHelper(dragSwipeCallback)
        touchHelper.attachToRecyclerView(binding.queueList)

        // Item click listener
        fastAdapter.onClickListener = { _, _, i, _ -> onItemClick(i) }

        initToolbar()
        initSelectionToolbar()
        attachButtons(fastAdapter)

        addCustomBackControl()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.getQueue().observe(viewLifecycleOwner) { result -> onQueueChanged(result) }
        viewModel.getContentHashToShowFirst().observe(viewLifecycleOwner) { hash: Long ->
            contentHashToDisplayFirst = hash
        }
        viewModel.getNewSearch().observe(viewLifecycleOwner) { b: Boolean -> newSearch = b }
    }

    private fun addCustomBackControl() {
        callback?.remove()
        val localCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                customBackPress()
            }
        }
        activity.get()?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, localCallback)
        callback = localCallback
    }

    private fun customBackPress() {
        // If content is selected, deselect it
        if (selectExtension.selections.isNotEmpty()) {
            selectExtension.deselect(selectExtension.selections.toMutableSet())
            updateSelectionToolbarVis(false)
        } else {
            callback?.remove()
            activity.get()?.let { act ->
                act.onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                    act.finish()
                }
            }
        }
    }

    private fun initToolbar() {
        activity.get()?.getToolbar()?.let {
            val searchMenu = it.menu.findItem(R.id.action_search)
            val mainSearchView = searchMenu?.actionView as SearchView?
            mainSearchView?.findViewById<View>(androidx.appcompat.R.id.search_close_btn)
                ?.setOnClickListener {
                    invalidateNextQueryTextChange = true
                    mainSearchView.setQuery("", false)
                    mainSearchView.isIconified = true
                    viewModel.searchQueueUniversal("")
                }
            searchMenu.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    invalidateNextQueryTextChange = true

                    // Re-sets the query on screen, since default behaviour removes it right after collapse _and_ expand
                    if (query.isNotEmpty()) // Use of handler allows to set the value _after_ the UI has auto-cleared it
                    // Without that handler the view displays with an empty value
                        Handler(Looper.getMainLooper()).postDelayed({
                            invalidateNextQueryTextChange = true
                            mainSearchView?.setQuery(query, false)
                        }, 100)
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    invalidateNextQueryTextChange = true
                    return true
                }
            })
            mainSearchView?.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
            mainSearchView?.setIconifiedByDefault(true)
            mainSearchView?.queryHint = getString(R.string.library_search_hint)
            // Change display when text query is typed
            mainSearchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(s: String): Boolean {
                    query = s.trim()
                    viewModel.searchQueueUniversal(query)
                    mainSearchView.clearFocus()
                    return true
                }

                override fun onQueryTextChange(s: String): Boolean {
                    if (invalidateNextQueryTextChange) { // Should not happen when search panel is closing or opening
                        invalidateNextQueryTextChange = false
                    } else if (s.isEmpty()) {
                        searchClearDebouncer.submit(1)
                    } else searchClearDebouncer.clear()
                    return true
                }
            })
            val cancelAllMenu = it.menu.findItem(R.id.action_cancel_all)
            cancelAllMenu.setOnMenuItemClickListener {
                // Don't do anything if the queue is empty
                if (0 == itemAdapter.adapterItemCount) return@setOnMenuItemClickListener true

                // Just do it if the queue has a single item
                if (1 == itemAdapter.adapterItemCount) onCancelAll() else  // Ask if there's more than 1 item
                    MaterialAlertDialogBuilder(
                        requireContext(), ThemeHelper.getIdForCurrentTheme(
                            requireContext(), R.style.Theme_Light_Dialog
                        )
                    ).setIcon(R.drawable.ic_warning).setCancelable(false)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.confirm_cancel_all)
                        .setPositiveButton(R.string.yes) { dialog1, _ ->
                            dialog1.dismiss()
                            onCancelAll()
                        }.setNegativeButton(R.string.no) { dialog12, _ -> dialog12.dismiss() }
                        .create().show()
                true
            }
            it.menu.findItem(R.id.action_queue_prefs).setOnMenuItemClickListener {
                onSettingsClick()
                true
            }
            it.menu.findItem(R.id.action_invert_queue).setOnMenuItemClickListener {
                viewModel.invertQueue()
                true
            }
            it.menu.findItem(R.id.action_import_downloads).setOnMenuItemClickListener {
                invoke(parentFragmentManager)
                true
            }
            it.menu.findItem(R.id.action_error_stats).setOnMenuItemClickListener {
                showErrorStats()
                true
            }
        }
    }

    private fun initSelectionToolbar() {
        activity.get()?.getSelectionToolbar()?.let {
            it.setNavigationOnClickListener { _ ->
                selectExtension.apply {
                    deselect(selections.toMutableSet())
                }
                it.visibility = View.GONE
            }
            it.setOnMenuItemClickListener { menuItem: MenuItem ->
                onSelectionMenuItemClicked(menuItem)
            }
        }
    }

    private fun updateSelectionToolbarVis(vis: Boolean) {
        activity.get()?.getSelectionToolbar()?.visibility = if (vis) View.VISIBLE else View.GONE
    }

    // Process the move command while keeping scroll position in memory
    // https://stackoverflow.com/questions/27992427/recyclerview-adapter-notifyitemmoved0-1-scrolls-screen
    private fun processMove(positions: List<Int>, consumer: (List<Int>) -> Unit) {
        topItemPosition = getTopItemPosition()
        offsetTop = 0
        if (topItemPosition >= 0) {
            val firstView = llm.findViewByPosition(topItemPosition)
            if (firstView != null) offsetTop =
                llm.getDecoratedTop(firstView) - llm.getTopDecorationHeight(firstView)
        }
        consumer.invoke(positions)
        if (query.isEmpty()) recordMoveFromFirstPos(positions)
    }

    private fun attachButtons(fastAdapter: FastAdapter<ContentItem>) {
        // Site button
        fastAdapter.addEventHook(object : ClickEventHook<ContentItem>() {
            override fun onClick(
                v: View, position: Int, fastAdapter: FastAdapter<ContentItem>, item: ContentItem
            ) {
                if (selectExtension.selections.isNotEmpty()) return
                val c = item.content
                if (c != null) ContentHelper.viewContentGalleryPage(v.context, c)
            }

            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return if (viewHolder is ContentItem.ViewHolder) {
                    viewHolder.siteButton
                } else super.onBind(viewHolder)
            }
        })

        // Top button
        fastAdapter.addEventHook(object : ClickEventHook<ContentItem>() {
            override fun onClick(
                v: View, position: Int, fastAdapter: FastAdapter<ContentItem>, item: ContentItem
            ) {
                if (selectExtension.selections.isNotEmpty()) return
                processMove(listOf(position)) { relativePositions: List<Int> ->
                    viewModel.moveTop(relativePositions)
                }
            }

            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return if (viewHolder is ContentItem.ViewHolder) {
                    viewHolder.topButton
                } else super.onBind(viewHolder)
            }
        })

        // Bottom button
        fastAdapter.addEventHook(object : ClickEventHook<ContentItem>() {
            override fun onClick(
                v: View, position: Int, fastAdapter: FastAdapter<ContentItem>, item: ContentItem
            ) {
                if (selectExtension.selections.isNotEmpty()) return
                processMove(listOf(position)) { relativePositions: List<Int> ->
                    viewModel.moveBottom(relativePositions)
                }
            }

            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return if (viewHolder is ContentItem.ViewHolder) {
                    viewHolder.bottomButton
                } else super.onBind(viewHolder)
            }
        })
    }

    /**
     * Download event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadEvent(event: DownloadEvent) {
        Timber.v("Event received : %s.%s", event.eventType, event.step)
        val errorStatsMenu = activity.get()?.getToolbar()?.menu?.findItem(R.id.action_error_stats)
        errorStatsMenu?.isVisible = event.pagesKO > 0
        displayMotive(event)
        when (event.eventType) {
            DownloadEvent.Type.EV_PREPARATION -> updateControlBar(
                event.step,
                event.log,
                event.content
            )

            DownloadEvent.Type.EV_PROGRESS -> updateProgress(
                event.content,
                event.pagesOK,
                event.pagesKO,
                event.pagesTotal,
                event.getNumberRetries(),
                event.downloadedSizeB,
                false
            )

            DownloadEvent.Type.EV_UNPAUSED -> {
                updateProgress(false)
                updateControlBar()
            }

            DownloadEvent.Type.EV_SKIPPED -> {
                // Books switch / display handled directly by the adapter
                bottomBarBinding.queueInfo.text = ""
                bottomBarBinding.queueDownloadPreparationProgressBar.visibility = View.INVISIBLE
            }

            DownloadEvent.Type.EV_COMPLETE -> {
                bottomBarBinding.queueDownloadPreparationProgressBar.visibility = View.INVISIBLE
                if (0 == itemAdapter.adapterItemCount) errorStatsMenu?.isVisible = false
                updateControlBar()
            }

            DownloadEvent.Type.EV_PAUSED, DownloadEvent.Type.EV_CANCELED, DownloadEvent.Type.EV_CONTENT_INTERRUPTED -> {
                // Don't update the UI if it is in the process of canceling all items
                if (isCancelingAll) return
                bottomBarBinding.queueDownloadPreparationProgressBar.visibility = View.INVISIBLE
                if (null == event.content) updateProgress(true)
                else updateProgress(event.content, true)
                updateControlBar()
            }

            else -> {
                if (isCancelingAll) return
                bottomBarBinding.queueDownloadPreparationProgressBar.visibility = View.INVISIBLE
                if (null == event.content) updateProgress(true)
                else updateProgress(event.content, true)
                updateControlBar()
            }
        }
    }

    private fun displayMotive(event: DownloadEvent) {
        // Display motive, if any
        @StringRes val motiveMsg: Int
        when (event.motive) {
            DownloadEvent.Motive.NO_INTERNET -> motiveMsg = R.string.paused_no_internet
            DownloadEvent.Motive.NO_WIFI -> motiveMsg = R.string.paused_no_wifi
            DownloadEvent.Motive.NO_STORAGE -> {
                val spaceLeft = FileHelper.formatHumanReadableSize(
                    event.downloadedSizeB, resources
                )
                Snackbar.make(
                    binding.root,
                    getString(R.string.paused_no_storage, spaceLeft),
                    BaseTransientBottomBar.LENGTH_SHORT
                ).setAnchorView(bottomBarBinding.backgroundBottomBar).show()
                return
            }

            DownloadEvent.Motive.NO_DOWNLOAD_FOLDER -> motiveMsg = R.string.paused_no_dl_folder
            DownloadEvent.Motive.DOWNLOAD_FOLDER_NOT_FOUND -> motiveMsg =
                R.string.paused_dl_folder_not_found

            DownloadEvent.Motive.DOWNLOAD_FOLDER_NO_CREDENTIALS -> {
                motiveMsg = R.string.paused_dl_folder_credentials
                PermissionHelper.requestExternalStorageReadWritePermission(
                    requireActivity(), PermissionHelper.RQST_STORAGE_PERMISSION
                )
            }

            DownloadEvent.Motive.STALE_CREDENTIALS -> motiveMsg =
                R.string.paused_dl_stale_online_credentials

            DownloadEvent.Motive.NO_AVAILABLE_DOWNLOADS -> motiveMsg =
                R.string.paused_dl_no_available_downloads

            DownloadEvent.Motive.NONE -> motiveMsg = -1
        }
        if (motiveMsg != -1) Snackbar.make(
            binding.root, getString(motiveMsg), BaseTransientBottomBar.LENGTH_SHORT
        ).setAnchorView(bottomBarBinding.backgroundBottomBar).show()
    }

    private fun formatStep(step: DownloadEvent.Step, log: String?): String {
        val standardMsg = resources.getString(formatStep(step))
        return if (log != null) "$standardMsg $log" else standardMsg
    }

    @StringRes
    private fun formatStep(step: DownloadEvent.Step): Int {
        return when (step) {
            DownloadEvent.Step.INIT -> R.string.step_init
            DownloadEvent.Step.PROCESS_IMG -> R.string.step_prepare_img
            DownloadEvent.Step.FETCH_IMG -> R.string.step_fetch_img
            DownloadEvent.Step.PREPARE_FOLDER -> R.string.step_prepare_folder
            DownloadEvent.Step.PREPARE_DOWNLOAD -> R.string.step_prepare_download
            DownloadEvent.Step.SAVE_QUEUE -> R.string.step_save_queue
            DownloadEvent.Step.WAIT_PURGE -> R.string.step_wait_purge
            DownloadEvent.Step.START_DOWNLOAD -> R.string.step_start_download
            DownloadEvent.Step.COMPLETE_DOWNLOAD -> R.string.step_complete_download
            DownloadEvent.Step.REMOVE_DUPLICATE -> R.string.step_remove_duplicate
            DownloadEvent.Step.NONE -> R.string.empty_string
        }
    }

    /**
     * Download preparation event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPrepDownloadEvent(event: DownloadPreparationEvent) {
        bottomBarBinding.queueDownloadPreparationProgressBar.apply {
            if (!isShown && !event.isCompleted() && !isPaused() && !isEmpty()) {
                visibility = View.VISIBLE
                bottomBarBinding.queueInfo.setText(R.string.queue_preparing)
                isPreparingDownload = true
                updateProgress(false)
            } else if (isShown && event.isCompleted()) {
                visibility = View.INVISIBLE
            }

            progress = if (event.total > 0)
                event.done * 100 / event.total
            else
                100
        }
    }

    /**
     * Service destroyed event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onServiceDestroyed(event: ServiceDestroyedEvent) {
        if (event.service != R.id.download_service) return
        updateProgress(true)
        updateControlBar()
    }

    /**
     * Update main progress bar and bottom progress panel for current (1st in queue) book
     *
     * @param pagesOK         Number of pages successfully downloaded for current (1st in queue) book
     * @param pagesKO         Number of pages whose download has failed for current (1st in queue) book
     * @param totalPages      Total pages of current (1st in queue) book
     * @param numberRetries   Current number of download auto-retries for current (1st in queue) book
     * @param downloadedSizeB Current size of downloaded content (in bytes)
     * @param forceDisplay    True to force display even if the queue is paused
     */
    private fun updateProgress(
        content: Content?,
        pagesOK: Int,
        pagesKO: Int,
        totalPages: Int,
        numberRetries: Int,
        downloadedSizeB: Long,
        forceDisplay: Boolean
    ) {
        if ((!ContentQueueManager.isQueuePaused || forceDisplay) && itemAdapter.adapterItemCount > 0 && content != null) {
            contentId = content.id
            // Pages download has started
            if (pagesKO + pagesOK > 1 || forceDisplay) {
                // Downloader reports about the cover thumbnail too
                // Display one less page to avoid confusing the user
                val totalPagesDisplay = max(0, totalPages - 1)
                val pagesOKDisplay = max(0, pagesOK - 1)

                // Update book progress bar
                // NB : use the instance _inside the adapter_ to pass values
                fastAdapter.getItemById(content.uniqueHash())?.first?.content?.let { c ->
                    c.setProgress(pagesOKDisplay.toLong() + pagesKO)
                    c.setDownloadedBytes(downloadedSizeB)
                    c.qtyPages = totalPagesDisplay
                    updateProgress(content, false)
                }

                // Update information bar
                val message = StringBuilder()
                val processedPagesFmt =
                    StringHelper.formatIntAsStr(pagesOKDisplay, totalPagesDisplay.toString().length)
                message.append(
                    resources.getString(
                        R.string.queue_bottom_bar_processed, processedPagesFmt, totalPagesDisplay
                    )
                )
                if (pagesKO > 0) message.append(" ").append(
                    resources.getQuantityString(
                        R.plurals.queue_bottom_bar_errors, pagesKO, pagesKO
                    )
                )
                if (numberRetries > 0) message.append(" ").append(
                    resources.getString(
                        R.string.queue_bottom_bar_retry,
                        numberRetries,
                        Preferences.getDlRetriesNumber()
                    )
                )
                val avgSpeedKbps = getAvgSpeedKbps().toInt()
                if (avgSpeedKbps > 0) message.append(" @ ")
                    .append(resources.getString(R.string.queue_bottom_bar_speed, avgSpeedKbps))
                bottomBarBinding.queueInfo.text = message.toString()
                isPreparingDownload = false
            }
        }
    }

    private fun isPaused(): Boolean {
        return ContentQueueManager.isQueuePaused || !ContentQueueManager.isQueueActive(
            requireActivity()
        )
    }

    private fun isEmpty(): Boolean {
        return 0 == itemAdapter.adapterItemCount
    }

    private fun onQueueChanged(result: List<QueueRecord>) {
        Timber.d(">>Queue changed ! Size=%s", result.size)
        val empty = result.isEmpty()

        // Don't process changes while everything is being canceled, it usually kills the UI as too many changes are processed at the same time
        if (isCancelingAll && !empty) return

        // Update list visibility
        binding.queueEmptyTxt.visibility = if (empty) View.VISIBLE else View.GONE

        // Update displayed books
        val contentItems = result.mapIndexed { i, c ->
            ContentItem(c, query.isNotEmpty(), touchHelper, 0 == i)
            { item -> onCancelSwipedBook(item) }
        }.distinct()

        if (newSearch) itemAdapter.setNewList(contentItems, false)
        else set(itemAdapter, contentItems, LibraryContentFragment.CONTENT_ITEM_DIFF_CALLBACK)

        Handler(Looper.getMainLooper()).postDelayed({ differEndCallback() }, 150)
        updateControlBar()

        // Signal swipe-to-cancel though a tooltip
        if (!empty) TooltipHelper.showTooltip(
            requireContext(),
            R.string.help_swipe_cancel,
            ArrowOrientation.BOTTOM,
            binding.queueList,
            viewLifecycleOwner
        )
        newSearch = false
    }

    /**
     * Callback for the end of item diff calculations
     * Activated when all _adapter_ items are placed on their definitive position
     */
    private fun differEndCallback() {
        if (contentHashToDisplayFirst != 0L) {
            val targetPos = fastAdapter.getPosition(contentHashToDisplayFirst)
            if (targetPos > -1) listRefreshDebouncer.submit(targetPos)
            contentHashToDisplayFirst = 0
            return
        }
        // Reposition the list on the initial top item position
        if (topItemPosition >= 0) {
            val targetPos = topItemPosition
            listRefreshDebouncer.submit(targetPos)
            topItemPosition = -1
        }
        // Refresh the item that moved from the 1st position
        if (itemToRefreshIndex > -1) {
            fastAdapter.notifyAdapterItemChanged(itemToRefreshIndex)
            itemToRefreshIndex = -1
        }
    }

    /**
     * Callback for the end of recycler updates
     * Activated when all _displayed_ items are placed on their definitive position
     */
    private fun onRecyclerUpdated(topItemPosition: Int) {
        llm.scrollToPositionWithOffset(
            topItemPosition, offsetTop
        ) // Used to restore position after activity has been stopped and recreated
    }

    private fun updateControlBar(
        preparationStep: DownloadEvent.Step = DownloadEvent.Step.NONE,
        log: String? = null,
        content: Content? = null
    ) {
        val isActive = !isEmpty() && !isPaused()
        Timber.d(
            "Queue state : E/P/A > %s/%s/%s -- %s elements",
            isEmpty(),
            isPaused(),
            isActive,
            itemAdapter.adapterItemCount
        )

        // Update list visibility
        binding.queueEmptyTxt.visibility = if (isEmpty()) View.VISIBLE else View.GONE

        // Update control bar status
        bottomBarBinding.apply {
            if (isPreparingDownload && !isEmpty()) {
                queueInfo.setText(R.string.queue_preparing)
            } else {
                queueInfo.text = formatStep(preparationStep, log)
            }
            if (isActive) {
                actionButton.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireActivity(),
                        R.drawable.ic_action_pause
                    )
                )
                if (content != null)
                    queueStatus.text = resources.getString(R.string.queue_dl, content.title)

                // Stop blinking animation, if any
                queueInfo.clearAnimation()
                queueStatus.clearAnimation()
            } else {
                if (isEmpty()) {
                    activity.get()
                        ?.getToolbar()?.menu?.findItem(R.id.action_error_stats)?.isVisible = false
                    queueStatus.text = ""
                } else if (isPaused()) {
                    actionButton.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireActivity(),
                            R.drawable.ic_action_play
                        )
                    )
                    queueStatus.setText(R.string.queue_paused)
                    queueInfo.text = ""

                    // Set blinking animation when queue is paused
                    val animation = BlinkAnimation(750, 20)
                    queueStatus.startAnimation(animation)
                    queueInfo.startAnimation(animation)
                }
            }
        }
    }

    private fun showErrorStats() {
        if (itemAdapter.adapterItemCount > 0) {
            if (contentId > -1) ErrorStatsDialogFragment.invoke(parentFragmentManager, contentId)
        }
    }

    private fun updateProgress(isPausedevent: Boolean) {
        itemAdapter.adapterItems.forEach {
            it.content?.let { c -> updateProgress(c, isPausedevent, false) }
        }
    }

    private fun updateProgress(
        content: Content?,
        isPausedevent: Boolean,
        isIndividual: Boolean = true
    ) {
        if (null == content) return
        binding.queueList.findViewHolderForItemId(content.uniqueHash())?.let {
            fastAdapter.getItemById(content.uniqueHash())?.first?.updateProgress(
                it,
                isPausedevent,
                isIndividual
            )
        }
    }

    private fun onItemClick(item: ContentItem): Boolean {
        if (selectExtension.selections.isEmpty()) {
            var c = item.content
            // Process the click
            if (null == c) {
                ToastHelper.toast(R.string.err_no_content)
                return false
            }
            // Retrieve the latest version of the content if storage URI is unknown
            // (may happen when the item is fetched before it is processed by the downloader)
            if (c.storageUri.isEmpty()) c = ObjectBoxDAO(requireContext()).selectContent(c.id)
            return if (c != null) {
                if (!ContentHelper.openReader(
                        requireContext(), c, -1, null, false, false
                    )
                ) ToastHelper.toast(R.string.err_no_content)
                true
            } else false
        }
        return false
    }

    private fun onCancelSwipedBook(item: ContentItem) {
        // Deleted book is the last selected books => disable selection mode
        if (item.isSelected) {
            selectExtension.apply {
                deselect(item)
                if (selections.isEmpty()) updateSelectionToolbarVis(false)
            }
        }
        if (item.content != null) viewModel.cancel(listOfNotNull(item.content))
    }

    private fun onCancelBooks(c: List<Content>) {
        if (c.size > 2) {
            isCancelingAll = true
            ProgressDialogFragment.invoke(
                parentFragmentManager,
                resources.getString(R.string.cancel_queue_progress),
                R.plurals.book
            )
        }
        viewModel.cancel(c)
    }

    private fun onCancelAll() {
        isCancelingAll = true
        ProgressDialogFragment.invoke(
            parentFragmentManager,
            resources.getString(R.string.cancel_queue_progress),
            R.plurals.book
        )
        viewModel.cancelAll()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onProcessStickyEvent(event: ProcessEvent) {
        // Filter on cancel complete event
        if (R.id.generic_progress != event.processId) return
        EventBus.getDefault().removeStickyEvent(event)
        if (event.eventType == ProcessEvent.Type.COMPLETE) onCancelComplete()
    }

    private fun onCancelComplete() {
        isCancelingAll = false
        viewModel.refresh()
        if (selectExtension.selections.isEmpty()) updateSelectionToolbarVis(false)
    }

    /**
     * Calculate the position of the top visible item of the book list
     *
     * @return position of the top visible item of the book list
     */
    private fun getTopItemPosition(): Int {
        return max(
            llm.findFirstVisibleItemPosition(), llm.findFirstCompletelyVisibleItemPosition()
        )
    }

    /**
     * DRAG, DROP & SWIPE METHODS
     */
    private fun recordMoveFromFirstPos(from: Int, to: Int) {
        if (0 == from) itemToRefreshIndex = to
    }

    private fun recordMoveFromFirstPos(positions: List<Int>) {
        // Only useful when moving the 1st item to the bottom
        if (positions.isNotEmpty() && 0 == positions[0])
            itemToRefreshIndex = itemAdapter.adapterItemCount - positions.size
    }

    override fun itemTouchOnMove(oldPosition: Int, newPosition: Int): Boolean {
        onMove(itemAdapter, oldPosition, newPosition) // change position
        recordMoveFromFirstPos(oldPosition, newPosition)
        return true
    }

    override fun itemTouchDropped(oldPosition: Int, newPosition: Int) {
        // Save final position of item in DB
        viewModel.moveAbsolute(oldPosition, newPosition)
        recordMoveFromFirstPos(oldPosition, newPosition)

        // Delay execution of findViewHolderForAdapterPosition to give time for the new layout to
        // be calculated (if not, it might return null under certain circumstances)
        Handler(Looper.getMainLooper()).postDelayed({
            val vh: RecyclerView.ViewHolder? =
                binding.queueList.findViewHolderForAdapterPosition(newPosition)
            if (vh is IDraggableViewHolder) {
                (vh as IDraggableViewHolder).onDropped()
            }
        }, 75)
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
        val vh: RecyclerView.ViewHolder? =
            binding.queueList.findViewHolderForAdapterPosition(position)
        if (vh is ISwipeableViewHolder) {
            (vh as ISwipeableViewHolder).onSwiped()
        }
    }

    override fun itemUnswiped(position: Int) {
        val vh: RecyclerView.ViewHolder? =
            binding.queueList.findViewHolderForAdapterPosition(position)
        if (vh is ISwipeableViewHolder) {
            (vh as ISwipeableViewHolder).onUnswiped()
        }
    }

    /**
     * Show the viewer settings dialog
     */
    private fun onSettingsClick() {
        val intent = Intent(requireActivity(), PrefsActivity::class.java)
        val prefsBundle = PrefsBundle()
        prefsBundle.isDownloaderPrefs = true
        intent.putExtras(prefsBundle.bundle)
        requireContext().startActivity(intent)
    }

    @SuppressLint("NonConstantResourceId")
    private fun onSelectionMenuItemClicked(menuItem: MenuItem): Boolean {
        val selectedItems: Set<ContentItem> = selectExtension.selectedItems
        val selectedPositions: List<Int>
        var keepToolbar = false
        when (menuItem.itemId) {
            R.id.action_select_queue_cancel -> {
                val selectedContent = selectedItems.mapNotNull { obj -> obj.content }
                if (selectedContent.isNotEmpty()) askDeleteSelected(selectedContent)
            }

            R.id.action_select_queue_top -> {
                selectedPositions = selectedItems.map { i -> fastAdapter.getPosition(i) }.sorted()
                selectExtension.apply {
                    deselect(selections.toMutableSet())
                }
                if (selectedPositions.isNotEmpty()) processMove(
                    selectedPositions
                ) { relativePositions: List<Int> ->
                    viewModel.moveTop(relativePositions)
                }
            }

            R.id.action_select_queue_bottom -> {
                selectedPositions = selectedItems.map { i -> fastAdapter.getPosition(i) }.sorted()
                selectExtension.apply {
                    deselect(selections.toMutableSet())
                }
                if (selectedPositions.isNotEmpty())
                    processMove(selectedPositions) { relativePositions: List<Int> ->
                        viewModel.moveBottom(relativePositions)
                    }
            }

            R.id.action_download_scratch -> {
                askRedownloadSelectedScratch()
                keepToolbar = true
            }

            R.id.action_change_mode -> {
                val menu = build(requireContext(), requireActivity())
                show(menu,
                    binding.queueList,
                    { position: Int, _: PowerMenuItem? ->
                        onNewModeSelected(position)
                        menu.dismiss()
                    },
                    { leaveSelectionMode() }
                )
            }

            R.id.action_freeze -> {
                val selectedIds = selectedItems.mapNotNull { i -> i.queueRecord?.id }
                selectExtension.apply {
                    deselect(selections.toMutableSet())
                }
                if (selectedIds.isNotEmpty()) viewModel.toogleFreeze(selectedIds)
            }

            R.id.action_select_all -> {
                // Make certain _everything_ is properly selected (selectExtension.select() as doesn't get everything the 1st time it's called)
                var count = 0
                selectExtension.apply {
                    while (selections.size < itemAdapter.adapterItemCount && ++count < 5)
                        select(IntRange(0, itemAdapter.adapterItemCount - 1))
                }
                keepToolbar = true
            }

            else -> {}
        }
        if (!keepToolbar) updateSelectionToolbarVis(false)
        return true
    }

    private fun leaveSelectionMode() {
        selectExtension.apply {
            selectOnLongClick = true
            // Warning : next line makes FastAdapter cycle through all items,
            // which has a side effect of calling TiledPageList.onPagePlaceholderInserted,
            // flagging the end of the list as being the last displayed position
            val selection = selections
            if (selection.isNotEmpty()) deselect(selections.toMutableSet())
        }
        updateSelectionToolbarVis(false)
    }

    private fun onNewModeSelected(downloadMode: Int) {
        val selection = selectExtension.selections
        val selectedContentIds = selection
            .map { pos -> itemAdapter.getAdapterItem(pos).content }
            .mapNotNull { obj -> obj?.id }
        leaveSelectionMode()
        viewModel.setDownloadMode(selectedContentIds, downloadMode)
    }

    private fun updateSelectionToolbar(selectedCount: Long) {
        activity.get()?.getSelectionToolbar()?.title = resources.getQuantityString(
            R.plurals.items_selected, selectedCount.toInt(), selectedCount.toInt()
        )
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private fun onSelectionChanged2() {
        selectExtension.let {
            val selectedCount = it.selections.size
            if (0 == selectedCount) {
                updateSelectionToolbarVis(false)
                it.selectOnLongClick = true
            } else {
                updateSelectionToolbar(selectedCount.toLong())
                updateSelectionToolbarVis(true)
            }
        }
    }

    /**
     * Display the yes/no dialog to make sure the user really wants to delete selected items
     *
     * @param items Items to be deleted if the answer is yes
     */
    private fun askDeleteSelected(items: List<Content>) {
        val context = getActivity() ?: return
        val builder = MaterialAlertDialogBuilder(context)
        val title = context.resources.getQuantityString(R.plurals.ask_cancel_multiple, items.size)
        builder.setMessage(title).setPositiveButton(
            R.string.yes
        ) { _, _ ->
            selectExtension.apply {
                deselect(selections.toMutableSet())
            }
            onCancelBooks(items)
        }.setNegativeButton(
            R.string.no
        ) { _, _ ->
            selectExtension.apply {
                deselect(selections.toMutableSet())
            }
        }
            .setOnCancelListener {
                selectExtension.apply {
                    deselect(selections.toMutableSet())
                }
            }.create().show()
    }

    private fun askRedownloadSelectedScratch() {
        val selectedItems: Set<ContentItem> = selectExtension.selectedItems
        val contents: MutableList<Content> = ArrayList()
        for (ci in selectedItems) {
            val c = ci.content
            if (null == c || c.isBeingProcessed) continue  // Don't redownload if the content is being purged
            contents.add(c)
        }
        val message = resources.getQuantityString(R.plurals.redownload_confirm, contents.size)
        MaterialAlertDialogBuilder(
            requireContext(),
            ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog)
        ).setIcon(R.drawable.ic_warning).setCancelable(false).setTitle(R.string.app_name)
            .setMessage(message).setPositiveButton(
                R.string.yes
            ) { dialog1, _ ->
                dialog1.dismiss()
                activity.get()?.redownloadContent(
                    contents,
                    reparseContent = true,
                    reparseImages = true
                )

                // Visually reset the progress of selected items
                selectedItems.forEach {
                    if (it.content != null)
                        updateProgress(it.content, 0, 0, 1, 0, 0, true)
                }

                selectExtension.apply {
                    deselect(selections.toMutableSet())
                }
                updateSelectionToolbarVis(false)
            }.setNegativeButton(
                R.string.no
            ) { dialog12, _ ->
                dialog12.dismiss()
                selectExtension.apply {
                    deselect(selections.toMutableSet())
                }
                updateSelectionToolbarVis(false)
            }.create().show()
    }
}