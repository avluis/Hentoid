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
    private volatile State mCurrentState = State.NON_INIT;
    private List<Content> contentList = new ArrayList<>();

    protected ContentListener mListener;

    protected String mQuery;
    protected int mCurrentPage;
    protected int mBooksPerPage;
    protected int mOrderStyle;
    protected List<String> mTagFilter = new ArrayList<>();
    protected List<Integer> mSiteFilter = new ArrayList<>();

    public SearchContent(final Context context, final ContentListener listener) {
        db = HentoidDB.getInstance(context);
        mListener = listener;
    }

    public void retrieveResults(String query, int currentPage, int booksPerPage, List<String> tags, List<Integer> sites, int orderStyle) {
        mQuery = query;
        mCurrentPage = currentPage;
        mBooksPerPage = booksPerPage;
        mOrderStyle = orderStyle;
        mTagFilter.clear();
        if (tags != null) mTagFilter.addAll(tags);
        mSiteFilter.clear();
        if (sites != null) mSiteFilter.addAll(sites);

        retrieveResults();
    }

    private void retrieveResults() {
        Timber.d("Retrieving results.");

        if (mCurrentState == State.READY) {
            mListener.onContentReady(true, contentList);
            mListener.onContentFailed(false);
            return;
        } else if (mCurrentState == State.FAILED) {
            mListener.onContentReady(false, contentList);
            mListener.onContentFailed(true);
            return;
        }

        mCurrentState = State.INIT;

        new SearchTask(this).execute();
    }

    private synchronized State retrieveContent(String query, int currentPage, int booksPerPage, List<String> tagFilter, List<Integer> sites, int orderStyle) {
        Timber.d("Retrieving content.");
        try {
            if (mCurrentState == State.INIT) {
                mCurrentState = State.DONE;

                contentList = db.selectContentByQuery(query, query, currentPage, booksPerPage, tagFilter, sites, orderStyle);

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

        private WeakReference<SearchContent> activityReference;

        // only retain a weak reference to the activity
        SearchTask(SearchContent context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected State doInBackground(Void... params) {
            SearchContent activity = activityReference.get();
            return activity.retrieveContent(activity.mQuery, activity.mCurrentPage, activity.mBooksPerPage, activity.mTagFilter, activity.mSiteFilter, activity.mOrderStyle);
        }

        @Override
        protected void onPostExecute(State current) {
            SearchContent activity = activityReference.get();
            if (activity.mListener != null) {
                activity.mListener.onContentReady(current == State.READY, activity.contentList);
                activity.mListener.onContentFailed(current == State.FAILED);
            }
        }
    }

    public interface ContentListener {
        void onContentReady(boolean success, List<Content> contentList);

        void onContentFailed(boolean failure);
    }
}
