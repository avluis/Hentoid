package me.devsaki.hentoid.viewholders

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import coil3.dispose
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IExpandable
import com.mikepenz.fastadapter.IParentItem
import com.mikepenz.fastadapter.ISubItem
import com.mikepenz.fastadapter.items.AbstractItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.ImageItemBundle
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.util.image.loadStill

private const val HEART_SYMBOL = "❤"

class ImageFileItem(private val image: ImageFile, private val showChapter: Boolean) :
    AbstractItem<ImageFileItem.ViewHolder>(),
    IExpandable<ImageFileItem.ViewHolder>,
    INestedItem<ImageFileItem.ViewHolder> {
    private val chapter: Chapter =
        image.linkedChapter ?: Chapter(1, "", "") // Default display when nothing is set
    private var isCurrent = false
    private val expanded = false

    init {
        identifier = image.uniqueHash()
    }

    // Return a copy, not the original instance that has to remain in synch with its visual representation
    fun getImage(): ImageFile {
        return image
    }

    fun setCurrent(current: Boolean) {
        isCurrent = current
    }

    fun isFavourite(): Boolean {
        return image.favourite
    }

    fun getChapterOrder(): Int {
        return chapter.order
    }

    override val isAutoExpanding: Boolean
        get() = true

    @Suppress("UNUSED_PARAMETER")
    override var isExpanded: Boolean
        get() = expanded
        set(value) {}

    @Suppress("UNUSED_PARAMETER")
    override var parent: IParentItem<*>?
        get() = null
        set(value) {}

    @Suppress("UNUSED_PARAMETER")
    override var subItems: MutableList<ISubItem<*>>
        get() = mutableListOf()
        set(value) { /* Nothing */ }
    override val layoutRes: Int
        get() = R.layout.item_reader_gallery_image

    override fun getLevel(): Int {
        return 1
    }

    override val type: Int
        get() = R.id.gallery_image

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder internal constructor(view: View) :
        FastAdapter.ViewHolder<ImageFileItem>(view) {
        private val pageNumberTxt: TextView =
            ViewCompat.requireViewById(view, R.id.viewer_gallery_pagenumber_text)
        private val image: ImageView = ViewCompat.requireViewById(view, R.id.viewer_gallery_image)
        private val checkedIndicator: ImageView = ViewCompat.requireViewById(
            view, R.id.checked_indicator
        )
        private val chapterOverlay: TextView =
            ViewCompat.requireViewById(view, R.id.chapter_overlay)

        override fun bindView(item: ImageFileItem, payloads: List<Any>) {

            // Payloads are set when the content stays the same but some properties alone change
            if (payloads.isNotEmpty()) {
                val bundle = payloads[0] as Bundle
                val bundleParser = ImageItemBundle(bundle)
                val boolValue = bundleParser.isFavourite
                if (boolValue != null) item.image.favourite = boolValue
                val intValue = bundleParser.chapterOrder
                if (intValue != null) item.chapter.order = intValue
            }
            updateText(item)

            // Checkmark
            if (item.isSelected) checkedIndicator.visibility =
                View.VISIBLE else checkedIndicator.visibility =
                View.GONE

            // Chapter overlay
            if (item.showChapter) {
                var chapterText = checkedIndicator.context.resources.getString(
                    R.string.gallery_display_chapter,
                    item.chapter.order
                )
                if (item.chapter.order == Int.MAX_VALUE) chapterText = "" // Don't show temp values
                chapterOverlay.text = chapterText
                chapterOverlay.setBackgroundColor(
                    ContextCompat.getColor(
                        chapterOverlay.context,
                        if (0 == item.chapter.order % 2) R.color.black_opacity_50 else R.color.white_opacity_25
                    )
                )
                chapterOverlay.visibility = View.VISIBLE
            } else chapterOverlay.visibility = View.GONE

            // Image
            image.loadStill(item.image.fileUri)
            /*
            Glide.with(image)
                .load(Uri.parse(item.image.fileUri))
                .signature(ObjectKey(item.image.uniqueHash()))
                .addListener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        var handled = false
                        // If animated, only load frame zero as a plain bitmap
                        if (target != null && resource is FrameAnimationDrawable<*>) {
                            target.onResourceReady(
                                BitmapDrawable(
                                    image.resources,
                                    resource.frameSeqDecoder.getFrameBitmap(0)
                                ), null
                            )
                            resource.stop()
                            handled = true
                        }
                        return handled
                    }
                })
                .apply(glideOptionCenterInside)
                .into(image)

             */
        }

        private fun updateText(item: ImageFileItem) {
            val currentBegin = if (item.isCurrent) ">" else ""
            val currentEnd = if (item.isCurrent) "<" else ""
            val isFavourite = if (item.isFavourite()) HEART_SYMBOL else ""
            pageNumberTxt.text = pageNumberTxt.resources.getString(
                R.string.gallery_display_page,
                currentBegin,
                item.image.displayOrder + 1,
                isFavourite,
                currentEnd
            )
            if (item.isCurrent) pageNumberTxt.setTypeface(null, Typeface.BOLD)
        }

        override fun unbindView(item: ImageFileItem) {
            image.dispose()
            //if (isValidContextForGlide(image)) Glide.with(image).clear(image)
        }
    }
}