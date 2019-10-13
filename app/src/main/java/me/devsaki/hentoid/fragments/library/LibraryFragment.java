package me.devsaki.hentoid.fragments.library;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.activities.SearchActivity;
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle;
import me.devsaki.hentoid.adapters.ContentAdapter2;
import me.devsaki.hentoid.adapters.PagedContentAdapter;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.RandomSeedSingleton;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.widget.LibraryPager;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;
import static com.annimon.stream.Collectors.toCollection;

public class LibraryFragment extends BaseFragment /*implements FlexibleAdapter.EndlessScrollListener*/ {

    private final PagedContentAdapter endlessAdapter = new PagedContentAdapter.Builder()
            .setBookClickListener(this::onItemClick)
            .setSourceClickListener(this::onSourceClick)
            .build();

    private final ContentAdapter2 pagerAdapter = new ContentAdapter2.Builder()
            .setBookClickListener(this::onItemClick)
            .setSourceClickListener(this::onSourceClick)
            .build();

    private LibraryViewModel viewModel;
    private PagedList<Content> library;

    // ======== UI
    private final LibraryPager pager = new LibraryPager(this::onPreviousClick, this::onNextClick, this::onPageChange);
    // "Search" button on top menu
    private MenuItem searchMenu;
    // "Toggle favourites" button on top menu
    private MenuItem favsMenu;
    // "Sort" button on top menu
    private MenuItem orderMenu;
    // Action view associated with search menu button
    private SearchView mainSearchView;
    // Bar with group that has the advancedSearchButton and its background View
    private View advancedSearchBar;
    // CLEAR button on the filter bar
    private TextView searchClearButton;

    private RecyclerView recyclerView;


    // ======== VARIABLES
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private long backButtonPressed;

    // Used to ignore native calls to onQueryTextChange
    private boolean invalidateNextQueryTextChange = false;


    // === SEARCH
    // Last search parameters; used to determine whether or not page number should be reset to 1
    // NB : populated by getCurrentSearchParams
    private String lastSearchParams = "";
    // Current text search query
    private String query = "";
    // Current metadata search query
    private List<Attribute> metadata = Collections.emptyList();


