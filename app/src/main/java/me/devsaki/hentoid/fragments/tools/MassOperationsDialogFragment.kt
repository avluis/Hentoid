package me.devsaki.hentoid.fragments.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import kotlinx.coroutines.launch
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.ToolsActivity
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.databinding.DialogToolsMassOperationsBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.viewholders.DrawerItem
import me.devsaki.hentoid.widget.ContentSearchManager
import me.devsaki.hentoid.workers.BaseDeleteWorker

const val SEARCH_ARGS = "search_args"

class MassOperationsDialogFragment : BaseDialogFragment<MassOperationsDialogFragment.Parent>() {

    companion object {
        fun invoke(fragment: Fragment, contentSearchBundle: Bundle?) {
            val args = Bundle()
            args.putBundle(SEARCH_ARGS, contentSearchBundle)
            invoke(fragment, MassOperationsDialogFragment(), args)
        }
    }


    // == UI
    private val itemAdapter = ItemAdapter<DrawerItem<Any>>()
    private val fastAdapter = FastAdapter.with(itemAdapter)

    private var binding: DialogToolsMassOperationsBinding? = null

    private lateinit var contentSearchBundle: ContentSearchManager.ContentSearchBundle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireNotNull(arguments) { "No arguments found" }
        contentSearchBundle = requireArguments().getBundle(SEARCH_ARGS)
            ?.let { ContentSearchManager.ContentSearchBundle(it) }
            ?: ContentSearchManager.ContentSearchBundle()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        binding = DialogToolsMassOperationsBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        binding?.apply {
            massOperation.index = Settings.massOperation
            massOperationScope.index = Settings.massOperationScope

            massOperation.setOnIndexChangeListener { _ -> refresh() }
            massOperationScope.setOnIndexChangeListener { _ -> refresh() }

            keepFavGroups.setOnCheckedChangeListener { _, _ -> refresh() }
            confirm.setOnCheckedChangeListener { _, _ -> refresh() }
            actionButton.setOnClickListener { onActionClick() }

            warningsList.adapter = fastAdapter
        }

        refresh()
    }

    private fun refresh() {
        binding?.apply {
            Settings.massOperation = massOperation.index
            Settings.massOperationScope = massOperationScope.index

            externalTxt.isVisible =
                Settings.externalLibraryUri.isNotEmpty()
                        && !Settings.isDeleteExternalLibrary
                        && 0 == massOperation.index
            actionButton.isEnabled = confirm.isChecked

            lifecycleScope.launch {
                val invertScope = 1 == massOperationScope.index
                val counts = BaseDeleteWorker.selectScopedContent(
                    ObjectBoxDAO(),
                    contentSearchBundle,
                    invertScope,
                    keepFavGroups.isChecked,
                    1 == massOperation.index
                )
                val text = resources.getQuantityString(
                    R.plurals.book_keep,
                    counts.totalCount - counts.scope.size,
                    counts.totalCount - counts.scope.size
                ) + " / " + resources.getQuantityString(
                    if (0 == massOperation.index) R.plurals.book_delete else R.plurals.book_stream,
                    counts.totalCount,
                    counts.totalCount
                )
                if (counts.warnings.isNotEmpty()) {
                    itemAdapter.clear()
                    counts.warnings.forEachIndexed { idx, resId ->
                        itemAdapter.add(
                            DrawerItem(
                                resources.getString(resId),
                                R.drawable.ic_warning,
                                idx.toLong(),
                                true
                            )
                        )
                    }
                    warningsList.isVisible = true
                }
                bookCount.text = text
                bookCount.isVisible = true
            }
        }
    }

    private fun onActionClick() {
        binding?.apply {
            parent?.onMassProcess(
                if (0 == massOperation.index) ToolsActivity.MassOperation.DELETE else ToolsActivity.MassOperation.STREAM,
                1 == massOperationScope.index,
                keepFavGroups.isChecked
            )
        }
        dismissAllowingStateLoss()
    }

    interface Parent {
        fun onMassProcess(
            operation: ToolsActivity.MassOperation,
            invertScope: Boolean,
            keepGroupPrefs: Boolean
        )
    }
}