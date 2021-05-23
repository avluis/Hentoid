package me.devsaki.hentoid.fragments;

import android.app.Activity;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle;
import me.devsaki.hentoid.adapters.AvailableAttributeAdapter;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.viewmodels.SearchViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;
import static java.lang.String.format;

/**
 * Bottom fragment that displays the available attributes in the advanced search screen
 * <p>
 * TODO 1 : look into recyclerview.extensions.ListAdapter for a RecyclerView.Adapter that can issue
 * appropriate notify commands based on list diff
 * <p>
 * TODO 2 : Use PagedList and FastAdapter to reduce boilerplate code used to display the endless list of available attributes
 * NB : only possible when ObjectBox implements SELECT Object.Field, COUNT(Object.Field) GROUP BY Object.Field to handle
 * source selection in a native ObjectBox query.
 */
public class SearchBottomSheetFragment extends BottomSheetDialogFragment {

    /**
     * Strings submitted to this will be debounced to {@link #searchMasterData} after the given
     * delay.
     *
     * @see Debouncer
     */
    private Debouncer<String> searchMasterDataDebouncer;


    // Panel that displays the "waiting for metadata info" visuals
    private View tagWaitPanel;
    // Image that displays metadata search message (e.g. loading up / too many results / no result)
    private TextView tagWaitMessage;
    // Search bar
    private SearchView tagSearchView;
    // Container where all proposed attributes are loaded
    private AvailableAttributeAdapter attributeAdapter;

    private boolean clearOnSuccess; // Flag to clear the adapter on content reception

    // Current page of paged content (used to display the attributes list as an endless list)
    private int currentPage;
    private long mTotalSelectedCount; // Total count of current available attributes

    // Selected attribute types (selection done in the activity view)
    private List<AttributeType> selectedAttributeTypes = new ArrayList<>();

    // ViewModel of the current activity
    private SearchViewModel viewModel;

    // Flag to indicate is the fragment has been initialized, to avoid a double LiveData notification
    // See https://stackoverflow.com/a/50474911
    private boolean isInitialized = false;


    // ======== CONSTANTS
    private static final int ATTRS_PER_PAGE = 40;

    private boolean excludeAttr = false;

    public static void invoke(@NonNull Context context, @NonNull FragmentManager fragmentManager, AttributeType[] types, boolean excludeClicked) {
        SearchActivityBundle.Builder builder = new SearchActivityBundle.Builder();

        builder.setAttributeTypes(types);
        builder.setExcludeMode(excludeClicked);

        SearchBottomSheetFragment searchBottomSheetFragment = new SearchBottomSheetFragment();
        searchBottomSheetFragment.setArguments(builder.getBundle());
        ThemeHelper.setStyle(context, searchBottomSheetFragment, STYLE_NORMAL, R.style.Theme_Light_BottomSheetDialog);
        searchBottomSheetFragment.show(fragmentManager, "searchBottomSheetFragment");
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        Bundle bundle = getArguments();
        if (bundle != null) {
            SearchActivityBundle.Parser parser = new SearchActivityBundle.Parser(bundle);
            selectedAttributeTypes = parser.getAttributeTypes();
            excludeAttr = parser.getExcludeMode();
            long groupId = parser.getGroupId();
            currentPage = 1;

            if (selectedAttributeTypes.isEmpty()) {
                throw new IllegalArgumentException("Initialization failed");
            }

            ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
            viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(SearchViewModel.class);
            viewModel.setAttributeTypes(selectedAttributeTypes);
            viewModel.setGroup(groupId);
        }
        searchMasterDataDebouncer = new Debouncer<>(context, 1000, this::searchMasterData);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.include_search_filter_category, container, false);
        AttributeType mainAttr = selectedAttributeTypes.get(0);

        // Image that displays current metadata type icon (e.g. face icon for character)
        ImageView tagWaitImage = requireViewById(rootView, R.id.tag_wait_image);
        tagWaitImage.setImageResource(mainAttr.getIcon());

        // Image that displays current metadata type title (e.g. "Character search")
        TextView tagWaitTitle = requireViewById(rootView, R.id.tag_wait_title);
        tagWaitTitle.setText(format("%s search", StringHelper.capitalizeString(mainAttr.name())));

        tagWaitPanel = requireViewById(rootView, R.id.tag_wait_panel);
        tagWaitMessage = requireViewById(rootView, R.id.tag_wait_description);
        RecyclerView attributeMosaic = requireViewById(rootView, R.id.tag_suggestion);
        FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(this.getContext());
//        layoutManager.setAlignContent(AlignContent.FLEX_START); <-- not supported
        layoutManager.setAlignItems(AlignItems.STRETCH);
        layoutManager.setFlexWrap(FlexWrap.WRAP);
        attributeMosaic.setLayoutManager(layoutManager);
        attributeAdapter = new AvailableAttributeAdapter();
        attributeAdapter.setOnScrollToEndListener(this::loadMore);
        attributeAdapter.setOnClickListener(this::onAttributeChosen);
        attributeMosaic.setAdapter(attributeAdapter);

