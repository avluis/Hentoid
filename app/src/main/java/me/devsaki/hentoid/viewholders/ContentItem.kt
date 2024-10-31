package me.devsaki.hentoid.viewholders

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.drag.IExtendedDraggable
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.listeners.TouchEventHook
import com.mikepenz.fastadapter.swipe.IDrawerSwipeableViewHolder
import com.mikepenz.fastadapter.swipe.ISwipeable
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils.adjustAlpha
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils.getSelectablePressedBackground
import com.mikepenz.fastadapter.utils.DragDropUtil.bindDragHandle
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.ContentItemBundle
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.core.requireById
import me.devsaki.hentoid.core.setMiddleEllipsis
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.database.domains.QueueRecord
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.ui.BlinkAnimation
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.download.ContentQueueManager.isQueueActive
import me.devsaki.hentoid.util.download.ContentQueueManager.isQueuePaused
import me.devsaki.hentoid.util.formatArtistForDisplay
import me.devsaki.hentoid.util.formatSeriesForDisplay
import me.devsaki.hentoid.util.formatTagsForDisplay
import me.devsaki.hentoid.util.generateIdForPlaceholder
import me.devsaki.hentoid.util.getFlagResourceId
import me.devsaki.hentoid.util.getRatingResourceId
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.image.loadCover
import me.devsaki.hentoid.util.image.loadStill
import me.devsaki.hentoid.util.isInQueue
import timber.log.Timber
import java.util.Locale

