package me.devsaki.hentoid.fragments.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.drag.SimpleDragCallback
import com.mikepenz.fastadapter.select.getSelectExtension
import com.mikepenz.fastadapter.utils.DragDropUtil.onMove
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.OnMenuItemClickListener
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.SiteBookmark
import me.devsaki.hentoid.database.domains.urlsAreSame
import me.devsaki.hentoid.databinding.FragmentWebBookmarksBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.fragments.SelectSiteDialogFragment
import me.devsaki.hentoid.ui.invokeInputDialog
import me.devsaki.hentoid.util.copyPlainTextToClipboard
import me.devsaki.hentoid.util.dimensAsDp
import me.devsaki.hentoid.util.launchBrowserFor
import me.devsaki.hentoid.util.toastShort
import me.devsaki.hentoid.viewholders.IDraggableViewHolder
import me.devsaki.hentoid.viewholders.TextItem
import me.devsaki.hentoid.viewmodels.BrowserViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

private const val HOME_UNICODE = "\uD83C\uDFE0"

class BookmarksDrawerFragment : Fragment(R.layout.fragment_web_bookmarks),
    ItemTouchCallback,
    SelectSiteDialogFragment.Parent,
    BookmarksImportDialogFragment.Parent {

    // == COMMUNICATION
    // Viewmodel
    private lateinit var viewModel: BrowserViewModel

    // === UI
    private var binding: FragmentWebBookmarksBinding? = null
    private var editMenu: MenuItem? = null
    private var copyMenu: MenuItem? = null
    private var homeMenu: MenuItem? = null

    private val itemAdapter = ItemAdapter<TextItem<SiteBookmark>>()
    private val fastAdapter = FastAdapter.with(itemAdapter)
    private var selectExtension = fastAdapter.getSelectExtension()
    private lateinit var touchHelper: ItemTouchHelper

    // === VARIABLES
    private var parent: Parent? = null

    private lateinit var browserSite: Site
    private lateinit var site: Site
    private lateinit var title: String
    private lateinit var url: String
    private lateinit var bookmarkedSites: List<Site>

    // Bookmark ID of the current webpage
    private var bookmarkId: Long = -1

    // Used to ignore native calls to onBookClick right after that book has been deselected
    private var invalidateNextBookClick = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        parent = null
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentWebBookmarksBinding.inflate(inflater, container, false)

        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelFactory(requireActivity().application)
        )[BrowserViewModel::class.java]

        viewModel.getBrowserSite().observe(viewLifecycleOwner) { onSiteChanged(browserSite = it) }
        viewModel.getBookmarksSite().observe(viewLifecycleOwner) { onSiteChanged(site = it) }
        viewModel.pageUrl().observe(viewLifecycleOwner) { url = it }
        viewModel.pageTitle().observe(viewLifecycleOwner) { title = it }
        viewModel.bookmarks().observe(viewLifecycleOwner) {
            onBookmarksChanged(it)
            updateBookmarkButton(it)
        }
        viewModel.bookmarkedSites().observe(viewLifecycleOwner) { bookmarkedSites = it }

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        parent = activity as Parent?

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension.isSelectable = true
        selectExtension.multiSelect = true
        selectExtension.selectOnLongClick = true
        selectExtension.selectWithItemUpdate = true
        selectExtension.selectionListener =
            object : ISelectionListener<TextItem<SiteBookmark>> {
                override fun onSelectionChanged(
                    item: TextItem<SiteBookmark>,
                    selected: Boolean
                ) {
                    onSelectionChanged()
                }
            }
        val helper = FastAdapterPreClickSelectHelper(
            fastAdapter, selectExtension
        )
        fastAdapter.onPreClickListener =
            { _, _, _, position: Int -> helper.onPreClickListener(position) }
        fastAdapter.onPreLongClickListener =
            { _, _, _, position: Int -> helper.onPreLongClickListener(position) }


        // Activate drag & drop
        val dragCallback = SimpleDragCallback(this)
        dragCallback.notifyAllDrops = true
        touchHelper = ItemTouchHelper(dragCallback)

        viewModel.reloadBookmarks()

        binding?.apply {
            touchHelper.attachToRecyclerView(recyclerview)
            recyclerview.adapter = fastAdapter
            fastAdapter.onClickListener =
                { _, _, i: TextItem<SiteBookmark>, _ -> onItemClick(i) }

            fastAdapter.addEventHook(
                TextItem.DragHandlerTouchEvent { position ->
                    val vh =
                        recyclerview.findViewHolderForAdapterPosition(position)
                    if (vh != null) touchHelper.startDrag(vh)
                }
            )

            // Top toolbar
            toolbar.setOnMenuItemClickListener { toolbarOnItemClicked(it) }

            // Selection toolbar
            selectionToolbar.setOnMenuItemClickListener { menuItem ->
                selectionToolbarOnItemClicked(menuItem)
            }
            editMenu = selectionToolbar.menu.findItem(R.id.action_edit)
            copyMenu = selectionToolbar.menu.findItem(R.id.action_copy)
            homeMenu = selectionToolbar.menu.findItem(R.id.action_home)
        }
    }

    private fun onSiteChanged(site: Site? = null, browserSite: Site? = null) {
        if (site != null) this.site = site
        if (browserSite != null) {
            this.site = browserSite
            this.browserSite = browserSite
        }
        binding?.apply {
            toolbar.menu.findItem(R.id.action_home).icon =
                ContextCompat.getDrawable(requireContext(), this@BookmarksDrawerFragment.site.ico)
        }
    }

    private fun onBookmarksChanged(bookmarks: List<SiteBookmark>) {
        // Add site home as 1st bookmark
        val siteHome =
            SiteBookmark(site = site, title = getString(R.string.bookmark_homepage), url = site.url)
        // Mark as homepage if no custom homepage has been set
        if (!bookmarks.any { it.isHomepage }) siteHome.isHomepage = true
        val bookmarksWithHome = bookmarks.toMutableList()
        bookmarksWithHome.add(0, siteHome)

        // Convert to items
        val items = bookmarksWithHome.mapIndexed { index, b ->
            val prefix = if (b.isHomepage) "$HOME_UNICODE " else ""
            TextItem(
                "$prefix${b.title}",
                b,
                draggable = index > 0,
                reformatCase = true,
                isHighlighted = b.isHomepage,
                centered = false,
                touchHelper = touchHelper,
                index > 0
            )
        }
        itemAdapter.set(items)
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private fun onSelectionChanged() {
        val selectedCount = selectExtension.selectedItems.size
        binding?.apply {
            if (0 == selectedCount) {
                selectionToolbar.isVisible = false
                bookmarkCurrentBtn.isVisible = true
                selectExtension.selectOnLongClick = true
                invalidateNextBookClick = true
                Handler(Looper.getMainLooper()).postDelayed({
                    invalidateNextBookClick = false
                }, 200)
            } else {
                editMenu?.isVisible = 1 == selectedCount
                copyMenu?.isVisible = 1 == selectedCount
                homeMenu?.isVisible = 1 == selectedCount
                bookmarkCurrentBtn.isVisible = false
                selectionToolbar.isVisible = true
            }
        }
    }

    private fun updateBookmarkButton(bookmarks: List<SiteBookmark>) {
        val currentBookmark = bookmarks.firstOrNull { urlsAreSame(it.url, url) }
        bookmarkId =
            if (currentBookmark != null && currentBookmark.id > 0) currentBookmark.id else -1

        binding?.bookmarkCurrentBtn?.apply {
            if (bookmarkId > -1) {
                icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_bookmark_full
                )
                setText(R.string.unbookmark_current)
                setOnClickListener { onBookmarkBtnClickedRemove() }
                this@BookmarksDrawerFragment.parent?.updateBookmarkButton(true)
            } else {
                icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_bookmark
                )
                setText(R.string.bookmark_current)
                setOnClickListener { onBookmarkBtnClickedAdd() }
                this@BookmarksDrawerFragment.parent?.updateBookmarkButton(false)
            }
        }
    }

    private fun onBookmarkBtnClickedAdd() {
        invokeInputDialog(requireContext(), R.string.bookmark_edit_title, {
            viewModel.addBookmark(it)
        }, title)
    }

    private fun onBookmarkBtnClickedRemove() {
        viewModel.deleteBookmark(bookmarkId)
        bookmarkId = -1
    }

    override fun onSiteSelected(site: Site, altCode: Int) {
        viewModel.setBookmarksSite(site)
        viewModel.reloadBookmarks()
    }

    @SuppressLint("NonConstantResourceId")
    private fun toolbarOnItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_sort -> {
                val powerMenu = PowerMenu.Builder(requireContext())
                    .addItem(
                        PowerMenuItem(
                            resources.getString(R.string.sort_ascending),
                            iconRes = R.drawable.ic_simple_arrow_up,
                            tag = 0
                        )
                    )
                    .addItem(
                        PowerMenuItem(
                            resources.getString(R.string.sort_descending),
                            iconRes = R.drawable.ic_simple_arrow_down,
                            tag = 1
                        )
                    )
                    .setAnimation(MenuAnimation.SHOWUP_TOP_LEFT)
                    .setMenuRadius(10f)
                    .setLifecycleOwner(viewLifecycleOwner)
                    .setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.white_opacity_87)
                    )
                    .setTextTypeface(Typeface.DEFAULT)
                    .setMenuColor(ContextCompat.getColor(requireContext(), R.color.dark_gray))
                    .setTextSize(dimensAsDp(requireContext(), R.dimen.text_subtitle_1))
                    .setWidth(resources.getDimension(R.dimen.popup_menu_width).toInt())
                    .setAutoDismiss(true)
                    .build()
                powerMenu.onMenuItemClickListener = OnMenuItemClickListener { _, item ->
                    when (item.tag) {
                        0 -> askReloadBookmarks(true)
                        1 -> askReloadBookmarks(false)
                        else -> {} // Nothing
                    }
                    powerMenu.dismiss()
                }
                powerMenu.setIconColor(
                    ContextCompat.getColor(requireContext(), R.color.white_opacity_87)
                )
                binding?.apply {
                    powerMenu.showAsAnchorRightBottom(toolbar)
                }
            }

            R.id.action_import -> {
                BookmarksImportDialogFragment.invoke(this, site)
            }

            R.id.action_home -> {
                SelectSiteDialogFragment.invoke(
                    this,
                    getString(R.string.bookmark_change_site),
                    bookmarkedSites.map { it.code },
                    showAltSites = false
                )
            }
        }
        return true
    }

    @SuppressLint("NonConstantResourceId")
    private fun selectionToolbarOnItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_copy -> copySelectedItem()
            R.id.action_edit -> editSelectedItem()
            R.id.action_delete -> purgeSelectedItems()
            R.id.action_home -> toggleHomeSelectedItem()
            else -> {
                binding?.selectionToolbar?.visibility = View.GONE
                return false
            }
        }
        return true
    }

    private fun askReloadBookmarks(sortAsc: Boolean) {
        val title = resources.getString(R.string.bookmark_sort_ask)
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setMessage(title).setPositiveButton(R.string.yes) { _, _ ->
            viewModel.reloadBookmarks(sortAsc)
        }.setNegativeButton(R.string.no) { _, _ ->
            // Do nothing
        }.create().show()
    }

    /**
     * Callback for the "share item" action button
     */
    private fun copySelectedItem() {
        val selectedItems: Set<TextItem<SiteBookmark>> = selectExtension.selectedItems
        val context: Context? = activity
        if (1 == selectedItems.size && context != null) {
            val b = selectedItems.first().getObject()
            if (b != null && copyPlainTextToClipboard(context, b.url)) {
                toastShort(R.string.web_url_clipboard)
                binding?.selectionToolbar?.visibility = View.INVISIBLE
            }
        }
    }

    /**
     * Callback for the "share item" action button
     */
    private fun editSelectedItem() {
        val selectedItems: Set<TextItem<SiteBookmark>> = selectExtension.selectedItems
        if (1 == selectedItems.size) {
            val b = selectedItems.first().getObject()
            if (b != null) invokeInputDialog(
                requireActivity(),
                R.string.bookmark_edit_title,
                { s -> onEditTitle(s) },
                b.title
            ) { selectExtension.deselect(selectExtension.selections.toMutableSet()) }
        }
    }

    private fun onEditTitle(newTitle: String) {
        val selectedItems: Set<TextItem<SiteBookmark>> = selectExtension.selectedItems
        val context: Context? = activity
        if (1 == selectedItems.size && context != null) {
            selectedItems.first().getObject()?.let { b ->
                b.title = newTitle
                viewModel.updateBookmark(b)
                binding?.selectionToolbar?.visibility = View.INVISIBLE
            }
        }
    }

    /**
     * Callback for the "delete item" action button
     */
    private fun purgeSelectedItems() {
        val selectedItems: Set<TextItem<SiteBookmark>> = selectExtension.selectedItems
        val context: Context? = activity
        if (selectedItems.isNotEmpty() && context != null) {
            val selectedContent = selectedItems.mapNotNull { it.getObject() }
            if (selectedContent.isNotEmpty()) {
                viewModel.deleteBookmarks(selectedContent.map { it.id })
                binding?.selectionToolbar?.visibility = View.INVISIBLE
            }
        }
    }

    /**
     * Callback for the "toggle as welcome page" action button
     */
    private fun toggleHomeSelectedItem() {
        val selectedItems: Set<TextItem<SiteBookmark>> = selectExtension.selectedItems
        val context: Context? = activity
        if (1 == selectedItems.size && context != null) {
            val selectedContent = selectedItems.mapNotNull { it.getObject() }
            if (selectedContent.isNotEmpty()) {
                viewModel.setBookmarkAsHome(selectedContent[0].id)
                selectExtension.selectOnLongClick = true
                selectExtension.deselect(selectExtension.selections.toMutableSet())
                binding?.selectionToolbar?.visibility = View.INVISIBLE
            }
        }
    }

    private fun onItemClick(item: TextItem<SiteBookmark>): Boolean {
        if (selectExtension.selectedItems.isEmpty()) {
            if (!invalidateNextBookClick && item.getObject() != null) {
                val url = item.getObject()!!.url
                if (site == browserSite) parent?.loadUrl(url)
                else launchBrowserFor(requireActivity(), url)
                EventBus.getDefault().post(CommunicationEvent(CommunicationEvent.Type.CLOSE_DRAWER))
            } else invalidateNextBookClick = false
            return true
        } else {
            selectExtension.selectOnLongClick = false
        }
        return false
    }

    override fun itemTouchDropped(oldPosition: Int, newPosition: Int) {
        // Update visuals
        binding?.apply {
            val vh = recyclerview.findViewHolderForAdapterPosition(newPosition)
            if (vh is IDraggableViewHolder) {
                (vh as IDraggableViewHolder).onDropped()
            }
        }

        // Update DB
        if (oldPosition == newPosition) return
        viewModel.moveBookmark(oldPosition, newPosition)
    }

    override fun itemTouchOnMove(oldPosition: Int, newPosition: Int): Boolean {
        // Update visuals
        onMove(itemAdapter, oldPosition, newPosition) // change position
        return true
    }

    override fun itemTouchStartDrag(viewHolder: RecyclerView.ViewHolder) {
        // Update visuals
        if (viewHolder is IDraggableViewHolder) {
            (viewHolder as IDraggableViewHolder).onDragged()
        }
    }

    override fun itemTouchStopDrag(viewHolder: RecyclerView.ViewHolder) {
        // Nothing
    }

    override fun onLoaded() {
        viewModel.reloadBookmarks()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onCommunicationEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.Recipient.DRAWER) return
        if (CommunicationEvent.Type.CLOSED == event.type) viewModel.updateBookmarksJson()
    }

    interface Parent {
        fun loadUrl(url: String)
        fun updateBookmarkButton(newValue: Boolean)
    }
}