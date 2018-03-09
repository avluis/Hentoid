package me.devsaki.hentoid.fragments;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageButton;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.DownloadsFragment;
import me.devsaki.hentoid.adapters.ContentAdapter.ContentsWipedListener;
import me.devsaki.hentoid.adapters.ContentAdapter.EndlessScrollListener;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

/**
 * Created by avluis on 08/26/2016.
 * Presents the list of downloaded works to the user in an endless scroll list.
 */
public class EndlessFragment extends DownloadsFragment implements ContentsWipedListener,
        EndlessScrollListener {

    @Override
    protected void attachScrollListener() {
        mListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // Show toolbar:
                if (!override && mAdapter.getItemCount() > 0) {
                    // At top of list
                    if (llm.findViewByPosition(llm.findFirstVisibleItemPosition())
                            .getTop() == 0 && llm.findFirstVisibleItemPosition() == 0) {
                        showToolbar(true, false);
                        if (newContent) {
                            toolTip.setVisibility(View.VISIBLE);
                        }
                    }

                    // Last item in list
                    if (llm.findLastVisibleItemPosition() == mAdapter.getItemCount() - 1) {
                        showToolbar(true, false);
                    } else {
                        // When scrolling up
                        if (dy < -10) {
                            showToolbar(true, false);
                            if (newContent) {
                                toolTip.setVisibility(View.VISIBLE);
                            }
                            // When scrolling down
                        } else if (dy > 100) {
                            showToolbar(false, false);
                            if (newContent) {
                                toolTip.setVisibility(View.GONE);
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    protected void attachRefresh(View rootView) {
        ImageButton btnRefresh = rootView.findViewById(R.id.btnRefresh);
        btnRefresh.setOnClickListener(v -> {
            if (isLoaded) {
                update();
            }
        });

        btnRefresh.setOnLongClickListener(v -> false);
    }

    @Override
    protected void queryPrefs() {
        super.queryPrefs();

        booksPerPage = Preferences.Default.PREF_QUANTITY_PER_PAGE_DEFAULT;
    }

    @Override
    protected void checkResults() {
/*
        if (isLoaded && result != null) {
            Timber.d("Result is not null.");
            result.clear();
        } else {
            Timber.d("Contents are null.");
        }
*/
        mAdapter.setEndlessScrollListener(this);

        if (0 == mAdapter.getItemCount())
        {
            if (!isLoaded) update();
            else checkContent(true);
        } else {
            checkContent(false);
            mAdapter.setContentsWipedListener(this);
        }

        /*
        if (result != null) {
            Timber.d("Result is not null.");
            Timber.d("Are results loaded? %s", isLoaded);
            if (0 == mAdapter.getItemCount() && !isLoaded) {
                Timber.d("Result is empty!");
                update();
            }
            checkContent(false);
            mAdapter.setContentsWipedListener(this);
        } else {
            Timber.d("Result is null.");

            if (isLoaded) { // Do not load anything if a loading activity is already taking place
                update();
                checkContent(true);
            }
        }
        */

        if (!query.isEmpty()) {
            Timber.d("Saved Query: %s", query);
            if (isLoaded) update();
        }
    }

    @Override
    protected void showToolbar(boolean show, boolean override) {
        this.override = override;

        if (override) {
            if (show) {
                toolbar.setVisibility(View.VISIBLE);
            } else {
                toolbar.setVisibility(View.GONE);
            }
        } else {
            toolbar.setVisibility(View.GONE);
        }
    }

    @Override
    protected void displayResults(List<Content> results) {
        toggleUI(SHOW_DEFAULT);

        //mAdapter.replaceAll(results);
        mAdapter.add(results);

         toggleUI(SHOW_RESULT);
         updatePager(); // NB : In EndlessFragment, a "page" is a group of loaded books. Last page is reached when scrolling reaches the very end of the book list

        /*
        if (query.isEmpty()) {
            Timber.d("Query empty");
            if (result != null && !result.isEmpty()) {
                Timber.d("Result items : %s",result.size());
                if (contents == null) {
                    contents = result;
                    mAdapter.setContentList(contents);
                    mListView.setAdapter(mAdapter);
                } else {
                    int curSize = mAdapter.getItemCount();
                    Timber.d("CurSize : %s",curSize);
                    contents.addAll(result);

                    int size = contents.size()-1;
                    if (size < 0) size = 0;
                    mAdapter.notifyItemRangeInserted(curSize, size);
                }

                toggleUI(SHOW_RESULT);
                updatePager();
            }
        } else {
            Timber.d("Query: %s", query);
            if (result != null && !result.isEmpty()) {
                Timber.d("Result: Match.");

                List<Content> searchResults = result;
                mAdapter.setContentList(searchResults);
                mListView.setAdapter(mAdapter);

                toggleUI(SHOW_RESULT);
                showToolbar(true, true);
                updatePager();
            } else {
                Timber.d("Result: Nothing to match.");
                displayNoResults();
            }
        }
        */
    }

    @Override
    public void onLoadMore() {
        if (query.isEmpty()) {
            if (!isLastPage) {
                currentPage++;
                searchContent();
                Timber.d("Load more data now~");
            }
        } else {
            Timber.d("Endless Scrolling disabled.");
        }
    }
}
