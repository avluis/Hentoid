package me.devsaki.hentoid.fragments;

import android.app.Activity;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.BundleManager;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.IllegalTags;
import me.devsaki.hentoid.viewmodels.SearchViewModel;
import timber.log.Timber;

import static com.annimon.stream.Collectors.toList;
import static java.lang.String.format;
import static me.devsaki.hentoid.abstracts.DownloadsFragment.MODE_MIKAN;

/**
 * TODO: look into recyclerview.extensions.ListAdapter for a RecyclerView.Adapter that can issue
 * appropriate notify commands based on list diff
 */
public class SearchBottomSheetFragment extends BottomSheetDialogFragment {

    /**
     * Strings submitted to this will be debounced to {@link #searchMasterData(String)} after the given
     * delay.
     *
     * @see Debouncer
     */
    private final Debouncer<String> searchMasterDataDebouncer = new Debouncer<>(1000, this::searchMasterData);

    // Panel that displays the "waiting for metadata info" visuals
    private View tagWaitPanel;
    // Image that displays metadata search message (e.g. loading up / too many results / no result)
    private TextView tagWaitMessage;
    // Search bar
    private SearchView tagSearchView;
    // Container where all available attributes are loaded
    private ViewGroup attributeMosaic;

    // Mode : show library or show Mikan search
    private int mode;

    // Selected attribute types (selection done in the activity view)
    private List<AttributeType> selectedAttributeTypes = new ArrayList<>();

    // ViewModel of the current activity
    private SearchViewModel viewModel;


    // ======== CONSTANTS
    protected static final int MAX_ATTRIBUTES_DISPLAYED = 40;


    public static void show(FragmentManager fragmentManager, int mode, AttributeType[] types) {
        Bundle bundle = new Bundle();
        BundleManager manager = new BundleManager(bundle);

        manager.setMode(mode);
        manager.setAttributeTypes(types);

        SearchBottomSheetFragment searchBottomSheetFragment = new SearchBottomSheetFragment();
        searchBottomSheetFragment.setArguments(bundle);
        searchBottomSheetFragment.setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme);
        searchBottomSheetFragment.show(fragmentManager, null);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Bundle bundle = getArguments();
        if (bundle != null) {
            BundleManager manager = new BundleManager(bundle);
            mode = manager.getMode();
            selectedAttributeTypes = manager.getAttributeTypes();

            if (-1 == mode || selectedAttributeTypes.isEmpty()) {
                throw new RuntimeException("Initialization failed");
            }

            viewModel = ViewModelProviders.of(requireActivity()).get(SearchViewModel.class);
            viewModel.onCategoryChanged(selectedAttributeTypes);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.include_search_filter_category, container, false);
        AttributeType mainAttr = selectedAttributeTypes.get(0);

        // Image that displays current metadata type icon (e.g. face icon for character)
        ImageView tagWaitImage = view.findViewById(R.id.tag_wait_image);
        tagWaitImage.setImageResource(mainAttr.getIcon());

        // Image that displays current metadata type title (e.g. "Character search")
        TextView tagWaitTitle = view.findViewById(R.id.tag_wait_title);
        tagWaitTitle.setText(format("%s search", Helper.capitalizeString(mainAttr.name())));

        tagWaitPanel = view.findViewById(R.id.tag_wait_panel);
        tagWaitMessage = view.findViewById(R.id.tag_wait_description);
        attributeMosaic = view.findViewById(R.id.tag_suggestion);

