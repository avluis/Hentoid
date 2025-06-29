package me.devsaki.hentoid.fragments.metadata

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.databinding.DialogMetaGalleryBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.file.PdfManager
import me.devsaki.hentoid.util.file.extractArchiveEntriesCached
import me.devsaki.hentoid.util.file.getFileFromSingleUriString
import me.devsaki.hentoid.util.formatCacheKey
import me.devsaki.hentoid.viewholders.ImageFileItem
import java.io.File

/**
 * Dialog to pick a picture in a content gallery
 */
class GalleryPickerDialogFragment : BaseDialogFragment<GalleryPickerDialogFragment.Parent>() {

    companion object {
        const val KEY_IMGS = "image_ids"

        fun invoke(activity: FragmentActivity, images: List<ImageFile>) {
            val args = Bundle()
            args.putLongArray(KEY_IMGS, images.map { it.id }.toLongArray())
            invoke(activity, GalleryPickerDialogFragment(), args)
        }
    }

    // UI
    private var binding: DialogMetaGalleryBinding? = null
    private val itemAdapter = ItemAdapter<ImageFileItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)

    // === VARIABLES
    private lateinit var imageIds: LongArray


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imgs = requireArguments().getLongArray(KEY_IMGS)
        requireNotNull(imgs) { "No images provided" }
        require(imgs.isNotEmpty()) { "No images provided" }

        imageIds = imgs
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        binding = DialogMetaGalleryBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        lifecycleScope.launch {
            val images = loadImages()
            // 1st pass (and last for non-archives / PDFs)
            itemAdapter.set(images.map { ImageFileItem(it, false) })
            fastAdapter.onClickListener =
                { _: View?, _: IAdapter<ImageFileItem>, i: ImageFileItem, _: Int ->
                    onItemClick(i.getImage().order)
                }

            binding?.recyclerView?.adapter = fastAdapter

            if (images.isEmpty()) return@launch
            images[0].linkedContent?.let { content ->
                if (content.isPdf || content.isArchive) {
                    extractArchivePdf(content, images)
                    // 2nd pass to map extracted pics
                    itemAdapter.set(images.map { ImageFileItem(it, false) })
                }
            }
        }
    }

    private suspend fun loadImages(): List<ImageFile> = withContext(Dispatchers.IO) {
        val result: List<ImageFile>

        // Get image list from DB
        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            result = dao.selectImageFiles(imageIds).filter { it.isReadable }
        } finally {
            dao.cleanup()
        }

        result.forEachIndexed { idx, img -> img.displayOrder = idx }

        return@withContext result
    }

    private suspend fun extractArchivePdf(
        content: Content,
        imgs: List<ImageFile>
    ) = withContext(Dispatchers.IO) {
        // Unarchive to cache if archive
        getFileFromSingleUriString(requireContext(), content.storageUri)?.let { archiveFile ->
            val extractInstructions: MutableList<Triple<String, Long, String>> = ArrayList()
            // Load first 10 pics (roughly the same behaviour as the reader for now - see #1227)
            for (i in 0..minOf(imgs.size - 1, 9)) {
                val img = imgs[i]
                extractInstructions.add(
                    Triple(
                        img.url.replace(content.storageUri + File.separator, ""),
                        img.id,
                        formatCacheKey(img)
                    )
                )
            }

            if (content.isPdf) {
                val pdfManager = PdfManager()
                pdfManager.extractImagesCached(
                    requireContext(),
                    archiveFile,
                    extractInstructions,
                    interrupt = null,
                    { id, uri ->
                        imgs.first { it.id == id }.fileUri = uri.toString()
                    })
            } else if (content.isArchive) {
                requireContext().extractArchiveEntriesCached(
                    archiveFile.uri,
                    extractInstructions,
                    interrupt = null,
                    { id, uri ->
                        imgs.first { it.id == id }.fileUri = uri.toString()
                    })
            }
        }
    }

    /**
     * Callback for image item click
     *
     * @param pageOrder Order of the clicked image
     */
    private fun onItemClick(pageOrder: Int): Boolean {
        parent?.onPageSelected(pageOrder)
        dismissAllowingStateLoss()
        return true
    }

    interface Parent {
        fun onPageSelected(index: Int)
    }
}