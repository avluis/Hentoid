package me.devsaki.hentoid.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.viewholders.AttributeViewHolder

/**
 * Adapter for the available attributes list displayed in the advanced search screen
 * <p>
 * Can only be removed when prerequisites are met : see comments in {@link me.devsaki.hentoid.fragments.SearchBottomSheetFragment}
 */
// Threshold for infinite loading
private const val VISIBLE_THRESHOLD = 5

class AvailableAttributeAdapter : RecyclerView.Adapter<AttributeViewHolder>() {
    private val dataset: MutableList<Attribute> = ArrayList()
    private var onScrollToEndListener: Runnable? = null
    private var onClickListener: View.OnClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttributeViewHolder {
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.item_badge, parent, false)
        return AttributeViewHolder(view)
    }

    fun setOnScrollToEndListener(listener: Runnable) {
        onScrollToEndListener = listener
    }

    fun setOnClickListener(listener: View.OnClickListener) {
        onClickListener = listener
    }

    override fun onBindViewHolder(holder: AttributeViewHolder, position: Int) {
        if (position == itemCount - VISIBLE_THRESHOLD) onScrollToEndListener?.run()
        holder.bindTo(dataset[position])
        holder.itemView.setOnClickListener(onClickListener)
    }

    override fun getItemCount(): Int {
        return dataset.size
    }

    fun add(attrs: List<Attribute>) {
        dataset.addAll(attrs)
        notifyDataSetChanged()
    }

    fun clear() {
        dataset.clear()
        notifyDataSetChanged()
    }

    fun remove(attribute: Attribute) {
        val index = dataset.indexOf(attribute)
        dataset.remove(attribute)
        notifyItemRemoved(index)
    }
}