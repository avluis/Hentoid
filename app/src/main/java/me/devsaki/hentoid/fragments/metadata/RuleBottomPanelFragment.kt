package me.devsaki.hentoid.fragments.metadata

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.select.getSelectExtension
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.IncludeRulesControlsBinding
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.setStyle
import me.devsaki.hentoid.viewholders.AttributeTypeFilterItem
import me.devsaki.hentoid.viewholders.TextItem
import me.devsaki.hentoid.viewmodels.RulesEditViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory

class RuleBottomPanelFragment : BottomSheetDialogFragment() {

    // UI
    private var _binding: IncludeRulesControlsBinding? = null
    private val binding get() = _binding!!

    // Field filter
    private val fieldItemAdapter = ItemAdapter<TextItem<Int>>()
    private val fieldFastAdapter: FastAdapter<TextItem<Int>> = FastAdapter.with(fieldItemAdapter)
    private val fieldSelectExtension = fieldFastAdapter.getSelectExtension()

    // Attribute type filter
    private val typeItemAdapter = ItemAdapter<AttributeTypeFilterItem>()
    private val typeFastAdapter = FastAdapter.with(typeItemAdapter)


    // VARS
    private lateinit var viewModel: RulesEditViewModel
    private val allAttributeTypes = listOf(
        AttributeType.ARTIST,
        AttributeType.CIRCLE,
        AttributeType.SERIE,
        AttributeType.TAG,
        AttributeType.CHARACTER,
        AttributeType.LANGUAGE
    )


    override fun onAttach(context: Context) {
        super.onAttach(context)

        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[RulesEditViewModel::class.java]
        viewModel.attributeTypeFilter.observe(requireActivity()) { updateAttributeFilters(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = IncludeRulesControlsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initUI(requireContext())
    }

    private fun initUI(context: Context) {
        fieldSelectExtension.apply {
            isSelectable = true
            multiSelect = false
            selectOnLongClick = false
            selectWithItemUpdate = true
            allowDeselection = false
            selectionListener = object : ISelectionListener<TextItem<Int>> {
                override fun onSelectionChanged(item: TextItem<Int>, selected: Boolean) {
                    if (selected) onSortFieldChanged()
                }
            }
        }
        binding.let {
            it.fieldList.adapter = fieldFastAdapter
            fieldItemAdapter.set(getSortFields(context, Preferences.getRuleSortField()))

            it.tagFilter.adapter = typeFastAdapter
            it.sortAscDesc.addOnButtonCheckedListener { _, i, b ->
                if (!b) return@addOnButtonCheckedListener
                Preferences.setRuleSortDesc(i == R.id.sort_descending)
                viewModel.loadRules()
            }
        }
        typeFastAdapter.onClickListener =
            { _: View?, _: IAdapter<AttributeTypeFilterItem>, i: AttributeTypeFilterItem, _: Int ->
                onAttributeFilterChanged(i)
            }
    }

    private fun updateAttributeFilters(currentFilter: AttributeType) {
        typeItemAdapter.set(allAttributeTypes.map { attrType ->
            AttributeTypeFilterItem(
                attrType,
                currentFilter == attrType
            )
        })
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
            AttributeType.UNDEFINED
        else
            item.tag as AttributeType
        viewModel.setAttributeType(attributeTypeFilter)
        return true
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private fun onSortFieldChanged() {
        val selectedItems = fieldSelectExtension.selectedItems
        if (selectedItems.isEmpty()) return

        val item = selectedItems.first()
        val code = item.getObject()
        if (code != null) {
            Preferences.setRuleSortField(code)
            viewModel.loadRules()
        }
        updateSortDirection()
    }

    private fun getSortFields(context: Context, currentSortField: Int): List<TextItem<Int>> {
        return listOf(
            TextItem(
                context.resources.getString(R.string.meta_rule_source),
                Preferences.Constant.ORDER_FIELD_SOURCE_NAME,
                false, (Preferences.Constant.ORDER_FIELD_SOURCE_NAME == currentSortField)
            ),
            TextItem(
                context.resources.getString(R.string.meta_rule_target),
                Preferences.Constant.ORDER_FIELD_TARGET_NAME,
                false, (Preferences.Constant.ORDER_FIELD_TARGET_NAME == currentSortField)
            )
        )
    }

    companion object {
        fun invoke(
            context: Context,
            fragmentManager: FragmentManager
        ) {
            val bottomFragment = RuleBottomPanelFragment()
            context.setStyle(
                bottomFragment,
                DialogFragment.STYLE_NORMAL,
                R.style.Theme_Light_BottomSheetDialog
            )
            bottomFragment.show(fragmentManager, "RuleBottomPanelFragment")
        }
    }
}