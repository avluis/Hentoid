package me.devsaki.hentoid.viewholders

import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.mikepenz.fastadapter.ClickListener
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.IClickable
import com.mikepenz.fastadapter.ISubItem
import com.mikepenz.fastadapter.drag.IExtendedDraggable
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem
import com.mikepenz.fastadapter.listeners.TouchEventHook
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils
import com.mikepenz.fastadapter.ui.utils.StringHolder
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.requireById
import me.devsaki.hentoid.core.setMiddleEllipsis
import me.devsaki.hentoid.database.domains.ImageFile

/**
 * Inspired by mikepenz
 */
class SubExpandableItem<T>(
    private val mTouchHelper: ItemTouchHelper,
    val name: String,
    entity: T?
) :
    AbstractExpandableItem<SubExpandableItem.ViewHolder>(),
    IClickable<SubExpandableItem<T>>, ISubItem<SubExpandableItem.ViewHolder>,
    IExtendedDraggable<SubExpandableItem.ViewHolder>,
    INestedItem<SubExpandableItem.ViewHolder> {

    init {
        super.tag = entity
    }

    var header: String? = null
    var description: StringHolder? = null
    var cover: ImageFile? = null
    private var selectable = false
    private var draggable = false

    private var mOnClickListener: ClickListener<SubExpandableItem<T>>? = null

    //we define a clickListener in here so we can directly animate
    /**
     * we overwrite the item specific click listener so we can automatically animate within the item
     *
     * @return
     */
    @Suppress("SetterBackingFieldAssignment")
    override var onItemClickListener: ClickListener<SubExpandableItem<T>>? =
        { v: View?, adapter: IAdapter<SubExpandableItem<T>>, item: SubExpandableItem<T>, position: Int ->
            if (item.subItems.isNotEmpty()) {
                v?.findViewById<View>(R.id.expand_handle)?.let {
                    if (!item.isExpanded) {
                        it.animate().rotation(180f).start()
                    } else {
                        it.animate().rotation(0f).start()
                    }
                }
            }
            mOnClickListener?.invoke(v, adapter, item, position) ?: true
        }
        set(onClickListener) {
            this.mOnClickListener = onClickListener // on purpose
        }

    override var onPreItemClickListener: ClickListener<SubExpandableItem<T>>?
        get() = null
        set(_) {}

    override var isSelectable: Boolean
        get() = selectable
        set(value) {
            selectable = value
        }

    /**
     * defines the type defining this item. must be unique. preferably an id
     *
     * @return the type
     */
    override val type: Int
        get() = R.id.expandable_item

    /**
     * defines the layout which will be used for this item in the list
     *
     * @return the layout for this item
     */
    override val layoutRes: Int
        get() = R.layout.item_expandable

    fun withHeader(header: String): SubExpandableItem<T> {
        this.header = header
        return this
    }

    fun withDescription(description: String): SubExpandableItem<T> {
        this.description = StringHolder(description)
        return this
    }

    fun withDescription(@StringRes descriptionRes: Int): SubExpandableItem<T> {
        this.description = StringHolder(descriptionRes)
        return this
    }

    fun withCover(imageFile: ImageFile?): SubExpandableItem<T> {
        this.cover = imageFile
        return this
    }

    fun withDraggable(value: Boolean): SubExpandableItem<T> {
        this.draggable = value
        return this
    }

    /**
     * binds the data of this item onto the viewHolder
     *
     * @param holder the viewHolder of this item
     */
    override fun bindView(holder: ViewHolder, payloads: List<Any>) {
        super.bindView(holder, payloads)

        //get the context
        val ctx = holder.itemView.context

        //set the background for the item
        holder.view.clearAnimation()
        holder.view.background = FastAdapterUIUtils.getSelectableBackground(
            ctx,
            ctx.getColor(R.color.white_opacity_25),
            true
        )

        // Texts
        holder.name.text = name
        holder.name.post {
            holder.name.setMiddleEllipsis()
        }
        StringHolder.applyToOrHide(description, holder.description)

        // Cover
        attachCover(holder.cover)

        holder.dragHandle.visibility = if (draggable) View.VISIBLE else View.GONE

        if (subItems.isEmpty() || isSelected) holder.expandHandle.visibility = View.GONE
        else holder.expandHandle.visibility = View.VISIBLE

        // TODO don't show subItems when selected ?
        holder.selectedFlag.visibility = if (isSelected) View.VISIBLE else View.GONE

        if (isExpanded) holder.expandHandle.rotation = 0f
        else holder.expandHandle.rotation = 180f
    }

    private fun attachCover(ivCover: ImageView) {
        cover?.apply {
            val thumbLocation = usableUri
            if (thumbLocation.isEmpty()) {
                ivCover.visibility = View.INVISIBLE
                return
            }
            ivCover.visibility = View.VISIBLE
            // Use content's cookies to load image (useful for ExHentai when viewing queue screen)
            ivCover.load(thumbLocation)
        }
    }

    override fun unbindView(holder: ViewHolder) {
        super.unbindView(holder)
        holder.name.text = null
        holder.description.text = null
        //make sure all animations are stopped
        holder.expandHandle.clearAnimation()
    }

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    /**
     * our ViewHolder
     */
    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.requireById(R.id.material_drawer_name)
        val description: TextView = view.requireById(R.id.material_drawer_description)
        val expandHandle: ImageView = view.requireById(R.id.expand_handle)
        val selectedFlag: ImageView = view.requireById(R.id.selected_flag)
        val dragHandle: ImageView = view.requireById(R.id.ivReorder)
        val cover: ImageView = view.requireById(R.id.ivCover)
    }

    override val touchHelper: ItemTouchHelper
        get() = mTouchHelper

    // Dragging
    override val isDraggable: Boolean
        get() = draggable

    override fun getDragView(viewHolder: ViewHolder): View {
        return viewHolder.dragHandle
    }

    class DragHandlerTouchEvent<T>(val action: (position: Int) -> Unit) :
        TouchEventHook<SubExpandableItem<T>>() {
        override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
            return if (viewHolder is ViewHolder) viewHolder.dragHandle else null
        }

        override fun onTouch(
            v: View,
            event: MotionEvent,
            position: Int,
            fastAdapter: FastAdapter<SubExpandableItem<T>>,
            item: SubExpandableItem<T>
        ): Boolean {
            return if (event.action == MotionEvent.ACTION_DOWN) {
                action(position)
                true
            } else {
                false
            }
        }
    }

    override fun getLevel(): Int {
        return 0
    }
}
