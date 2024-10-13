package me.devsaki.hentoid.viewholders

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.Group
import androidx.core.content.ContextCompat
import coil3.load
import com.google.android.material.materialswitch.MaterialSwitch
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.DuplicateItemBundle
import me.devsaki.hentoid.core.requireById
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.ui.BlinkAnimation
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.formatArtistForDisplay
import me.devsaki.hentoid.util.getFlagResourceId
import me.devsaki.hentoid.util.getThemedColor

class DuplicateItem(result: DuplicateEntry, private val viewType: ViewType) :
    AbstractItem<DuplicateItem.ViewHolder>() {

    enum class ViewType {
        MAIN, DETAILS
    }

    var content: Content? = null
        private set

    private var isReferenceItem = false
    private var canDelete = false

    private var nbDuplicates = 0
    private var titleScore = -1f
    private var coverScore = -1f
    private var artistScore = -1f
    private var totalScore = -1f
    var keep = true
        private set
    var isBeingDeleted = false
        private set

    init {
        identifier = result.uniqueHash()
        if (viewType == ViewType.MAIN) {
            content = result.referenceContent
        } else {
            content = result.duplicateContent
            titleScore = result.titleScore
            coverScore = result.coverScore
            artistScore = result.artistScore
            totalScore = result.calcTotalScore()
            keep = result.keep
            isBeingDeleted = result.isBeingDeleted
        }
        content?.let {
            canDelete = it.status != StatusContent.EXTERNAL || Preferences.isDeleteExternalLibrary()
        }
        nbDuplicates = result.nbDuplicates
        isReferenceItem = titleScore > 1f
    }

    override val layoutRes: Int
        get() = if (ViewType.MAIN == viewType) R.layout.item_duplicate_main
        else if (ViewType.DETAILS == viewType) R.layout.item_duplicate_detail
        else R.layout.item_queue

    override val type: Int
        get() = R.id.duplicate

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder internal constructor(view: View) :
        FastAdapter.ViewHolder<DuplicateItem>(view) {
        // Common elements
        private val baseLayout: View = itemView.requireById(R.id.item)
        private val tvTitle: TextView = itemView.requireById(R.id.tvTitle)
        private val ivCover: ImageView? = itemView.findViewById(R.id.ivCover)
        private val ivFlag: ImageView = itemView.requireById(R.id.ivFlag)
        private val ivSite: ImageView = itemView.requireById(R.id.ivSite)
        private val tvArtist: TextView? = itemView.findViewById(R.id.tvArtist)
        private val tvPages: TextView? = itemView.findViewById(R.id.tvPages)
        private val ivFavourite: ImageView = itemView.findViewById(R.id.ivFavourite)
        private val ivExternal: ImageView = itemView.findViewById(R.id.ivExternal)

        // Specific to main screen
        private var viewDetails: TextView? = itemView.findViewById(R.id.view_details)

        // Specific to details screen
        private var tvLaunchCode: TextView? = itemView.findViewById(R.id.tvLaunchCode)
        private var scores: Group? = itemView.findViewById(R.id.scores)
        private var keepDeleteGroup: Group? = itemView.findViewById(R.id.keep_delete_group)
        private var titleScore: TextView? = itemView.findViewById(R.id.title_score)
        private var coverScore: TextView? = itemView.findViewById(R.id.cover_score)
        private var artistScore: TextView? = itemView.findViewById(R.id.artist_score)
        private var totalScore: TextView? = itemView.findViewById(R.id.total_score)
        private var keepButton: TextView? = itemView.findViewById(R.id.keep_choice)
        private var deleteButton: TextView? = itemView.findViewById(R.id.delete_choice)
        var keepDeleteSwitch: MaterialSwitch? = itemView.findViewById(R.id.keep_delete)

        override fun bindView(item: DuplicateItem, payloads: List<Any>) {
            item.content ?: return

            // Payloads are set when the content stays the same but some properties alone change
            if (payloads.isNotEmpty()) {
                val bundle = payloads[0] as Bundle
                val bundleParser = DuplicateItemBundle(bundle)
                var boolValue = bundleParser.isKeep
                if (boolValue != null) item.keep = boolValue
                boolValue = bundleParser.isBeingDeleted
                if (boolValue != null) item.isBeingDeleted = boolValue
            }
            updateLayoutVisibility(item)
            if (ivCover != null) attachCover(item.content!!)
            attachFlag(item.content!!)
            attachTitle(item.content!!)
            if (tvLaunchCode != null) attachLaunchCode(item.content!!)
            if (tvArtist != null) attachArtist(item.content!!)
            if (tvPages != null) attachPages(item.content!!)
            if (titleScore != null) attachScores(item)
            attachButtons(item)
        }

        private fun updateLayoutVisibility(item: DuplicateItem) {
            if (item.isBeingDeleted) baseLayout.startAnimation(
                BlinkAnimation(500, 250)
            ) else baseLayout.clearAnimation()
        }

        private fun attachCover(content: Content) {
            ivCover?.let {
                val thumbLocation = content.cover.usableUri
                if (thumbLocation.isEmpty()) {
                    it.visibility = View.INVISIBLE
                    return
                }
                it.visibility = View.VISIBLE
                // Use content's cookies to load image (useful for ExHentai when viewing queue screen)
                it.load(thumbLocation)
                /*
                if (thumbLocation.startsWith("http")) {
                    bindOnlineCover(thumbLocation, content)?.let { glideUrl ->
                        Glide.with(it).load(glideUrl).apply(glideRequestOptions).into(it)
                    }
                } else Glide.with(it)
                    .load(Uri.parse(thumbLocation))
                    .apply(glideRequestOptions)
                    .into(it)
                 */
            }
        }

        private fun attachFlag(content: Content) {
            @DrawableRes val resId = getFlagResourceId(ivFlag.context, content)
            if (resId != 0) {
                ivFlag.setImageResource(resId)
                ivFlag.visibility = View.VISIBLE
            } else {
                ivFlag.visibility = View.GONE
            }
        }

        private fun attachTitle(content: Content) {
            val title: CharSequence = content.title
            tvTitle.text = title
            tvTitle.setTextColor(tvTitle.context.getThemedColor(R.color.card_title_light))
        }

        private fun attachLaunchCode(content: Content) {
            val res = tvPages!!.context.resources
            tvLaunchCode!!.text = res.getString(R.string.book_launchcode, content.uniqueSiteId)
        }

        private fun attachArtist(content: Content) {
            tvArtist!!.text = formatArtistForDisplay(tvArtist.context, content)
        }

        private fun attachPages(content: Content) {
            tvPages!!.visibility = if (0 == content.qtyPages) View.INVISIBLE else View.VISIBLE
            val context = tvPages.context
            val template = context.resources.getQuantityString(
                R.plurals.work_pages_library,
                content.getNbDownloadedPages().toInt(),
                content.getNbDownloadedPages(),
                content.size * 1.0 / (1024 * 1024)
            )
            tvPages.text = template
        }

        private fun attachScores(item: DuplicateItem) {
            titleScore?.let {
                val res = it.context.resources
                if (!item.isReferenceItem) {
                    scores?.visibility = View.VISIBLE
                    if (item.titleScore > -1.0) it.text = res.getString(
                        R.string.duplicate_title_score, item.titleScore * 100
                    ) else it.setText(R.string.duplicate_title_score_nodata)
                    if (item.coverScore > -1.0) coverScore?.text = res.getString(
                        R.string.duplicate_cover_score, item.coverScore * 100
                    ) else coverScore?.setText(R.string.duplicate_cover_score_nodata)
                    if (item.artistScore > -1.0) artistScore?.text = res.getString(
                        R.string.duplicate_artist_score, item.artistScore * 100
                    ) else artistScore?.setText(R.string.duplicate_artist_score_nodata)
                    totalScore?.text =
                        res.getString(R.string.percent_no_digits, item.totalScore * 100)
                } else { // Reference item
                    scores?.visibility = View.GONE
                }
            }
        }

        private fun attachButtons(item: DuplicateItem) {
            val context = tvPages!!.context
            val content = item.content ?: return

            // Source icon
            val site = content.site
            if (site != Site.NONE) {
                val img = site.ico
                ivSite.setImageResource(img)
                ivSite.visibility = View.VISIBLE
            } else {
                ivSite.visibility = View.GONE
            }

            // External icon
            if (content.status == StatusContent.EXTERNAL) {
                if (content.isArchive) ivExternal.setImageResource(R.drawable.ic_archive)
                else if (content.isPdf) ivExternal.setImageResource(R.drawable.ic_pdf_file)
                else ivExternal.setImageResource(R.drawable.ic_folder_full)
                ivExternal.visibility = View.VISIBLE
            } else ivExternal.visibility = View.GONE

            // Favourite icon
            if (content.favourite) {
                ivFavourite.setImageResource(R.drawable.ic_fav_full)
                ivFavourite.tooltipText = context.getText(R.string.book_favourite_success)
            } else {
                ivFavourite.setImageResource(R.drawable.ic_fav_empty)
                ivFavourite.tooltipText = context.getText(R.string.book_unfavourite_success)
            }

            // View details icon
            viewDetails?.text = context.resources.getQuantityString(
                R.plurals.duplicate_count, item.nbDuplicates + 1, item.nbDuplicates + 1
            )

            if (item.canDelete) {
                keepDeleteGroup?.visibility = View.VISIBLE

                // Keep and delete buttons
                keepButton?.apply {
                    @ColorInt val targetColor =
                        if (item.keep) context.getThemedColor(R.color.secondary_light)
                        else ContextCompat.getColor(context, R.color.medium_gray)
                    setTextColor(targetColor)

                    val drawables = compoundDrawablesRelative
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        drawables[0]?.colorFilter =
                            BlendModeColorFilter(targetColor, BlendMode.SRC_IN)
                    } else {
                        @Suppress("DEPRECATION") drawables[0]?.setColorFilter(
                            targetColor,
                            PorterDuff.Mode.SRC_IN
                        )
                    }

                    setOnClickListener {
                        keepDeleteSwitch?.isChecked = true
                        keepDeleteSwitch?.callOnClick()
                    }
                }

                deleteButton?.apply {
                    @ColorInt val targetColor = if (!item.keep) context.getThemedColor(
                        R.color.secondary_light
                    ) else ContextCompat.getColor(context, R.color.medium_gray)
                    setTextColor(targetColor)

                    val drawables = compoundDrawablesRelative
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        drawables[0]?.colorFilter =
                            BlendModeColorFilter(targetColor, BlendMode.SRC_IN)
                    } else {
                        @Suppress("DEPRECATION") drawables[0]?.setColorFilter(
                            targetColor,
                            PorterDuff.Mode.SRC_IN
                        )
                    }

                    setOnClickListener {
                        keepDeleteSwitch?.isChecked = false
                        keepDeleteSwitch?.callOnClick()
                    }
                }
                keepDeleteSwitch?.isChecked = item.keep
            } else {
                keepDeleteGroup?.visibility = View.INVISIBLE
                keepDeleteSwitch?.isChecked = true
            }
        }

        val siteButton: View
            get() = ivSite

        override fun unbindView(item: DuplicateItem) {
            ivCover?.let {
                //if (isValidContextForGlide(it)) Glide.with(it).clear(it)
            }
        }
    }
}