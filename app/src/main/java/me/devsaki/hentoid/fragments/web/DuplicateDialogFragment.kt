package me.devsaki.hentoid.fragments.web

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp.Companion.getInstance
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.databinding.DialogWebDuplicateBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.formatArtistForDisplay
import me.devsaki.hentoid.util.getFlagResourceId
import me.devsaki.hentoid.util.getGlideOptionCenterImage
import me.devsaki.hentoid.util.isInQueue
import me.devsaki.hentoid.util.openReader
import org.greenrobot.eventbus.EventBus

private const val KEY_CONTENT_ID = "contentId"
private const val KEY_ONLINE_CONTENT_PAGES = "onlineContentPages"
private const val KEY_CONTENT_SIMILARITY = "similarity"
private const val KEY_IS_DOWNLOAD_PLUS = "downloadPlus"

class DuplicateDialogFragment : BaseDialogFragment<DuplicateDialogFragment.Parent>() {

    companion object {
        fun invoke(
            parent: FragmentActivity,
            libraryContentId: Long,
            onlineContentNbPages: Int,
            similarity: Float,
            isDownloadPlus: Boolean
        ) {
            val args = Bundle()
            args.putLong(KEY_CONTENT_ID, libraryContentId)
            args.putInt(KEY_ONLINE_CONTENT_PAGES, onlineContentNbPages)
            args.putFloat(KEY_CONTENT_SIMILARITY, similarity)
            args.putBoolean(KEY_IS_DOWNLOAD_PLUS, isDownloadPlus)
            invoke(parent, DuplicateDialogFragment(), args, isCancelable = false)
        }
    }

    private var binding: DialogWebDuplicateBinding? = null

    enum class ActionMode {
        DOWNLOAD,  // Download book
        DOWNLOAD_PLUS,  // Download new pages
        REPLACE // Replace existing book
    }

    // === VARIABLES
    private var contentId: Long = 0
    private var onlineContentPages = 0
    private var similarity = 0f
    private var isDownloadPlus = false

    private val glideRequestOptions = getGlideOptionCenterImage(getInstance())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkNotNull(arguments) { "No arguments found" }

        arguments?.apply {
            contentId = getLong(KEY_CONTENT_ID, -1)
            require(contentId >= 1) { "No content ID found" }
            onlineContentPages = getInt(KEY_ONLINE_CONTENT_PAGES)
            similarity = getFloat(KEY_CONTENT_SIMILARITY)
            isDownloadPlus = getBoolean(KEY_IS_DOWNLOAD_PLUS, false)
        }
    }

    override fun onDestroyView() {
        EventBus.getDefault().unregister(this)
        super.onDestroyView()
        binding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View? {
        binding = DialogWebDuplicateBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        val context = requireContext()
        val libraryContent = loadLibraryContent() ?: return

        binding?.apply {
            subtitle.setText(if (isDownloadPlus) R.string.duplicate_alert_subtitle_pages else R.string.duplicate_alert_subtitle_book)
            downloadPlusBtn.visibility = if (isDownloadPlus) View.VISIBLE else View.GONE
            chAlwaysDownload.visibility = if (isDownloadPlus) View.GONE else View.VISIBLE
            chNeverExtraOnDupes.visibility = if (isDownloadPlus) View.VISIBLE else View.GONE
            tvTitle.text = libraryContent.title
            tvTitle.isSelected = true
            val cover = libraryContent.cover
            val thumbLocation = cover.usableUri
            if (thumbLocation.isEmpty()) {
                ivCover.visibility = View.INVISIBLE
            } else {
                ivCover.visibility = View.VISIBLE
                ivCover.setOnClickListener {
                    openReader(
                        context, libraryContent, -1, null,
                        forceShowGallery = false,
                        newTask = true
                    )
                }
                if (thumbLocation.startsWith("http")) Glide.with(ivCover).load(thumbLocation)
                    .apply(glideRequestOptions).into(ivCover) else Glide.with(ivCover)
                    .load(Uri.parse(thumbLocation)).apply(glideRequestOptions).into(ivCover)
            }
            @DrawableRes val resId = getFlagResourceId(context, libraryContent)
            if (resId != 0) {
                ivFlag.setImageResource(resId)
                ivFlag.visibility = View.VISIBLE
            } else {
                ivFlag.visibility = View.GONE
            }
            tvArtist.text = formatArtistForDisplay(context, libraryContent)
            tvPagesLibrary.visibility =
                if (0 == libraryContent.qtyPages) View.INVISIBLE else View.VISIBLE
            val stringRes: Int =
                if (isInQueue(libraryContent.status)) R.string.work_pages_duplicate_dialog_queue else R.string.work_pages_duplicate_dialog_library
            tvPagesLibrary.text = resources.getString(stringRes, libraryContent.qtyPages)
            tvPagesSource.visibility = if (0 == onlineContentPages) View.INVISIBLE else View.VISIBLE
            tvPagesSource.text = resources.getString(
                R.string.work_pages_duplicate_dialog_source, onlineContentPages
            )
            tvStatus.text =
                if (isInQueue(libraryContent.status)) resources.getString(R.string.duplicate_in_queue)
                else resources.getString(R.string.duplicate_in_library)

            // Buttons
            val site = libraryContent.site
            if (site != null && site != Site.NONE) {
                val img = site.ico
                ivSite.setImageResource(img)
                ivSite.visibility = View.VISIBLE
            } else {
                ivSite.visibility = View.GONE
            }

            val isStreamed = libraryContent.downloadMode == DownloadMode.STREAM
            if (libraryContent.status == StatusContent.EXTERNAL) {
                var resourceId =
                    if (libraryContent.isArchive) R.drawable.ic_archive else R.drawable.ic_folder_full
                // External streamed is streamed icon
                if (isStreamed) resourceId = R.drawable.ic_action_download_stream
                ivStorage.setImageResource(resourceId)
            } else {
                ivStorage.setImageResource(if (isStreamed) R.drawable.ic_action_download_stream else R.drawable.ic_storage)
            }

            if (libraryContent.favourite) {
                ivFavourite.setImageResource(R.drawable.ic_fav_full)
            } else {
                ivFavourite.setImageResource(R.drawable.ic_fav_empty)
            }

            // Similarity score
            tvScore.text = context.getString(
                R.string.duplicate_alert_similarity, similarity * 100
            )
            cancelBtn.setOnClickListener { dismissAllowingStateLoss() }
            replaceBtn.setOnClickListener { submit(ActionMode.REPLACE) }
            downloadBtn.setOnClickListener { submit(ActionMode.DOWNLOAD) }
            downloadPlusBtn.setOnClickListener { submit(ActionMode.DOWNLOAD_PLUS) }
        }
    }

    private fun loadLibraryContent(): Content? {
        val dao: CollectionDAO = ObjectBoxDAO()
        return try {
            dao.selectContent(contentId)
        } finally {
            dao.cleanup()
        }
    }

    private fun submit(actionMode: ActionMode) {
        binding?.apply {
            if (chAlwaysDownload.isChecked) Preferences.setDownloadDuplicateAsk(false)
            if (chNeverExtraOnDupes.isChecked) Preferences.setDownloadDuplicateTry(false)
        }
        parent?.onDownloadDuplicate(actionMode)
        dismissAllowingStateLoss()
    }


    interface Parent {
        fun onDownloadDuplicate(actionMode: ActionMode)
    }
}