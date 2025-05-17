package me.devsaki.hentoid.widget

import android.content.Context
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
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

    fun getRoot(): String {
        return values.root
    }

    fun clearFilters() {
        setQuery("")
    }

    fun clear() {
        clearFilters()
        explorer?.close()
        explorer = null
    }

    suspend fun getFoldersFast(context: Context, root: Uri): List<DisplayFile> =
        withContext(Dispatchers.IO) {
            Timber.d("Navigating to $root")
            val previousRootStr = explorer?.root?.path ?: ""
            val rootStr = root.path ?: ""
            val needNewExplorer = previousRootStr.isEmpty() ||
                    (!previousRootStr.startsWith(rootStr) && !rootStr.startsWith(previousRootStr))
            if (needNewExplorer) explorer = FileExplorer(context, root)
            val theExplorer = explorer ?: return@withContext emptyList()
            values.root = root.toString()

            val nameFilter = if (values.query.isNotBlank()) {
                NameFilter { it.contains(values.query, true) }
            } else null
            val rootDoc =
                theExplorer.getDocumentFromTreeUri(context, root) ?: return@withContext emptyList()
            val docs = theExplorer.listDocumentFiles(
                context, rootDoc, nameFilter
            )

            // Count contents to see if we have a folder book
            var displayFiles = docs.map { DisplayFile(it, false, root) }

            // Apply sort field
            displayFiles = when (values.sortField) {
                Settings.Value.ORDER_FIELD_DOWNLOAD_COMPLETION_DATE ->
                    displayFiles.sortedBy { it.lastModified * if (values.sortDesc) -1 else 1 }

                else -> displayFiles.sortedWith(InnerNameNumberDisplayFileComparator(values.sortDesc))
            }

            // Add 'Up one level' item at position 0
            displayFiles = displayFiles.toMutableList()
            displayFiles.add(
                0,
                DisplayFile(
                    context.resources.getString(R.string.up_level),
                    DisplayFile.Type.UP_BUTTON
                )
            )

            return@withContext displayFiles
        }

    suspend fun getFoldersDetails(context: Context, root: Uri): Flow<DisplayFile> =
        withContext(Dispatchers.IO) {
            Timber.d("Navigating to $root")
            val previousRootStr = explorer?.root?.path ?: ""
            val rootStr = root.path ?: ""
            val needNewExplorer = previousRootStr.isEmpty() ||
                    (!previousRootStr.startsWith(rootStr) && !rootStr.startsWith(previousRootStr))
            if (needNewExplorer) explorer = FileExplorer(context, root)
            val theExplorer = explorer ?: return@withContext emptyFlow()
            values.root = rootStr

            val nameFilter = if (values.query.isNotBlank()) {
                NameFilter { it.contains(values.query, true) }
            } else null
            val rootDoc =
                theExplorer.getDocumentFromTreeUri(context, root) ?: return@withContext emptyFlow()

            // TODO sorting at file level

            // Add 'Up one level' item at position 0
            val flowUp = flowOf(
                DisplayFile(
                    context.resources.getString(R.string.up_level),
                    DisplayFile.Type.UP_BUTTON
                )
            )
            val flowFiles =
                theExplorer.listDocumentFilesFw(context, rootDoc, nameFilter)
                    .map {
                        // Count contents to see if we have a folder book
                        val imgChildren = if (it.isDirectory) {
                            theExplorer.listFiles(context, it, imageNamesFilter)
                        } else emptyList()
                        val coverUri = imgChildren.firstOrNull()?.uri ?: Uri.EMPTY
                        val res = DisplayFile(it, imgChildren.size > 1, root)
                        res.coverUri = coverUri
                        res.nbChildren = imgChildren.size
                        res
                    }
                    .flowOn(Dispatchers.IO)

            return@withContext merge(flowUp, flowFiles)
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