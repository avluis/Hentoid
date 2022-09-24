package me.devsaki.hentoid.fragments.metadata

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.core.view.ViewCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import com.annimon.stream.Optional
import com.annimon.stream.Stream
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.select.SelectExtension
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.RenamingRulesActivity
import me.devsaki.hentoid.core.isFinishing
import me.devsaki.hentoid.databinding.IncludeRulesControlsBinding
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.viewholders.AttributeTypeFilterItem
import me.devsaki.hentoid.viewholders.TextItem
import me.devsaki.hentoid.viewmodels.RulesEditViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory


class MetaEditRuleTopPanel(activity: RenamingRulesActivity) : DefaultLifecycleObserver {

    // UI
    private lateinit var binding: IncludeRulesControlsBinding
    private lateinit var menuView: View
    private lateinit var menuWindow: PopupWindow

    // Field filter
    private val fieldItemAdapter = ItemAdapter<TextItem<Int>>()
    private val fieldFastAdapter: FastAdapter<TextItem<Int>> = FastAdapter.with(fieldItemAdapter)
    private var fieldSelectExtension: SelectExtension<TextItem<Int>>? = null

    // Attribute type filter
    private val typeItemAdapter = ItemAdapter<AttributeTypeFilterItem>()
    private val typeFastAdapter = FastAdapter.with(typeItemAdapter)


    // VARS
    private val viewModel: RulesEditViewModel
    private val allAttributeTypes = listOf(
        AttributeType.ARTIST,
        AttributeType.CIRCLE,
        AttributeType.SERIE,
        AttributeType.TAG,
        AttributeType.CHARACTER,
        AttributeType.LANGUAGE
    )
    private lateinit var lifecycleOwner: LifecycleOwner
    private var isShowing: Boolean = false


    init {
        setLifecycleOwnerFromContext(activity)
        initFrame(activity)
        initUI(activity)

        val vmFactory = ViewModelFactory(activity.application)
        viewModel = ViewModelProvider(activity, vmFactory)[RulesEditViewModel::class.java]
    }

    private fun setLifecycleOwnerFromContext(context: Context) {
        if (context is LifecycleOwner) {
            setLifecycleOwner(context as LifecycleOwner)
        }
    }

    private fun setLifecycleOwner(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(this)
        this.lifecycleOwner = lifecycleOwner
    }

    private fun initFrame(activity: RenamingRulesActivity) {
        binding = IncludeRulesControlsBinding.inflate(
            LayoutInflater.from(activity),
            activity.window.decorView as ViewGroup,
            false
        )
        menuView = binding.root
        menuWindow = PopupWindow(
            menuView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        )
        // Outside click auto-dismisses the menu
        menuWindow.isFocusable = true
        menuWindow.setOnDismissListener { dismiss() }
    }

    private fun initUI(context: Context) {
        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        fieldSelectExtension = fieldFastAdapter.getOrCreateExtension(SelectExtension::class.java)
        fieldSelectExtension?.let {
            it.isSelectable = true
            it.multiSelect = false
            it.selectOnLongClick = false
            it.selectWithItemUpdate = true
            it.allowDeselection = false
            it.selectionListener = object : ISelectionListener<TextItem<Int>> {
                override fun onSelectionChanged(item: TextItem<Int>, selected: Boolean) {
                    if (selected) onSortFieldChanged()
                }
            }
        }
        binding.let {
            it.fieldList.adapter = fieldFastAdapter
            fieldItemAdapter.set(getSortFields(context))

            it.tagFilter.adapter = typeFastAdapter
            typeItemAdapter.set(allAttributeTypes.map { attrType ->
                AttributeTypeFilterItem(
                    attrType,
                    false
                )
            })

            typeFastAdapter.onClickListener =
                { _: View?, _: IAdapter<AttributeTypeFilterItem>, i: AttributeTypeFilterItem, _: Int ->
                    onAttributeFilterChanged(i)
                }
        }
    }

    fun showAsDropDown(anchor: View) {
        if (!isShowing
            && ViewCompat.isAttachedToWindow(anchor)
            && !anchor.context.isFinishing()
        ) {
            isShowing = true
            menuWindow.showAsDropDown(anchor)
        } else {
            dismiss()
        }
    }

    fun dismiss() {
        if (isShowing) {
            menuWindow.dismiss()
            isShowing = false
        }
    }


    private fun updateSortDirection() {
        binding.let {
            val currentPrefSortDesc = Preferences.isRuleSortDesc()
            it.sortAscDesc.check(if (currentPrefSortDesc) R.id.sort_descending else R.id.sort_ascending)
        }
    }


    /**
     * Callback for attribute filter item click
     *
     * @param item AttributeTypeFilterItem that has been clicked on
     */
    private fun onAttributeFilterChanged(item: AttributeTypeFilterItem): Boolean {
        val attributeTypeFilter = if (item.isSelected)
            item.tag as AttributeType
        else
            AttributeType.UNDEFINED
        viewModel.setAttributeType(attributeTypeFilter)
        return true
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private fun onSortFieldChanged() {
        if (null == fieldSelectExtension) return
        val item: Optional<TextItem<Int>> =
            Stream.of(fieldSelectExtension!!.selectedItems).findFirst()
        if (item.isPresent) {
            val code = item.get().getTag()
            if (code != null) {
                Preferences.setRuleSortField(code)
                viewModel.loadRules()
            }
        }
        updateSortDirection()
    }


    private fun getSortFields(context: Context): List<TextItem<Int>> {
        return listOf(
            TextItem(
                context.resources.getString(R.string.meta_rule_source),
                Preferences.Constant.ORDER_FIELD_SOURCE_NAME,
                false
            ),
            TextItem(
                context.resources.getString(R.string.meta_rule_target),
                Preferences.Constant.ORDER_FIELD_TARGET_NAME,
                false
            )
        )
    }
}