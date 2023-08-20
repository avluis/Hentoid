package me.devsaki.hentoid.fragments.library;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.select.SelectExtension;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.LibraryActivity;
import me.devsaki.hentoid.activities.bundles.LibraryBottomSortFilterBundle;
import me.devsaki.hentoid.databinding.IncludeLibrarySortFilterBottomPanelBinding;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.viewholders.TextItem;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.widget.ContentSearchManager;
import me.devsaki.hentoid.widget.GroupSearchManager;

public class LibraryBottomSortFilterFragment extends BottomSheetDialogFragment {

    private LibraryViewModel viewModel;

    // UI
    private IncludeLibrarySortFilterBottomPanelBinding binding = null;
    private final ImageView[] stars = new ImageView[6];

    // RecyclerView controls
    private final ItemAdapter<TextItem<Integer>> itemAdapter = new ItemAdapter<>();
    private final FastAdapter<TextItem<Integer>> fastAdapter = FastAdapter.with(itemAdapter);
    private SelectExtension<TextItem<Integer>> selectExtension;

    // Variables
    private boolean isUngroupedGroupDisplayed;
    private boolean isGroupsDisplayed;
    private boolean favouriteFilter;
    private boolean nonFavouriteFilter;
    private boolean completedFilter;
    private boolean notCompletedFilter;
    private int ratingFilter = -1;
    private @ColorInt
    int greyColor;
    private @ColorInt
    int selectedColor;


    public static synchronized void invoke(
            Context context,
            FragmentManager fragmentManager,
            boolean isGroupsDisplayed,
            boolean isUngroupedGroupDisplayed) {
        // Don't re-create it if already shown
        for (Fragment fragment : fragmentManager.getFragments())
            if (fragment instanceof LibraryBottomSortFilterFragment) return;

        LibraryBottomSortFilterBundle builder = new LibraryBottomSortFilterBundle();
        builder.setGroupsDisplayed(isGroupsDisplayed);
        builder.setUngroupedGroupDisplayed(isUngroupedGroupDisplayed);

        LibraryBottomSortFilterFragment libraryBottomSheetFragment = new LibraryBottomSortFilterFragment();
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
            isGroupsDisplayed = parser.isGroupsDisplayed();
            isUngroupedGroupDisplayed = parser.isUngroupedGroupDisplayed();
        }

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(LibraryViewModel.class);

        viewModel.getContentSearchManagerBundle().observe(this, b -> {
            if (isGroupsDisplayed) return;
            ContentSearchManager.ContentSearchBundle searchBundle = new ContentSearchManager.ContentSearchBundle(b);
            favouriteFilter = searchBundle.getFilterBookFavourites();
            nonFavouriteFilter = searchBundle.getFilterBookNonFavourites();
            completedFilter = searchBundle.getFilterBookCompleted();
            notCompletedFilter = searchBundle.getFilterBookNotCompleted();
            ratingFilter = searchBundle.getFilterRating();
            updateFilters();
        });
        viewModel.getGroupSearchManagerBundle().observe(this, b -> {
            if (!isGroupsDisplayed) return;
            GroupSearchManager.GroupSearchBundle searchBundle = new GroupSearchManager.GroupSearchBundle(b);
            favouriteFilter = searchBundle.getFilterFavourites();
            nonFavouriteFilter = searchBundle.getFilterNonFavourites();
            ratingFilter = searchBundle.getFilterRating();
            updateFilters();
        });