        tagSearchView = view.findViewById(R.id.tag_filter);
        tagSearchView.setSearchableInfo(getSearchableInfo(requireActivity())); // Associate searchable configuration with the SearchView
        tagSearchView.setQueryHint("Search " + Helper.buildListAsString(selectedAttributeTypes));
        tagSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {

                if (MODE_MIKAN == mode && mainAttr.equals(AttributeType.TAG) && IllegalTags.isIllegal(s)) {
                    Snackbar.make(view, R.string.masterdata_illegal_tag, Snackbar.LENGTH_LONG).show();
                } else if (!s.isEmpty()) {
                    searchMasterData(s);
                }
                tagSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (MODE_MIKAN == mode && mainAttr.equals(AttributeType.TAG) && IllegalTags.isIllegal(s)) {
                    Snackbar.make(view, R.string.masterdata_illegal_tag, Snackbar.LENGTH_LONG).show();
                    searchMasterDataDebouncer.clear();
                } else /*if (!s.isEmpty())*/ {
                    searchMasterDataDebouncer.submit(s);
                }

                return true;
            }
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        searchMasterData("");
        // Update attribute mosaic buttons state according to available metadata
//        viewModel.getAvailableAttributes(selectedAttributeTypes);
//        viewModel.getAvailableAttributesData().observe(this, this::updateAttributeMosaic);
        viewModel.getProposedAttributesData().observe(this, this::onAttributesReady);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        searchMasterDataDebouncer.clear();
    }

    /**
     * Loads the attributes corresponding to the given AttributeType, filtered with the given
     * string
     *
     * @param filter Filter to apply to the attributes name (only retrieve attributes with name like
     *               %s%)
     */
    private void searchMasterData(final String filter) {
        tagWaitMessage.startAnimation(new BlinkAnimation(750, 20));
        tagWaitMessage.setText(R.string.downloads_loading);

        tagWaitPanel.setVisibility(View.VISIBLE);

        viewModel.onCategoryFilterChanged(filter);
    }

    private void onAttributesReady(SearchViewModel.AttributeSearchResult results) {
        if (!results.success) {
            onAttributesFailed(results.message);
            return;
        }

        attributeMosaic.removeAllViews();
        tagWaitMessage.clearAnimation();

        List<Attribute> selectedAttributes = viewModel.getSelectedAttributesData().getValue();

        // Remove unavailable and selected attributes
        List<Attribute> finalSelectedAttributes = (selectedAttributes == null) ? Collections.emptyList() : selectedAttributes;
        List<Attribute> attributes = Stream.of(results.attributes)
                .filter(value -> value.getCount() > 0)
                .filter(value -> !finalSelectedAttributes.contains(value))
                .collect(toList());

        if (attributes.isEmpty()) {
            tagWaitMessage.setText(R.string.masterdata_no_result);
        } else if (attributes.size() > MAX_ATTRIBUTES_DISPLAYED) {
            String searchQuery = tagSearchView.getQuery().toString();

            String errMsg = (0 == searchQuery.length()) ? getString(R.string.masterdata_too_many_results_noquery) : getString(R.string.masterdata_too_many_results_query);
            tagWaitMessage.setText(errMsg.replace("%1", searchQuery));
        } else {
            tagWaitPanel.setVisibility(View.GONE);

            // Display buttons
            for (Attribute attr : attributes) {
                addChoiceChip(attributeMosaic, attr);
            }
        }
    }

    private void onAttributesFailed(String message) {
        Timber.w(message);
        Snackbar bar = Snackbar.make(Objects.requireNonNull(getView()), message, Snackbar.LENGTH_SHORT);
        // Set retry button if Mikan mode on
        if (MODE_MIKAN == mode) {
            bar.setAction("RETRY", v -> viewModel.onCategoryFilterChanged(tagSearchView.getQuery().toString()));
            bar.setDuration(Snackbar.LENGTH_LONG);
        }
        bar.show();

        tagWaitPanel.setVisibility(View.GONE);
    }

    private void addChoiceChip(ViewGroup parent, Attribute attribute) {
        String label = formatAttributeLabel(attribute);

        TextView chip = (TextView) getLayoutInflater().inflate(R.layout.item_chip_choice, parent, false);
        chip.setText(label);
        chip.setTag(attribute);
        chip.setOnClickListener(this::onAttributeChosen);

        parent.addView(chip);
    }

    /**
     * Handler for Attribute button click
     *
     * @param button Button that has been clicked on
     */
    private void onAttributeChosen(View button) {
        Attribute a = (Attribute) button.getTag();

        if (null == viewModel.getSelectedAttributesData().getValue() || !viewModel.getSelectedAttributesData().getValue().contains(a)) { // Add selected tag
            button.setPressed(true);
            viewModel.onAttributeSelected(a);
            searchMasterData(tagSearchView.getQuery().toString());
        }
    }

    private String formatAttributeLabel(Attribute attribute) {
        return format("%s %s", attribute.getName(), attribute.getCount() > 0 ? "(" + attribute.getCount() + ")" : "");
    }

    /**
     * Utility method
     *
     * @param activity the activity to get the SearchableInfo from
     */
    private static SearchableInfo getSearchableInfo(Activity activity) {
        final SearchManager searchManager = (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
        if (searchManager == null) throw new RuntimeException();
        return searchManager.getSearchableInfo(activity.getComponentName());
    }
}
