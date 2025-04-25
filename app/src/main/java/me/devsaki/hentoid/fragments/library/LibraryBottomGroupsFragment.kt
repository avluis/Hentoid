package me.devsaki.hentoid.fragments.library

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.select.SelectExtension
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.LibraryBottomSortFilterBundle
import me.devsaki.hentoid.databinding.IncludeLibraryGroupsBottomPanelBinding
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.setStyle
import me.devsaki.hentoid.viewholders.TextItem
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory

class LibraryBottomGroupsFragment : BottomSheetDialogFragment() {
    private lateinit var viewModel: LibraryViewModel

    // UI
    private var binding: IncludeLibraryGroupsBottomPanelBinding? = null

    // RecyclerView controls
    private val itemAdapter = ItemAdapter<TextItem<Int>>()
    private val fastAdapter = FastAdapter.with(itemAdapter)
    private var selectExtension: SelectExtension<TextItem<Int>>? = null

    // Variables
    private var isCustomGroupingAvailable = false
    private var isDynamicGroupingAvailable = false

    companion object {
        @Synchronized
        fun invoke(
            context: Context,
            fragmentManager: FragmentManager
        ) {
            // Don't re-create it if already shown
            for (fragment in fragmentManager.fragments) if (fragment is LibraryBottomGroupsFragment) return
            val builder = LibraryBottomSortFilterBundle()
            val libraryBottomSheetFragment = LibraryBottomGroupsFragment()
            libraryBottomSheetFragment.arguments = builder.bundle
            context.setStyle(
                libraryBottomSheetFragment,
                STYLE_NORMAL,
                R.style.Theme_Light_BottomSheetDialog
            )
            libraryBottomSheetFragment.show(fragmentManager, "libraryBottomSheetFragment")
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[LibraryViewModel::class.java]
        viewModel.isCustomGroupingAvailable.observe(this) { b: Boolean ->
            isCustomGroupingAvailable = b
            itemAdapter.set(getGroupings())
        }
        viewModel.isDynamicGroupingAvailable.observe(this) { b: Boolean ->
            isDynamicGroupingAvailable = b
            itemAdapter.set(getGroupings())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = IncludeLibraryGroupsBottomPanelBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // SORT TAB
        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.requireOrCreateExtension()
        selectExtension?.apply {
            isSelectable = true
            multiSelect = false
            selectOnLongClick = false
            selectWithItemUpdate = true
            allowDeselection = false
            selectionListener =
                object : ISelectionListener<TextItem<Int>> {
                    override fun onSelectionChanged(item: TextItem<Int>, selected: Boolean) {
                        if (selected) onSelectionChanged(item)
                    }
                }
        }
        binding?.apply {
            list.adapter = fastAdapter
            updateArtistVisibility()
            artistDisplayGrp.addOnButtonCheckedListener { _, _, _ ->
                val code =
                    if (artistDisplayArtists.isChecked && artistDisplayGroups.isChecked) Settings.Value.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS
                    else if (artistDisplayArtists.isChecked) Settings.Value.ARTIST_GROUP_VISIBILITY_ARTISTS else Settings.Value.ARTIST_GROUP_VISIBILITY_GROUPS
                Settings.artistGroupVisibility = code
                updateArtistVisibility()
                viewModel.searchGroup()
            }
        }
    }

    private fun getGroupings(): List<TextItem<Int>> {
        val result: MutableList<TextItem<Int>> = ArrayList()
        result.add(createFromGrouping(Grouping.FLAT))
        result.add(createFromGrouping(Grouping.ARTIST))
        result.add(createFromGrouping(Grouping.DL_DATE))
        if (isDynamicGroupingAvailable) result.add(createFromGrouping(Grouping.DYNAMIC))
        if (isCustomGroupingAvailable) result.add(createFromGrouping(Grouping.CUSTOM))
        return result
    }

    private fun createFromGrouping(grouping: Grouping): TextItem<Int> {
        return TextItem(
            resources.getString(grouping.displayName),
            grouping.id,
            true,
            Settings.groupingDisplay == grouping.id
        )
    }

    private fun updateArtistVisibility() {
        binding?.apply {
            val visibility =
                if (Settings.getGroupingDisplayG() == Grouping.ARTIST) View.VISIBLE else View.INVISIBLE
            artistDisplayTxt.visibility = visibility
            artistDisplayGrp.visibility = visibility
            val code = Settings.artistGroupVisibility
            artistDisplayArtists.isChecked =
                Settings.Value.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS == code || Settings.Value.ARTIST_GROUP_VISIBILITY_ARTISTS == code
            artistDisplayGroups.isChecked =
                Settings.Value.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS == code || Settings.Value.ARTIST_GROUP_VISIBILITY_GROUPS == code
        }
    }

    /**
     * Callback for any selected item
     */
    private fun onSelectionChanged(item: TextItem<Int>) {
        item.getObject()?.let {
            viewModel.setGrouping(it)
            updateArtistVisibility()
        }
    }
}