package me.devsaki.hentoid.widget

import android.content.Context
import android.net.Uri
import android.os.Bundle
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.InnerNameNumberDisplayFileComparator
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.file.DisplayFile
import me.devsaki.hentoid.util.file.FileExplorer
import me.devsaki.hentoid.util.file.NameFilter
import me.devsaki.hentoid.util.image.imageNamesFilter
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.string
import timber.log.Timber

class FolderSearchManager() {

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

    fun getFolders(context: Context, root: Uri): List<DisplayFile> {
        Timber.d("Navigating to $root")
        val previousRootStr = explorer?.root?.path ?: ""
        val rootStr = root.path ?: ""
        val needNewExplorer = previousRootStr.isEmpty() ||
                (!previousRootStr.startsWith(rootStr) && !rootStr.startsWith(previousRootStr))
        if (needNewExplorer) explorer = FileExplorer(context, root)

        val nameFilter = if (values.query.isNotBlank()) {
            NameFilter { it.contains(values.query, true) }
        } else null
        val rootDoc = explorer?.getDocumentFromTreeUri(context, root) ?: return emptyList()
        val docs = explorer?.listDocumentFiles(
            context, rootDoc, nameFilter
        ) ?: return emptyList()

        // Count contents to see if we have a folder book
        var displayFiles = docs.map {
            val nbImgChildren = if (it.isDirectory) {
                explorer?.countFiles(it, imageNamesFilter) ?: 0
            } else 0
            val res = DisplayFile(it, nbImgChildren > 1, root)
            res.nbChildren = nbImgChildren
            res
        }

        // Apply sort field
        displayFiles = when (values.sortField) {
            Settings.Value.ORDER_FIELD_DOWNLOAD_COMPLETION_DATE -> displayFiles.sortedBy { it.lastModified }
            else -> displayFiles.sortedWith(InnerNameNumberDisplayFileComparator())
        }
        if (values.sortDesc) displayFiles = displayFiles.reversed()

        // Add 'Up one level' item at position 0
        displayFiles = displayFiles.toMutableList()
        displayFiles.add(
            0,
            DisplayFile(context.resources.getString(R.string.up_level), DisplayFile.Type.UP_BUTTON)
        )
        return displayFiles
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