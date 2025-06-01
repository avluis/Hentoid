package me.devsaki.hentoid.viewholders

import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.json.GithubRelease
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val NOT_A_DIGIT by lazy { "\\D".toRegex() }

class GitHubReleaseItem(releaseStruct: GithubRelease) :
    AbstractItem<GitHubReleaseItem.ReleaseViewHolder>() {

    val tagName: String = releaseStruct.tagName.replace("v", "")
    private val name: String = releaseStruct.name
    private val description: String = releaseStruct.body
    private val creationDate: Date = releaseStruct.creationDate
    val apkUrl: String = releaseStruct.getApkAssetUrl()

    fun isTagPrior(tagName: String): Boolean {
        return getIntFromTagName(this.tagName) <= getIntFromTagName(tagName)
    }

    private fun getIntFromTagName(tagName: String): Int {
        var result = 0
        val parts = tagName.split(".")
        if (parts.isNotEmpty()) result = 10000 * parts[0].replace(NOT_A_DIGIT, "").toInt()
        if (parts.size > 1) result += 100 * parts[1].replace(NOT_A_DIGIT, "").toInt()
        if (parts.size > 2) result += parts[2].replace(NOT_A_DIGIT, "")
            .toInt()
        return result
    }

    override fun getViewHolder(v: View): ReleaseViewHolder {
        return ReleaseViewHolder(v)
    }

    override val layoutRes: Int
        get() = R.layout.item_changelog
    override val type: Int
        get() = R.id.github_release

    class ReleaseViewHolder(view: View) : FastAdapter.ViewHolder<GitHubReleaseItem>(view) {
        private val title: TextView = ViewCompat.requireViewById<TextView>(view, R.id.changelogReleaseTitle)
        private val itemAdapter = ItemAdapter<GitHubReleaseDescItem>()

        init {
            val releaseDescriptionAdapter = FastAdapter.with(itemAdapter)
            val releasedDescription = ViewCompat.requireViewById<RecyclerView>(
                view, R.id.changelogReleaseDescription
            )
            releasedDescription.adapter = releaseDescriptionAdapter
        }

        override fun bindView(item: GitHubReleaseItem, payloads: List<Any>) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            setTitle(item.name + " (" + dateFormat.format(item.creationDate) + ")")
            clearContent()
            // Parse content and add lines to the description
            for (s in item.description.split("\r\n")) { // TODO - refactor this code with its copy in UpdateSuccessDialogFragment
                val des = s.trim { it <= ' ' }
                if (des.startsWith("-")) addListContent(des) else addDescContent(des)
            }
        }

        fun setTitle(title: String) {
            this.title.text = title
        }

        private fun clearContent() {
            itemAdapter.clear()
        }

        private fun addDescContent(text: String) {
            itemAdapter.add(GitHubReleaseDescItem(text, GitHubReleaseDescItem.Type.DESCRIPTION))
        }

        private fun addListContent(text: String) {
            itemAdapter.add(GitHubReleaseDescItem(text, GitHubReleaseDescItem.Type.LIST_ITEM))
        }

        override fun unbindView(item: GitHubReleaseItem) {
            // No specific behaviour to implement
        }
    }
}