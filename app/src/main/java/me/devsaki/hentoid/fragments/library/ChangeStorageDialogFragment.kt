package me.devsaki.hentoid.fragments.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.databinding.DialogLibraryStorageMethodBinding
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.fragments.BaseDialogFragment

/**
 * Dialog to change the storage method of selected books
 */
class ChangeStorageDialogFragment : BaseDialogFragment<ChangeStorageDialogFragment.Parent>() {
    companion object {
        private const val BOOK_IDS = "BOOK_IDS"

        operator fun invoke(parent: Fragment, bookIds: LongArray) {
            val args = Bundle()
            args.putLongArray(BOOK_IDS, bookIds)
            invoke(parent, ChangeStorageDialogFragment(), args)
        }
    }

    private lateinit var contentIds: LongArray

    private var binding: DialogLibraryStorageMethodBinding? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        binding = DialogLibraryStorageMethodBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        check(arguments != null)

        contentIds = requireArguments().getLongArray(BOOK_IDS)!!

        // Get existing storage methods
        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            val contents = dao.selectContent(contentIds)
            val nbPdf = contents.count { it.isPdf }
            val nbArchive = contents.count { it.isArchive }
            val nbStreamed = contents.count { DownloadMode.STREAM == it.downloadMode }
            val nbFolders = contents.size - nbPdf - nbArchive - nbStreamed

            val onlyFolders = nbFolders == (contents.size - nbPdf)
            val onlyArchive = nbArchive == (contents.size - nbPdf)
            val onlyStreamed = nbFolders == (contents.size - nbPdf)

            binding?.apply {
                selector.addOnButtonCheckedListener { _, checkedId, isChecked ->
                    if (!isChecked) return@addOnButtonCheckedListener

                    description.isVisible = true

                    description.text = resources.getString(
                        when (checkedId) {
                            choiceFolder.id -> R.string.storage_folder_description
                            choiceArchive.id -> R.string.storage_archive_description
                            choiceStreamed.id -> R.string.storage_streamed_description
                            else -> R.string.empty_string
                        }
                    )
                }

                if (onlyFolders) choiceFolder.isSelected = true
                if (onlyArchive) choiceArchive.isSelected = true
                if (onlyStreamed) choiceStreamed.isSelected = true

                // Item click listener
                actionButton.setOnClickListener { onOkClick() }
            }
        } finally {
            dao.cleanup()
        }
    }

    private fun onOkClick() {
        binding?.apply {
            parent?.onChangeStorageSuccess(
                when (selector.checkedButtonId) {
                    choiceArchive.id -> DownloadMode.DOWNLOAD_ARCHIVE
                    choiceStreamed.id -> DownloadMode.STREAM
                    else -> DownloadMode.DOWNLOAD
                }
            )
        }
        dismissAllowingStateLoss()
    }

    interface Parent {
        fun onChangeStorageSuccess(targetStorage: DownloadMode)
    }
}