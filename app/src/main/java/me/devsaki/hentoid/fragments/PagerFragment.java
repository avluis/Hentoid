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
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 08/26/2016.
 * Presents the list of downloaded works to the user in a classic pager.
 */
public class PagerFragment extends DownloadsFragment implements ContentsWipedListener {
    private static final String TAG = LogHelper.makeLogTag(PagerFragment.class);

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
            LogHelper.d(TAG, "Result is not null.");
            LogHelper.d(TAG, "Are results loaded? " + isLoaded);
            if (result.isEmpty() && !isLoaded) {
                LogHelper.d(TAG, "Result is empty!");
                update();
            }
            checkContent(false);
            mAdapter.setContentsWipedListener(this);
        } else {
            LogHelper.d(TAG, "Result is null.");

            update();
            checkContent(true);
        }

        if (!query.isEmpty()) {
            LogHelper.d(TAG, "Saved Query: " + query);
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
        //clearSelection();
        result = search.getContent();

        if (isLoaded) {
            toggleUI(0);
        }

        if (query.isEmpty()) {
            if (result != null && !result.isEmpty()) {

                List<Content> singleResult = result;
                mAdapter.setContentList(singleResult);
                mListView.setAdapter(mAdapter);

                toggleUI(SHOW_RESULT);
                updatePager();
            } else {
                LogHelper.d(TAG, "Result: Nothing to match.");
                displayNoResults();
            }
        } else {
            LogHelper.d(TAG, "Query: " + query);
            if (result != null && !result.isEmpty()) {
                LogHelper.d(TAG, "Result: Match.");

                List<Content> searchResults = result;
                mAdapter.setContentList(searchResults);
                mListView.setAdapter(mAdapter);

                toggleUI(SHOW_RESULT);
                showToolbar(true, true);
                updatePager();
            } else {
                LogHelper.d(TAG, "Result: Nothing to match.");
                displayNoResults();
            }
        }
    }
}
