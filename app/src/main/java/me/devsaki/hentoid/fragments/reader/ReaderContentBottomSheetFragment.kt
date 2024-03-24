package me.devsaki.hentoid.fragments.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.IncludeReaderContentBottomPanelBinding
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.image.tintBitmap
import me.devsaki.hentoid.util.setStyle
import me.devsaki.hentoid.viewmodels.ReaderViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory

class ReaderContentBottomSheetFragment : BottomSheetDialogFragment() {

    // Communication
    private lateinit var viewModel: ReaderViewModel

    // UI
    private var _binding: IncludeReaderContentBottomPanelBinding? = null
    private val binding get() = _binding!!
    private val stars: Array<ImageView?> = arrayOfNulls(5)

    // VARS
    private var contentId = -1L
    private var pageIndex = -1
    private var openOnTap = false
    private var currentRating = -1
    private val glideRequestOptions: RequestOptions

    init {
        val context: Context = HentoidApp.getInstance()
        val tintColor = context.getThemedColor(R.color.light_gray)

        val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.ic_hentoid_trans)
        val d: Drawable = BitmapDrawable(context.resources, tintBitmap(bmp, tintColor))

        val centerInside: Transformation<Bitmap> = CenterInside()
        glideRequestOptions = RequestOptions()
            .optionalTransform(centerInside)
            .error(d)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[ReaderViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireNotNull(arguments) { "No arguments found" }
        contentId = requireArguments().getLong(CONTENT_ID, -1)
        require(contentId > -1)
        pageIndex = requireArguments().getInt(PAGE_INDEX, -1)
        require(pageIndex > -1)
        openOnTap = requireArguments().getBoolean(OPEN_ON_TAP, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = IncludeReaderContentBottomPanelBinding.inflate(inflater, container, false)

        stars[0] = binding.rating1
        stars[1] = binding.rating2
        stars[2] = binding.rating3
        stars[3] = binding.rating4
        stars[4] = binding.rating5

        binding.imgActionFavourite.setOnClickListener { onFavouriteClick() }
        for (i in 0..4) {
            stars[i]?.setOnClickListener { setRating(i + 1) }
        }

        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            var content: Content? = null
            withContext(Dispatchers.IO) {
                val dao = ObjectBoxDAO()
                try {
                    content = dao.selectContent(contentId)
                } finally {
                    dao.cleanup()
                }
            }
            onContentChanged(content)
        }
    }

    private fun onContentChanged(content: Content?) {
        if (null == content) return

        val thumbLocation = content.cover.usableUri
        if (thumbLocation.isEmpty()) {
            binding.ivCover.visibility = View.INVISIBLE
        } else {
            binding.ivCover.visibility = View.VISIBLE
            if (thumbLocation.startsWith("http")) {
                val glideUrl = ContentHelper.bindOnlineCover(thumbLocation, content)
                if (glideUrl != null) {
                    Glide.with(binding.ivCover)
                        .load(glideUrl)
                        .apply(glideRequestOptions)
                        .into(binding.ivCover)
                }
            } else Glide.with(binding.ivCover)
                .load(Uri.parse(thumbLocation))
                .apply(glideRequestOptions)
                .into(binding.ivCover)
        }
        if (openOnTap) binding.ivCover.setOnClickListener {
            ContentHelper.openReader(
                requireActivity(),
                content,
                -1,
                null,
                false,
                true
            )
        }
        binding.contentTitle.text = content.title
        binding.contentArtist.text = ContentHelper.formatArtistForDisplay(requireContext(), content)
        updateFavouriteDisplay(content.isFavourite)
        updateRatingDisplay(content.rating)
        val tagTxt = ContentHelper.formatTagsForDisplay(content)
        if (tagTxt.isEmpty()) {
            binding.contentTags.visibility = View.GONE
        } else {
            binding.contentTags.visibility = View.VISIBLE
            binding.contentTags.text = tagTxt
        }
    }


    private fun updateFavouriteDisplay(isFavourited: Boolean) {
        if (isFavourited) binding.imgActionFavourite.setImageResource(R.drawable.ic_fav_full) else binding.imgActionFavourite.setImageResource(
            R.drawable.ic_fav_empty
        )
    }

    private fun updateRatingDisplay(rating: Int) {
        currentRating = rating
        for (i in 5 downTo 1) stars[i - 1]?.setImageResource(if (i <= rating) R.drawable.ic_star_full else R.drawable.ic_star_empty)
    }

    private fun setRating(rating: Int) {
        val targetRating = if (currentRating == rating) 0 else rating
        viewModel.setContentRating(targetRating) { r: Int -> this.updateRatingDisplay(r) }
    }

    private fun onFavouriteClick() {
        viewModel.toggleContentFavourite(pageIndex) { isFavourited: Boolean ->
            this.updateFavouriteDisplay(isFavourited)
        }
    }

    companion object {
        const val CONTENT_ID = "content_id"
        const val PAGE_INDEX = "page_index"
        const val OPEN_ON_TAP = "open_on_tap"
        fun invoke(
            context: Context,
            fragmentManager: FragmentManager,
            contentId: Long,
            pageIndex: Int,
            openOnTap: Boolean
        ) {
            val fragment = ReaderContentBottomSheetFragment()

            context.setStyle(
                fragment,
                DialogFragment.STYLE_NORMAL,
                R.style.Theme_Light_BottomSheetDialog
            )

            val args = Bundle()
            args.putLong(CONTENT_ID, contentId)
            args.putInt(PAGE_INDEX, pageIndex)
            args.putBoolean(OPEN_ON_TAP, openOnTap)
            fragment.arguments = args
            fragment.show(fragmentManager, "metaEditBottomSheetFragment")
        }
    }
}