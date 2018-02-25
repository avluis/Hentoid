package me.devsaki.hentoid.fragments;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageButton;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.DownloadsFragment;
import me.devsaki.hentoid.adapters.ContentAdapter.ContentsWipedListener;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.Helper;
import timber.log.Timber;

/**
 * Created by avluis on 08/26/2016.
 * Presents the list of downloaded works to the user in a classic pager.
 */
public class PagerFragment extends DownloadsFragment implements ContentsWipedListener {

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
                        if (newContent) {
                            toolTip.setVisibility(View.VISIBLE);
                        }
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
                commitRefresh();
            }
        });

        btnRefresh.setOnLongClickListener(v -> {
            if (currentPage != 1 && isLoaded) {
                Helper.toast(mContext, R.string.moving_to_first_page);
                clearQuery(1);

                return true;
            } else if (currentPage == 1 && isLoaded) {
                Helper.toast(mContext, R.string.on_first_page);
                commitRefresh();

                return true;
            }

            return false;
        });
    }

    @Override
    protected void checkResults() {
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

            update();
            checkContent(true);
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
            if (show) {
                toolbar.setVisibility(View.VISIBLE);
            } else {
                toolbar.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void displayResults() {
        result = search.getContent();

        if (isLoaded) {
            toggleUI(0);
        }

        if (query.isEmpty()) {
            if (result != null && !result.isEmpty()) {

                List<Content> singleResult = result;
                mAdapter.replaceAll(singleResult);
                mListView.setAdapter(mAdapter);

                toggleUI(SHOW_RESULT);
                updatePager();
            }
        } else {
            Timber.d("Query: %s", query);
            if (result != null && !result.isEmpty()) {
                Timber.d("Result: Match.");

                List<Content> searchResults = result;
                mAdapter.replaceAll(searchResults);
                mListView.setAdapter(mAdapter);

                toggleUI(SHOW_RESULT);
                showToolbar(true, true);
                updatePager();
            } else {
                Timber.d("Result: Nothing to match.");
                displayNoResults();
            }
        }
    }
}
