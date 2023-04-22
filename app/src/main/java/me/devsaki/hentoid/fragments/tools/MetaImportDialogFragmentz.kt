package me.devsaki.hentoid.fragments.tools

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.WORK_CLOSEABLE
import me.devsaki.hentoid.databinding.DialogToolsMetaImportBinding
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.events.ServiceDestroyedEvent
import me.devsaki.hentoid.json.JsonContentCollection
import me.devsaki.hentoid.notification.import_.ImportNotificationChannel
import me.devsaki.hentoid.util.ImportHelper
import me.devsaki.hentoid.util.ImportHelper.PickFileContract
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.workers.MetadataImportWorker
import me.devsaki.hentoid.workers.data.MetadataImportData
import org.apache.commons.lang3.tuple.ImmutablePair
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.io.IOException

class MetaImportDialogFragmentz : DialogFragment() {
    // UI
    private var binding: DialogToolsMetaImportBinding? = null

    private var isServiceGracefulClose = false


    private val pickFile = registerForActivityResult(PickFileContract())
    { result: ImmutablePair<Int, Uri> ->
        onFilePickerResult(result.left, result.right)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogToolsMetaImportBinding.inflate(inflater, container, false)
        EventBus.getDefault().register(this)
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
        binding = null
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        val browseModes = resources.getStringArray(R.array.tools_import_empty_books_entries)
        val browseItems = ArrayList(listOf(*browseModes))
        binding?.apply {
            importEmptyBooksOptions.setItems(browseItems)
            importEmptyBooksOptions.setOnSpinnerItemSelectedListener { _, _: String?, _, _: String -> refreshDisplay() }
            importSelectFileBtn.setOnClickListener { pickFile.launch(0) }
        }
    }

