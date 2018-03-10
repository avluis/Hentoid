package me.devsaki.hentoid.fragments;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import java.util.List;

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
public class EndlessFragment extends DownloadsFragment implements EndlessScrollListener {

    @Override
    protected void queryPrefs() {
        super.queryPrefs();

        booksPerPage = Preferences.Default.PREF_QUANTITY_PER_PAGE_DEFAULT;
    }

    @Override
    protected void checkResults() {
        mAdapter.setEndlessScrollListener(this);

        if (0 == mAdapter.getItemCount())
        {
            if (!isLoaded) update();
            else checkContent(true);
        } else {
            checkContent(false);
            mAdapter.setContentsWipedListener(this);
        }

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

        mAdapter.add(results);

         toggleUI(SHOW_RESULT);
         updatePager(); // NB : In EndlessFragment, a "page" is a group of loaded books. Last page is reached when scrolling reaches the very end of the book list
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
