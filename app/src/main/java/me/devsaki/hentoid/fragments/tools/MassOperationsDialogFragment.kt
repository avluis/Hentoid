package me.devsaki.hentoid.fragments.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.ToolsActivity
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.databinding.DialogToolsMassOperationsBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.widget.ContentSearchManager

class MassOperationsDialogFragment : BaseDialogFragment<MassOperationsDialogFragment.Parent>() {

    companion object {
        const val SEARCH_ARGS = "search_args"

        fun invoke(fragment: Fragment, contentSearchBundle: Bundle?) {
            val args = Bundle()
            args.putBundle(SEARCH_ARGS, contentSearchBundle)
            invoke(fragment, MassOperationsDialogFragment(), args)
        }
    }


    // == UI
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
        }

        refresh()
    }

    private suspend fun countBooks(invertScope: Boolean, keepFavGroups: Boolean): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            val dao = ObjectBoxDAO()
            try {
                var allCount = 0
                dao.streamStoredContent(false, -1, false)
                { allCount++ }

                val currentFilterContent =
                    ContentSearchManager.searchContentIds(contentSearchBundle, dao).toSet()

                val scope = if (invertScope) {
                    val processedContentIds: MutableSet<Long> = HashSet()
                    dao.streamStoredContent(false, -1, false)
                    { c -> if (!currentFilterContent.contains(c.id)) processedContentIds.add(c.id) }
                    processedContentIds
                } else {
                    currentFilterContent
                }

                if (keepFavGroups) {
                    val favGroupsContent =
                        dao.selectStoredFavContentIds(false, groupFavs = true).toSet()
                    Pair(allCount, scope.filterNot { e -> favGroupsContent.contains(e) }.count())
                } else {
                    Pair(allCount, scope.count())
                }
            } finally {
                dao.cleanup()
            }
        }
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
                val counts = countBooks(invertScope, keepFavGroups.isChecked)
                val text = resources.getQuantityString(
                    R.plurals.book_keep,
                    counts.first - counts.second,
                    counts.first - counts.second
                ) + " / " + resources.getQuantityString(
                    if (0 == massOperation.index) R.plurals.book_delete else R.plurals.book_stream,
                    counts.second,
                    counts.second
                )
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