    private fun onFilePickerResult(resultCode: Int, uri: Uri) {
        binding?.apply {
            when (resultCode) {
                ImportHelper.PickerResult.OK -> {
                    // File selected
                    val doc = DocumentFile.fromSingleUri(requireContext(), uri) ?: return
                    importSelectFileBtn.visibility = View.GONE
                    checkFile(doc)
                }

                ImportHelper.PickerResult.KO_CANCELED -> Snackbar.make(
                    root,
                    R.string.import_canceled,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                ImportHelper.PickerResult.KO_NO_URI -> Snackbar.make(
                    root,
                    R.string.import_invalid,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                ImportHelper.PickerResult.KO_OTHER -> Snackbar.make(
                    root,
                    R.string.import_other,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()

                else -> {}
            }
        }
    }

    private fun checkFile(jsonFile: DocumentFile) {
        binding?.apply {
            // Display indeterminate progress bar while file is being deserialized
            importProgressText.setText(R.string.checking_file)
            importProgressBar.isIndeterminate = true
            importProgressText.visibility = View.VISIBLE
            importProgressBar.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    deserialiseJson(jsonFile)
                }
                onFileDeserialized(result, jsonFile)
            } catch (e: Exception) {
                binding?.apply {
                    val fileName = StringHelper.protect(jsonFile.name)
                    binding?.apply {
                        importProgressText.text = resources.getString(
                            R.string.import_file_invalid,
                            fileName
                        )
                        importProgressBar.visibility = View.INVISIBLE
                    }
                }
                Timber.w(e)
            }
        }
    }

    private fun onFileDeserialized(
        collection: JsonContentCollection?,
        jsonFile: DocumentFile
    ) {
        binding?.apply {
            importProgressText.visibility = View.GONE
            importProgressBar.visibility = View.GONE
            if (null == collection || collection.isEmpty) {
                importFileInvalidText.text = resources.getString(
                    R.string.import_file_invalid,
                    jsonFile.name
                )
                importFileInvalidText.visibility = View.VISIBLE
            } else {
                importSelectFileBtn.visibility = View.GONE
                importFileInvalidText.visibility = View.GONE
                // Don't link the groups, just count the books
                val librarySize = collection.jsonLibrary.size
                if (librarySize > 0) {
                    importFileLibraryChk.text = resources.getQuantityString(
                        R.plurals.import_file_library,
                        librarySize,
                        librarySize
                    )
                    importFileLibraryChk.setOnCheckedChangeListener { _, _ -> refreshDisplay() }
                    importFileLibraryChk.visibility = View.VISIBLE
                }
                val mQueueSize = collection.jsonQueue.size
                if (mQueueSize > 0) {
                    importFileQueueChk.text = resources.getQuantityString(
                        R.plurals.import_file_queue,
                        mQueueSize,
                        mQueueSize
                    )
                    importFileQueueChk.setOnCheckedChangeListener { _, _ -> refreshDisplay() }
                    importFileQueueChk.visibility = View.VISIBLE
                }
                val mGroupsSize = collection.getGroups(Grouping.CUSTOM).size
                if (mGroupsSize > 0) {
                    importFileGroupsChk.text = resources.getQuantityString(
                        R.plurals.import_file_groups,
                        mGroupsSize,
                        mGroupsSize
                    )
                    importFileGroupsChk.setOnCheckedChangeListener { _, _ -> refreshDisplay() }
                    importFileGroupsChk.visibility = View.VISIBLE
                }
                val bookmarksSize = collection.bookmarks.size
                if (bookmarksSize > 0) {
                    importFileBookmarksChk.text = resources.getQuantityString(
                        R.plurals.import_file_bookmarks,
                        bookmarksSize,
                        bookmarksSize
                    )
                    importFileBookmarksChk.setOnCheckedChangeListener { _, _ -> refreshDisplay() }
                    importFileBookmarksChk.visibility = View.VISIBLE
                }
                importRunBtn.visibility = View.VISIBLE
                importRunBtn.isEnabled = false
                importRunBtn.setOnClickListener {
                    runImport(
                        jsonFile.uri.toString(),
                        importModeAdd.isChecked,
                        importFileLibraryChk.isChecked,
                        importEmptyBooksOptions.selectedIndex,
                        importFileQueueChk.isChecked,
                        importFileGroupsChk.isChecked,
                        importFileBookmarksChk.isChecked
                    )
                }
            }
        }
    }

    // Gray out run button if no option is selected
    private fun refreshDisplay() {
        binding?.apply {
            importEmptyBooksLabel.visibility =
                if (importFileLibraryChk.isChecked) View.VISIBLE else View.GONE
            importEmptyBooksOptions.visibility =
                (if (importFileLibraryChk.isChecked) View.VISIBLE else View.GONE)
            importRunBtn.isEnabled =
                importFileLibraryChk.isChecked && importEmptyBooksOptions.selectedIndex > -1 || !importFileLibraryChk.isChecked && (importFileQueueChk.isChecked || importFileBookmarksChk.isChecked)
        }
    }

    private fun deserialiseJson(jsonFile: DocumentFile): JsonContentCollection? {
        val result: JsonContentCollection = try {
            JsonHelper.jsonToObject(
                requireContext(), jsonFile,
                JsonContentCollection::class.java
            )
        } catch (e: IOException) {
            Timber.w(e)
            return null
        }
        return result
    }

    private fun runImport(
        jsonUri: String,
        add: Boolean,
        importLibrary: Boolean,
        emptyBooksOption: Int,
        importQueue: Boolean,
        importCustomGroups: Boolean,
        importBookmarks: Boolean
    ) {
        binding?.apply {
            importMode.isEnabled = false
            importModeAdd.isEnabled = false
            importModeReplace.isEnabled = false
            importFileLibraryChk.isEnabled = false
            importFileQueueChk.isEnabled = false
            importFileGroupsChk.isEnabled = false
            importFileBookmarksChk.isEnabled = false
            importEmptyBooksOptions.isEnabled = false
            importRunBtn.visibility = View.GONE
            isCancelable = false
            val builder = MetadataImportData.Builder()
            builder.setJsonUri(jsonUri)
            builder.setIsAdd(add)
            builder.setIsImportLibrary(importLibrary)
            builder.setEmptyBooksOption(emptyBooksOption)
            builder.setIsImportQueue(importQueue)
            builder.setIsImportCustomGroups(importCustomGroups)
            builder.setIsImportBookmarks(importBookmarks)
            ImportNotificationChannel.init(requireContext())
            importProgressText.setText(R.string.starting_import)
            importProgressBar.isIndeterminate = true
            importProgressText.visibility = View.VISIBLE
            importProgressBar.visibility = View.VISIBLE
            val workManager = WorkManager.getInstance(requireContext())
            workManager.enqueueUniqueWork(
                R.id.metadata_import_service.toString(),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequest.Builder(MetadataImportWorker::class.java)
                    .setInputData(builder.data)
                    .addTag(WORK_CLOSEABLE).build()
            )
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onImportEvent(event: ProcessEvent) {
        if (event.processId != R.id.import_metadata) return
        importEvent(event)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onImportStickyEvent(event: ProcessEvent) {
        if (event.processId != R.id.import_metadata) return
        EventBus.getDefault().removeStickyEvent(event)
        importEvent(event)
    }

    private fun importEvent(event: ProcessEvent) {
        binding?.apply {
            if (ProcessEvent.EventType.PROGRESS == event.eventType) {
                val progress = event.elementsOK + event.elementsKO
                val itemTxt = resources.getQuantityString(R.plurals.item, progress)
                importProgressText.text = resources.getString(
                    R.string.generic_progress,
                    progress,
                    event.elementsTotal,
                    itemTxt
                )
                importProgressBar.max = event.elementsTotal
                importProgressBar.progress = progress
                importProgressBar.isIndeterminate = false
            } else if (ProcessEvent.EventType.COMPLETE == event.eventType) {
                isServiceGracefulClose = true
                Snackbar.make(
                    root,
                    resources.getQuantityString(
                        R.plurals.import_result,
                        event.elementsOK,
                        event.elementsOK,
                        event.elementsTotal
                    ),
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()
            }
        }
        // Dismiss after 3s, for the user to be able to see the snackbar
        Handler(Looper.getMainLooper()).postDelayed({ dismissAllowingStateLoss() }, 3000)
    }

    /**
     * Service destroyed event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onServiceDestroyed(event: ServiceDestroyedEvent) {
        if (event.service != R.id.metadata_import_service) return
        if (!isServiceGracefulClose) {
            binding?.apply {
                Snackbar.make(
                    root,
                    R.string.import_unexpected,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()
            }
            Handler(Looper.getMainLooper()).postDelayed({ dismissAllowingStateLoss() }, 3000)
        }
    }

    companion object {
        // Empty files import options
        const val DONT_IMPORT = 0
        const val IMPORT_AS_EMPTY = 1
        const val IMPORT_AS_STREAMED = 2
        const val IMPORT_AS_ERROR = 3

        operator fun invoke(fragmentManager: FragmentManager) {
            val fragment = MetaImportDialogFragmentz()
            fragment.show(fragmentManager, null)
        }
    }
}