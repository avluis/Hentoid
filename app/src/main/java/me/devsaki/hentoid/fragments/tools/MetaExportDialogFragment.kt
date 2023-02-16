package me.devsaki.hentoid.fragments.tools

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.Consts
import me.devsaki.hentoid.core.startBrowserActivity
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.DialogToolsMetaExportBinding
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.json.JsonContentCollection
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.file.FileHelper
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class MetaExportDialogFragment : DialogFragment(R.layout.dialog_tools_meta_export) {

    // == UI
    private var binding: DialogToolsMetaExportBinding? = null

    // == VARIABLES
    private lateinit var dao: CollectionDAO
    private var locationIndex = 0


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogToolsMetaExportBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        dao = ObjectBoxDAO(requireContext())
        val nbLibraryBooks = dao.countAllInternalBooks("", false)
        val nbQueueBooks = dao.countAllQueueBooks()
        val nbBookmarks = dao.countAllBookmarks()
        binding?.apply {
            exportQuestion.setOnCheckedChangeListener { _, id ->
                run {
                    exportQuestion.isEnabled = false
                    exportQuestionYes.isEnabled = false
                    exportQuestionNo.isEnabled = false
                    val yes = (R.id.export_question_yes == id)
                    exportGroupYes.isVisible = yes
                    exportGroupNo.isVisible = !yes
                }
            }

            exportLocation.text = resources.getString(
                R.string.export_location,
                resources.getString(R.string.refresh_location_internal)
            )
            exportLocation.setOnClickListener {
                MaterialAlertDialogBuilder(requireActivity())
                    .setCancelable(true)
                    .setSingleChoiceItems(
                        R.array.export_location_entries,
                        locationIndex
                    ) { dialog, which ->
                        locationIndex = which
                        exportLocation.text = resources.getString(
                            R.string.export_location,
                            resources.getStringArray(R.array.export_location_entries)[locationIndex]
                        )
                        refreshFavsDisplay()
                        dialog.dismiss()
                    }
                    .create()
                    .show()
            }

            exportFavsOnly.setOnCheckedChangeListener { _, _ -> refreshFavsDisplay() }
            if (nbLibraryBooks > 0) {
                exportFileLibraryChk.text = resources.getQuantityString(
                    R.plurals.export_file_library,
                    nbLibraryBooks.toInt(),
                    nbLibraryBooks.toInt()
                )
                exportFileLibraryChk.setOnCheckedChangeListener { _, _ -> refreshDisplay() }
                exportGroupNo.addView(exportFileLibraryChk)
            }
            if (nbQueueBooks > 0) {
                exportFileQueueChk.text = resources.getQuantityString(
                    R.plurals.export_file_queue,
                    nbQueueBooks.toInt(),
                    nbQueueBooks.toInt()
                )
                exportFileQueueChk.setOnCheckedChangeListener { _, _ -> refreshDisplay() }
                exportGroupNo.addView(exportFileQueueChk)
            }
            if (nbBookmarks > 0) {
                exportFileBookmarksChk.text = resources.getQuantityString(
                    R.plurals.export_file_bookmarks,
                    nbBookmarks.toInt(),
                    nbBookmarks.toInt()
                )
                exportFileBookmarksChk.setOnCheckedChangeListener { _, _ -> refreshDisplay() }
                exportGroupNo.addView(exportFileBookmarksChk)
            }

            // Open library transfer FAQ
            exportWikiLink.setOnClickListener { requireActivity().startBrowserActivity(Consts.URL_GITHUB_WIKI_TRANSFER) }
            exportRunBtn.isEnabled = false
            if (0L == nbLibraryBooks + nbQueueBooks + nbBookmarks)
                exportRunBtn.visibility = View.GONE
            else exportRunBtn.setOnClickListener {
                runExport(
                    exportFileLibraryChk.isChecked,
                    exportFavsOnly.isChecked,
                    exportGroups.isChecked,
                    exportFileQueueChk.isChecked,
                    exportFileBookmarksChk.isChecked
                )
            }
        }
    }

    // Gray out run button if no option is selected
    private fun refreshDisplay() {
        binding?.apply {
            exportRunBtn.isEnabled =
                exportFileQueueChk.isChecked || exportFileLibraryChk.isChecked || exportFileBookmarksChk.isChecked
            exportLocation.isVisible = exportFileLibraryChk.isChecked
            exportFavsOnly.isVisible = exportFileLibraryChk.isChecked
            exportGroups.isVisible = exportFileLibraryChk.isChecked
        }
    }

    private fun refreshFavsDisplay() {
        binding?.let {
            val nbLibraryBooks = dao.countAllInternalBooks(
                getSelectedRootPath(locationIndex),
                it.exportFavsOnly.isChecked
            )
            it.exportFileLibraryChk.text = resources.getQuantityString(
                R.plurals.export_file_library,
                nbLibraryBooks.toInt(),
                nbLibraryBooks.toInt()
            )
            refreshDisplay()
        }
    }

    private fun getSelectedRootPath(locationIndex: Int): String {
        return if (locationIndex > 0) {
            var root =
                ContentHelper.getPathRoot(if (1 == locationIndex) StorageLocation.PRIMARY_1 else StorageLocation.PRIMARY_2)
            if (root.isEmpty()) root = "FAIL" // Auto-fails condition if location is not set
            root
        } else ""
    }

    private fun runExport(
        exportLibrary: Boolean,
        exportFavsOnly: Boolean,
        exportCustomGroups: Boolean,
        exportQueue: Boolean,
        exportBookmarks: Boolean
    ) {
        isCancelable = false

        binding?.let {
            it.exportFileLibraryChk.isEnabled = false
            it.exportFileQueueChk.isEnabled = false
            it.exportFileBookmarksChk.isEnabled = false
            it.exportRunBtn.visibility = View.GONE
            it.exportProgressBar.isIndeterminate = true

            // fixes <= Lollipop progressBar tinting
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) it.exportProgressBar.indeterminateDrawable.colorFilter =
                PorterDuffColorFilter(
                    ThemeHelper.getColor(
                        requireContext(),
                        R.color.secondary_light
                    ), PorterDuff.Mode.SRC_IN
                )
            it.exportProgressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val collection = getExportedCollection(
                            exportLibrary,
                            exportFavsOnly,
                            exportCustomGroups,
                            exportQueue,
                            exportBookmarks
                        )
                        return@withContext JsonHelper.serializeToJson(
                            collection,
                            JsonContentCollection::class.java
                        )
                    } catch (e: Exception) {
                        Timber.w(e)
                        Helper.logException(e)
                        Snackbar.make(
                            it.root,
                            R.string.export_failed,
                            BaseTransientBottomBar.LENGTH_LONG
                        ).show()
                        // Dismiss after 3s, for the user to be able to see and use the snackbar
                        delay(3000)
                        dismissAllowingStateLoss()
                    }
                    return@withContext ""
                }
                if (result.isNotEmpty()) {
                    it.exportProgressBar.max = 2
                    it.exportProgressBar.progress = 1
                    it.exportProgressBar.isIndeterminate = false
                    onJsonSerialized(
                        result,
                        exportLibrary,
                        exportFavsOnly,
                        exportQueue,
                        exportBookmarks
                    )
                }
                it.exportProgressBar.progress = 2
            }
        }
    }

    private fun getExportedCollection(
        exportLibrary: Boolean,
        exportFavsOnly: Boolean,
        exportCustomgroups: Boolean,
        exportQueue: Boolean,
        exportBookmarks: Boolean
    ): JsonContentCollection {
        val jsonContentCollection = JsonContentCollection()

        if (exportLibrary) dao.streamAllInternalBooks(
            getSelectedRootPath(locationIndex),
            exportFavsOnly
        ) { content: Content ->
            jsonContentCollection.addToLibrary(content)
        } // Using streaming here to support large collections
        if (exportQueue) {
            val regularQueue = dao.selectQueue()
            val errorsQueue = dao.selectErrorContent()
            val exportedQueue = regularQueue.filter { qr -> qr.contentId > 0 }
                .map { qr ->
                    val c = qr.content.target
                    c.isFrozen = qr.isFrozen
                    return@map c
                }.toMutableList()
            exportedQueue.addAll(errorsQueue)
            jsonContentCollection.queue = exportedQueue
        }
        if (exportCustomgroups) jsonContentCollection.setGroups(
            Grouping.CUSTOM,
            dao.selectGroups(Grouping.CUSTOM.id)
        )
        if (exportBookmarks) jsonContentCollection.bookmarks = dao.selectAllBookmarks()
        jsonContentCollection.renamingRules =
            dao.selectRenamingRules(AttributeType.UNDEFINED, null)
        return jsonContentCollection
    }

    private fun onJsonSerialized(
        json: String,
        exportLibrary: Boolean,
        exportFavsOnly: Boolean,
        exportQueue: Boolean,
        exportBookmarks: Boolean
    ) {
        // Use a random number to avoid erasing older exports by mistake
        var targetFileName = Helper.getRandomInt(9999).toString() + ".json"
        if (exportBookmarks) targetFileName = "bkmks-$targetFileName"
        if (exportQueue) targetFileName = "queue-$targetFileName"
        if (exportLibrary && !exportFavsOnly) targetFileName =
            "library-$targetFileName" else if (exportLibrary) targetFileName =
            "favs-$targetFileName"
        targetFileName = "export-$targetFileName"
        try {
            FileHelper.openNewDownloadOutputStream(
                requireContext(),
                targetFileName,
                JsonHelper.JSON_MIME_TYPE
            ).use { newDownload ->
                ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8))
                    .use { input -> Helper.copy(input, newDownload) }
            }
            binding?.let {
                Snackbar.make(
                    it.root,
                    R.string.copy_download_folder_success,
                    BaseTransientBottomBar.LENGTH_LONG
                )
                    .setAction(R.string.open_folder) {
                        FileHelper.openFile(
                            requireContext(),
                            FileHelper.getDownloadsFolder()
                        )
                    }
                    .show()
            }
        } catch (e: IOException) {
            binding?.let {
                Snackbar.make(
                    it.root,
                    R.string.copy_download_folder_fail,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()
            }
        } catch (e: IllegalArgumentException) {
            binding?.let {
                Snackbar.make(
                    it.root,
                    R.string.copy_download_folder_fail,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()
            }
        }
        dao.cleanup()
        // Dismiss after 3s, for the user to be able to see and use the snackbar
        Handler(Looper.getMainLooper()).postDelayed({ this.dismissAllowingStateLoss() }, 3000)
    }

    companion object {
        fun invoke(fragmentManager: FragmentManager) {
            val fragment = MetaExportDialogFragment()
            fragment.show(fragmentManager, null)
        }
    }
}