        greyColor = ContextCompat.getColor(context, R.color.medium_gray);
        selectedColor = ThemeHelper.getColor(context, R.color.secondary_light);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = IncludeLibrarySortFilterBottomPanelBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Hack to show the bottom sheet in expanded state by default (https://stackoverflow.com/a/45706484/8374722)
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
                if (dialog != null) {
                    FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                    if (bottomSheet != null) {
                        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                        behavior.setSkipCollapsed(true);
                    }
                }
            }
        });

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.getOrCreateExtension(SelectExtension.class);
        if (selectExtension != null) {
            selectExtension.setSelectable(true);
            selectExtension.setMultiSelect(false);
            selectExtension.setSelectOnLongClick(false);
            selectExtension.setSelectWithItemUpdate(true);
            selectExtension.setAllowDeselection(false);
            selectExtension.setSelectionListener((item, selected) -> {
                if (selected) this.onSelectionChanged();
            });
        }
        binding.list.setAdapter(fastAdapter);
        itemAdapter.set(getSortFields());

        updateSortDirection();

        binding.sortRandom.setOnClickListener(v -> {
            viewModel.shuffleContent();
            viewModel.searchContent();
        });
        binding.sortAscDesc.addOnButtonCheckedListener((g, i, b) -> {
            if (!b) return;
            if (isGroupsDisplayed) {
                Preferences.setGroupSortDesc(i == R.id.sort_descending);
                viewModel.searchGroup();
            } else {
                Preferences.setContentSortDesc(i == R.id.sort_descending);
                viewModel.searchContent();
            }
        });

        binding.filterFavsBtn.setOnClickListener(
                v -> {
                    favouriteFilter = !favouriteFilter;
                    updateFilters();
                    if (isGroupsDisplayed)
                        viewModel.setGroupFavouriteFilter(favouriteFilter);
                    else
                        viewModel.setContentFavouriteFilter(favouriteFilter);
                }
        );
        binding.filterNonFavsBtn.setOnClickListener(
                v -> {
                    nonFavouriteFilter = !nonFavouriteFilter;
                    updateFilters();
                    if (isGroupsDisplayed)
                        viewModel.setGroupNonFavouriteFilter(nonFavouriteFilter);
                    else
                        viewModel.setContentNonFavouriteFilter(nonFavouriteFilter);
                }
        );
        binding.filterCompletedBtn.setOnClickListener(
                v -> {
                    completedFilter = !completedFilter;
                    updateFilters();
                    viewModel.setCompletedFilter(completedFilter);
                }
        );
        binding.filterNotCompletedBtn.setOnClickListener(
                v -> {
                    notCompletedFilter = !notCompletedFilter;
                    updateFilters();
                    viewModel.setNotCompletedFilter(notCompletedFilter);
                }
        );

        stars[0] = binding.filterRatingNone;
        stars[1] = binding.filterRating1;
        stars[2] = binding.filterRating2;
        stars[3] = binding.filterRating3;
        stars[4] = binding.filterRating4;
        stars[5] = binding.filterRating5;

        for (int i = 0; i < 6; i++) {
            final int rating = i;
            stars[i].setOnClickListener(v -> setRating(rating, false));
        }
    }

    private void updateSortDirection() {
        boolean isRandom = ((isGroupsDisplayed ? Preferences.getGroupSortField() : Preferences.getContentSortField()) == Preferences.Constant.ORDER_FIELD_RANDOM);
        if (isRandom) {
            binding.sortAscending.setVisibility(View.GONE);
            binding.sortDescending.setVisibility(View.GONE);
            binding.sortRandom.setVisibility(View.VISIBLE);
            binding.sortRandom.setChecked(true);
        } else {
            binding.sortRandom.setVisibility(View.GONE);
            binding.sortAscending.setVisibility(View.VISIBLE);
            binding.sortDescending.setVisibility(View.VISIBLE);
            boolean currentPrefSortDesc = isGroupsDisplayed ? Preferences.isGroupSortDesc() : Preferences.isContentSortDesc();
            binding.sortAscDesc.check(currentPrefSortDesc ? R.id.sort_descending : R.id.sort_ascending);
        }
    }

    private void updateFilters() {
        binding.filterFavsBtn.setColorFilter(favouriteFilter ? selectedColor : greyColor);
        binding.filterNonFavsBtn.setColorFilter(nonFavouriteFilter ? selectedColor : greyColor);

        int completeFiltersVisibility = isGroupsDisplayed ? View.GONE : View.VISIBLE;
        binding.filterCompletedBtn.setVisibility(completeFiltersVisibility);
        binding.filterNotCompletedBtn.setVisibility(completeFiltersVisibility);

        binding.filterCompletedBtn.setColorFilter(completedFilter ? selectedColor : greyColor);
        binding.filterNotCompletedBtn.setColorFilter(notCompletedFilter ? selectedColor : greyColor);

        setRating(ratingFilter, true);
    }

    private List<TextItem<Integer>> getSortFields() {
        List<TextItem<Integer>> result = new ArrayList<>();
        if (isGroupsDisplayed) {
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_TITLE));
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_CHILDREN));
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE));
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_CUSTOM));
        } else {
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_TITLE));
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_ARTIST));
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_NB_PAGES));
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE));
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_DOWNLOAD_COMPLETION_DATE));
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_UPLOAD_DATE));
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_READ_DATE));
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_READS));
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_SIZE));
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_READ_PROGRESS));
            if (Preferences.getGroupingDisplay().canReorderBooks() && !isUngroupedGroupDisplayed)
                result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_CUSTOM));
            result.add(createFromFieldCode(Preferences.Constant.ORDER_FIELD_RANDOM));
        }
        return result;
    }

    private TextItem<Integer> createFromFieldCode(int sortFieldCode) {
        int currentPrefFieldCode = isGroupsDisplayed ? Preferences.getGroupSortField() : Preferences.getContentSortField();
        return new TextItem<>(
                getResources().getString(LibraryActivity.getNameFromFieldCode(sortFieldCode)),
                sortFieldCode,
                true,
                currentPrefFieldCode == sortFieldCode);
    }

    private void setRating(int rating, boolean init) {
        // Tap current rating -> clear
        boolean clear = !init && rating == ratingFilter;

        for (int i = 5; i >= 0; i--) {
            boolean activated = i <= rating && !clear;
            if (i > 0)
                stars[i].setImageResource(activated ? R.drawable.ic_star_full : R.drawable.ic_star_empty);
            int color = activated ? selectedColor : greyColor;
            // Don't colour the 1st icon if we're choosing at least 1 star
            if (activated && rating > 0 && 0 == i) color = greyColor;
            stars[i].setColorFilter(color);
        }

        ratingFilter = clear ? -1 : rating;
        if (!init) {
            if (isGroupsDisplayed)
                viewModel.setGroupRatingFilter(ratingFilter);
            else
                viewModel.setContentRatingFilter(ratingFilter);
        }
    }

    /**
     * Callback for any selection change (i.e. another sort field has been selected)
     */
    private void onSelectionChanged() {
        Optional<TextItem<Integer>> item = Stream.of(selectExtension.getSelectedItems()).findFirst();
        if (item.isPresent()) {
            Integer code = item.get().getTag();
            if (code != null)
                if (isGroupsDisplayed) {
                    Preferences.setGroupSortField(code);
                    viewModel.searchGroup();
                } else {
                    Preferences.setContentSortField(code);
                    viewModel.searchContent();
                }
        }
        updateSortDirection();
    }
}
