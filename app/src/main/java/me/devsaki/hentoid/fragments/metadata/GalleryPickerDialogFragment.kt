package me.devsaki.hentoid.fragments.metadata

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.databinding.DialogMetaGalleryBinding
import me.devsaki.hentoid.viewholders.ImageFileItem

/**
 * Dialog to pick a picture in a content gallery
 */
class GalleyPickerDialogFragment : DialogFragment() {

    // UI
    private var _binding: DialogMetaGalleryBinding? = null
    private val binding get() = _binding!!
    private val itemAdapter = ItemAdapter<ImageFileItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)

    // === VARIABLES
    private var parent: Parent? = null
    private lateinit var imageIds: LongArray


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imgs = requireArguments().getLongArray(KEY_IMGS)
        requireNotNull(imgs) { "No images provided" }
        require(imgs.isNotEmpty()) { "No images provided" }

        imageIds = imgs
        parent = activity as Parent
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        _binding = DialogMetaGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        parent = null
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        itemAdapter.set(loadImages().map { i -> ImageFileItem(i, false) })

        fastAdapter.onClickListener =
            { _: View?, _: IAdapter<ImageFileItem>, i: ImageFileItem, _: Int ->
                onItemClick(i.getImage().order)
            }

        binding.recyclerView.adapter = fastAdapter
    }

    private fun loadImages(): List<ImageFile> {
        val result: List<ImageFile>
        val dao: CollectionDAO = ObjectBoxDAO(requireContext())
        try {
            result = dao.selectImageFiles(imageIds)
        } finally {
            dao.cleanup()
        }
        return result
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

    companion object {
        const val KEY_IMGS = "image_ids"

        fun invoke(fragmentManager: FragmentManager, images: List<ImageFile>) {
            val fragment = GalleyPickerDialogFragment()

            val args = Bundle()
            args.putLongArray(KEY_IMGS, images.map { i -> i.id }.toLongArray())
            fragment.arguments = args

            fragment.show(fragmentManager, null)
        }
    }

    interface Parent {
        fun onPageSelected(index: Int)
    }
}