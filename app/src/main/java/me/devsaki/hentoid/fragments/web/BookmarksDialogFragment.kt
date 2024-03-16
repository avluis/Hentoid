package me.devsaki.hentoid.fragments.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.drag.SimpleDragCallback
import com.mikepenz.fastadapter.select.SelectExtension
import com.mikepenz.fastadapter.utils.DragDropUtil.onMove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.SiteBookmark
import me.devsaki.hentoid.databinding.DialogWebBookmarksBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.fragments.SelectSiteDialogFragment
import me.devsaki.hentoid.ui.InputDialog.invokeInputDialog
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.viewholders.IDraggableViewHolder
import me.devsaki.hentoid.viewholders.TextItem
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper

class BookmarksDialogFragment : DialogFragment(), ItemTouchCallback,
    SelectSiteDialogFragment.Parent {

    companion object {
        private const val KEY_SITE = "site"
        private const val KEY_TITLE = "title"
        private const val KEY_URL = "url"

        fun invoke(
            parent: FragmentActivity,
            site: Site,
            title: String,
            url: String
        ) {
            val fragment = BookmarksDialogFragment()
            val args = Bundle()
            args.putInt(KEY_SITE, site.code)
            args.putString(KEY_TITLE, title)
            args.putString(KEY_URL, url)
            fragment.arguments = args
            fragment.show(parent.supportFragmentManager, null)
        }
    }

    // === UI
    private var binding: DialogWebBookmarksBinding? = null
    private var editMenu: MenuItem? = null
    private var copyMenu: MenuItem? = null
    private var homeMenu: MenuItem? = null

    private val itemAdapter = ItemAdapter<TextItem<SiteBookmark>>()
    private val fastAdapter = FastAdapter.with(itemAdapter)
    private lateinit var selectExtension: SelectExtension<TextItem<SiteBookmark>>
    private lateinit var touchHelper: ItemTouchHelper

    // === VARIABLES
    private var parent: Parent? = null
    private lateinit var initialSite: Site
    private lateinit var site: Site
    private lateinit var title: String
    private lateinit var url: String

    // Bookmark ID of the current webpage
    private var bookmarkId: Long = -1

    // Used to ignore native calls to onBookClick right after that book has been deselected
    private var invalidateNextBookClick = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkNotNull(arguments) { "No arguments found" }

        arguments?.apply {
            site = Site.searchByCode(getInt(KEY_SITE).toLong())
            initialSite = site
            title = getString(KEY_TITLE, "")
            url = getString(KEY_URL, "")
        }
        parent = activity as Parent
    }

    override fun onDestroy() {
        parent = null

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao: CollectionDAO = ObjectBoxDAO(HentoidApp.getInstance())
                try {
                    Helper.updateBookmarksJson(HentoidApp.getInstance(), dao)
                } finally {
                    dao.cleanup()
                }
            }
        }

        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogWebBookmarksBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.getOrCreateExtension(SelectExtension::class.java)!!

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

        val bookmarks = reloadBookmarks()
        val currentBookmark =
            bookmarks.firstOrNull { b -> SiteBookmark.urlsAreSame(b.url, url) }
        if (currentBookmark != null && currentBookmark.id > 0) bookmarkId = currentBookmark.id
        updateBookmarkButton()

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
            toolbar.setOnMenuItemClickListener { menuItem ->
                toolbarOnItemClicked(menuItem)
            }
            toolbar.menu.findItem(R.id.action_home).icon =
                ContextCompat.getDrawable(requireContext(), site.ico)

            // Selection toolbar
            selectionToolbar.setOnMenuItemClickListener { menuItem ->
                selectionToolbarOnItemClicked(menuItem)
            }
            editMenu = selectionToolbar.menu.findItem(R.id.action_edit)
            copyMenu = selectionToolbar.menu.findItem(R.id.action_copy)
            homeMenu = selectionToolbar.menu.findItem(R.id.action_home)
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun reloadBookmarks(): List<SiteBookmark> {
        val bookmarks: List<SiteBookmark>
        val dao: CollectionDAO = ObjectBoxDAO(requireContext())
        bookmarks = try {
            reloadBookmarks(dao)
        } finally {
            dao.cleanup()
        }
        return bookmarks
    }

    private fun reloadBookmarks(dao: CollectionDAO): List<SiteBookmark> {
        val bookmarks = dao.selectBookmarks(site).toMutableList()
        // Add site home as 1st bookmark
        bookmarks.add(0, SiteBookmark(site, getString(R.string.bookmark_homepage), site.url))
        itemAdapter.set(bookmarks.mapIndexed { index, b ->
            TextItem(
                b.title,
                b,
                draggable = index > 0,
                reformatCase = true,
                isHighlighted = b.isHomepage,
                centered = false,
                touchHelper = touchHelper,
                index > 0
            )
        })
        return bookmarks
    }

    private fun getUnbookmarkedSites(): List<Site> {
        val dao: CollectionDAO = ObjectBoxDAO(requireContext())
        try {
            val bookmarkedSites = dao.selectAllBookmarks().groupBy { it.site }.keys
            return Site.entries.filterNot { bookmarkedSites.contains(it) }
        } finally {
            dao.cleanup()
        }
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private fun onSelectionChanged() {
        val selectedCount = selectExtension.selectedItems.size
        binding?.apply {
            if (0 == selectedCount) {
                selectionToolbar.visibility = View.GONE
                selectExtension.selectOnLongClick = true
                invalidateNextBookClick = true
                Handler(Looper.getMainLooper()).postDelayed({
                    invalidateNextBookClick = false
                }, 200)
            } else {
                editMenu?.isVisible = 1 == selectedCount
                copyMenu?.isVisible = 1 == selectedCount
                homeMenu?.isVisible = 1 == selectedCount
                selectionToolbar.visibility = View.VISIBLE
            }
        }
    }

    private fun updateBookmarkButton() {
        binding?.bookmarkCurrentBtn?.apply {
            if (bookmarkId > -1) {
                icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_bookmark_full
                )
                setText(R.string.unbookmark_current)
                setOnClickListener { onBookmarkBtnClickedRemove() }
                this@BookmarksDialogFragment.parent?.updateBookmarkButton(true)
            } else {
                icon = ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_bookmark
                )
                setText(R.string.bookmark_current)
                setOnClickListener { onBookmarkBtnClickedAdd() }
                this@BookmarksDialogFragment.parent?.updateBookmarkButton(false)
            }
        }
    }

    private fun onBookmarkBtnClickedAdd() {
        invokeInputDialog(requireContext(), R.string.bookmark_edit_title, {
            val dao: CollectionDAO = ObjectBoxDAO(requireContext())
            try {
                bookmarkId = dao.insertBookmark(SiteBookmark(site, it, url))
                reloadBookmarks(dao)
                fastAdapter.notifyAdapterDataSetChanged()
            } finally {
                dao.cleanup()
            }
            updateBookmarkButton()
        }, title)
    }

    private fun onBookmarkBtnClickedRemove() {
        val dao: CollectionDAO = ObjectBoxDAO(requireContext())
        try {
            dao.deleteBookmark(bookmarkId)
            bookmarkId = -1
            reloadBookmarks(dao)
            fastAdapter.notifyAdapterDataSetChanged()
        } finally {
            dao.cleanup()
        }
        updateBookmarkButton()
    }

    override fun onSiteSelected(site: Site, altCode: Int) {
        this.site = site

        val bookmarks = reloadBookmarks()
        val currentBookmark =
            bookmarks.firstOrNull { b -> SiteBookmark.urlsAreSame(b.url, url) }
        if (currentBookmark != null && currentBookmark.id > 0) bookmarkId = currentBookmark.id
        updateBookmarkButton()

        binding?.apply {
            toolbar.menu.findItem(R.id.action_home).icon =
                ContextCompat.getDrawable(requireContext(), site.ico)
        }
    }

    @SuppressLint("NonConstantResourceId")
    private fun toolbarOnItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_home -> {
                SelectSiteDialogFragment.invoke(
                    childFragmentManager,
                    getString(R.string.bookmark_change_site),
                    getUnbookmarkedSites().map { it.code },
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

    /**
     * Callback for the "share item" action button
     */
    private fun copySelectedItem() {
        val selectedItems: Set<TextItem<SiteBookmark>> = selectExtension.selectedItems
        val context: Context? = activity
        if (1 == selectedItems.size && context != null) {
            val b = selectedItems.first().getObject()
            if (b != null && Helper.copyPlainTextToClipboard(context, b.url)) {
                ToastHelper.toastShort(context, R.string.web_url_clipboard)
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
            val b = selectedItems.first().getObject()
            if (b != null) {
                b.title = newTitle
                val dao: CollectionDAO = ObjectBoxDAO(context)
                try {
                    dao.insertBookmark(b)
                    reloadBookmarks(dao)
                    fastAdapter.notifyAdapterDataSetChanged()
                    binding?.selectionToolbar?.visibility = View.INVISIBLE
                } finally {
                    dao.cleanup()
                }
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
            val selectedContent =
                selectedItems.mapNotNull { obj -> obj.getObject() }
            if (selectedContent.isNotEmpty()) {
                val dao: CollectionDAO = ObjectBoxDAO(context)
                try {
                    for (b in selectedContent) {
                        if (b.id == bookmarkId) {
                            bookmarkId = -1
                            updateBookmarkButton()
                        }
                        dao.deleteBookmark(b.id)
                    }
                    reloadBookmarks(dao)
                    fastAdapter.notifyAdapterDataSetChanged()
                    binding?.selectionToolbar?.visibility = View.INVISIBLE
                } finally {
                    dao.cleanup()
                }
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
            val selectedContent =
                selectedItems.mapNotNull { obj -> obj.getObject() }
            if (selectedContent.isNotEmpty()) {
                val selectedBookmark = selectedContent[0]
                val dao: CollectionDAO = ObjectBoxDAO(context)
                try {
                    val bookmarks = dao.selectBookmarks(site)
                    for (b in bookmarks) {
                        if (b.id == selectedBookmark.id) b.isHomepage =
                            !b.isHomepage else b.isHomepage =
                            false
                    }
                    dao.insertBookmarks(bookmarks)
                    reloadBookmarks(dao)
                    fastAdapter.notifyAdapterDataSetChanged()
                    selectExtension.selectOnLongClick = true
                    selectExtension.deselect(selectExtension.selections.toMutableSet())
                    binding?.selectionToolbar?.visibility = View.INVISIBLE
                } finally {
                    dao.cleanup()
                }
            }
        }
    }

    private fun onItemClick(item: TextItem<SiteBookmark>): Boolean {
        if (selectExtension.selectedItems.isEmpty()) {
            if (!invalidateNextBookClick && item.getObject() != null) {
                val url = item.getObject()!!.url
                if (site == initialSite) parent?.loadUrl(url)
                else ContentHelper.launchBrowserFor(requireActivity(), url)
                dismiss()
            } else invalidateNextBookClick = false
            return true
        } else {
            selectExtension.selectOnLongClick = false
        }
        return false
    }

    override fun itemTouchDropped(oldPosition: Int, newPosition: Int) {
        // Update  visuals
        binding?.apply {
            val vh = recyclerview.findViewHolderForAdapterPosition(newPosition)
            if (vh is IDraggableViewHolder) {
                (vh as IDraggableViewHolder).onDropped()
            }
        }

        // Update DB
        if (oldPosition == newPosition) return
        val dao: CollectionDAO = ObjectBoxDAO(requireContext())
        try {
            val bookmarks = dao.selectBookmarks(site)
            if (oldPosition < 0 || oldPosition >= bookmarks.size) return

            // Move the item
            val fromValue = bookmarks[oldPosition]
            val delta = if (oldPosition < newPosition) 1 else -1
            var i = oldPosition
            while (i != newPosition) {
                bookmarks[i] = bookmarks[i + delta]
                i += delta
            }
            bookmarks[newPosition] = fromValue

            // Renumber everything and update the DB
            var index = 1
            for (b in bookmarks) {
                b.order = index++
                dao.insertBookmark(b)
            }
        } finally {
            dao.cleanup()
        }
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

    interface Parent {
        fun loadUrl(url: String)
        fun updateBookmarkButton(newValue: Boolean)
    }
}