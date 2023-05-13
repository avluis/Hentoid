package me.devsaki.hentoid.fragments.reader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.DiffCallback
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil.set
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.drag.SimpleDragCallback
import com.mikepenz.fastadapter.expandable.getExpandableExtension
import com.mikepenz.fastadapter.select.getSelectExtension
import com.mikepenz.fastadapter.utils.DragDropUtil.onMove
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.ReaderActivity
import me.devsaki.hentoid.activities.bundles.ImageItemBundle
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.databinding.FragmentReaderGalleryBinding
import me.devsaki.hentoid.fragments.ProgressDialogFragment
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Preferences.Constant.*
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.util.exception.ContentNotProcessedException
import me.devsaki.hentoid.viewholders.INestedItem
import me.devsaki.hentoid.viewholders.ImageFileItem
import me.devsaki.hentoid.viewholders.SubExpandableItem
import me.devsaki.hentoid.viewmodels.ReaderViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.DragSelectTouchListener
import me.devsaki.hentoid.widget.DragSelectTouchListener.OnDragSelectListener
import me.devsaki.hentoid.widget.DragSelectionProcessor
import me.devsaki.hentoid.widget.DragSelectionProcessor.ISelectionHandler
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper
import me.devsaki.hentoid.widget.ReaderKeyListener
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import timber.log.Timber
import java.lang.ref.WeakReference


class ReaderGalleryFragment : Fragment(R.layout.fragment_reader_gallery), ItemTouchCallback {

    enum class EditMode {
        NONE,  // Plain gallery
        EDIT_CHAPTERS, // Screen with foldable and draggable chapters
        ADD_CHAPTER // Screen with tappable images to add and remove chapters
    }

    // ======== COMMUNICATION
    // Viewmodel
    private lateinit var viewModel: ReaderViewModel

    // Activity
    private lateinit var activity: WeakReference<ReaderActivity>

    // === UI
    private var binding: FragmentReaderGalleryBinding? = null
    private lateinit var itemSetCoverMenu: MenuItem
    private lateinit var showFavouritePagesMenu: MenuItem
    private lateinit var editChaptersMenu: MenuItem
    private lateinit var addChapterMenu: MenuItem
    private lateinit var removeChaptersMenu: MenuItem
    private lateinit var confirmReorderMenu: MenuItem
    private lateinit var cancelReorderMenu: MenuItem