        tagSearchView = requireViewById(rootView, R.id.tag_filter);
        tagSearchView.setSearchableInfo(getSearchableInfo(requireActivity())); // Associate searchable configuration with the SearchView
        tagSearchView.setQueryHint("Search " + android.text.TextUtils.join(", ", selectedAttributeTypes));
        tagSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {

                if (!s.isEmpty()) searchMasterData(s);
                tagSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                searchMasterDataDebouncer.submit(s);

                return true;
            }
        });

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel.getAvailableAttributesData().observe(getViewLifecycleOwner(), this::onAttributesReady);
        searchMasterData("");
    }

    @Override
    public void onResume() {
        super.onResume();
        isInitialized = true;
    }

    @Override
    public void onDestroyView() {
        searchMasterDataDebouncer.clear();
        super.onDestroyView();
    }

    /**
     * Load the attributes corresponding to the given AttributeType, filtered with the given
     * string (applying "contains" filter)
     *
     * @param filter Filter to apply to the attributes name (only retrieve attributes with name like
     *               %s%)
     */
    private void searchMasterData(final String filter) {
        currentPage = 1;
        searchMasterData(filter, true, true);
    }

    /**
     * Search the attributes master data according to the given parameters
     *
     * @param filter              Filter to apply to the attributes name (only retrieve attributes with name like %s%)
     * @param displayLoadingImage True if a "loading..." image has to be displayed
     * @param clearOnSuccess      True if the currently displayed list should be clear when this call succeeds
     *                            (should be true for new searches; false for "load more" queries)
     */
    private void searchMasterData(final String filter, boolean displayLoadingImage, boolean clearOnSuccess) {
        if (displayLoadingImage) {
            tagWaitMessage.startAnimation(new BlinkAnimation(750, 20));
            tagWaitMessage.setText(R.string.downloads_loading);
            tagWaitPanel.setVisibility(View.VISIBLE);
        }
        this.clearOnSuccess = clearOnSuccess;

        viewModel.setAttributeQuery(filter, currentPage, ATTRS_PER_PAGE);
    }

    /**
     * Observer for changes in the available attributes (= query results)
     *
     * @param results Available attributes according to current search query
     */
    private void onAttributesReady(CollectionDAO.AttributeQueryResult results) {
        if (!isInitialized) return; // Hack to avoid double calls from LiveData

        tagWaitMessage.clearAnimation();

        List<Attribute> selectedAttributes = viewModel.getSelectedAttributesData().getValue();
        selectedAttributes = (null == selectedAttributes) ?
                Collections.emptyList()
                : Stream.of(selectedAttributes).filter(a -> selectedAttributeTypes.contains(a.getType())).toList();

        // Remove selected attributes from the result set
        results.attributes.removeAll(selectedAttributes);

        mTotalSelectedCount = results.totalSelectedAttributes/* - selectedAttributes.size()*/;
        if (clearOnSuccess) attributeAdapter.clear();
        if (0 == mTotalSelectedCount) {
            String searchQuery = tagSearchView.getQuery().toString();
            if (searchQuery.isEmpty()) this.dismiss();
            else tagWaitMessage.setText(R.string.masterdata_no_result);
        } else {
            tagWaitPanel.setVisibility(View.GONE);
            attributeAdapter.setFormatWithNamespace(selectedAttributeTypes.size() > 1);
            attributeAdapter.add(results.attributes);
        }
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
            a.setExcluded(excludeAttr);
            viewModel.addSelectedAttribute(a);
            // Empty query and display all attributes again
            tagSearchView.setQuery("", false);
            searchMasterData("");
        }
    }

    /**
     * Utility method
     *
     * @param activity the activity to get the SearchableInfo from
     */
    private static SearchableInfo getSearchableInfo(Activity activity) {
        final SearchManager searchManager = (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
        if (searchManager == null) throw new IllegalArgumentException();
        return searchManager.getSearchableInfo(activity.getComponentName());
    }

    /**
     * Indicates if the current "page" of loaded attributes is the last one
     */
    private boolean isLastPage() {
        return (currentPage * ATTRS_PER_PAGE >= mTotalSelectedCount);
    }

    /**
     * Callback when the recyclerview has reached the end of loaded items
     * Load the next set of items
     */
    private void loadMore() {
        if (!isLastPage()) { // NB : A "page" is a group of loaded attributes. Last page is reached when scrolling reaches the very end of the list
            Timber.d("Load more data now~");
            currentPage++;
            searchMasterData(tagSearchView.getQuery().toString(), false, false);
        }
    }
}
