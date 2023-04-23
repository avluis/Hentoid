package me.devsaki.hentoid.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.DiffCallback
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil
import com.mikepenz.fastadapter.select.SelectExtension
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.RenamingRuleBundle
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.databinding.ActivityRulesBinding
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.fragments.metadata.MetaEditRuleDialogFragment
import me.devsaki.hentoid.fragments.metadata.RuleBottomPanelFragment
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.viewholders.RuleItem
import me.devsaki.hentoid.viewmodels.RulesEditViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory


class RenamingRulesActivity : BaseActivity(), MetaEditRuleDialogFragment.Parent {

    // == Communication
    private lateinit var viewModel: RulesEditViewModel

    // == UI
    private var binding: ActivityRulesBinding? = null

    // Action view associated with search menu button
    private lateinit var actionSearchView: SearchView

    // Rules
    private val itemAdapter = ItemAdapter<RuleItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)
    private lateinit var selectExtension: SelectExtension<RuleItem>

    // == Vars
    // Used to ignore native calls to onQueryTextChange
    private var invalidateNextQueryTextChange: Boolean = false
    private var queryFilter = ""
    private var attributeTypeFilter = AttributeType.UNDEFINED

    private val ruleItemDiffCallback: DiffCallback<RuleItem> =
        object : DiffCallback<RuleItem> {
            override fun areItemsTheSame(oldItem: RuleItem, newItem: RuleItem): Boolean {
                return oldItem.identifier == newItem.identifier
            }

            override fun areContentsTheSame(
                oldItem: RuleItem,
                newItem: RuleItem
            ): Boolean {
                return oldItem.attrType == newItem.attrType
                        && oldItem.source.equals(newItem.source, true)
                        && oldItem.target.equals(newItem.target, true)
            }

            override fun getChangePayload(
                oldItem: RuleItem,
                oldItemPosition: Int,
                newItem: RuleItem,
                newItemPosition: Int
            ): Any? {
                val diffBundleBuilder = RenamingRuleBundle()
                if (oldItem.attrType != newItem.attrType) {
                    diffBundleBuilder.attrType = newItem.attrType.code
                }
                if (oldItem.source != newItem.source) {
                    diffBundleBuilder.source = newItem.source
                }
                if (oldItem.target != newItem.target) {
                    diffBundleBuilder.target = newItem.target
                }
                return if (diffBundleBuilder.isEmpty) null else diffBundleBuilder.bundle
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)

        binding = ActivityRulesBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        val vmFactory = ViewModelFactory(application)
        viewModel = ViewModelProvider(this, vmFactory)[RulesEditViewModel::class.java]

        bindUI()
        bindInteractions()

        viewModel.rulesList.observe(this) { this.onRulesChanged(it) }
        viewModel.attributeTypeFilter.observe(this) { attributeTypeFilter = it }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    private fun onRulesChanged(rules: List<RenamingRule>) {
        val items = rules.map { r -> RuleItem(r) }
        FastAdapterDiffUtil.set(itemAdapter, items, ruleItemDiffCallback)
    }

    private fun bindUI() {
        binding?.let {
            it.toolbar.setNavigationOnClickListener { finish() }
            it.toolbar.setOnMenuItemClickListener(this::onToolbarItemClicked)
            it.selectionToolbar.setOnMenuItemClickListener(this::onSelectionToolbarItemClicked)

            val searchMenu = it.toolbar.menu.findItem(R.id.action_search)
            searchMenu.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    invalidateNextQueryTextChange = true

                    // Re-sets the query on screen, since default behaviour removes it right after collapse _and_ expand
                    if (queryFilter.isNotEmpty()) // Use of handler allows to set the value _after_ the UI has auto-cleared it
                    // Without that handler the view displays with an empty value
                        Handler(Looper.getMainLooper()).postDelayed({
                            invalidateNextQueryTextChange = true
                            actionSearchView.setQuery(queryFilter, false)
                        }, 100)
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    invalidateNextQueryTextChange = true
                    return true
                }
            })

            actionSearchView = searchMenu.actionView as SearchView
            actionSearchView.setIconifiedByDefault(true)
            // Change display when text query is typed
            // Change display when text query is typed
            actionSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(s: String): Boolean {
                    queryFilter = s
                    viewModel.setQuery(queryFilter)
                    actionSearchView.clearFocus()
                    return true
                }

                override fun onQueryTextChange(s: String): Boolean {
                    if (invalidateNextQueryTextChange) { // Should not happen when search panel is closing or opening
                        invalidateNextQueryTextChange = false
                    } else if (s.isEmpty()) {
                        queryFilter = ""
                        viewModel.setQuery(queryFilter)
                    }
                    return true
                }
            })
        }
    }

    private fun bindInteractions() {
        binding?.let {
            // Rules list init
            it.list.adapter = fastAdapter
            // New rule FAB
            it.tagsFab.setOnClickListener {
                MetaEditRuleDialogFragment.invoke(this, true, 0, attributeTypeFilter)
            }

        }
        selectExtension = fastAdapter.requireOrCreateExtension()
        selectExtension.let { se ->
            se.isSelectable = true
            se.multiSelect = true
            se.selectOnLongClick = true
            se.selectWithItemUpdate = true
            se.selectionListener = object : ISelectionListener<RuleItem> {
                override fun onSelectionChanged(item: RuleItem, selected: Boolean) {
                    onSelectionChanged()
                }
            }
        }
        fastAdapter.onClickListener =
            { _: View?, _: IAdapter<RuleItem>, i: RuleItem, _: Int ->
                onItemClick(i)
            }
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private fun onSelectionChanged() {
        val selectedItems: Set<RuleItem> = selectExtension.selectedItems
        val selectedCount = selectedItems.size
        if (0 == selectedCount) {
            binding?.selectionToolbar?.visibility = View.GONE
            selectExtension.selectOnLongClick = true
        } else {
            binding?.selectionToolbar?.visibility = View.VISIBLE
        }
    }

    private fun onToolbarItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_sort_filter -> showSortFilterPanel()
            else -> return true
        }
        return true
    }

    private fun onSelectionToolbarItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_delete -> deleteSelectedItems()
            else -> return true
        }
        return true
    }

    private fun showSortFilterPanel() {
        RuleBottomPanelFragment.invoke(
            this,
            supportFragmentManager
        )
    }

    private fun deleteSelectedItems() {
        val selectedItems = selectExtension.selectedItems
        val builder = MaterialAlertDialogBuilder(this)
        val title = resources.getQuantityString(
            R.plurals.ask_delete_multiple,
            selectedItems.size,
            selectedItems.size
        )
        builder.setMessage(title)
            .setPositiveButton(
                R.string.ok
            ) { _, _ ->
                leaveSelectionMode()
                viewModel.removeRules(selectedItems.map { i -> i.rule.id })
            }
            .setNegativeButton(R.string.cancel, null)
            .create().show()
    }

    // TODO doc
    private fun leaveSelectionMode() {
        selectExtension.selectOnLongClick = true
        // Warning : next line makes FastAdapter cycle through all items,
        // which has a side effect of calling TiledPageList.onPagePlaceholderInserted,
        // flagging the end of the list as being the last displayed position
        val selection = selectExtension.selections.toMutableSet()
        if (selection.isNotEmpty()) selectExtension.deselect(selection)
        binding?.selectionToolbar?.visibility = View.GONE
    }

    /**
     * Callback for attribute item click
     *
     * @param item RuleItem that has been clicked on
     */
    private fun onItemClick(item: RuleItem): Boolean {
        if (selectExtension.selectOnLongClick) {
            editRule(item.rule)
            return true
        }
        return false
    }


    private fun editRule(rule: RenamingRule) {
        MetaEditRuleDialogFragment.invoke(this, false, rule.id)
    }

    override fun onCreateRule(type: AttributeType, source: String, target: String) {
        viewModel.createRule(type, source, target)
    }

    override fun onEditRule(id: Long, source: String, target: String) {
        viewModel.editRule(id, source, target)
    }

    override fun onRemoveRule(id: Long) {
        viewModel.removeRules(listOf(id))
    }
}