    // Settings
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = this::onSharedPreferenceChanged;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_library, container, false);

        viewModel = ViewModelProviders.of(requireActivity()).get(LibraryViewModel.class);
        Preferences.registerPrefsChangedListener(prefsListener);

        initUI(view);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel.getLibraryPaged().observe(this, this::onPagedLibraryChanged);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.downloads_menu, menu);

        orderMenu = menu.findItem(R.id.action_order);
        searchMenu = menu.findItem(R.id.action_search);
        searchMenu.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                advancedSearchBar.setVisibility(View.VISIBLE);
                invalidateNextQueryTextChange = true;

                // Re-sets the query on screen, since default behaviour removes it right after collapse _and_ expand
                if (!query.isEmpty())
                    // Use of handler allows to set the value _after_ the UI has auto-cleared it
                    // Without that handler the view displays with an empty value
                    new Handler().postDelayed(() -> {
                        invalidateNextQueryTextChange = true;
                        mainSearchView.setQuery(query, false);
                    }, 100);

                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (!isSearchQueryActive()) {
                    advancedSearchBar.setVisibility(View.GONE);
                }
                invalidateNextQueryTextChange = true;
                return true;
            }
        });

        favsMenu = menu.findItem(R.id.action_favourites);
        updateFavouriteFilter();
        favsMenu.setOnMenuItemClickListener(item -> {
            favsMenu.setChecked(!favsMenu.isChecked());
            updateFavouriteFilter();
            viewModel.toggleFavouriteFilter();
            return true;
        });

        mainSearchView = (SearchView) searchMenu.getActionView();
        mainSearchView.setIconifiedByDefault(true);
        mainSearchView.setQueryHint(getString(R.string.search_hint));
        // Change display when text query is typed
        mainSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                query = s;
                viewModel.searchUniversal(query);
                mainSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (invalidateNextQueryTextChange) { // Should not happen when search panel is closing or opening
                    invalidateNextQueryTextChange = false;
                } else if (s.isEmpty()) {
                    query = "";
                    viewModel.searchUniversal(query);
                    searchClearButton.setVisibility(View.GONE);
                }

                return true;
            }
        });

        // Sets the starting book sort icon according to the current sort order
        orderMenu.setIcon(getIconFromSortOrder(Preferences.getContentSortOrder()));
    }

    /**
     * Update favourite filter button appearance (icon and color) on a book
     */
    private void updateFavouriteFilter() {
        favsMenu.setIcon(favsMenu.isChecked() ? R.drawable.ic_fav_full : R.drawable.ic_fav_empty); // TODO handle that with a selector at the UI level ?
    }

    private static int getIconFromSortOrder(int sortOrder) {
        switch (sortOrder) {
            case Preferences.Constant.ORDER_CONTENT_LAST_DL_DATE_FIRST:
                return R.drawable.ic_menu_sort_321;
            case Preferences.Constant.ORDER_CONTENT_LAST_DL_DATE_LAST:
                return R.drawable.ic_menu_sort_123;
            case Preferences.Constant.ORDER_CONTENT_TITLE_ALPHA:
                return R.drawable.ic_menu_sort_az;
            case Preferences.Constant.ORDER_CONTENT_TITLE_ALPHA_INVERTED:
                return R.drawable.ic_menu_sort_za;
            case Preferences.Constant.ORDER_CONTENT_LEAST_READ:
                return R.drawable.ic_menu_sort_unread;
            case Preferences.Constant.ORDER_CONTENT_MOST_READ:
                return R.drawable.ic_menu_sort_read;
            case Preferences.Constant.ORDER_CONTENT_LAST_READ:
                return R.drawable.ic_menu_sort_last_read;
            case Preferences.Constant.ORDER_CONTENT_RANDOM:
                return R.drawable.ic_menu_sort_random;
            default:
                return R.drawable.ic_error;
        }
    }

    /**
     * Callback method used when a sort method is selected in the sort drop-down menu => Updates the
     * UI according to the chosen sort method
     *
     * @param item MenuItem that has been selected
     * @return true if the order has been successfully processed
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int contentSortOrder;

        switch (item.getItemId()) {
            case R.id.action_order_AZ:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_TITLE_ALPHA;
                break;
            case R.id.action_order_321:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_LAST_DL_DATE_FIRST;
                break;
            case R.id.action_order_ZA:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_TITLE_ALPHA_INVERTED;
                break;
            case R.id.action_order_123:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_LAST_DL_DATE_LAST;
                break;
            case R.id.action_order_least_read:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_LEAST_READ;
                break;
            case R.id.action_order_most_read:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_MOST_READ;
                break;
            case R.id.action_order_last_read:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_LAST_READ;
                break;
            case R.id.action_order_random:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_RANDOM;
                RandomSeedSingleton.getInstance().renewSeed();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        orderMenu.setIcon(getIconFromSortOrder(contentSortOrder));
        Preferences.setContentSortOrder(contentSortOrder);
        viewModel.performSearch();

        return true;
    }

    /**
     * Indicates whether a search query is active (using universal search or advanced search) or not
     *
     * @return True if a search query is active (using universal search or advanced search); false if not (=whole unfiltered library selected)
     */
    private boolean isSearchQueryActive() {
        return (!query.isEmpty() || !metadata.isEmpty());
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewModel != null) viewModel.onSaveState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (viewModel != null) viewModel.onRestoreState(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Display the "update success" dialog when an update is detected on a release version
        if (!BuildConfig.DEBUG) {
            if (0 == Preferences.getLastKnownAppVersionCode()) { // Don't show that during first run
                Preferences.setLastKnownAppVersionCode(BuildConfig.VERSION_CODE);
            } else if (Preferences.getLastKnownAppVersionCode() < BuildConfig.VERSION_CODE) {
                UpdateSuccessDialogFragment.invoke(requireFragmentManager());
                Preferences.setLastKnownAppVersionCode(BuildConfig.VERSION_CODE);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 999
                && resultCode == Activity.RESULT_OK
                && data != null && data.getExtras() != null) {
            Uri searchUri = new SearchActivityBundle.Parser(data.getExtras()).getUri();

            if (searchUri != null) {
                query = searchUri.getPath();
                metadata = SearchActivityBundle.Parser.parseSearchUri(searchUri);
                viewModel.search(query, metadata);
            }
        }
    }

    @Override
    public void onDestroy() {
        Preferences.unregisterPrefsChangedListener(prefsListener);
        super.onDestroy();
    }

    private void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        Timber.i("Prefs change detected : %s", key);
        switch (key) {
            case Preferences.Key.PREF_ENDLESS_SCROLL:
                initPagingMethod(Preferences.getEndlessScroll());
                break;
            default:
                // Other changes aren't handled here
        }
    }

    private void initUI(View rootView) {
        advancedSearchBar = rootView.findViewById(R.id.advanced_search_background);
        // TextView used as advanced search button
        TextView advancedSearchButton = rootView.findViewById(R.id.advanced_search_btn);
        advancedSearchButton.setOnClickListener(v -> onAdvancedSearchButtonClick());

        searchClearButton = rootView.findViewById(R.id.search_clear_btn);
        searchClearButton.setOnClickListener(v -> {
            query = "";
            mainSearchView.setQuery("", false);
            metadata.clear();
            searchClearButton.setVisibility(View.GONE);
            viewModel.searchUniversal("");
        });

        recyclerView = requireViewById(rootView, R.id.library_list);

        // Pager
        pager.initUI(rootView);
        initPagingMethod(Preferences.getEndlessScroll());
    }

    private void onAdvancedSearchButtonClick() {
        Intent search = new Intent(this.getContext(), SearchActivity.class);

        SearchActivityBundle.Builder builder = new SearchActivityBundle.Builder();

        if (!metadata.isEmpty())
            builder.setUri(SearchActivityBundle.Builder.buildSearchUri(metadata));
        search.putExtras(builder.getBundle());

        startActivityForResult(search, 999);
        searchMenu.collapseActionView();
    }

    private void initPagingMethod(boolean isEndless) {
        if (isEndless) {
            pager.disable();
            recyclerView.setAdapter(endlessAdapter);
            if (library != null) endlessAdapter.submitList(library);
        } else {
            pager.enable();
            recyclerView.setAdapter(pagerAdapter);
            if (library != null) loadPagerAdapter(library);
        }
    }

    private void loadPagerAdapter(PagedList<Content> library) {
        if (library.isEmpty()) pagerAdapter.setShelf(Collections.emptyList());
        else {
            int minIndex = (pager.getCurrentPageNumber() - 1) * Preferences.getContentPageQuantity();
            int maxIndex = Math.min(minIndex + Preferences.getContentPageQuantity(), library.size() - 1);

            pagerAdapter.setShelf(library.subList(minIndex, maxIndex + 1));
        }
        pagerAdapter.notifyDataSetChanged();
    }

    private void onPagedLibraryChanged(PagedList<Content> result) {
        Timber.d(">>Library changed ! Size=%s", result.size());
        if (result.size() > 0) Timber.d(">>1st item is ID %s", result.get(0).getId());

        updateTitle(result.size(), result.size()); // TODO total size = size of unfiltered content

        if (isSearchQueryActive()) {
            advancedSearchBar.setVisibility(View.VISIBLE);
            searchClearButton.setVisibility(View.VISIBLE);
            if (result.size() > 0 && searchMenu != null) searchMenu.collapseActionView();
        } else {
            advancedSearchBar.setVisibility(View.GONE);
        }

        // User searches a book ID
        // => Suggests searching through all sources except those where the selected book ID is already in the collection
        if (Helper.isNumeric(query)) {
            ArrayList<Integer> siteCodes = Stream.of(result)
                    .filter(content -> query.equals(content.getUniqueSiteId()))
                    .map(Content::getSite)
                    .map(Site::getCode)
                    .collect(toCollection(ArrayList::new));

            SearchBookIdDialogFragment.invoke(requireFragmentManager(), query, siteCodes);
        }

        if (Preferences.getEndlessScroll()) endlessAdapter.submitList(result);
        else {
            /* TODO - this is not always what we want (e.g. new download coming when browsing)
        this behaviour should occur only after a voluntary action
         */
            pager.setCurrentPage(1);
            pager.setPageCount((int) Math.ceil(result.size() * 1.0 / Preferences.getContentPageQuantity()));
            loadPagerAdapter(result);
        }

        /* TODO - this is not always what we want (e.g. new download coming when browsing)
        this behaviour should occur only after a voluntary action
         */
        recyclerView.scrollToPosition(0);

        library = result;
    }

    /**
     * Update the screen title according to current search filter (#TOTAL BOOKS) if no filter is
     * enabled (#FILTERED / #TOTAL BOOKS) if a filter is enabled
     */
    private void updateTitle(long totalSelectedCount, long totalCount) {
        Activity activity = getActivity();
        if (activity != null) { // Has to be crash-proof; sometimes there's no activity there...
            String title;
            if (totalSelectedCount == totalCount)
                title = totalCount + " items";
            else {
                Resources res = getResources();
                title = res.getQuantityString(R.plurals.number_of_book_search_results, (int) totalSelectedCount, (int) totalSelectedCount, (int) totalSelectedCount);
            }
            activity.setTitle(title);
        }
    }


    private void onSourceClick(Content content) {
        ContentHelper.viewContent(requireContext(), content);
    }

    private void onItemClick(Content content) {
        ContentHelper.openContent(requireContext(), content, viewModel.getSearchManagerBundle());
    }

    @Override
    public boolean onBackPressed() {
        if (backButtonPressed + 2000 > SystemClock.elapsedRealtime()) {
            return true;
        } else {
            backButtonPressed = SystemClock.elapsedRealtime();
            Context ctx = getContext();
            if (ctx != null) ToastUtil.toast(ctx, R.string.press_back_again);
        }
        return false;
    }

    private void onPreviousClick(View v) {
        pager.previousPage();
        handleNewPage();
    }

    private void onNextClick(View v) {
        pager.nextPage();
        handleNewPage();
    }

    private void onPageChange(int page) {
        pager.setCurrentPage(page);
        handleNewPage();
    }

    private void handleNewPage() {
        int page = pager.getCurrentPageNumber();
        pager.setCurrentPage(page); // TODO - handle this transparently...
        loadPagerAdapter(library);
    }
}
