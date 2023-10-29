package me.devsaki.hentoid.viewholders

import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import me.devsaki.hentoid.R

class GitHubReleaseDescItem(val text: String, val entryType: Type) :
    AbstractItem<GitHubReleaseDescItem.ReleaseDescriptionViewHolder>() {

    enum class Type {
        DESCRIPTION,
        LIST_ITEM
    }

    override val layoutRes: Int
        get() = R.layout.item_text
    override val type: Int
        get() = R.id.github_release_description

    override fun getViewHolder(v: View): ReleaseDescriptionViewHolder {
        return ReleaseDescriptionViewHolder(v)
    }

    class ReleaseDescriptionViewHolder(view: View) :
        FastAdapter.ViewHolder<GitHubReleaseDescItem>(view) {
        private val LINE_PADDING: Int
        private val title: TextView

        init {
            title = ViewCompat.requireViewById(view, R.id.item_txt)
            LINE_PADDING = view.resources.getDimension(R.dimen.changelog_line_padding).toInt()
        }

        override fun bindView(item: GitHubReleaseDescItem, payloads: List<Any>) {
            if (item.entryType == Type.DESCRIPTION) setDescContent(item.text)
            else if (item.entryType == Type.LIST_ITEM) setListContent(item.text)
        }

        fun setDescContent(text: String) {
            title.text = text
            title.setPadding(0, LINE_PADDING, 0, 0)
        }

        fun setListContent(text: String) {
            title.text = text
            title.setPadding(LINE_PADDING * 2, LINE_PADDING, 0, 0)
        }

        override fun unbindView(item: GitHubReleaseDescItem) {
            // No specific behaviour to implement
        }
    }
}