package me.devsaki.hentoid.viewholders

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.drag.IExtendedDraggable
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.swipe.ISwipeable
import com.mikepenz.fastadapter.utils.DragDropUtil.bindDragHandle
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.GroupItemBundle
import me.devsaki.hentoid.core.requireById
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.GroupItem
import me.devsaki.hentoid.database.isReachable
import me.devsaki.hentoid.ui.BlinkAnimation
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.getGlideOptionCenterImage
import me.devsaki.hentoid.util.getRatingResourceId
import me.devsaki.hentoid.util.isValidContextForGlide

class GroupDisplayItem(
    val group: Group,
    override val touchHelper: ItemTouchHelper?,
    private val viewType: ViewType
) : AbstractItem<GroupDisplayItem.ViewHolder>(), IExtendedDraggable<GroupDisplayItem.ViewHolder>,
    ISwipeable {

    enum class ViewType {
        LIBRARY, LIBRARY_GRID, LIBRARY_EDIT
    }

    init {
        identifier = group.uniqueHash()
    }

    override val layoutRes: Int
        get() = if (ViewType.LIBRARY_GRID == viewType) R.layout.item_library_group_grid else R.layout.item_library_group

    override val type: Int
        get() = R.id.group

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    override val isSwipeable: Boolean
        get() = true

    override fun isDirectionSupported(direction: Int): Boolean {
        return ItemTouchHelper.LEFT == direction
    }

    override val isDraggable: Boolean
        get() = ViewType.LIBRARY_EDIT == viewType

    override fun getDragView(viewHolder: ViewHolder): View? {
        return viewHolder.ivReorder
    }


    class ViewHolder internal constructor(view: View) :
        FastAdapter.ViewHolder<GroupDisplayItem>(view) {
        private val baseLayout: View = view.requireById(R.id.item)
        private val title: TextView = view.requireById(R.id.tvTitle)
        private val ivFavourite: ImageView = view.requireById(R.id.ivFavourite)
        private val ivRating: ImageView = view.requireById(R.id.iv_rating)
        private var ivCover: ImageView? = view.findViewById(R.id.ivCover)
        var ivReorder: View? = view.findViewById(R.id.ivReorder)
        private var selectionBorder: View? = view.findViewById(R.id.selection_border)
        private var coverUri = ""

        private var glideRequestOptions = getGlideOptionCenterImage(view.context)

        override fun bindView(item: GroupDisplayItem, payloads: List<Any>) {
            // Payloads are set when the content stays the same but some properties alone change
            if (payloads.isNotEmpty()) {
                val bundle = payloads[0] as Bundle
                val bundleParser = GroupItemBundle(bundle)
                val stringValue = bundleParser.coverUri
                if (stringValue != null) coverUri = stringValue
                val boolValue = bundleParser.isFavourite
                if (boolValue != null) item.group.isFavourite = boolValue
                val intValue = bundleParser.rating
                if (intValue != null) item.group.rating = intValue
            }

            if (item.group.isBeingProcessed)
                baseLayout.startAnimation(BlinkAnimation(500, 250))
            else baseLayout.clearAnimation()

            selectionBorder?.isVisible = item.isSelected

            val isGrid = (ViewType.LIBRARY_GRID == item.viewType)

            ivReorder?.apply {
                if (ViewType.LIBRARY_EDIT == item.viewType) {
                    visibility = View.VISIBLE
                    @Suppress("UNCHECKED_CAST")
                    bindDragHandle(
                        this@ViewHolder,
                        item as IExtendedDraggable<RecyclerView.ViewHolder>
                    )
                } else {
                    visibility = View.INVISIBLE
                }
            }

            if (ivCover != null) {
                var coverContent: Content? = null
                if (!item.group.coverContent.isNull) coverContent = item.group.coverContent.target
                else if (!item.group.items.isEmpty()) {
                    item.group.items[0].let {
                        if (it.content.isReachable(it)) {
                            val c = it.content.target
                            if (c != null) coverContent = c
                        }
                    }
                }
                var uri = coverUri
                coverContent?.let { uri = it.cover.usableUri }
                attachCover(uri)
            }

            val items: List<GroupItem>? = item.group.items
            val numberStr =
                if (items.isNullOrEmpty()) ivFavourite.context.getString(R.string.empty) else items.size.toString() + ""
            title.text = String.format("%s (%s)", item.group.name, numberStr)

            ivFavourite.isVisible = (!isGrid || Settings.libraryDisplayGridFav)
            if (item.group.isFavourite) {
                ivFavourite.setImageResource(R.drawable.ic_fav_full)
            } else {
                ivFavourite.setImageResource(R.drawable.ic_fav_empty)
            }

            ivRating.isVisible = (!isGrid || Settings.libraryDisplayGridRating)
            ivRating.setImageResource(getRatingResourceId(item.group.rating))
        }

        private fun attachCover(uri: String) {
            ivCover?.let {
                if (uri.isEmpty()) {
                    it.visibility = View.INVISIBLE
                    return
                }
                it.visibility = View.VISIBLE
                if (uri.startsWith("http")) Glide.with(it)
                    .load(uri)
                    .signature(ObjectKey(uri))
                    .apply(glideRequestOptions)
                    .into(it)
                else Glide.with(it)
                    .load(Uri.parse(uri))
                    .signature(ObjectKey(uri))
                    .apply(glideRequestOptions)
                    .into(it)
            }
        }

        val favouriteButton: View
            get() = ivFavourite
        val ratingButton: View
            get() = ivRating

        override fun unbindView(item: GroupDisplayItem) {
            ivCover?.let {
                if (isValidContextForGlide(it)) Glide.with(it).clear(it)
            }
        }
    }
}