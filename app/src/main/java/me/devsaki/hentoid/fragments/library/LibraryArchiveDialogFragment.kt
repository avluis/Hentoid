package me.devsaki.hentoid.fragments.library

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.fragment.app.FragmentActivity
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.material.textfield.TextInputLayout
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.WORK_CLOSEABLE
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.DialogLibraryArchiveBinding
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.ImportHelper
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.file.RQST_STORAGE_PERMISSION
import me.devsaki.hentoid.util.file.requestExternalStorageReadWritePermission
import me.devsaki.hentoid.workers.ArchiveWorker
import org.apache.commons.lang3.tuple.ImmutablePair

class LibraryArchiveDialogFragment : BaseDialogFragment<LibraryArchiveDialogFragment.Parent>() {
    companion object {

        const val KEY_CONTENTS = "contents"

        fun invoke(parent: FragmentActivity, contentList: List<Content>) {
            invoke(parent, LibraryArchiveDialogFragment(), getArgs(contentList))
        }

        private fun getArgs(contentList: List<Content>): Bundle {
            val args = Bundle()
            args.putLongArray(
                KEY_CONTENTS, contentList.map { c -> c.id }.toLongArray()
            )
            return args
        }
    }


    // UI
    private var binding: DialogLibraryArchiveBinding? = null

    // === VARIABLES
    private lateinit var contentIds: LongArray

    private val pickFolder =
        registerForActivityResult(ImportHelper.PickFolderContract()) { result: ImmutablePair<Int, Uri> ->
            onFolderPickerResult(result.left, result.right)
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireNotNull(arguments) { "No arguments found" }
        val contentIdArg = arguments?.getLongArray(KEY_CONTENTS)
        require(!(null == contentIdArg || contentIdArg.isEmpty())) { "No content IDs" }
        contentIds = contentIdArg!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        binding = DialogLibraryArchiveBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        refreshControls(true)

        binding?.apply {
            targetFolder.setOnIndexChangeListener { index ->
                when (index) {
                    0 -> Settings.archiveTargetFolder =
                        Settings.Value.ARCHIVE_TARGET_FOLDER_DOWNLOADS

                    targetFolder.entries.size - 1 -> { // Last item => Pick a folder
                        // Make sure permissions are set
                        if (requireActivity().requestExternalStorageReadWritePermission(
                                RQST_STORAGE_PERMISSION
                            )
                        ) {
                            // Run folder picker
                            pickFolder.launch(StorageLocation.NONE)
                        }
                    }

                    else -> Settings.archiveTargetFolder = Settings.latestTargetFolderUri
                }
                refreshControls()
            }
            targetFormat.setOnIndexChangeListener { index ->
                Settings.archiveTargetFormat = index
                refreshControls()
            }
            overwriteSwitch.setOnCheckedChangeListener { _, isChecked ->
                Settings.isArchiveOverwrite = isChecked
                refreshControls()
            }
            deleteSwitch.setOnCheckedChangeListener { _, isChecked ->
                Settings.isArchiveDeleteOnSuccess = isChecked
                refreshControls()
            }
            action.setOnClickListener { onActionClick(buildWorkerParams()) }
        }
    }

    private fun refreshControls(applyValues: Boolean = false) {
        binding?.apply {
            if (applyValues) {
                val entries = mutableListOf(
                    resources.getString(R.string.folder_device_downloads),
                    resources.getString(R.string.folder_other)
                )
                if (Settings.latestTargetFolderUri.isNotEmpty()) {
                    val uri = Uri.parse(Settings.latestTargetFolderUri)
                    if (FileHelper.getDocumentFromTreeUriString(
                            requireContext(),
                            uri.toString()
                        ) != null
                    ) {
                        entries.add(
                            1,
                            FileHelper.getFullPathFromUri(
                                requireContext(),
                                Uri.parse(Settings.latestTargetFolderUri)
                            )
                        )
                    }
                }
                targetFolder.entries = entries
                targetFolder.index =
                    if (Settings.archiveTargetFolder == Settings.latestTargetFolderUri) 1 else 0

                targetFormat.index = Settings.archiveTargetFormat

                overwriteSwitch.isChecked = Settings.isArchiveOverwrite
                deleteSwitch.isChecked = Settings.isArchiveDeleteOnSuccess
            }
        }
    }

    private fun onFolderPickerResult(resultCode: Int, uri: Uri) {
        when (resultCode) {
            ImportHelper.PickerResult.OK -> {
                // Persist I/O permissions; keep existing ones if present
                ImportHelper.persistLocationCredentials(
                    requireContext(),
                    uri,
                    listOf(
                        StorageLocation.PRIMARY_1,
                        StorageLocation.PRIMARY_2,
                        StorageLocation.EXTERNAL
                    )
                )
                Settings.latestTargetFolderUri = uri.toString()
                Settings.archiveTargetFolder = uri.toString()
                refreshControls(true)
            }

            else -> {}
        }
    }

    private fun buildWorkerParams(): ArchiveWorker.Params {
        binding!!.apply {
            return ArchiveWorker.Params(
                Settings.archiveTargetFolder,
                targetFormat.index,
                overwriteSwitch.isChecked,
                deleteSwitch.isChecked
            )
        }
    }

    private fun onActionClick(params: ArchiveWorker.Params) {
        binding?.apply {
            // Check if no dialog is in error state
            val nbError = container.children
                .filter { c -> c is TextInputLayout }
                .map { c -> c as TextInputLayout }
                .count { til -> til.isErrorEnabled }

            if (nbError > 0) return

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val serializedParams = moshi.adapter(ArchiveWorker.Params::class.java).toJson(params)

            val myData: Data = workDataOf(
                "IDS" to contentIds,
                "PARAMS" to serializedParams
            )

            val workManager = WorkManager.getInstance(requireContext())
            workManager.enqueueUniqueWork(
                R.id.archive_service.toString(),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequest.Builder(ArchiveWorker::class.java)
                    .setInputData(myData)
                    .addTag(WORK_CLOSEABLE).build()
            )
        }
        parent?.leaveSelectionMode()
        dismissAllowingStateLoss()
    }

    interface Parent {
        fun leaveSelectionMode()
    }
}