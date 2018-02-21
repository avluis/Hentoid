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
import me.devsaki.hentoid.util.ConstsPrefs;
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
                if (!override && result != null && result.size() > 0) {
                    // At top of list
                    if (llm.findViewByPosition(llm.findFirstVisibleItemPosition())
                            .getTop() == 0 && llm.findFirstVisibleItemPosition() == 0) {
                        showToolbar(true, false);
                        if (newContent) {
                            toolTip.setVisibility(View.VISIBLE);
                        }
                    }

                    // Last item in list
                    if (llm.findLastVisibleItemPosition() == result.size() - 1) {
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
        ImageButton btnRefresh = (ImageButton) rootView.findViewById(R.id.btnRefresh);
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

        qtyPages = ConstsPrefs.PREF_QUANTITY_PER_PAGE_DEFAULT;
    }

    @Override
    protected void checkResults() {
        if (contents != null) {
            Timber.d("Contents are not null.");
        } else if (isLoaded && result != null) {
            Timber.d("Result is not null.");
            result.clear();
        } else {
            Timber.d("Contents are null.");
        }
        mAdapter.setEndlessScrollListener(this);

        if (result != null) {
            Timber.d("Result is not null.");
            Timber.d("Are results loaded? %s", isLoaded);
            if (result.isEmpty() && !isLoaded) {
                Timber.d("Result is empty!");
                update();
            }
            checkContent(false);
            mAdapter.setContentsWipedListener(this);
        } else {
            Timber.d("Result is null.");

            if (isLoaded) { // Do not load anything if a loading activity is already
                update();
                checkContent(true);
            }
        }

        if (!query.isEmpty()) {
            Timber.d("Saved Query: %s", query);
            update();
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
    protected void displayResults() {
        result = search.getContent();

        if (isLoaded) {
            toggleUI(SHOW_DEFAULT);
        }

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
                    mAdapter.notifyItemRangeInserted(curSize, contents.size() - 1);
                }

                toggleUI(SHOW_RESULT);
                updatePager();
                mAdapter.enableFooter(false);
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
                mAdapter.enableFooter(false);
            } else {
                Timber.d("Result: Nothing to match.");
                displayNoResults();
            }
        }
    }

    @Override
    public void onLoadMore() {
        if (query.isEmpty()) {
            if (!isLastPage) {
                currentPage++;
                searchContent();
                Timber.d("Load more data now~");
                mAdapter.enableFooter(true);
            }
        } else {
            Timber.d("Endless Scrolling disabled.");
            mAdapter.enableFooter(false);
        }
    }
}
