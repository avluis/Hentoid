package me.devsaki.hentoid.fragments.library;

import static me.devsaki.hentoid.util.Preferences.Constant.ARTIST_GROUP_VISIBILITY_ARTISTS;
import static me.devsaki.hentoid.util.Preferences.Constant.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS;
import static me.devsaki.hentoid.util.Preferences.Constant.ARTIST_GROUP_VISIBILITY_GROUPS;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.select.SelectExtension;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.LibraryBottomSortFilterBundle;
import me.devsaki.hentoid.databinding.IncludeLibraryGroupsBottomPanelBinding;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.viewholders.TextItem;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;

public class LibraryBottomGroupsFragment extends BottomSheetDialogFragment {

    private LibraryViewModel viewModel;

    // UI
    private IncludeLibraryGroupsBottomPanelBinding binding = null;

    // RecyclerView controls
    private final ItemAdapter<TextItem<Integer>> itemAdapter = new ItemAdapter<>();
    private final FastAdapter<TextItem<Integer>> fastAdapter = FastAdapter.with(itemAdapter);
    private SelectExtension<TextItem<Integer>> selectExtension;

    // Variables
    private boolean isCustomGroupingAvailable;

    public static void invoke(
            Context context,
            FragmentManager fragmentManager) {
        LibraryBottomSortFilterBundle builder = new LibraryBottomSortFilterBundle();

        LibraryBottomGroupsFragment libraryBottomSheetFragment = new LibraryBottomGroupsFragment();
        libraryBottomSheetFragment.setArguments(builder.getBundle());
        ThemeHelper.setStyle(context, libraryBottomSheetFragment, STYLE_NORMAL, R.style.Theme_Light_BottomSheetDialog);
        libraryBottomSheetFragment.show(fragmentManager, "libraryBottomSheetFragment");
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        Bundle bundle = getArguments();
        if (bundle != null) {
            LibraryBottomSortFilterBundle parser = new LibraryBottomSortFilterBundle(bundle);
        }

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(LibraryViewModel.class);

        viewModel.isCustomGroupingAvailable().observe(this, b -> isCustomGroupingAvailable = b);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = IncludeLibraryGroupsBottomPanelBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // SORT TAB
        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.getOrCreateExtension(SelectExtension.class);
        if (selectExtension != null) {
            selectExtension.setSelectable(true);
            selectExtension.setMultiSelect(false);
            selectExtension.setSelectOnLongClick(false);
            selectExtension.setSelectWithItemUpdate(true);
            selectExtension.setAllowDeselection(false);
            selectExtension.setSelectionListener((i, b) -> this.onSelectionChanged());
        }
        binding.list.setAdapter(fastAdapter);
        itemAdapter.set(getGroupings());

        updateArtistVisiblity();

        binding.artistDisplayGrp.addOnButtonCheckedListener((g, i, b) -> {
            if (!b) return;
            int code;
            if (binding.artistDisplayArtists.isChecked() && binding.artistDisplayGroups.isChecked())
                code = ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS;
            else if (binding.artistDisplayArtists.isChecked())
                code = ARTIST_GROUP_VISIBILITY_ARTISTS;
            else code = ARTIST_GROUP_VISIBILITY_GROUPS;
            Preferences.setArtistGroupVisibility(code);
            updateArtistVisiblity();
            viewModel.searchGroup();
        });
    }

    private List<TextItem<Integer>> getGroupings() {
        List<TextItem<Integer>> result = new ArrayList<>();
        result.add(createFromGrouping(Grouping.FLAT));
        result.add(createFromGrouping(Grouping.ARTIST));
        result.add(createFromGrouping(Grouping.DL_DATE));
        if (isCustomGroupingAvailable) result.add(createFromGrouping(Grouping.CUSTOM));
        return result;
    }

    private TextItem<Integer> createFromGrouping(@NonNull Grouping grouping) {
        return new TextItem<>(
                getResources().getString(grouping.getName()),
                grouping.getId(),
                true,
                Preferences.getGroupingDisplay().getId() == grouping.getId());
    }

    private void updateArtistVisiblity() {
        int visibility = (Preferences.getGroupingDisplay() == Grouping.ARTIST) ? View.VISIBLE : View.GONE;
        binding.artistDisplayTxt.setVisibility(visibility);
        binding.artistDisplayGrp.setVisibility(visibility);
        int code = Preferences.getArtistGroupVisibility();
        binding.artistDisplayArtists.setChecked(ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS == code || ARTIST_GROUP_VISIBILITY_ARTISTS == code);
        binding.artistDisplayGroups.setChecked(ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS == code || ARTIST_GROUP_VISIBILITY_GROUPS == code);
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private void onSelectionChanged() {
        Optional<TextItem<Integer>> item = Stream.of(selectExtension.getSelectedItems()).findFirst();
        if (item.isPresent()) {
            Integer code = item.get().getTag();
            if (code != null) {
                Preferences.setGroupingDisplay(code);
                updateArtistVisiblity();
                viewModel.searchGroup();
            }
        }
    }
}