    private val itemAdapter = ItemAdapter<ImageFileItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)
    private val selectExtension = fastAdapter.getSelectExtension()

    private val expandableItemAdapter = ItemAdapter<INestedItem<SubExpandableItem.ViewHolder>>()
    private val expandableFastAdapter = FastAdapter.with(expandableItemAdapter)
    private val expandableExtension = expandableFastAdapter.getExpandableExtension()
    private var touchHelper: ItemTouchHelper? = null

    private var mDragSelectTouchListener: DragSelectTouchListener? = null

    // === VARIABLES
    // Used to ignore native calls to onBookClick right after that book has been deselected
    private var startIndex = 0
    private var firstMoveDone = false
    private var isContentDynamic = false

    private var editMode = EditMode.NONE
    private var isReorderingChapters = false

    private var filterFavouritesState = false
    private var shuffledState = false


    private val imageDiffCallback: DiffCallback<ImageFileItem> =
        object : DiffCallback<ImageFileItem> {
            override fun areItemsTheSame(oldItem: ImageFileItem, newItem: ImageFileItem): Boolean {
                return oldItem.identifier == newItem.identifier
            }

            override fun areContentsTheSame(
                oldItem: ImageFileItem,
                newItem: ImageFileItem
            ): Boolean {
                val oldImage = oldItem.image
                val newImage = newItem.image
                return if (null == oldImage || null == newImage) false else oldItem.isFavourite == newItem.isFavourite && oldItem.chapterOrder == newItem.chapterOrder
            }

            override fun getChangePayload(
                oldItem: ImageFileItem,
                oldItemPosition: Int,
                newItem: ImageFileItem,
                newItemPosition: Int
            ): Any? {
                val oldImage = oldItem.image
                val newImage = newItem.image
                if (null == oldImage || null == newImage) return false
                val diffBundleBuilder = ImageItemBundle()
                if (oldImage.isFavourite != newImage.isFavourite) {
                    diffBundleBuilder.isFavourite = newImage.isFavourite
                }
                if (oldItem.chapterOrder != newItem.chapterOrder) {
                    diffBundleBuilder.chapterOrder = newItem.chapterOrder
                }
                return if (diffBundleBuilder.isEmpty) null else diffBundleBuilder.bundle
            }
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(requireActivity() is ReaderActivity) { "Parent activity has to be a ReaderActivity" }
        activity = WeakReference(requireActivity() as ReaderActivity)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentReaderGalleryBinding.inflate(inflater, container, false)

        activity.get()?.window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, true)
        }

        updateListAdapter(editMode == EditMode.EDIT_CHAPTERS)
        binding?.apply {
            FastScrollerBuilder(recyclerView).build()
            recyclerView.setHasFixedSize(true)

            // Toolbar
            toolbar.setNavigationOnClickListener { onBackClick() }
            toolbar.setOnMenuItemClickListener { clickedMenuItem: MenuItem ->
                when (clickedMenuItem.itemId) {
                    R.id.action_show_favorite_pages -> {
                        viewModel.filterFavouriteImages(!filterFavouritesState)
                    }

                    R.id.action_edit_chapters -> {
                        setChapterEditMode(EditMode.EDIT_CHAPTERS)
                    }

                    R.id.action_add_remove_chapters -> {
                        setChapterEditMode(EditMode.ADD_CHAPTER)
                    }

                    R.id.action_edit_confirm -> {
                        onConfirmChapterReordering()
                    }

                    R.id.action_edit_cancel -> {
                        onCancelChapterReordering()
                    }

                    R.id.action_remove_chapters -> {
                        val builder = MaterialAlertDialogBuilder(
                            requireActivity(),
                            ThemeHelper.getIdForCurrentTheme(
                                requireContext(),
                                R.style.Theme_Light_Dialog
                            )
                        )
                        val title = requireActivity().getString(R.string.ask_clear_chapters)
                        builder.setMessage(title)
                            .setPositiveButton(R.string.yes) { _, _ -> stripChapters() }
                            .setNegativeButton(R.string.no) { _, _ -> }
                            .create().show()
                    }
                }
                true
            }
            showFavouritePagesMenu = toolbar.menu.findItem(R.id.action_show_favorite_pages)
            editChaptersMenu = toolbar.menu.findItem(R.id.action_edit_chapters)
            removeChaptersMenu = toolbar.menu.findItem(R.id.action_remove_chapters)
            addChapterMenu = toolbar.menu.findItem(R.id.action_add_remove_chapters)
            confirmReorderMenu = toolbar.menu.findItem(R.id.action_edit_confirm)
            cancelReorderMenu = toolbar.menu.findItem(R.id.action_edit_cancel)
            itemSetCoverMenu = selectionToolbar.menu.findItem(R.id.action_set_group_cover)
            selectionToolbar.setNavigationOnClickListener {
                selectExtension.deselect(selectExtension.selections.toMutableSet())
                selectionToolbar.visibility = View.GONE
                toolbar.visibility = View.VISIBLE
            }
            selectionToolbar.setOnMenuItemClickListener { menuItem: MenuItem ->
                onSelectionMenuItemClicked(menuItem)
            }
            updateToolbar()
        }
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[ReaderViewModel::class.java]
        viewModel.getStartingIndex().observe(viewLifecycleOwner) { startingIndex: Int ->
            onStartingIndexChanged(startingIndex)
        }
        viewModel.getViewerImages().observe(viewLifecycleOwner) { images ->
            onImagesChanged(images)
        }
        viewModel.getContent().observe(viewLifecycleOwner) { content -> onContentChanged(content) }
        viewModel.getShowFavouritesOnly().observe(viewLifecycleOwner) { showFavouriteOnly ->
            onShowFavouriteChanged(showFavouriteOnly)
        }
        viewModel.getShuffled().observe(
            viewLifecycleOwner
        ) { shuffled: Boolean ->
            onShuffledChanged(shuffled)
        }
    }

    override fun onDestroy() {
        binding?.apply {
            recyclerView.adapter = null
        }
        binding = null
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        (requireActivity() as ReaderActivity).registerKeyListener(
            ReaderKeyListener(lifecycleScope).setOnBackListener { onBackClick() }
        )
    }

    private fun onBackClick() {
        when (editMode) {
            EditMode.EDIT_CHAPTERS -> setChapterEditMode(EditMode.NONE)
            EditMode.ADD_CHAPTER -> setChapterEditMode(
                EditMode.EDIT_CHAPTERS
            )

            else -> requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onStop() {
        (requireActivity() as ReaderActivity).unregisterKeyListener()
        super.onStop()
    }

    private fun updateListAdapter(isChapterEditMode: Boolean) {
        if (isChapterEditMode) {
            if (!expandableFastAdapter.hasObservers()) expandableFastAdapter.setHasStableIds(true)
            expandableItemAdapter.clear()

            binding?.apply {
                val glm = recyclerView.layoutManager as GridLayoutManager?
                if (glm != null) {
                    val spanCount = Preferences.getReaderGalleryColumns()
                    glm.spanCount = spanCount

                    // Use the correct size to display chapter separators, if any
                    val spanSizeLookup: SpanSizeLookup = object : SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            return if (expandableFastAdapter.getItemViewType(position) == R.id.expandable_item) {
                                spanCount
                            } else 1
                        }
                    }
                    glm.spanSizeLookup = spanSizeLookup
                }
            }

            // Activate drag & drop
            val dragCallback = SimpleDragCallback(this)
            dragCallback.notifyAllDrops = true
            touchHelper = ItemTouchHelper(dragCallback)
            binding?.apply {
                touchHelper?.attachToRecyclerView(recyclerView)
                recyclerView.adapter = expandableFastAdapter
                expandableFastAdapter.addEventHook(
                    SubExpandableItem.DragHandlerTouchEvent<Chapter> { position: Int ->
                        val vh = recyclerView.findViewHolderForAdapterPosition(position)
                        if (vh != null) touchHelper?.startDrag(vh)
                    }
                )

                // Item click listener
                expandableFastAdapter.onClickListener = { _, _, i, _ -> onNestedItemClick(i) }

                // Select on swipe
                if (mDragSelectTouchListener != null) {
                    recyclerView.removeOnItemTouchListener(mDragSelectTouchListener!!)
                    mDragSelectTouchListener = null
                }
            }
        } else { // Gallery mode
            binding?.apply {
                if (!fastAdapter.hasObservers()) fastAdapter.setHasStableIds(true)
                itemAdapter.clear()

                // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
                selectExtension.apply {
                    isSelectable = true
                    multiSelect = true
                    selectOnLongClick = true
                    selectWithItemUpdate = true
                    selectionListener = object : ISelectionListener<ImageFileItem> {
                        override fun onSelectionChanged(item: ImageFileItem, selected: Boolean) {
                            onSelectionChanged2()
                        }
                    }
                }
                val helper = FastAdapterPreClickSelectHelper(selectExtension)
                fastAdapter.onPreClickListener =
                    { v, adapter, item, position ->
                        helper.onPreClickListener(
                            v,
                            adapter,
                            item,
                            position
                        )
                    }
                fastAdapter.onPreLongClickListener =
                    { v, a, i, p ->
                        // Warning : specific code for drag selection
                        mDragSelectTouchListener?.startDragSelection(p)
                        helper.onPreLongClickListener(v, a, i, p)
                    }

                // Item click listener
                fastAdapter.onClickListener = { _, _, i, _ -> onItemClick(i) }
                val glm = recyclerView.layoutManager as GridLayoutManager?
                if (glm != null) {
                    val spanCount = Preferences.getReaderGalleryColumns()
                    glm.spanCount = spanCount

                    // Use the correct size to display chapter separators, if any
                    val spanSizeLookup: SpanSizeLookup = object : SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            return if (fastAdapter.getItemViewType(position) == R.id.expandable_item) {
                                spanCount
                            } else 1
                        }
                    }
                    glm.spanSizeLookup = spanSizeLookup
                }

                touchHelper?.attachToRecyclerView(null)
                recyclerView.adapter = fastAdapter

                // Select / deselect on swipe
                val onDragSelectionListener: OnDragSelectListener =
                    DragSelectionProcessor(object : ISelectionHandler {
                        override fun getSelection(): Set<Int> {
                            return selectExtension.selections
                        }

                        override fun isSelected(index: Int): Boolean {
                            return selectExtension.selections.contains(index)
                        }

                        override fun updateSelection(
                            start: Int,
                            end: Int,
                            isSelected: Boolean,
                            calledFromOnStart: Boolean
                        ) {
                            if (isSelected) selectExtension.select(
                                IntRange(start, end)
                            ) else selectExtension.deselect(
                                IntRange(start, end).toMutableList()
                            )
                        }
                    }).withMode(DragSelectionProcessor.Mode.Simple)

                DragSelectTouchListener().withSelectListener(onDragSelectionListener).let {
                    mDragSelectTouchListener = it
                    recyclerView.addOnItemTouchListener(it)
                }
            }
        }
    }

    private fun onContentChanged(content: Content?) {
        if (null == content) return
        val chapters = content.chaptersList
        isContentDynamic = content.isDynamic
        if (chapters.isNullOrEmpty()) return
        binding?.apply {
            chapterSelector.onFocusChangeListener =
                OnFocusChangeListener { _: View?, hasFocus: Boolean ->
                    Timber.i("hasFocus %s", hasFocus)
                }
            chapterSelector.entries = chapters.sortedBy { obj -> obj.order }
                .filter { c: Chapter -> c.order > -1 }.map { obj: Chapter -> obj.name }
                .toList()
            chapterSelector.index = 0
            chapterSelector.setOnIndexChangeListener { index ->
                val imgs: List<ImageFile>? =
                    chapters.first { ch -> ch.order == index + 1 }.imageFiles
                if (!imgs.isNullOrEmpty()) {
                    viewModel.setViewerStartingIndex(imgs[0].order - 1)
                    moveToIndex(imgs[0].order - 1, true)
                }
            }
            chapterSelector.visibility = View.VISIBLE
        }
    }

    private fun onImagesChanged(images: List<ImageFile>) {
        if (editMode == EditMode.EDIT_CHAPTERS) { // Expandable chapters
            val chapterItems: MutableList<INestedItem<SubExpandableItem.ViewHolder>> = ArrayList()
            var isArchive = false
            if (images.isNotEmpty()) isArchive = images[0].content.target.isArchive
            val chapters = images
                .asSequence()
                .mapNotNull { obj -> obj.linkedChapter }
                .sortedBy { obj -> obj.order }
                .filter { c -> c.order > -1 }
                .distinct().toList()
            var displayOrder = 0
            for (c in chapters) {
                val expandableItem =
                    SubExpandableItem(touchHelper!!, c.name, c).withDraggable(!isArchive)
                expandableItem.identifier = c.id
                val imgs: MutableList<ImageFileItem> = ArrayList()
                val chpImgs: List<ImageFile>? = c.imageFiles
                if (chpImgs != null) {
                    for (img in chpImgs) {
                        // Reconstitute display order that has been lost because of @Transient property
                        img.displayOrder = displayOrder++
                        if (img.isReadable) {
                            val holder = ImageFileItem(img, false)
                            imgs.add(holder)
                        }
                    }
                }
                expandableItem.subItems.addAll(imgs)
                chapterItems.add(expandableItem)
            }

            // One last category for chapterless images
            val chapterlessImages = images.filter { i: ImageFile -> null == i.linkedChapter }
            if (chapterlessImages.isNotEmpty()) {
                val expandableItem = SubExpandableItem(
                    touchHelper!!,
                    resources.getString(R.string.gallery_no_chapter),
                    null
                ).withDraggable(!isArchive)
                expandableItem.identifier = Long.MAX_VALUE
                val imgs: MutableList<ImageFileItem> = ArrayList()
                for (img in chapterlessImages) {
                    val holder = ImageFileItem(img, false)
                    imgs.add(holder)
                }
                expandableItem.subItems.addAll(imgs)
                chapterItems.add(expandableItem)
            }
            expandableItemAdapter.set(chapterItems)
        } else { // Classic gallery
            var imgs: MutableList<ImageFileItem> = ArrayList()
            for (img in images) {
                val holder = ImageFileItem(img, editMode == EditMode.ADD_CHAPTER)
                if (startIndex == img.displayOrder) holder.setCurrent(true)
                imgs.add(holder)
            }
            // Remove duplicates
            imgs = imgs.distinct().toMutableList()
            set(
                itemAdapter,
                imgs,
                imageDiffCallback
            )
        }
        updateToolbar()
        Handler(Looper.getMainLooper()).postDelayed({
            moveToIndex(
                startIndex,
                false
            )
        }, 150)
    }

    private fun onStartingIndexChanged(startingIndex: Int) {
        startIndex = startingIndex
    }

    private fun onShowFavouriteChanged(showFavouriteOnly: Boolean) {
        filterFavouritesState = showFavouriteOnly
        updateFavouriteDisplay(filterFavouritesState)
    }

    private fun onShuffledChanged(shuffled: Boolean) {
        shuffledState = shuffled
        updateToolbar()
    }

    @SuppressLint("NonConstantResourceId")
    private fun onSelectionMenuItemClicked(menuItem: MenuItem): Boolean {
        val selectedItems: Set<ImageFileItem> = selectExtension.selectedItems
        when (menuItem.itemId) {
            R.id.action_delete -> if (selectedItems.isNotEmpty()) {
                val selectedImages = selectedItems.mapNotNull { obj: ImageFileItem -> obj.image }
                askDeleteSelected(selectedImages)
            }

            R.id.action_set_group_cover -> if (selectedItems.isNotEmpty()) {
                val selectedImages =
                    selectedItems.firstNotNullOfOrNull { obj: ImageFileItem -> obj.image }
                if (selectedImages != null) askSetSelectedCover(selectedImages)
            }

            R.id.action_toggle_favorite_pages -> if (selectedItems.isNotEmpty()) {
                val selectedImages = selectedItems.mapNotNull { obj: ImageFileItem -> obj.image }
                viewModel.toggleImageFavourite(selectedImages) { onFavouriteSuccess() }
            }

            else -> {
                binding?.apply {
                    selectionToolbar.visibility = View.GONE
                    toolbar.visibility = View.VISIBLE
                }
                return false
            }
        }
        binding?.apply {
            selectionToolbar.visibility = View.GONE
            toolbar.visibility = View.VISIBLE
        }
        return true
    }

    private fun updateToolbar() {
        showFavouritePagesMenu.isVisible =
            editMode == EditMode.NONE && hasFavourite() && !isContentDynamic
        editChaptersMenu.isVisible =
            editMode == EditMode.NONE && !shuffledState && !isContentDynamic
        addChapterMenu.isVisible = editMode == EditMode.EDIT_CHAPTERS && !isReorderingChapters
        removeChaptersMenu.isVisible = editMode == EditMode.EDIT_CHAPTERS && !isReorderingChapters
        confirmReorderMenu.isVisible = isReorderingChapters
        cancelReorderMenu.isVisible = isReorderingChapters
        binding?.apply {
            if (chapterSelector.index > -1) chapterSelector.visibility =
                if (editMode == EditMode.NONE) View.VISIBLE else View.GONE
        }
    }

    private fun updateSelectionToolbar(selectedCount: Long) {
        itemSetCoverMenu.isVisible = 1L == selectedCount
        binding?.apply {
            selectionToolbar.title = resources.getQuantityString(
                R.plurals.items_selected,
                selectedCount.toInt(),
                selectedCount.toInt()
            )
        }
    }

    private fun onItemClick(item: ImageFileItem): Boolean {
        val img = item.image
        if (img != null) {
            if (editMode == EditMode.NONE) { // View image in gallery
                viewModel.setViewerStartingIndex(img.displayOrder)
                if (0 == parentFragmentManager.backStackEntryCount) { // Gallery mode (Library -> gallery -> pager)
                    parentFragmentManager
                        .beginTransaction()
                        .replace(android.R.id.content, ReaderPagerFragment())
                        .addToBackStack(null)
                        .commit()
                } else { // Pager mode (Library -> pager -> gallery -> pager)
                    parentFragmentManager.popBackStack(
                        null,
                        FragmentManager.POP_BACK_STACK_INCLUSIVE
                    ) // Leave only the latest element in the back stack
                }
            } else { // Create/remove chapter
                viewModel.createRemoveChapter(img) {
                    ToastHelper.toast(
                        R.string.chapter_toggle_failed,
                        img.order
                    )
                }
            }
            return true
        }
        return false
    }

    private fun onNestedItemClick(item: INestedItem<*>): Boolean {
        if (item.getLevel() > 0) {
            val img = (item as ImageFileItem).image
            viewModel.setViewerStartingIndex(img.displayOrder)
            if (0 == parentFragmentManager.backStackEntryCount) { // Gallery mode (Library -> gallery -> pager)
                parentFragmentManager
                    .beginTransaction()
                    .replace(android.R.id.content, ReaderPagerFragment())
                    .addToBackStack(null)
                    .commit()
            } else { // Pager mode (Library -> pager -> gallery -> pager)
                parentFragmentManager.popBackStack(
                    null,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE
                ) // Leave only the latest element in the back stack
            }
            return true
        }
        return false
    }

    private fun onFavouriteSuccess() {
        selectExtension.deselect(selectExtension.selections.toMutableSet())
        selectExtension.selectOnLongClick = true
        updateToolbar()
    }

    private fun updateFavouriteDisplay(showFavouritePages: Boolean) {
        showFavouritePagesMenu.setIcon(if (showFavouritePages) R.drawable.ic_filter_favs_on else R.drawable.ic_filter_favs_off)
        updateToolbar()
    }

    /**
     * Returns true if the current book has at least a favourite
     *
     * @return True if the current book has at least a favourite
     */
    private fun hasFavourite(): Boolean {
        val images: List<ImageFileItem> = itemAdapter.adapterItems
        for (item in images) if (item.isFavourite) return true
        return false
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private fun onSelectionChanged2() {
        val selectedCount = selectExtension.selections.size
        binding?.apply {
            if (0 == selectedCount) {
                selectionToolbar.visibility = View.GONE
                toolbar.visibility = View.VISIBLE
                selectExtension.selectOnLongClick = true
            } else {
                updateSelectionToolbar(selectedCount.toLong())
                selectionToolbar.visibility = View.VISIBLE
                toolbar.visibility = View.GONE
            }
        }
    }

    /**
     * Display the yes/no dialog to make sure the user really wants to delete selected items
     *
     * @param items Items to be deleted if the answer is yes
     */
    private fun askDeleteSelected(items: List<ImageFile>) {
        activity.get()?.let {
            val builder = MaterialAlertDialogBuilder(it)
            val title = it.resources.getQuantityString(
                R.plurals.ask_delete_multiple,
                items.size,
                items.size
            )
            builder.setMessage(title)
                .setPositiveButton(R.string.yes)
                { _, _ ->
                    selectExtension.deselect(selectExtension.selections.toMutableSet())
                    viewModel.deletePages(items) { t: Throwable -> onDeleteError(t) }
                }
                .setNegativeButton(R.string.no)
                { _, _ -> selectExtension.deselect(selectExtension.selections.toMutableSet()) }
                .create().show()
        }
    }

    /**
     * Callback for the failure of the "delete item" action
     */
    private fun onDeleteError(t: Throwable) {
        Timber.e(t)
        if (t is ContentNotProcessedException) {
            binding?.apply {
                val message =
                    if (null == t.message) resources.getString(R.string.page_removal_failed) else t.message!!
                Snackbar.make(recyclerView, message, BaseTransientBottomBar.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Move the list to make the currently viewed image visible
     */
    private fun moveToIndex(index: Int, force: Boolean) {
        binding?.apply {
            if (!firstMoveDone || force) {
                if (itemAdapter.adapterItemCount > index) {
                    val llm = recyclerView.layoutManager as LinearLayoutManager?
                    llm?.scrollToPositionWithOffset(index, 0)
                } else recyclerView.scrollToPosition(0)
                firstMoveDone = true
            }
        }
    }

    /**
     * Display the yes/no dialog to make sure the user really wants to set the cover
     *
     * @param item Item that contains the image to set as a cover
     */
    private fun askSetSelectedCover(item: ImageFile) {
        activity.get()?.let {
            val builder = MaterialAlertDialogBuilder(it)
            val title = it.resources.getString(R.string.viewer_ask_cover)
            builder.setMessage(title)
                .setPositiveButton(R.string.yes)
                { _, _ ->
                    selectExtension.deselect(selectExtension.selections.toMutableSet())
                    viewModel.setCover(item)
                }
                .setNegativeButton(R.string.no)
                { _, _ -> selectExtension.deselect(selectExtension.selections.toMutableSet()) }
                .setOnCancelListener {
                    selectExtension.deselect(selectExtension.selections.toMutableSet())
                }
                .create().show()
        }
    }

    /**
     * Switch the screen into the given edit mode
     *
     * @param mode Edit mode to switch the screen to
     */
    private fun setChapterEditMode(mode: EditMode) {
        this.editMode = mode
        updateToolbar()
        binding?.apply {
            chapterEditHelpBanner.visibility =
                if (editMode == EditMode.ADD_CHAPTER) View.VISIBLE else View.GONE
            updateListAdapter(editMode == EditMode.EDIT_CHAPTERS)
        }
        // Don't filter favs when editing chapters
        if (filterFavouritesState) {
            viewModel.filterFavouriteImages(editMode == EditMode.NONE)
        } else viewModel.repostImages()

    }

    /**
     * Strip all chapters from the current content
     */
    private fun stripChapters() {
        viewModel.stripChapters { ToastHelper.toast(R.string.chapters_remove_failed) }
    }

    override fun itemTouchDropped(oldPosition: Int, newPosition: Int) {
        if (oldPosition == newPosition) return
        if (oldPosition < 0 || newPosition < 0) return
        if (expandableItemAdapter.getAdapterItem(oldPosition).getLevel() > 0) return
        val nbLevelZeroItems =
            expandableItemAdapter.adapterItems.count { i: INestedItem<SubExpandableItem.ViewHolder> -> 0 == i.getLevel() }
        if (newPosition > nbLevelZeroItems - 1) return

        isReorderingChapters = true
        updateToolbar()
    }

    override fun itemTouchOnMove(oldPosition: Int, newPosition: Int): Boolean {
        if (oldPosition < 0 || newPosition < 0) return false
        if (expandableItemAdapter.getAdapterItem(oldPosition).getLevel() > 0) return false
        val nbLevelZeroItems =
            expandableItemAdapter.adapterItems.count { i: INestedItem<SubExpandableItem.ViewHolder> -> 0 == i.getLevel() }
        if (newPosition > nbLevelZeroItems - 1) return false

        // Update visuals
        onMove(expandableItemAdapter, oldPosition, newPosition)
        return true
    }

    override fun itemTouchStartDrag(viewHolder: RecyclerView.ViewHolder) {
        // Nothing
    }

    override fun itemTouchStopDrag(viewHolder: RecyclerView.ViewHolder) {
        // Nothing
    }

    private fun onConfirmChapterReordering() {
        // Save final position of items in DB
        viewModel.saveChapterPositions(
            expandableItemAdapter.adapterItems.mapNotNull { c -> c.tag }.map { ch -> ch as Chapter }
        )
        ProgressDialogFragment.invoke(
            parentFragmentManager,
            resources.getString(R.string.renaming_progress), R.plurals.file
        )
        isReorderingChapters = false
        updateToolbar()
    }

    private fun onCancelChapterReordering() {
        viewModel.repostImages()
        isReorderingChapters = false
        updateToolbar()
    }
}