package me.devsaki.hentoid.fragments;

import android.view.View;

import java.util.List;

import me.devsaki.hentoid.abstracts.DownloadsFragment;
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
    protected boolean queryPrefs() {
        boolean result = super.queryPrefs();

        booksPerPage = Preferences.Default.PREF_QUANTITY_PER_PAGE_DEFAULT;
        return result;
    }

    @Override
    protected void attachScrollListener() {
        super.attachScrollListener();
        mAdapter.setEndlessScrollListener(this);
    }

    @Override
    protected void showToolbar(boolean show) {
        pagerToolbar.setVisibility(View.GONE);
    }

    @Override
    protected void displayResults(List<Content> results, int totalSelectedContent) {
        if (isSearchReplaceResults) {
            mAdapter.replaceAll(results);
        } else {
            mAdapter.add(results);
        }
        toggleUI(SHOW_RESULT);
    }

    @Override
    public void onLoadMore() {
        if (query.isEmpty()) {
            if (!isLastPage()) { // NB : In EndlessFragment, a "page" is a group of loaded books. Last page is reached when scrolling reaches the very end of the book list
                currentPage++;
                searchContent(false);
                Timber.d("Load more data now~");
            }
        } else {
            Timber.d("Endless Scrolling disabled.");
        }
    }
}
