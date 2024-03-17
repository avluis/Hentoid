package me.devsaki.hentoid.fragments.metadata

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.databinding.DialogMetaGalleryBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.viewholders.ImageFileItem

/**
 * Dialog to pick a picture in a content gallery
 */
class GalleryPickerDialogFragment : BaseDialogFragment<GalleryPickerDialogFragment.Parent>() {

    companion object {
        const val KEY_IMGS = "image_ids"

        fun invoke(activity: FragmentActivity, images: List<ImageFile>) {
            val args = Bundle()
            args.putLongArray(KEY_IMGS, images.map { i -> i.id }.toLongArray())
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
    ): View {
        binding = DialogMetaGalleryBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        itemAdapter.set(loadImages().map { i -> ImageFileItem(i, false) })

        fastAdapter.onClickListener =
            { _: View?, _: IAdapter<ImageFileItem>, i: ImageFileItem, _: Int ->
                onItemClick(i.getImage().order)
            }

        binding?.recyclerView?.adapter = fastAdapter
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

    interface Parent {
        fun onPageSelected(index: Int)
    }
}