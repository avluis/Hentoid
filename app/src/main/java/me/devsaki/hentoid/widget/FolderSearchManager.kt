package me.devsaki.hentoid.widget

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.file.DisplayFile
import me.devsaki.hentoid.util.file.FileExplorer
import me.devsaki.hentoid.util.file.getDocumentFromTreeUri
import me.devsaki.hentoid.util.image.imageNamesFilter
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.string
import timber.log.Timber

class FolderSearchManager() {

    val files = MutableLiveData<List<DisplayFile>>()
    private val values = FolderSearchBundle()
    private var explorer: FileExplorer? = null


    fun toBundle(): Bundle {
        val result = Bundle()
        saveToBundle(result)
        return result
    }

    fun saveToBundle(b: Bundle) {
        b.putAll(values.bundle)
    }

    fun loadFromBundle(b: Bundle) {
        values.bundle.putAll(b)
    }

    fun setQuery(value: String) {
        values.query = value
    }

    fun setSortField(value: Int) {
        values.sortField = value
    }

    fun setSortDesc(value: Boolean) {
        values.sortDesc = value
    }

    fun clearFilters() {
        setQuery("")
    }

    fun clear() {
        clearFilters()
        explorer?.close()
        explorer = null
    }

    fun getFolders(context: Context, root: Uri, parent: Uri) {
        Timber.d("Navigating to $root with parent $parent")
        val previousRoot = explorer?.root ?: Uri.EMPTY
        if (previousRoot != root) explorer = FileExplorer(context, root)

        val rootDoc = getDocumentFromTreeUri(context, root) ?: return
        val docs = explorer?.listDocumentFiles(context, rootDoc) ?: return

        // Count contents to see if we have a folder book
        val displayFiles = docs.map {
            val nbChildren = if (it.isDirectory) {
                explorer?.countFiles(it, imageNamesFilter) ?: 0
            } else 0
            val res = DisplayFile(it, nbChildren > 1)
            res.nbChildren = nbChildren
            res
        }.toMutableList()
        // Add 'Up one level' item at position 0
        displayFiles.add(
            0,
            DisplayFile(parent, context.resources.getString(R.string.up_level))
        )
        files.postValue(displayFiles)
    }

    class FolderSearchBundle(val bundle: Bundle = Bundle()) {

        var root by bundle.string(default = "")

        var query by bundle.string(default = "")

        var sortField by bundle.int(default = Settings.groupSortField)

        var sortDesc by bundle.boolean(default = Settings.isGroupSortDesc)

        fun isFilterActive(): Boolean {
            return query.isNotEmpty()
        }
    }
}