package me.devsaki.hentoid.widget

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.file.DisplayFile
import me.devsaki.hentoid.util.file.FileExplorer
import me.devsaki.hentoid.util.file.getDocumentFromTreeUri
import me.devsaki.hentoid.util.int
import me.devsaki.hentoid.util.string

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

    fun getFolders(context: Context, root: Uri) {
        val rootUri = values.root.toUri()
        if (null == explorer) explorer = FileExplorer(context, root)
        else if (explorer!!.root != rootUri) explorer = FileExplorer(context, root)

        val rootDoc = getDocumentFromTreeUri(context, rootUri) ?: return
        val docs = explorer?.listDocumentFiles(context, rootDoc) ?: return
        files.postValue(docs.map { DisplayFile(it) })
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