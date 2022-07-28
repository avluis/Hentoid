package me.devsaki.hentoid.activities

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.annotation.DrawableRes
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.webp.decoder.WebpDrawable
import com.bumptech.glide.integration.webp.decoder.WebpDrawableTransformation
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.request.RequestOptions
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.MetaEditActivityBundle
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.ActivityMetaEditBinding
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.ThemeHelper

class MetadataEditActivity : BaseActivity() {

    private var binding: ActivityMetaEditBinding? = null

    private lateinit var contents: List<Content>
    private lateinit var dao: CollectionDAO
    private var isSingleBook: Boolean = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)

        binding = ActivityMetaEditBinding.inflate(layoutInflater)
        binding?.let {
            setContentView(it.root)
            it.toolbar.setOnMenuItemClickListener(this::onToolbarItemClicked)
        }

        dao = ObjectBoxDAO(this)

        if (null == intent || null == intent.extras) throw IllegalArgumentException("Required intent not found")

        val parser = MetaEditActivityBundle(intent.extras!!)
        val contentIds = parser.contentIds
        if (null == contentIds || contentIds.isEmpty()) throw IllegalArgumentException("Required init arguments not found")

        isSingleBook = (1 == contentIds.size)
        contents = dao.selectContent(contentIds)
        if (contents.isEmpty()) throw IllegalArgumentException("Provided Content ID not found")

        if (isSingleBook) bindSingleBookUI()
        else bindMultipleBooksUI()

        bindInteractions()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
        dao.cleanup()
    }

    private fun bindSingleBookUI() {
        val content = contents[0]
        binding?.let {
            // Title
            it.tvTitle.text = content.title
            it.titleNew.editText?.setText(content.title)

            // Artist
            it.tvArtist.text = ContentHelper.formatArtistForDisplay(this, content)

            // Series
            var text = ContentHelper.formatSeriesForDisplay(this, content)
            if (text.isEmpty()) {
                it.tvSeries.visibility = View.GONE
            } else {
                it.tvSeries.visibility = View.VISIBLE
                it.tvSeries.text = text
            }

            // Tags
            text = ContentHelper.formatTagsForDisplay(content)
            if (text.isEmpty()) {
                it.tvTags.visibility = View.GONE
            } else {
                it.tvTags.visibility = View.VISIBLE
                it.tvTags.text = text
            }

            // Cover
            val thumbLocation = content.cover.usableUri
            if (thumbLocation.isEmpty()) {
                it.ivCover.visibility = View.INVISIBLE
            } else {
                it.ivCover.visibility = View.VISIBLE
                // Use content's cookies to load image (useful for ExHentai when viewing queue screen)
                // Use content's cookies to load image (useful for ExHentai when viewing queue screen)
                if (thumbLocation.startsWith("http")) {
                    val glideUrl = ContentHelper.bindOnlineCover(content, thumbLocation)
                    if (glideUrl != null) {
                        Glide.with(it.ivCover)
                            .load(glideUrl)
                            .apply(glideRequestOptions)
                            .into(it.ivCover)
                    }
                } else  // From stored picture
                    Glide.with(it.ivCover)
                        .load(Uri.parse(thumbLocation))
                        .apply(glideRequestOptions)
                        .into(it.ivCover)
            }

            // Flag
            @DrawableRes val resId = ContentHelper.getFlagResourceId(this, content)
            if (resId != 0) {
                it.ivFlag.setImageResource(resId)
                it.ivFlag.visibility = View.VISIBLE
            } else {
                it.ivFlag.visibility = View.GONE
            }
        }
    }

    private fun bindMultipleBooksUI() {
        binding?.let {
            // TODO
        }
    }

    private fun bindInteractions() {
        binding?.let {
            // Attributes box init
            val layoutManager = FlexboxLayoutManager(this)
            layoutManager.alignItems = AlignItems.STRETCH
            layoutManager.flexWrap = FlexWrap.WRAP
            it.tags.layoutManager = layoutManager
            // TODO use FastAdapter here, what happens on that screen is no rocket science
            it.tags.adapter = attributeAdapter

            // Title
            it.tvTitle.setOnClickListener {
                binding?.let { b2 ->
                    b2.titleNew.visibility = View.VISIBLE
                    b2.tags.visibility = View.GONE
                }
            }

            // Artist
            it.tvArtist.setOnClickListener {
                binding?.let { b2 ->
                    b2.tags
                    b2.titleNew.visibility = View.GONE
                    b2.tags.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun onToolbarItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_edit_confirm -> confirmEdit()
            R.id.action_edit_cancel -> cancelEdit()
            else -> return true
        }
        return true
    }

    private fun confirmEdit() {
        // TODO save to DB
        finish()
    }

    private fun cancelEdit() {
        onBackPressed()
    }

    /**
     * Handler for Attribute button click
     *
     * @param button Button that has been clicked on
     */
    private fun onAttributeClicked(button: View) {
        val a = button.tag as Attribute
        // TODO
    }

    companion object {
        val centerInside: Transformation<Bitmap> = CenterInside()
        val glideRequestOptions = RequestOptions()
            .optionalTransform(centerInside)
            .optionalTransform(WebpDrawable::class.java, WebpDrawableTransformation(centerInside))
    }
}