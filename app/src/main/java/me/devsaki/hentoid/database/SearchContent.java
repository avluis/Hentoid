package me.devsaki.hentoid.database;

import android.content.Context;
import android.os.AsyncTask;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import timber.log.Timber;

/**
 * Created by avluis on 04/12/2016.
 * Grabs content from db with provided query
 */
public class SearchContent {

    private final HentoidDB db;
    private volatile State mCurrentState;
    private List<Content> contentList = new ArrayList<>();
    private int totalContent;

    private final ContentListener mListener;

    private String mTitleQuery;
    private String mAuthorQuery;
    private int mCurrentPage;
    private int mBooksPerPage;
    private int mOrderStyle;
    private boolean mFilterFavourites;
    private final List<String> mTagFilter = new ArrayList<>();
    private final List<Integer> mSiteFilter = new ArrayList<>();

    public SearchContent(final Context context, final ContentListener listener) {
        db = HentoidDB.getInstance(context);
        mListener = listener;
    }

    public void retrieveResults(String titleQuery, String authorQuery, int currentPage, int booksPerPage, List<String> tags, List<Integer> sites, boolean filterFavourites, int orderStyle) {
        mTitleQuery = titleQuery;
        mAuthorQuery = authorQuery;
        mCurrentPage = currentPage;
        mBooksPerPage = booksPerPage;
        mOrderStyle = orderStyle;
        mFilterFavourites = filterFavourites;
        mTagFilter.clear();
        if (tags != null) mTagFilter.addAll(tags);
        mSiteFilter.clear();
        if (sites != null) mSiteFilter.addAll(sites);

        mCurrentState = State.NON_INIT;
        contentList.clear();
        totalContent = 0;

        retrieveResults();
    }

    private void retrieveResults() {
        Timber.d("Retrieving results.");

        if (mCurrentState == State.READY) {
            mListener.onContentReady(true, contentList, totalContent);
            mListener.onContentFailed(false);
            return;
        } else if (mCurrentState == State.FAILED) {
            mListener.onContentReady(false, contentList, totalContent);
            mListener.onContentFailed(true);
            return;
        }

        mCurrentState = State.INIT;

        new SearchTask(this).execute();
    }

    private synchronized State retrieveContent(String titleQuery, String authorQuery, int currentPage, int booksPerPage, List<String> tagFilter, List<Integer> sites, boolean filterFavourites, int orderStyle) {
        Timber.d("Retrieving content.");
        try {
            if (mCurrentState == State.INIT) {
                mCurrentState = State.DONE;

                contentList = db.selectContentByQuery(titleQuery, authorQuery, currentPage, booksPerPage, tagFilter, sites, filterFavourites, orderStyle);
                // Fetch total query count (since query are paged, query results count is always <= booksPerPage)
                totalContent = db.countContentByQuery(titleQuery, authorQuery, tagFilter, sites, filterFavourites);

                mCurrentState = State.READY;
            }
        } catch (Exception e) {
            Timber.e(e, "Could not load data from db");
        } finally {
            if (mCurrentState != State.READY) {
                // Something bad happened!
                Timber.w("Failed...");
                mCurrentState = State.FAILED;
            }
        }
        return mCurrentState;
    }

    private enum State {
        NON_INIT, INIT, DONE, READY, FAILED
    }


    private static class SearchTask extends AsyncTask<Void, Void, State> {

        private final WeakReference<SearchContent> activityReference;

        // only retain a weak reference to the activity
        SearchTask(SearchContent context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected State doInBackground(Void... params) {
            SearchContent activity = activityReference.get();
            return activity.retrieveContent(activity.mTitleQuery, activity.mAuthorQuery, activity.mCurrentPage, activity.mBooksPerPage, activity.mTagFilter, activity.mSiteFilter, activity.mFilterFavourites, activity.mOrderStyle);
        }

        @Override
        protected void onPostExecute(State current) {
            SearchContent activity = activityReference.get();
            if (activity != null && activity.mListener != null) {
                activity.mListener.onContentReady(current == State.READY, activity.contentList, activity.totalContent);
                activity.mListener.onContentFailed(current == State.FAILED);
            }
        }
    }

    public interface ContentListener {
        void onContentReady(boolean success, List<Content> contentList, int totalContent);

        void onContentFailed(boolean failure);
    }
}
