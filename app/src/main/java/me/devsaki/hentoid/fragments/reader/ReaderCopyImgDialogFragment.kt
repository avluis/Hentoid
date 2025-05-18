package me.devsaki.hentoid.fragments.reader

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.databinding.DialogReaderSaveImgBinding
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.PickFolderContract
import me.devsaki.hentoid.util.PickerResult
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.copy
import me.devsaki.hentoid.util.file.DEFAULT_MIME_TYPE
import me.devsaki.hentoid.util.file.RQST_STORAGE_PERMISSION
import me.devsaki.hentoid.util.file.fileExists
import me.devsaki.hentoid.util.file.findOrCreateDocumentFile
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getDownloadsFolder
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.file.getFullPathFromUri
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.file.openNewDownloadOutputStream
import me.devsaki.hentoid.util.file.requestExternalStorageReadWritePermission
import me.devsaki.hentoid.util.persistLocationCredentials
import java.io.File
import java.io.IOException
import java.io.OutputStream

class ReaderCopyImgDialogFragment : BaseDialogFragment<ReaderCopyImgDialogFragment.Parent>() {
    companion object {
        const val KEY_IMG_ID = "imgId"

        fun invoke(parent: Fragment, imgId: Long) {
            invoke(parent, ReaderCopyImgDialogFragment(), getArgs(imgId))
        }

        private fun getArgs(imgId: Long): Bundle {
            val args = Bundle()
            args.putLong(KEY_IMG_ID, imgId)
            return args
        }
    }


    // UI
    private var binding: DialogReaderSaveImgBinding? = null

    // === VARIABLES
    private var imageId = 0L

    private val pickFolder =
        registerForActivityResult(PickFolderContract()) {
            onFolderPickerResult(it.first, it.second)
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireNotNull(arguments) { "No arguments found" }
        val imgIdArg = arguments?.getLong(KEY_IMG_ID, 0) ?: 0
        require(imgIdArg > 0) { "No image ID" }
        imageId = imgIdArg
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View? {
        binding = DialogReaderSaveImgBinding.inflate(inflater, container, false)
        return binding?.root
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
                    0 -> Settings.readerTargetFolder =
                        Settings.Value.TARGET_FOLDER_DOWNLOADS

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

                    else -> Settings.readerTargetFolder = Settings.latestReaderTargetFolderUri
                }
                refreshControls()
            }
            action.setOnClickListener { onActionClick() }
        }
    }

    private fun refreshControls(applyValues: Boolean = false) {
        binding?.apply {
            if (applyValues) {
                val entries = mutableListOf(
                    resources.getString(R.string.folder_device_downloads),
                    resources.getString(R.string.folder_other)
                )
                if (Settings.latestReaderTargetFolderUri.isNotEmpty()) {
                    val uri = Settings.latestReaderTargetFolderUri.toUri()
                    if (getDocumentFromTreeUriString(
                            requireContext(),
                            uri.toString()
                        ) != null
                    ) {
                        entries.add(
                            1,
                            getFullPathFromUri(
                                requireContext(),
                                Settings.latestReaderTargetFolderUri.toUri()
                            )
                        )
                    }
                }
                targetFolder.entries = entries
                targetFolder.index =
                    if (Settings.readerTargetFolder == Settings.latestReaderTargetFolderUri) 1 else 0
            }
        }
    }

    private fun onFolderPickerResult(resultCode: PickerResult, uri: Uri) {
        when (resultCode) {
            PickerResult.OK -> {
                // Persist I/O permissions; keep existing ones if present
                persistLocationCredentials(requireContext(), uri)
                Settings.latestReaderTargetFolderUri = uri.toString()
                Settings.readerTargetFolder = uri.toString()
                refreshControls(true)
            }

            else -> {}
        }
    }

    private fun onActionClick() {
        var img: ImageFile?
        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            img = dao.selectImageFile(imageId)
        } finally {
            dao.cleanup()
        }

        img?.let {
            val prefix = it.linkedContent?.uniqueSiteId ?: it.contentId.toString()
            val targetFileName = prefix + "-" + it.name + "." + getExtension(it.fileUri)
            try {
                val fileUri = it.fileUri.toUri()
                if (!fileExists(requireContext(), fileUri)) return

                val targets = getTargets(targetFileName, it.mimeType)

                targets.first?.use { newDownload ->
                    getInputStream(requireContext(), fileUri)
                        .use { input -> copy(input, newDownload) }
                }
                val docFile = targets.second
                val file = targets.third
                val msg = if (null == docFile && file != null) R.string.copy_download_folder_success
                else R.string.copy_target_folder_success
                parent?.feedback(msg, docFile ?: file)
            } catch (_: IOException) {
                parent?.feedback(R.string.copy_target_folder_fail)
            } catch (_: IllegalArgumentException) {
                parent?.feedback(R.string.copy_target_folder_fail)
            } finally {
                dismissAllowingStateLoss()
            }
        }
    }

    private fun getTargets(
        targetFileName: String,
        mimeType: String
    ): Triple<OutputStream?, DocumentFile?, File?> {
        var outputStream: OutputStream? = null
        var docFile: DocumentFile? = null
        var file: File? = null

        if (Settings.readerTargetFolder != Settings.Value.TARGET_FOLDER_DOWNLOADS) {
            outputStream = getDocumentFromTreeUriString(
                requireContext(),
                Settings.readerTargetFolder
            )?.let { targetFolder ->
                docFile = targetFolder
                findOrCreateDocumentFile(
                    requireContext(),
                    targetFolder,
                    DEFAULT_MIME_TYPE,
                    targetFileName
                )?.let {
                    getOutputStream(requireContext(), it)
                }
            }
        }

        if (null == outputStream) {
            outputStream = openNewDownloadOutputStream(
                requireContext(),
                targetFileName,
                mimeType
            )
            docFile = null
            file = getDownloadsFolder()
        }
        return Triple(outputStream, docFile, file)
    }

    interface Parent {
        fun feedback(message: Int, file: Any? = null)
    }
}