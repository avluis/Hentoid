package me.devsaki.hentoid.fragments.library;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.annimon.stream.Stream;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.SearchActivity;
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle;
import me.devsaki.hentoid.adapters.ContentAdapter;
import me.devsaki.hentoid.adapters.LibraryAdapter;
import me.devsaki.hentoid.adapters.PagedContentAdapter;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.RandomSeedSingleton;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.widget.LibraryPager;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;
import static com.annimon.stream.Collectors.toCollection;

public class LibraryFragment extends BaseFragment {

    private final PagedContentAdapter endlessAdapter = new PagedContentAdapter.Builder()
            .setBookClickListener(this::onBookClick)
            .setSourceClickListener(this::onBookSourceClick)
            .setFavClickListener(this::onBookFavouriteClick)
            .setErrorClickListener(this::onBookErrorClick)
            .setSelectionChangedListener(this::onSelectionChanged)
            .build();

    private final ContentAdapter pagerAdapter = new ContentAdapter.Builder()
            .setBookClickListener(this::onBookClick)
            .setSourceClickListener(this::onBookSourceClick)
            .setFavClickListener(this::onBookFavouriteClick)
            .setErrorClickListener(this::onBookErrorClick)
            .setSelectionChangedListener(this::onSelectionChanged)
            .build();

    // ======== COMMUNICATION
    // Viewmodel
    private LibraryViewModel viewModel;
    // Settings listener
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = this::onSharedPreferenceChanged;

    // ======== UI
    // Wrapper for the bottom pager
    private final LibraryPager pager = new LibraryPager(this::handleNewPage);
    // Text that displays in the background when the list is empty
    private TextView emptyText;
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
    // Main view where books are displayed
    private RecyclerView recyclerView;


    // ======== VARIABLES
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private long backButtonPressed;
    // Used to ignore native calls to onQueryTextChange
    private boolean invalidateNextQueryTextChange = false;
    // Total number of books in the whole unfiltered library
    private int totalContentCount;
    // True when a new search has been performed and its results have not been handled yet
    private boolean newSearch = false;
    // Collection of books according to current filters
    private PagedList<Content> library;


    // === SEARCH PARAMETERS
    // Current text search query
    private String query = "";
    // Current metadata search query
    private List<Attribute> metadata = Collections.emptyList();


    // === TOOLBAR ACTION MODE
    // Action mode manager for the toolbar
    private ActionMode mActionMode;


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

        viewModel.getNewSearch().observe(this, this::onNewSearch);
        viewModel.getLibraryPaged().observe(this, this::onLibraryChanged);
        viewModel.getTotalContent().observe(this, this::onTotalContentChanged);
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

