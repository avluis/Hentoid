package me.devsaki.hentoid.activities

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.webp.decoder.WebpDrawable
import com.bumptech.glide.integration.webp.decoder.WebpDrawableTransformation
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.request.RequestOptions
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.MetaEditActivityBundle
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.ActivityMetaEditBinding
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.fragments.metadata.GalleyPickerDialogFragment
import me.devsaki.hentoid.fragments.metadata.MetaEditBottomSheetFragment
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.viewholders.AttributeItem
import me.devsaki.hentoid.viewmodels.MetadataEditViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import timber.log.Timber

class MetadataEditActivity : BaseActivity(), GalleyPickerDialogFragment.Parent {

    // Communication
    private lateinit var viewModel: MetadataEditViewModel

    // UI
    private var binding: ActivityMetaEditBinding? = null
    private val itemAdapter = ItemAdapter<AttributeItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)

    // Vars
    private lateinit var contents: List<Content>
    private var contentAttributes = ArrayList<Attribute>()
    private var selectedAttributeTypes = ArrayList<AttributeType>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)

        binding = ActivityMetaEditBinding.inflate(layoutInflater)
        binding?.let {
            setContentView(it.root)
            it.toolbar.setOnMenuItemClickListener(this::onToolbarItemClicked)
        }

        if (null == intent || null == intent.extras) throw IllegalArgumentException("Required intent not found")

        val parser = MetaEditActivityBundle(intent.extras!!)
        val contentIds = parser.contentIds
        if (null == contentIds || contentIds.isEmpty()) throw IllegalArgumentException("Required init arguments not found")

        val vmFactory = ViewModelFactory(application)
        viewModel = ViewModelProvider(this, vmFactory)[MetadataEditViewModel::class.java]

        val currentContent = viewModel.getContent().value
        if (null == currentContent || currentContent.isEmpty()) { // ViewModel hasn't loaded anything yet (fresh start)
            viewModel.loadContent(contentIds)
        }

        bindInteractions()

        viewModel.getContent().observe(this) { this.onContentChanged(it) }
        viewModel.getContentAttributes().observe(this) { this.onContentAttributesChanged(it) }
        viewModel.getAttributeTypes().observe(this) { this.onSelectedAttributeTypesChanged(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    private fun onContentChanged(content: List<Content>) {
        contents = content
        if (1 == content.size) bindSingleBookUI()
        else bindMultipleBooksUI()
    }

    private fun onSelectedAttributeTypesChanged(data: List<AttributeType>) {
        selectedAttributeTypes.clear()
        selectedAttributeTypes.addAll(data)
        updateAttrsList()
    }

    private fun onContentAttributesChanged(data: List<Attribute>) {
        contentAttributes.clear()
        contentAttributes.addAll(data)
        updateAttrsList()
    }

    private fun updateAttrsList() {
        FastAdapterDiffUtil[itemAdapter] =
            contentAttributes.filter { a -> selectedAttributeTypes.contains(a.type) }
                .map { attr -> AttributeItem(attr) }
    }

    private fun bindSingleBookUI() {
        val content = contents[0]
        binding?.let {
            // Title
            it.tvTitle.text = content.title

            // Artist
            bindArtistUI(content)

            // Series
            bindSeriesUI(content)

            // Tags
            bindTagsUI(content)

            // Cover
            val thumbLocation = content.cover.usableUri
            if (thumbLocation.isEmpty()) {
                it.ivCover.visibility = View.INVISIBLE
            } else {
                it.ivCover.visibility = View.VISIBLE
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
            bindLanguagesUI(content)
        }
    }

    private fun bindArtistUI(content: Content) {
        binding?.tvArtist?.text = ContentHelper.formatArtistForDisplay(this, content)
    }

    private fun bindSeriesUI(content: Content) {
        val text = ContentHelper.formatSeriesForDisplay(this, content)
        if (text.isEmpty()) {
            binding?.tvSeries?.text =
                getString(R.string.work_series, resources.getString(R.string.work_untitled))
        } else {
            binding?.tvSeries?.text = text
        }
    }

    private fun bindTagsUI(content: Content) {
        val text = ContentHelper.formatTagsForDisplay(content)
        if (text.isEmpty()) {
            binding?.tvTags?.text = getString(R.string.work_untitled)
        } else {
            binding?.tvTags?.text = text
        }
    }

    private fun bindLanguagesUI(content: Content) {
        @DrawableRes val resId = ContentHelper.getFlagResourceId(this, content)
        if (resId != 0) {
            binding?.ivFlag?.setImageResource(resId)
        } else {
            binding?.ivFlag?.setImageResource(R.drawable.flag_unknown)
        }
    }

    private fun bindMultipleBooksUI() {
        val keep = String.format("<%s>", resources.getString(R.string.meta_keep))
        binding?.let {
            // Title
            val firstTitle = contents[0].title
            val anyDifferent = contents.find { c -> c.title != firstTitle }
            val title = if (null == anyDifferent) firstTitle else keep
            it.tvTitle.text = title

            // Artist
            var isDiff = areAttributesDifferent(
                contents,
                setOf(AttributeType.ARTIST, AttributeType.CIRCLE)
            )
            if (isDiff) it.tvArtist.text = keep
            else bindArtistUI(contents[0])

            // Series
            isDiff = areAttributesDifferent(contents, setOf(AttributeType.SERIE))
            if (isDiff) it.tvSeries.text = keep
            else bindSeriesUI(contents[0])

            // Tags
            isDiff =
                areAttributesDifferent(contents, setOf(AttributeType.TAG, AttributeType.CHARACTER))
            if (isDiff) it.tvTags.text = keep
            else bindTagsUI(contents[0])

            // No cover displayed for multiple books

            // Flag
            isDiff = areAttributesDifferent(contents, setOf(AttributeType.LANGUAGE))
            if (isDiff) it.ivFlag.setImageResource(R.drawable.flag_multiple)
            else bindLanguagesUI(contents[0])
        }
    }

    private fun bindInteractions() {
        binding?.let {
            // Attributes box init
            val layoutManager = FlexboxLayoutManager(this)
            layoutManager.alignItems = AlignItems.STRETCH
            layoutManager.flexWrap = FlexWrap.WRAP
            it.tags.layoutManager = layoutManager
            it.tags.adapter = fastAdapter

            fastAdapter.onClickListener =
                { _: View?, _: IAdapter<AttributeItem>, i: AttributeItem, _: Int -> onItemClick(i) }

            // Title
            it.tvTitle.setOnClickListener {
                binding?.let { b2 ->
                    b2.titleNew.editText?.setText(b2.tvTitle.text.toString())
                    b2.titleNew.visibility = View.VISIBLE
                    b2.tags.visibility = View.GONE
                    b2.tagsFab.visibility = View.GONE
                }
            }
            it.titleNew.editText?.addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        if (s != null) viewModel.setTitle(s.toString())
                    }

                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }
                }
            )

            // Artist
            it.tvArtist.setOnClickListener {
                binding?.let { b2 ->
                    viewModel.setAttributeTypes(
                        listOf(
                            AttributeType.ARTIST,
                            AttributeType.CIRCLE
                        )
                    )
                    b2.titleNew.visibility = View.GONE
                    b2.tags.visibility = View.VISIBLE
                    b2.tagsFab.visibility = View.VISIBLE
                }
            }

            // Series
            it.tvSeries.setOnClickListener {
                binding?.let { b2 ->
                    viewModel.setAttributeTypes(listOf(AttributeType.SERIE))
                    b2.titleNew.visibility = View.GONE
                    b2.tags.visibility = View.VISIBLE
                    b2.tagsFab.visibility = View.VISIBLE
                }
            }

            // Tags
            it.tvTags.setOnClickListener {
                binding?.let { b2 ->
                    viewModel.setAttributeTypes(
                        listOf(
                            AttributeType.TAG,
                            AttributeType.CHARACTER
                        )
                    )
                    b2.titleNew.visibility = View.GONE
                    b2.tags.visibility = View.VISIBLE
                    b2.tagsFab.visibility = View.VISIBLE
                }
            }
            it.tagsFab.setOnClickListener {
                MetaEditBottomSheetFragment.invoke(
                    this,
                    supportFragmentManager, true
                )
            }

            // Flag
            it.ivFlag.setOnClickListener {
                binding?.let { b2 ->
                    viewModel.setAttributeTypes(listOf(AttributeType.LANGUAGE))
                    b2.titleNew.visibility = View.GONE
                    b2.tags.visibility = View.VISIBLE
                    b2.tagsFab.visibility = View.VISIBLE
                }
            }

            // Cover
            it.ivCover.setOnClickListener {
                binding?.let { b2 ->
                    if (1 == contents.size) {
                        if (contents[0].isArchive) {
                            Snackbar.make(
                                b2.root,
                                R.string.meta_cover_archive_warning,
                                BaseTransientBottomBar.LENGTH_SHORT
                            ).show()
                        } else {
                            val imgs = contents[0].imageFiles?.filter { i -> i.isReadable }
                            if (imgs != null) {
                                b2.titleNew.visibility = View.GONE
                                b2.tags.visibility = View.GONE
                                GalleyPickerDialogFragment.invoke(
                                    supportFragmentManager,
                                    imgs
                                )
                            }
                        }
                    } else {
                        Snackbar.make(
                            b2.root,
                            R.string.meta_cover_multiple_warning,
                            BaseTransientBottomBar.LENGTH_SHORT
                        ).show()
                    }
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

    /**
     * Callback for attribute item click
     *
     * @param item AttributeItem that has been clicked on
     */
    private fun onItemClick(item: AttributeItem): Boolean {
        viewModel.removeContentAttribute(item.attribute)
        Timber.d("Attribute %s clicked", item.attribute.name)
        return true
    }

    private fun confirmEdit() {
        viewModel.saveContent()
        finish()
    }

    private fun cancelEdit() {
        onBackPressed()
    }

    /**
     * Callback from the gallery picker
     */
    override fun onPageSelected(index: Int) {
        viewModel.setCover(index)
    }

    private fun areAttributesDifferent(
        contents: List<Content>,
        types: Set<AttributeType>
    ): Boolean {
        if (contents.isEmpty()) return false

        val allAttrs = ArrayList<Set<Attribute>>()
        contents.forEach {
            allAttrs.add(it.attributes.filter { a -> types.contains(a.type) }.toSet())
        }
        val firstAttrs = allAttrs[0]
        allAttrs.forEach {
            if (it.size != firstAttrs.size) return true
            if (!it.containsAll(firstAttrs)) return true
        }
        return false
    }

    companion object {
        val centerInside: Transformation<Bitmap> = CenterInside()
        val glideRequestOptions = RequestOptions()
            .optionalTransform(centerInside)
            .optionalTransform(
                WebpDrawable::class.java,
                WebpDrawableTransformation(centerInside)
            )
    }
}