package me.devsaki.hentoid.fragments.reader

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil3.load
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.ReaderActivityBundle
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.databinding.IncludeReaderImageBottomPanelBinding
import me.devsaki.hentoid.util.exception.ContentNotProcessedException
import me.devsaki.hentoid.util.file.fileExists
import me.devsaki.hentoid.util.file.fileSizeFromUri
import me.devsaki.hentoid.util.file.formatHumanReadableSize
import me.devsaki.hentoid.util.file.getFullPathFromUri
import me.devsaki.hentoid.util.file.openFile
import me.devsaki.hentoid.util.file.shareFile
import me.devsaki.hentoid.util.getIdForCurrentTheme
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.image.getImageDimensions
import me.devsaki.hentoid.util.setStyle
import me.devsaki.hentoid.viewmodels.ReaderViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import timber.log.Timber
import java.io.File
import androidx.core.net.toUri

class ReaderImageBottomSheetFragment : BottomSheetDialogFragment(),
    ReaderCopyImgDialogFragment.Parent {

    // Communication
    private lateinit var viewModel: ReaderViewModel

    // UI
    private var binding: IncludeReaderImageBottomPanelBinding? = null

    // VARS
    private var imageIndex: Int = -1
    private var scale = -1f
    private var image: ImageFile? = null


    override fun onAttach(context: Context) {
        super.onAttach(context)

        val bundle = arguments
        if (bundle != null) {
            val parser = ReaderActivityBundle(bundle)
            imageIndex = parser.imageIndex
            require(-1 != imageIndex) { "Initialization failed : invalid image index" }
            scale = parser.scale
        }

        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[ReaderViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = IncludeReaderImageBottomPanelBinding.inflate(inflater, container, false)

        binding?.apply {
            imgActionFavourite.setOnClickListener { onFavouriteClick() }
            imgActionCopy.setOnClickListener { onCopyClick() }
            imgActionShare.setOnClickListener { onShareClick() }
            imgActionDelete.setOnClickListener { onDeleteClick() }
        }

        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.getViewerImages().observe(viewLifecycleOwner) { images ->
            this.onImagesChanged(images)
        }
    }


    /**
     * Observer for changes in the book's list of images
     *
     * @param images Book's list of images
     */
    private fun onImagesChanged(images: List<ImageFile>) {
        val grayColor = requireContext().getThemedColor(R.color.dark_gray)
        // Might happen when deleting the last page
        if (imageIndex >= images.size) imageIndex = images.size - 1

        images[imageIndex].let {
            image = it
            var filePath: String
            if (it.isArchived) {
                filePath = it.url
                val lastSeparator = filePath.lastIndexOf('/')
                val archiveUri = filePath.substring(0, lastSeparator)
                val fileName = filePath.substring(lastSeparator)
                filePath =
                    getFullPathFromUri(
                        requireContext(),
                        archiveUri.toUri()
                    ) + fileName
            } else {
                filePath =
                    getFullPathFromUri(requireContext(), it.fileUri.toUri())
            }

            binding?.apply {
                imagePath.text = filePath
                val imageExists = fileExists(requireContext(), it.fileUri.toUri())
                if (imageExists) {
                    lifecycleScope.launch {
                        val dimensions = getImageDimensions(requireContext(), it.fileUri)
                        val sizeStr: String = if (it.size > 0) {
                            formatHumanReadableSize(it.size, resources)
                        } else {
                            val size =
                                fileSizeFromUri(requireContext(), it.fileUri.toUri())
                            formatHumanReadableSize(size, resources)
                        }
                        imageStats.text = resources.getString(
                            R.string.viewer_img_details,
                            dimensions.x,
                            dimensions.y,
                            scale * 100,
                            sizeStr
                        )
                    }
                    ivThumb.load(it.fileUri)
                } else {
                    imageStats.setText(R.string.image_not_found)
                    imgActionFavourite.imageTintList = ColorStateList.valueOf(grayColor)
                    imgActionFavourite.isEnabled = false
                    imgActionCopy.imageTintList = ColorStateList.valueOf(grayColor)
                    imgActionCopy.isEnabled = false
                    imgActionShare.imageTintList = ColorStateList.valueOf(grayColor)
                    imgActionShare.isEnabled = false
                }
                // Don't allow deleting the image if it is archived
                if (it.isArchived) {
                    imgActionDelete.imageTintList = ColorStateList.valueOf(grayColor)
                    imgActionDelete.isEnabled = false
                } else {
                    imgActionDelete.imageTintList = null
                    imgActionDelete.isEnabled = true
                }
            }
            updateFavouriteDisplay(it.favourite)
        }
    }

    /**
     * Handle click on "Favourite" action button
     */
    private fun onFavouriteClick() {
        viewModel.toggleImageFavourite(imageIndex) { newState: Boolean ->
            onToggleFavouriteSuccess(newState)
        }
    }

    /**
     * Success callback when the new favourite'd state has been successfully persisted
     */
    private fun onToggleFavouriteSuccess(newState: Boolean) {
        image!!.favourite = newState
        updateFavouriteDisplay(newState)
    }

    /**
     * Update the display of the "favourite page" action button
     *
     * @param isFavourited True if the button has to represent a favourite page; false instead
     */
    private fun updateFavouriteDisplay(isFavourited: Boolean) {
        binding?.imgActionFavourite?.apply {
            if (isFavourited) setImageResource(R.drawable.ic_fav_full)
            else setImageResource(R.drawable.ic_fav_empty)
        }
    }

    /**
     * Handle click on "Copy" action button
     */
    private fun onCopyClick() {
        image?.let {
            ReaderCopyImgDialogFragment.invoke(this, it.id)
        }
    }

    /**
     * Handle click on "Share" action button
     */
    private fun onShareClick() {
        image?.let {
            val fileUri = it.fileUri.toUri()
            if (fileExists(requireContext(), fileUri)) shareFile(
                requireContext(),
                fileUri,
                "",
                "image/*"
            )
        }
    }

    /**
     * Handle click on "Delete" action button
     */
    private fun onDeleteClick() {
        MaterialAlertDialogBuilder(
            requireContext(),
            requireContext().getIdForCurrentTheme(R.style.Theme_Light_Dialog)
        )
            .setIcon(R.drawable.ic_warning)
            .setCancelable(false)
            .setTitle(R.string.app_name)
            .setMessage(R.string.viewer_ask_delete_page)
            .setPositiveButton(R.string.yes) { dialog1, _ ->
                dialog1.dismiss()
                viewModel.deletePage(imageIndex) { t: Throwable -> onDeleteError(t) }
            }
            .setNegativeButton(R.string.no) { dialog12, _ -> dialog12.dismiss() }
            .create()
            .show()
    }

    /**
     * Callback for the failure of the "delete item" action
     */
    private fun onDeleteError(t: Throwable) {
        Timber.e(t)
        if (t is ContentNotProcessedException) {
            val message =
                if (null == t.message) resources.getString(R.string.file_removal_failed) else t.message!!
            binding?.apply {
                Snackbar.make(root, message, BaseTransientBottomBar.LENGTH_LONG).show()
            }
        }
    }

    override fun feedback(message: Int, file: Any?) {
        binding?.apply {
            val snack = Snackbar.make(root, message, BaseTransientBottomBar.LENGTH_LONG)
            if (file != null) {
                snack.setAction(R.string.open_folder)
                {
                    if (file is DocumentFile) openFile(requireContext(), file)
                    else if (file is File) openFile(requireContext(), file)
                }
            }
            snack.show()
        }
    }

    companion object {
        fun invoke(
            context: Context,
            fragmentManager: FragmentManager,
            imageIndex: Int,
            currentScale: Float
        ) {
            val bottomSheetFragment = ReaderImageBottomSheetFragment()

            val builder = ReaderActivityBundle()
            builder.imageIndex = imageIndex
            builder.scale = currentScale
            bottomSheetFragment.arguments = builder.bundle

            context.setStyle(
                bottomSheetFragment,
                STYLE_NORMAL,
                R.style.Theme_Light_BottomSheetDialog
            )

            bottomSheetFragment.show(fragmentManager, "imageBottomSheetFragment")
        }
    }
}