        // Set the starting book sort icon according to the current sort order
        orderMenu.setIcon(getIconFromSortOrder(Preferences.getContentSortOrder()));
    }

    /**
     * Called when the action mode is created; startActionMode() was called.
     */
    private final ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        // Called when action mode is first created.
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.downloads_context_menu, menu);

            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode,
        // but may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            LibraryAdapter adapter = getAdapter();
            boolean isMultipleSelection = adapter.getSelectedItemsCount() > 1;

            menu.findItem(R.id.action_delete).setVisible(!isMultipleSelection);
            menu.findItem(R.id.action_share).setVisible(!isMultipleSelection);
            menu.findItem(R.id.action_archive).setVisible(!isMultipleSelection);
            menu.findItem(R.id.action_delete_sweep).setVisible(isMultipleSelection);

            return true;
        }

        // Called when the user selects a contextual menu item.
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_share:
                    shareSelectedItems();
                    mode.finish();

                    return true;
                case R.id.action_delete:
                case R.id.action_delete_sweep:
                    purgeSelectedItems();
                    mode.finish();

                    return true;
                case R.id.action_archive:
                    archiveSelectedItems();
                    mode.finish();

                    return true;
                default:
                    return false;
            }
        }

        // Called when the user exits the action mode.
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            getAdapter().clearSelection();
            mActionMode = null;
        }
    };

    /**
     * Callback for the "share item" action button
     */
    private void shareSelectedItems() {
        List<Content> selectedItems = getAdapter().getSelectedItems();
        Context context = getActivity();
        if (1 == selectedItems.size() && context != null)
            ContentHelper.shareContent(context, selectedItems.get(0));
    }

    /**
     * Callback for the "delete item" action button
     */
    private void purgeSelectedItems() {
        List<Content> selectedItems = getAdapter().getSelectedItems();
        if (!selectedItems.isEmpty()) askDeleteItems(selectedItems);
    }

    /**
     * Callback for the "archive item" action button
     */
    private void archiveSelectedItems() {
        List<Content> selectedItems = getAdapter().getSelectedItems();
        Context context = getActivity();
        if (1 == selectedItems.size() && context != null) {
            ToastUtil.toast(R.string.packaging_content);
            viewModel.archiveContent(selectedItems.get(0), this::onContentArchiveSuccess);
        }
    }

    /**
     * Callback for the success of the "archive item" action
     *
     * @param archive File containing the created archive
     */
    private void onContentArchiveSuccess(File archive) {
        Context context = getActivity();
        if (null == context) return;

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_STREAM,
                FileProvider.getUriForFile(context, FileHelper.AUTHORITY, archive));
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileHelper.getExtension(archive.getName()));
        sendIntent.setType(mimeType);

        try {
            context.startActivity(sendIntent);
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "No activity found to send %s", archive.getPath());
            ToastUtil.toast(context, R.string.error_send, Toast.LENGTH_LONG);
        }
    }

    /**
     * Display the yes/no dialog to make sure the user really wants to delete selected items
     *
     * @param items Items to be deleted if the answer is yes
     */
    private void askDeleteItems(final List<Content> items) {
        Context context = getActivity();
        if (null == context) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        String title = context.getResources().getQuantityString(R.plurals.ask_delete_multiple, items.size());
        builder.setMessage(title)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> {
                            getAdapter().clearSelection();
                            viewModel.deleteItems(items, this::onDeleteSuccess, this::onDeleteError);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog, which) -> getAdapter().clearSelection())
                .create().show();
    }

    /**
     * Callback for the success of the "delete item" action
     */
    private void onDeleteSuccess() {
        Context context = getActivity();
        if (context != null) ToastUtil.toast(context, "Selected items have been deleted.");
    }

    /**
     * Callback for the failure of the "delete item" action
     */
    private void onDeleteError(Throwable t) {
        Timber.e(t);
        if (t instanceof ContentNotRemovedException) {
            ContentNotRemovedException e = (ContentNotRemovedException) t;
            Snackbar snackbar = Snackbar.make(recyclerView, "Content removal failed", BaseTransientBottomBar.LENGTH_LONG);
            if (e.getContent() != null) {
                viewModel.flagContentDelete(e.getContent(), false);
                List<Content> contents = new ArrayList<>();
                contents.add(e.getContent());
                snackbar.setAction("RETRY", v -> viewModel.deleteItems(contents, this::onDeleteSuccess, this::onDeleteError));
            }
            snackbar.show();
        }
    }

    /**
     * Update favourite filter button appearance on the action bar
     */
    private void updateFavouriteFilter() {
        favsMenu.setIcon(favsMenu.isChecked() ? R.drawable.ic_fav_full : R.drawable.ic_fav_empty);
    }

    /**
     * Get the icon resource ID according to the sort order code
     */
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
     * Callback method used when a sort method is selected in the sort drop-down menu
     * Updates the UI according to the chosen sort method
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
            case R.id.action_favourites:
                contentSortOrder = Preferences.Constant.ORDER_CONTENT_FAVOURITE;
                item.setChecked(!item.isChecked());
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        // If favourite is selected, apply the filter
        if (Preferences.Constant.ORDER_CONTENT_FAVOURITE == contentSortOrder) {
            updateFavouriteFilter();
            viewModel.toggleFavouriteFilter();
        } else { // Update the order menu icon and run a new search
            orderMenu.setIcon(getIconFromSortOrder(contentSortOrder));
            Preferences.setContentSortOrder(contentSortOrder);
            viewModel.performSearch();
        }

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

    /**
     * Called when returning from the Advanced Search screen
     */
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

    @Override
    public boolean onBackPressed() {
        LibraryAdapter adapter = getAdapter();
        // If content is selected, deselect it
        if (adapter.getSelectedItemsCount() > 0) {
            adapter.clearSelection();
            backButtonPressed = 0;

            return false;
        }

        // If none of the above, user is asking to leave => use double-tap
        if (backButtonPressed + 2000 > SystemClock.elapsedRealtime()) {
            return true;
        } else {
            backButtonPressed = SystemClock.elapsedRealtime();
            Context c = getContext();
            if (c != null) ToastUtil.toast(getContext(), R.string.press_back_again);

            if (recyclerView.getLayoutManager() != null)
                ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
        }

        return false;
    }

    /**
     * Callback for any change in Preferences
     */
    private void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        Timber.i("Prefs change detected : %s", key);
        if (Preferences.Key.PREF_ENDLESS_SCROLL.equals(key)) {
            initPagingMethod(Preferences.getEndlessScroll());
        }
    }

    /**
     * Initialize the UI components
     *
     * @param rootView Root view of the library screen
     */
    private void initUI(View rootView) {
        emptyText = rootView.findViewById(R.id.library_empty_txt);

        advancedSearchBar = rootView.findViewById(R.id.advanced_search_group);
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
        // Disable blink animation on card change (bind holder)
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator)
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);

        // Pager
        pager.initUI(rootView);
        initPagingMethod(Preferences.getEndlessScroll());
    }

    /**
     * Handler for the "Advanced search" button
     */
    private void onAdvancedSearchButtonClick() {
        Intent search = new Intent(this.getContext(), SearchActivity.class);

        SearchActivityBundle.Builder builder = new SearchActivityBundle.Builder();

        if (!metadata.isEmpty())
            builder.setUri(SearchActivityBundle.Builder.buildSearchUri(metadata));
        search.putExtras(builder.getBundle());

        startActivityForResult(search, 999);
        searchMenu.collapseActionView();
    }

    /**
     * Initialize the paging method of the screen
     *
     * @param isEndless True if endless mode has to be set; false if paged mode has to be set
     */
    private void initPagingMethod(boolean isEndless) {
        if (isEndless) {
            pager.hide();
            recyclerView.setAdapter(endlessAdapter);
            if (library != null) endlessAdapter.submitList(library);
        } else {
            pager.show();
            recyclerView.setAdapter(pagerAdapter);
            if (library != null) loadBookshelf(library);
        }
    }

    /**
     * Loads current shelf of books to into the paged mode adapter
     * NB : A bookshelf is the portion of the collection that is displayed on screen by the paged mode
     * The width of the shelf is determined by the "Quantity per page" setting
     *
     * @param library Library to extract the shelf from
     */
    private void loadBookshelf(PagedList<Content> library) {
        if (library.isEmpty()) pagerAdapter.setShelf(Collections.emptyList());
        else {
            int minIndex = (pager.getCurrentPageNumber() - 1) * Preferences.getContentPageQuantity();
            int maxIndex = Math.min(minIndex + Preferences.getContentPageQuantity(), library.size());

            if (minIndex >= maxIndex) { // We just deleted the last item of the last page => Go back one page
                pager.setCurrentPage(pager.getCurrentPageNumber() - 1);
                loadBookshelf(library);
                return;
            }

            pagerAdapter.setShelf(library.subList(minIndex, maxIndex));
        }
        pagerAdapter.notifyDataSetChanged();
    }

    /**
     * LiveData callback when a new search takes place
     *
     * @param b Unused parameter (always set to true)
     */
    private void onNewSearch(Boolean b) {
        newSearch = b;
    }

    /**
     * LiveData callback when the library changes
     * - Either because a new search has been performed
     * - Or because a book has been downloaded, deleted, updated
     *
     * @param result Current library according to active filters
     */
    private void onLibraryChanged(PagedList<Content> result) {
        Timber.d(">>Library changed ! Size=%s", result.size());

        updateTitle(result.size(), totalContentCount);

        // Update background text
        if (result.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            if (isSearchQueryActive()) emptyText.setText(R.string.search_entry_not_found);
            else emptyText.setText(R.string.downloads_empty_library);
        } else emptyText.setVisibility(View.GONE);

        // Update visibility of advanced search bar
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

        // Update displayed books
        if (Preferences.getEndlessScroll()) endlessAdapter.submitList(result);
        else {
            if (newSearch) pager.setCurrentPage(1);
            pager.setPageCount((int) Math.ceil(result.size() * 1.0 / Preferences.getContentPageQuantity()));
            loadBookshelf(result);
        }

        // If the update is the result of a new search, let the items be sorted
        // and get back to the top of the list
        if (newSearch) new Handler().postDelayed(() -> recyclerView.scrollToPosition(0), 300);

        newSearch = false;
        library = result;
    }

    /**
     * LiveData callback when the total number of books changes (because of book download of removal)
     *
     * @param count Current book count in the whole, unfiltered library
     */
    private void onTotalContentChanged(Integer count) {
        totalContentCount = count;
        if (library != null) updateTitle(library.size(), totalContentCount);
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
                title = res.getQuantityString(R.plurals.number_of_book_search_results, (int) totalSelectedCount, (int) totalSelectedCount, totalCount);
            }
            activity.setTitle(title);
        }
    }

    /**
     * Callback for the book holder itself
     *
     * @param content Content that has been clicked on
     */
    private void onBookClick(Content content) {
        ContentHelper.openHentoidViewer(requireContext(), content, viewModel.getSearchManagerBundle());
    }

    /**
     * Callback for the "source" button of the book holder
     *
     * @param content Content whose "source" button has been clicked on
     */
    private void onBookSourceClick(Content content) {
        ContentHelper.viewContent(requireContext(), content);
    }

    /**
     * Callback for the "favourite" button of the book holder
     *
     * @param content Content whose "favourite" button has been clicked on
     */
    private void onBookFavouriteClick(Content content) {
        viewModel.toggleContentFavourite(content);
    }

    /**
     * Callback for the "error" button of the book holder
     *
     * @param content Content whose "error" button has been clicked on
     */
    private void onBookErrorClick(Content content) {
        int images;
        int imgErrors = 0;

        Context context = getContext();
        if (null == context) return;
        if (null == content.getImageFiles()) return;

        images = content.getImageFiles().size();

        for (ImageFile imgFile : content.getImageFiles())
            if (imgFile.getStatus() == StatusContent.ERROR) imgErrors++;

        String message = context.getString(R.string.redownload_dialog_message).replace("@clean", images - imgErrors + "").replace("@error", imgErrors + "").replace("@total", images + "");
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(R.string.redownload_dialog_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> downloadContent(context, content))
                .setNegativeButton(android.R.string.no, null)
                .setNeutralButton(R.string.redownload_view_log,
                        (dialog, which) -> showErrorLog(context, content))
                .show();
    }

    /**
     * Add the given content back to the download queue
     *
     * @param context Context to be used
     * @param content Content to add back to the download queue
     */
    private void downloadContent(@NonNull Context context, @NonNull final Content content) {
        viewModel.addContentToQueue(content);

        ContentQueueManager.getInstance().resumeQueue(context);

        Snackbar snackbar = Snackbar.make(recyclerView, R.string.add_to_queue, BaseTransientBottomBar.LENGTH_LONG);
        snackbar.setAction("VIEW QUEUE", v -> viewQueue());
        snackbar.show();
    }

    /**
     * Generate and show the detailed error log for the given content
     *
     * @param context Context to be used
     * @param content Content whose error log to be generated and shown
     */
    private void showErrorLog(@NonNull Context context, @NonNull final Content content) {
        List<ErrorRecord> errorLog = content.getErrorLog();
        List<String> log = new ArrayList<>();

        LogUtil.LogInfo errorLogInfo = new LogUtil.LogInfo();
        errorLogInfo.logName = "Error";
        errorLogInfo.fileName = "error_log" + content.getId();
        errorLogInfo.noDataMessage = "No error detected.";

        if (errorLog != null) {
            log.add("Error log for " + content.getTitle() + " [" + content.getUniqueSiteId() + "@" + content.getSite().getDescription() + "] : " + errorLog.size() + " errors");
            for (ErrorRecord e : errorLog) log.add(e.toString());
        }

        File logFile = LogUtil.writeLog(context, log, errorLogInfo);
        if (logFile != null) {
            Snackbar snackbar = Snackbar.make(recyclerView, R.string.cleanup_done, BaseTransientBottomBar.LENGTH_LONG);
            snackbar.setAction("READ LOG", v -> FileHelper.openFile(context, logFile));
            snackbar.show();
        }
    }

    /**
     * Get the currently active adapter (according to current mode : endless or paged)
     *
     * @return Currently active adapter
     */
    private LibraryAdapter getAdapter() {
        if (Preferences.getEndlessScroll()) return endlessAdapter;
        else return pagerAdapter;
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     *
     * @param selectedCount Number of currently selected items
     */
    private void onSelectionChanged(long selectedCount) {

        if (0 == selectedCount) {
            if (mActionMode != null) mActionMode.finish();
        } else {
            if (mActionMode == null)
                mActionMode = advancedSearchBar.startActionMode(mActionModeCallback);

            mActionMode.invalidate();
            mActionMode.setTitle(
                    selectedCount + (selectedCount > 1 ? " items selected" : " item selected"));
        }
    }

    /**
     * Handler for any page change
     */
    private void handleNewPage() {
        loadBookshelf(library);
        recyclerView.scrollToPosition(0);
    }

    /**
     * Navigate to the queue screen
     */
    private void viewQueue() {
        Intent intent = new Intent(requireContext(), QueueActivity.class);
        requireContext().startActivity(intent);
    }
}
