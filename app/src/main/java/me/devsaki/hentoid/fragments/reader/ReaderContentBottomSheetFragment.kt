package me.devsaki.hentoid.fragments.reader

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil3.load
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.IncludeReaderContentBottomPanelBinding
import me.devsaki.hentoid.util.formatArtistForDisplay
import me.devsaki.hentoid.util.formatTagsForDisplay
import me.devsaki.hentoid.util.openReader
import me.devsaki.hentoid.util.setStyle
import me.devsaki.hentoid.viewmodels.ReaderViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory

class ReaderContentBottomSheetFragment : BottomSheetDialogFragment() {

    // Communication
    private lateinit var viewModel: ReaderViewModel

    // UI
    private var binding: IncludeReaderContentBottomPanelBinding? = null
    private val stars: Array<ImageView?> = arrayOfNulls(5)

    // VARS
    private var contentId = -1L
    private var pageIndex = -1
    private var openOnTap = false
    private var currentRating = -1


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
    ): View? {
        binding = IncludeReaderContentBottomPanelBinding.inflate(inflater, container, false)
        binding?.apply {
            stars[0] = rating1
            stars[1] = rating2
            stars[2] = rating3
            stars[3] = rating4
            stars[4] = rating5

            imgActionFavourite.setOnClickListener { onFavouriteClick() }
            for (i in 0..4) {
                stars[i]?.setOnClickListener { setRating(i + 1) }
            }
        }
        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
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
        binding?.apply {

            // Cover
            if (thumbLocation.isEmpty()) {
                ivCover.visibility = View.INVISIBLE
            } else {
                ivCover.visibility = View.VISIBLE
                ivCover.load(thumbLocation)
                /*
                if (thumbLocation.startsWith("http")) {
                    val glideUrl = bindOnlineCover(thumbLocation, content)
                    if (glideUrl != null) {
                        Glide.with(ivCover)
                            .load(glideUrl)
                            .apply(glideRequestOptions)
                            .into(ivCover)
                    }
                } else Glide.with(ivCover)
                    .load(Uri.parse(thumbLocation))
                    .apply(glideRequestOptions)
                    .into(ivCover)

                 */
            }
            if (openOnTap) ivCover.setOnClickListener {
                openReader(
                    requireActivity(),
                    content,
                    -1,
                    null,
                    forceShowGallery = false,
                    newTask = true
                )
            }

            contentTitle.text = content.title
            contentArtist.text = formatArtistForDisplay(requireContext(), content)
            updateFavouriteDisplay(content.favourite)
            updateRatingDisplay(content.rating)
            val tagTxt = formatTagsForDisplay(content)
            if (tagTxt.isEmpty()) {
                contentTags.visibility = View.GONE
            } else {
                contentTags.visibility = View.VISIBLE
                contentTags.text = tagTxt
            }
            if (content.site.hasUniqueBookId)
                contentLaunchCode.text =
                    resources.getString(R.string.book_launchcode, content.uniqueSiteId)
            contentLaunchCode.isVisible = content.site.hasUniqueBookId
        }
    }

    private fun updateFavouriteDisplay(isFavourited: Boolean) {
        binding?.apply {
            if (isFavourited) imgActionFavourite.setImageResource(R.drawable.ic_fav_full)
            else imgActionFavourite.setImageResource(R.drawable.ic_fav_empty)
        }
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