package me.devsaki.hentoid.activities

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.DiffCallback
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.OnMenuItemClickListener
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.AttributeItemBundle
import me.devsaki.hentoid.activities.bundles.MetaEditActivityBundle
import me.devsaki.hentoid.core.setOnTextChangedListener
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.ActivityMetaEditBinding
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.fragments.metadata.AttributeTypePickerDialogFragment
import me.devsaki.hentoid.fragments.metadata.GalleryPickerDialogFragment
import me.devsaki.hentoid.fragments.metadata.MetaEditBottomSheetFragment
import me.devsaki.hentoid.fragments.metadata.MetaRenameDialogFragment
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.applyTheme
import me.devsaki.hentoid.util.bindOnlineCover
import me.devsaki.hentoid.util.getFlagResourceId
import me.devsaki.hentoid.util.glideOptionCenterInside
import me.devsaki.hentoid.viewholders.AttributeItem
import me.devsaki.hentoid.viewholders.AttributeTypeFilterItem
import me.devsaki.hentoid.viewmodels.MetadataEditViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory


class MetadataEditActivity : BaseActivity(), GalleryPickerDialogFragment.Parent,
    AttributeTypePickerDialogFragment.Parent, MetaRenameDialogFragment.Parent {

    // == Communication
    private lateinit var viewModel: MetadataEditViewModel

    // == UI
    private var binding: ActivityMetaEditBinding? = null

    // Attribute type filter
    private val itemFilterAdapter = ItemAdapter<AttributeTypeFilterItem>()
    private val fastFilterAdapter = FastAdapter.with(itemFilterAdapter)

    // Tags
    private val itemAdapter = ItemAdapter<AttributeItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)

    // == Vars
    private lateinit var contents: List<Content>
    private var contentAttributes = ArrayList<Attribute>()
    private var selectedAttributeTypes = ArrayList<AttributeType>()

    private val allAttributeTypes = listOf(
        AttributeType.ARTIST,
        AttributeType.CIRCLE,
        AttributeType.SERIE,
        AttributeType.TAG,
        AttributeType.CHARACTER,
        AttributeType.LANGUAGE
    )

    private val attributeItemDiffCallback: DiffCallback<AttributeItem> =
        object : DiffCallback<AttributeItem> {
            override fun areItemsTheSame(oldItem: AttributeItem, newItem: AttributeItem): Boolean {
                return oldItem.identifier == newItem.identifier
            }

            override fun areContentsTheSame(
                oldItem: AttributeItem,
                newItem: AttributeItem
            ): Boolean {
                return (oldItem.attribute == newItem.attribute)
                        && (oldItem.attribute.count == newItem.attribute.count)
                        && (oldItem.attribute.name.equals(newItem.attribute.name, true))
            }

            override fun getChangePayload(
                oldItem: AttributeItem,
                oldItemPosition: Int,
                newItem: AttributeItem,
                newItemPosition: Int
            ): Any? {
                val oldAttr = oldItem.attribute
                val newAttr = newItem.attribute
                val diffBundleBuilder = AttributeItemBundle()
                if (oldAttr.count != newAttr.count) {
                    diffBundleBuilder.count = newAttr.count
                }
                if (!oldAttr.name.equals(newAttr.name, true)) {
                    diffBundleBuilder.name = newAttr.name
                }
                return if (diffBundleBuilder.isEmpty) null else diffBundleBuilder.bundle
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()

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
        // ViewModel hasn't loaded anything yet (fresh start)
        if (currentContent.isNullOrEmpty()) viewModel.loadContent(contentIds)

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
        bindUI()
    }

    private fun onSelectedAttributeTypesChanged(data: List<AttributeType>) {
        selectedAttributeTypes.clear()
        selectedAttributeTypes.addAll(data)
        updateAttrsFilter()
        updateAttrsList()
    }

    private fun onContentAttributesChanged(data: List<Attribute>) {
        contentAttributes.clear()
        contentAttributes.addAll(data)
        updateAttrsList()
    }

    private fun updateAttrsFilter() {
        val items = allAttributeTypes.map { attrType ->
            AttributeTypeFilterItem(
                attrType,
                selectedAttributeTypes.size < allAttributeTypes.size
                        && selectedAttributeTypes.contains(attrType)
            )
        }
        itemFilterAdapter.set(items)
    }

    private fun updateAttrsList() {
        val items = contentAttributes.filter { a -> selectedAttributeTypes.contains(a.type) }
            .sortedWith(compareBy({ it.type }, { -it.count }, { it.name }))
            .map { attr -> AttributeItem(attr, contents.size > 1) }

        FastAdapterDiffUtil.set(itemAdapter, items, attributeItemDiffCallback)
    }

    private fun bindUI() {
        binding?.let {
            // Title
            it.tvTitle.visibility = if (contents.size > 1) View.GONE else View.VISIBLE
            it.tvTitle.text = contents[0].title

            // Tag filters
            bindTagFilterssUI()

            // Cover
            val thumbLocation = if (contents.size > 1) "" else contents[0].cover.usableUri
            if (thumbLocation.isEmpty()) {
                it.ivCover.visibility = View.INVISIBLE
            } else {
                it.ivCover.visibility = View.VISIBLE
                if (thumbLocation.startsWith("http")) {
                    bindOnlineCover(thumbLocation, contents[0])?.let { glideUrl ->
                        Glide.with(it.ivCover)
                            .load(glideUrl)
                            .apply(glideOptionCenterInside)
                            .into(it.ivCover)
                    }
                } else  // From stored picture
                    Glide.with(it.ivCover)
                        .load(Uri.parse(thumbLocation))
                        .apply(glideOptionCenterInside)
                        .into(it.ivCover)
            }

            // Flag (language)
            bindLanguagesUI()

            // Tags (default uncategorized display)
            viewModel.setAttributeTypes(allAttributeTypes)
        }
    }

    private fun bindLanguagesUI() {
        val attrContainer = Content()
        attrContainer.putAttributes(mergeAttributeMaps(contents, setOf(AttributeType.LANGUAGE)))
        if (1 == contents.size) {
            binding?.ivFlag?.visibility = View.VISIBLE
            @DrawableRes val resId = getFlagResourceId(this, attrContainer)
            if (resId != 0) {
                binding?.ivFlag?.setImageResource(resId)
            } else {
                binding?.ivFlag?.setImageResource(R.drawable.flag_unknown)
            }
        } else {
            binding?.ivFlag?.visibility = View.GONE
        }
    }

    private fun bindTagFilterssUI() {
        binding?.tagFilter?.adapter = fastFilterAdapter
        fastFilterAdapter.onClickListener =
            { _: View?, _: IAdapter<AttributeTypeFilterItem>, i: AttributeTypeFilterItem, _: Int ->
                onAttributeFilterClick(i)
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
                { _: View?, _: IAdapter<AttributeItem>, i: AttributeItem, _: Int ->
                    onAttributeClick(i)
                }

            fastAdapter.onLongClickListener =
                { _: View?, _: IAdapter<AttributeItem>, i: AttributeItem, _: Int ->
                    onItemLongClick(i)
                }

            // Title
            it.tvTitle.setOnClickListener {
                binding?.let { b2 ->
                    b2.titleNew.editText?.setText(b2.tvTitle.text.toString())
                    b2.titleNew.visibility = View.VISIBLE
                    b2.tags.visibility = View.GONE
                    b2.tagsFab.visibility = View.GONE
                }
            }
            it.titleNew.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                viewModel.setTitle(value)
            }
            it.titleNew.editText?.setOnEditorActionListener { _, actionId, _ ->
                var handled = false
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    retractTextEdit()
                    handled = true
                }
                handled
            }

            // Tags
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
                    if (contents.size > 1) {
                        Snackbar.make(
                            b2.root,
                            R.string.meta_cover_multiple_warning,
                            BaseTransientBottomBar.LENGTH_SHORT
                        ).show()
                    } else {
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
                                b2.tagsFab.visibility = View.GONE
                                GalleryPickerDialogFragment.invoke(this, imgs)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun retractTextEdit() {
        binding?.let {
            if (!it.titleNew.isVisible) return

            it.titleNew.visibility = View.GONE
            it.tags.visibility = View.VISIBLE
            it.tagsFab.visibility = View.VISIBLE
            // Make sure virtual keyboard is closed
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                it.root.windowToken,
                0
            )
        }
    }

    @Suppress("SameReturnValue")
    private fun onToolbarItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_edit_confirm -> confirmEdit()
            R.id.action_edit_cancel -> cancelEdit()
            else -> return true
        }
        return true
    }

    /**
     * Callback for attribute filter item click
     *
     * @param item AttributeTypeFilterItem that has been clicked on
     */
    private fun onAttributeFilterClick(item: AttributeTypeFilterItem): Boolean {
        retractTextEdit()
        if (item.isSelected)
            viewModel.setAttributeTypes(allAttributeTypes)
        else
            viewModel.setAttributeTypes(listOf(item.attributeType))
        return true
    }

    /**
     * Callback for attribute item click
     *
     * @param item AttributeItem that has been clicked on
     */
    private fun onAttributeClick(item: AttributeItem): Boolean {
        val powerMenuBuilder = PowerMenu.Builder(this)
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.menu_edit_name),
                    false,
                    R.drawable.ic_edit_square,
                    null,
                    null,
                    0
                )
            )
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.meta_replace_with),
                    false,
                    R.drawable.ic_replace,
                    null,
                    null,
                    1
                )
            )
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.remove_generic),
                    false,
                    R.drawable.ic_action_delete,
                    null,
                    null,
                    3
                )
            )
            .setAnimation(MenuAnimation.SHOWUP_TOP_LEFT)
            .setMenuRadius(10f)
            .setLifecycleOwner(this)
            .setTextColor(ContextCompat.getColor(this, R.color.white_opacity_87))
            .setTextTypeface(Typeface.DEFAULT)
            .setMenuColor(ContextCompat.getColor(this, R.color.dark_gray))
            .setWidth(resources.getDimension(R.dimen.popup_menu_width).toInt())
            .setTextSize(Helper.dimensAsDp(this, R.dimen.text_subtitle_1))
            .setAutoDismiss(true)

        if (contents.size > 1)
            powerMenuBuilder.addItem(
                1,
                PowerMenuItem(
                    resources.getString(R.string.meta_tag_all_selected),
                    false,
                    R.drawable.ic_action_select_all,
                    null,
                    null,
                    2
                )
            )

        val powerMenu = powerMenuBuilder.build()

        powerMenu.onMenuItemClickListener =
            OnMenuItemClickListener { _: Int, it: PowerMenuItem ->
                when (it.tag) {
                    0 -> { // Rename
                        MetaRenameDialogFragment.invoke(
                            this,
                            item.attribute.id
                        )
                    }

                    1 -> { // Replace with...
                        MetaEditBottomSheetFragment.invoke(
                            this,
                            supportFragmentManager, false, item.attribute.id
                        )
                    }

                    2 -> { // Tag all selected books
                        val builder = MaterialAlertDialogBuilder(this)
                        val title = resources.getString(
                            R.string.meta_tag_all_selected_confirm,
                            contents.size,
                            item.attribute.name
                        )
                        builder.setMessage(title)
                            .setPositiveButton(
                                R.string.ok
                            ) { _, _ ->
                                viewModel.addContentAttribute(item.attribute)
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .create().show()

                    }

                    else -> { // Remove
                        viewModel.removeContentAttribute(item.attribute)
                    }
                }
            }

        powerMenu.setIconColor(ContextCompat.getColor(this, R.color.white_opacity_87))
        powerMenu.showAtCenter(binding?.root)
        return true
    }

    /**
     * Callback for attribute item long click
     *
     * @param item AttributeItem that has been clicked on
     */
    private fun onItemLongClick(item: AttributeItem): Boolean {
        viewModel.removeContentAttribute(item.attribute)
        return true
    }

    private fun confirmEdit() {
        viewModel.saveContent()
        finish()
    }

    private fun cancelEdit() {
        finish()
    }

    /**
     * Callback from the gallery picker
     */
    override fun onPageSelected(index: Int) {
        viewModel.setCover(index)
    }

    private fun mergeAttributeMaps(
        contents: List<Content>,
        types: Set<AttributeType>
    ): AttributeMap {
        val result = AttributeMap()
        if (contents.isEmpty()) return result

        contents.forEach { content ->
            val localMap = content.attributeMap
            types.forEach { type ->
                val localAttrs = localMap[type]
                if (localAttrs != null) result.addAll(localAttrs)
            }
        }

        return result
    }

    override fun onNewAttributeSelected(name: String, type: AttributeType) {
        viewModel.createAssignNewAttribute(name, type)
        viewModel.resetSelectionFilter()
    }

    override fun onRenameAttribute(newName: String, id: Long, createRule: Boolean) {
        viewModel.renameAttribute(newName, id, createRule)
    }
}