class ContentItem : AbstractItem<ContentItem.ViewHolder>,
    IExtendedDraggable<ContentItem.ViewHolder>, ISwipeable {

    enum class ViewType {
        LIBRARY, LIBRARY_GRID, LIBRARY_EDIT, QUEUE, ERRORS, MERGE, SPLIT
    }

    val content: Content?
    val queueRecord: QueueRecord?
    val chapter: Chapter?

    private val viewType: ViewType
    private val showDragHandle: Boolean
    private val isEmpty: Boolean
    private val isFirst: Boolean
    private var deleteAction: Consumer<ContentItem>? = null

    // Drag, drop & swipe
    override val touchHelper: ItemTouchHelper?
    override val isSwipeable: Boolean

    // Constructor for empty placeholder
    constructor(viewType: ViewType) {
        content = null
        queueRecord = null
        chapter = null
        showDragHandle = false
        isFirst = false
        this.viewType = viewType
        touchHelper = null
        isEmpty = true
        isSwipeable = true
        identifier = generateIdForPlaceholder()
    }

    // Constructor for library and error item
    constructor(
        content: Content,
        touchHelper: ItemTouchHelper?,
        viewType: ViewType,
        deleteAction: Consumer<ContentItem>? = null
    ) {
        this.content = content
        queueRecord = null
        chapter = null
        showDragHandle = (viewType == ViewType.MERGE || viewType == ViewType.LIBRARY_EDIT)
        isFirst = false
        isEmpty = false
        this.viewType = viewType
        this.touchHelper = touchHelper
        this.deleteAction = deleteAction
        isSwipeable =
            !content.isBeingProcessed && (content.status != StatusContent.EXTERNAL || Settings.isDeleteExternalLibrary) && viewType != ViewType.MERGE
        identifier = content.uniqueHash()
    }

    // Constructor for queued item
    constructor(
        record: QueueRecord,
        isSearchActive: Boolean,
        touchHelper: ItemTouchHelper?,
        isFirst: Boolean,
        deleteAction: Consumer<ContentItem>?
    ) {
        content = record.content.target
        queueRecord = record
        chapter = null
        viewType = ViewType.QUEUE
        this.showDragHandle = !isSearchActive
        this.touchHelper = touchHelper
        this.deleteAction = deleteAction
        this.isFirst = isFirst
        isEmpty = null == content
        isSwipeable = true
        identifier = content?.uniqueHash() ?: generateIdForPlaceholder()
    }

    // Constructor for split
    constructor(chap: Chapter) {
        chapter = chap
        content = null
        queueRecord = null
        viewType = ViewType.SPLIT
        touchHelper = null
        showDragHandle = false
        isFirst = false
        isSwipeable = false
        isEmpty = false
        identifier = chapter.uniqueHash()
    }

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v, viewType)
    }

    override val layoutRes: Int
        get() = when (viewType) {
            ViewType.LIBRARY -> R.layout.item_library_content
            ViewType.LIBRARY_GRID -> R.layout.item_library_content_grid
            ViewType.MERGE -> R.layout.item_library_merge_split
            ViewType.SPLIT -> R.layout.item_library_merge_split
            else -> R.layout.item_queue
        }

    override val type: Int
        get() = R.id.content

    override val isDraggable: Boolean
        get() = ViewType.QUEUE == viewType || ViewType.LIBRARY_EDIT == viewType || ViewType.MERGE == viewType

    override fun isDirectionSupported(direction: Int): Boolean {
        return ItemTouchHelper.LEFT == direction
    }

    override fun getDragView(viewHolder: ViewHolder): View? {
        return viewHolder.ivReorder
    }

    val title: String
        get() = content?.title ?: (queueRecord?.content?.target?.title ?: (chapter?.name ?: ""))


    /**
     * Update download progress (queue items only)
     */
    fun updateProgress(vh: RecyclerView.ViewHolder, isPausedEvent: Boolean, isIndividual: Boolean) {
        content ?: return
        val pb = vh.itemView.findViewById<ProgressBar>(R.id.pbDownload) ?: return
        if (!isInQueue(content.status)) {
            pb.visibility = View.GONE
            return
        }

        val isQueueReady = isQueueActive(vh.itemView.context) && !isQueuePaused && !isPausedEvent
        content.computeProgress()
        content.computeDownloadedBytes()
        if (isQueueReady || content.getPercent() > 0) {
            val tvPages = vh.itemView.findViewById<TextView>(R.id.tvPages)
            if (content.getPercent() > 0) {
                pb.isIndeterminate = false
                pb.max = 100
                pb.progress = (content.getPercent() * 100).toInt()
                pb.visibility = View.VISIBLE

                val color: Int = if (isQueueReady && isIndividual) pb.context.getThemedColor(
                    R.color.secondary_light
                ) else ContextCompat.getColor(pb.context, R.color.medium_gray)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    pb.progressDrawable.colorFilter = BlendModeColorFilter(color, BlendMode.SRC_IN)
                } else {
                    @Suppress("DEPRECATION") pb.progressDrawable.setColorFilter(
                        color,
                        PorterDuff.Mode.SRC_IN
                    )
                }

                if (content.getBookSizeEstimate() > 0 && tvPages != null && View.VISIBLE == tvPages.visibility) {
                    var pagesText = tvPages.text.toString()
                    val separator = pagesText.indexOf(";")
                    if (separator > -1) pagesText = pagesText.substring(0, separator)
                    pagesText = "$pagesText; " + pb.context.resources.getString(
                        R.string.queue_content_size_estimate,
                        content.getBookSizeEstimate() / (1024 * 1024)
                    )
                    tvPages.text = pagesText
                }
            } else if (isQueueReady && isFirst) {
                pb.visibility = if (isIndividual) View.VISIBLE else View.GONE
                pb.isIndeterminate = true
            } else pb.visibility = View.GONE
        } else {
            pb.visibility = View.GONE
        }
    }

    class ViewHolder internal constructor(view: View, viewType: ViewType) :
        FastAdapter.ViewHolder<ContentItem>(view), IDraggableViewHolder, IDrawerSwipeableViewHolder,
        ISwipeableViewHolder {
        // Common elements
        private val baseLayout: View = view.requireById(R.id.item)
        private val tvTitle: TextView = view.requireById(R.id.tvTitle)
        private val ivCover: ImageView = view.requireById(R.id.ivCover)
        private val ivFlag: ImageView? = view.findViewById(R.id.ivFlag)
        private val ivSite: ImageView? = view.findViewById(R.id.queue_site_button)
        private val tvArtist: TextView? = view.findViewById(R.id.tvArtist)
        private val ivPages: ImageView? = view.findViewById(R.id.ivPages)
        private val tvPages: TextView? = view.findViewById(R.id.tvPages)
        private val ivError: ImageView? = view.findViewById(R.id.ivError)
        private val ivOnline: ImageView? = view.findViewById(R.id.ivOnline)
        override val swipeableView: View = view.findViewById(R.id.item_card) ?: ivCover
        private val deleteButton: View? = view.findViewById(R.id.delete_btn)

        // Specific to library content
        private var ivNew: View? = view.findViewById(R.id.lineNew)
        private var tvTags: TextView? = view.findViewById(R.id.tvTags)
        private var tvSeries: TextView? = view.findViewById(R.id.tvSeries)
        private var ivFavourite: ImageView? = view.findViewById(R.id.ivFavourite)
        private var ivRating: ImageView? = view.findViewById(R.id.iv_rating)
        private var ivExternal: ImageView? = view.findViewById(R.id.ivExternal)
        private var readingProgress: CircularProgressIndicator? =
            view.findViewById(R.id.reading_progress)
        private var ivCompleted: ImageView? = view.findViewById(R.id.ivCompleted)
        private var ivChapters: ImageView? = view.findViewById(R.id.ivChapters)
        private var tvChapters: TextView? = view.findViewById(R.id.tvChapters)
        private var ivStorage: ImageView? = view.findViewById(R.id.ivStorage)
        private var tvStorage: TextView? = view.findViewById(R.id.tvStorage)
        private var selectionBorder: View? = view.findViewById(R.id.selection_border)

        // Specific to Queued content
        var topButton: View? = view.findViewById(R.id.queueTopBtn)
        var bottomButton: View? = view.findViewById(R.id.queueBottomBtn)
        var ivReorder: View? = view.findViewById(R.id.ivReorder)
        var downloadButton: View? = view.findViewById(R.id.ivRedownload)

        private var deleteActionRunnable: Runnable? = null

        // Extra info to display in stacktraces
        private var debugStr = "[no data]"

        init {
            if (viewType == ViewType.SPLIT) {
                val color = view.context.getThemedColor(R.color.secondary_light)
                view.background =
                    getSelectablePressedBackground(view.context, adjustAlpha(color, 100), 50, true)
            }
        }

        override fun bindView(item: ContentItem, payloads: List<Any>) {
            if (item.isEmpty) {
                debugStr = "empty item"
                return  // Ignore placeholders from PagedList
            }

            // Payloads are set when the content stays the same but some properties alone change
            if (payloads.isNotEmpty()) {
                val bundle = payloads[0] as Bundle
                val bundleParser = ContentItemBundle(bundle)
                var boolValue = bundleParser.isBeingDeleted
                if (boolValue != null) item.content?.isBeingProcessed = boolValue
                boolValue = bundleParser.isFavourite
                if (boolValue != null) item.content?.favourite = boolValue
                var intValue = bundleParser.rating
                if (intValue != null) item.content?.rating = intValue
                boolValue = bundleParser.isCompleted
                if (boolValue != null) item.content?.completed = boolValue
                val longValue = bundleParser.reads
                if (longValue != null) item.content?.reads = longValue
                intValue = bundleParser.readPagesCount
                if (intValue != null) item.content?.readPagesCount = intValue
                var stringValue = bundleParser.coverUri
                if (stringValue != null) item.content?.cover?.fileUri = stringValue
                stringValue = bundleParser.title
                if (stringValue != null) item.content?.title = stringValue
                intValue = bundleParser.downloadMode
                if (intValue != null) item.content?.downloadMode = DownloadMode.fromValue(intValue)
                boolValue = bundleParser.frozen
                if (boolValue != null) item.queueRecord?.frozen = boolValue
                boolValue = bundleParser.processed
                if (boolValue != null) item.content?.isBeingProcessed = boolValue
            }

            if (BuildConfig.DEBUG) item.content?.apply {
                debugStr = "DB ID=" + id + "; site ID=" + uniqueSiteId + "; hashCode=" + hashCode()
            }

            item.deleteAction?.apply {
                deleteActionRunnable = Runnable { invoke(item) }
            }

            // Important to trigger the ViewHolder's global onClick/onLongClick events
            swipeableView.setOnClickListener { v: View -> if (v.parent is View) (v.parent as View).performClick() }
            swipeableView.setOnLongClickListener { v: View ->
                if (v.parent is View) return@setOnLongClickListener (v.parent as View).performLongClick()
                false
            }

            val isGrid = (ViewType.LIBRARY_GRID == item.viewType)

            updateLayoutVisibility(item)
            attachCover(item.content, item.chapter)
            attachTitle(item.content, item.queueRecord, item.chapter, isGrid)
            attachMetrics(item.content, item.chapter, item.viewType)
            item.content?.let {
                attachCompleted(it)
                attachReadingProgress(it)
                attachArtist(it)
                attachSeries(it)
                attachTags(it)
                attachFlag(it, isGrid)
            }
            attachButtons(item, isGrid)
            item.updateProgress(
                this, isPausedEvent = false, isIndividual = true
            )
            @Suppress("UNCHECKED_CAST") if (ivReorder != null) bindDragHandle(
                this, item as IExtendedDraggable<RecyclerView.ViewHolder>
            )
        }

        private fun updateLayoutVisibility(item: ContentItem) {
            baseLayout.isVisible = !item.isEmpty
            selectionBorder?.isVisible = item.isSelected

            if (item.content != null && item.content.isBeingProcessed)
                baseLayout.startAnimation(BlinkAnimation(500, 250))
            else baseLayout.clearAnimation()

            if (item.isSelected && BuildConfig.DEBUG) Timber.d("SELECTED " + item.title)

            // Unread indicator
            ivNew?.apply {
                visibility = View.GONE
                item.content?.let {
                    if (0L == it.reads) visibility = View.VISIBLE
                }
            }
        }

        private fun attachCover(content: Content?, chapter: Chapter?) {
            if (content != null) attachContentCover(content)
            else if (chapter != null) attachChapterCover(chapter)
        }

        private fun attachContentCover(content: Content) {
            ivCover.loadCover(content, true)
        }

        private fun attachChapterCover(chapter: Chapter) {
            val thumbLocation = chapter.imageList.firstOrNull()?.usableUri
            if (thumbLocation != null) {
                ivCover.loadStill(thumbLocation)
                ivCover.visibility = View.VISIBLE
            } else {
                ivCover.visibility = View.INVISIBLE
            }
        }

        private fun attachFlag(content: Content, isGrid: Boolean) {
            ivFlag?.apply {
                @DrawableRes val resId = getFlagResourceId(context, content)
                visibility = if (resId != 0 && (!isGrid || Settings.libraryDisplayGridLanguage)) {
                    setImageResource(resId)
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }

        private fun attachTitle(
            content: Content?, queueRecord: QueueRecord?, chapter: Chapter?, isGrid: Boolean
        ) {
            tvTitle.isVisible = (!isGrid || Settings.libraryDisplayGridTitle)
            if (!tvTitle.isVisible) return

            var title = tvTitle.context.getText(R.string.work_untitled)
            if (content != null) {
                title = content.title.ifEmpty { content.replacementTitle }
            } else if (chapter != null) {
                title = chapter.name
            }

            tvTitle.text = title
            tvTitle.post {
                tvTitle.setMiddleEllipsis()
            }

            var colorId: Int = R.color.card_title_light
            if (queueRecord != null && queueRecord.frozen) colorId = R.color.frozen_blue
            if (isGrid) colorId = R.color.white_opacity_87
            tvTitle.setTextColor(tvTitle.context.getThemedColor(colorId))
        }

        private fun attachCompleted(content: Content) {
            ivCompleted?.isVisible = content.completed
        }

        private fun attachReadingProgress(content: Content) {
            readingProgress?.apply {
                val imgs = content.imageList
                if (!content.completed) {
                    visibility = View.VISIBLE
                    max = imgs.count { imf -> imf.isReadable }
                    progress = content.readPagesCount
                } else {
                    visibility = View.INVISIBLE
                }
            }
        }

        private fun attachArtist(content: Content) {
            tvArtist?.apply {
                text = formatArtistForDisplay(context, content)
            }
        }

        private fun attachSeries(content: Content) {
            tvSeries?.apply {
                val text = formatSeriesForDisplay(context, content)
                if (text.isEmpty()) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    this.text = text
                }
            }
        }

        private fun attachMetrics(
            content: Content?, chapter: Chapter?, viewType: ViewType
        ) {
            tvPages ?: return // Mandatory

            var qtyPages = 0
            if (content != null) qtyPages = content.qtyPages
            else if (chapter != null) qtyPages = chapter.readableImageFiles.size

            tvPages.visibility = if (0 == qtyPages) View.INVISIBLE else View.VISIBLE

            val context = baseLayout.context
            val template: String
            if (viewType == ViewType.QUEUE || viewType == ViewType.ERRORS || viewType == ViewType.LIBRARY_EDIT || viewType == ViewType.MERGE || viewType == ViewType.SPLIT) {
                val nbPages = "$qtyPages"
                template = if (viewType == ViewType.ERRORS) {
                    val nbMissingPages = qtyPages - content!!.getNbDownloadedPages()
                    if (nbMissingPages > 0) {
                        val missingStr = " " + context.resources.getQuantityString(
                            R.plurals.work_pages_missing, nbMissingPages.toInt(), nbMissingPages
                        )
                        context.resources.getString(R.string.work_pages_queue, nbPages, missingStr)
                    } else context.resources.getString(R.string.work_pages_queue, nbPages, "")
                } else context.resources.getString(R.string.work_pages_queue, nbPages, "")
                tvPages.text = template
            } else { // Library (list; grid doesn't display these details)
                check(content != null)
                val isPlaceholder = content.status == StatusContent.PLACEHOLDER
                val phVisibility = if (isPlaceholder) View.GONE else View.VISIBLE
                tvPages.let { tv ->
                    ivPages?.visibility = phVisibility
                    tv.visibility = phVisibility
                    tv.text = String.format(Locale.ENGLISH, "%d", content.getNbDownloadedPages())
                }
                tvChapters?.let { tv ->
                    val chapters = content.chaptersList
                    val chapterVisibility =
                        if (isPlaceholder || chapters.isEmpty()) View.GONE else View.VISIBLE
                    ivChapters?.let { iv ->
                        iv.visibility = chapterVisibility
                        tv.visibility = chapterVisibility
                        if (chapterVisibility == View.VISIBLE) {
                            if (content.manuallyMerged) iv.setImageResource(R.drawable.ic_action_merge)
                            else iv.setImageResource(R.drawable.ic_chapter)
                            tv.text = String.format(Locale.ENGLISH, "%d", chapters.size)
                        }
                    }
                }
                tvStorage?.let { tv ->
                    val storageVisibility =
                        if (isPlaceholder || content.downloadMode == DownloadMode.STREAM) View.GONE else View.VISIBLE
                    ivStorage?.visibility = storageVisibility
                    tv.visibility = storageVisibility
                    if (storageVisibility == View.VISIBLE) {
                        val sizeMb = content.size / (1024.0 * 1024.0)
                        val sizeGb = sizeMb / 1024.0
                        if (sizeGb > 1) tv.text =
                            context.getString(R.string.library_metrics_storage_gb, sizeGb)
                        else tv.text =
                            context.getString(R.string.library_metrics_storage_mb, sizeMb)
                    }
                }
            }
        }

        private fun attachTags(content: Content) {
            tvTags?.apply {
                val tagTxt = formatTagsForDisplay(content)
                if (tagTxt.isEmpty()) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    text = tagTxt
                    setTextColor(context.getThemedColor(R.color.card_tags_light))
                }
            }
        }

        private fun attachButtons(item: ContentItem, isGrid: Boolean) {
            // Universal
            ivReorder?.visibility = if (item.showDragHandle) View.VISIBLE else View.INVISIBLE

            // Content-only
            val content = item.content ?: return

            // Source icon
            ivSite?.apply {
                val site = content.site
                visibility =
                    if (site != Site.NONE && (!isGrid || Settings.libraryDisplayGridSource)) {
                        val img = site.ico
                        setImageResource(img)
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
            }

            deleteButton?.apply {
                setOnClickListener { deleteActionRunnable?.run() }
                setOnLongClickListener {
                    deleteActionRunnable?.run()
                    true
                }
            }

            val isStreamed = content.downloadMode == DownloadMode.STREAM
            ivOnline?.isVisible = isStreamed && (!isGrid || Settings.libraryDisplayGridStorageInfo)
            if (ViewType.QUEUE == item.viewType || ViewType.LIBRARY_EDIT == item.viewType) {
                topButton?.visibility = View.VISIBLE
                bottomButton?.visibility = View.VISIBLE
            } else if (ViewType.ERRORS == item.viewType) {
                downloadButton?.visibility = View.VISIBLE
                ivError?.visibility = View.VISIBLE
            } else if (ViewType.LIBRARY == item.viewType || ViewType.LIBRARY_GRID == item.viewType) {
                ivExternal?.isVisible =
                    content.status == StatusContent.EXTERNAL && (!isGrid || Settings.libraryDisplayGridStorageInfo)
                ivStorage?.isVisible = (!isGrid || Settings.libraryDisplayGridStorageInfo)

                if (content.status == StatusContent.EXTERNAL) {
                    var resourceId =
                        if (content.isArchive) R.drawable.ic_archive
                        else if (content.isPdf) R.drawable.ic_pdf_file
                        else R.drawable.ic_folder_full
                    ivExternal?.setImageResource(resourceId)
                    // External streamed is streamed icon
                    if (isStreamed) resourceId = R.drawable.ic_action_download_stream
                    ivStorage?.setImageResource(resourceId)
                } else {
                    ivStorage?.setImageResource(if (isStreamed) R.drawable.ic_action_download_stream else R.drawable.ic_storage)
                }

                ivFavourite?.apply {
                    isVisible = (!isGrid || Settings.libraryDisplayGridFav)
                    if (content.favourite) setImageResource(R.drawable.ic_fav_full)
                    else setImageResource(R.drawable.ic_fav_empty)
                }

                ivRating?.apply {
                    isVisible = (!isGrid || Settings.libraryDisplayGridRating)
                    setImageResource(getRatingResourceId(content.rating))
                }
            }
        }

        val favouriteButton: View?
            get() = ivFavourite
        val ratingButton: View?
            get() = ivRating
        val siteButton: View?
            get() = ivSite
        val errorButton: View?
            get() = ivError

        override fun unbindView(item: ContentItem) {
            deleteActionRunnable = null
            debugStr = "[no data]"
            swipeableView.translationX = 0f
            ivCover.dispose()
        }

        override fun onDragged() {
            // TODO fix incorrect visual behaviour when dragging an item to 1st position
            //bookCard.setBackgroundColor(ThemeHelper.getColor(bookCard.getContext(), R.color.white_opacity_25));
        }

        override fun onDropped() {
            // TODO fix incorrect visual behaviour when dragging an item to 1st position
            //bookCard.setBackground(bookCard.getContext().getDrawable(R.drawable.bg_book_card));
        }

        override fun onSwiped() {
            // Nothing
        }

        override fun onUnswiped() {
            // Nothing
        }

        override fun toString(): String {
            return super.toString() + " " + debugStr
        }
    }

    class DragHandlerTouchEvent(private val action: Consumer<Int>) : TouchEventHook<ContentItem>() {
        override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
            return if (viewHolder is ViewHolder) viewHolder.ivReorder else null
        }

        override fun onTouch(
            v: View,
            event: MotionEvent,
            position: Int,
            fastAdapter: FastAdapter<ContentItem>,
            item: ContentItem
        ): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                action.invoke(position)
                return true
            }
            return false
        }
    }
}
