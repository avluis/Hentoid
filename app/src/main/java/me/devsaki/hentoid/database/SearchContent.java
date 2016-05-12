package me.devsaki.hentoid.database;

import android.content.Context;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 04/12/2016.
 * Grabs content from db with provided query
 */
public class SearchContent {
    private static final String TAG = LogHelper.makeLogTag(SearchContent.class);

    private final HentoidDB db;
    private final String mQuery;
    private final int mPage;
    private final int mQty;
    private final boolean mOrder;
    private volatile State mCurrentState = State.NON_INITIALIZED;
    private List<Content> contentList = new ArrayList<>();

    public SearchContent(final Context context, String query, int page, int qty, boolean order) {
        db = HentoidDB.getInstance(context);
        mQuery = query;
        mPage = page;
        mQty = qty;
        mOrder = order;
    }

    public List<Content> getContent() {
        return contentList;
    }

    public void retrieveResults(final Callback callback) {
        LogHelper.d(TAG, "Retrieving results.");

        if (mCurrentState == State.INITIALIZED) {
            callback.onContentReady(true);
            callback.onContentFailed(false);
            return;
        } else if (mCurrentState == State.FAILED) {
            callback.onContentReady(false);
            callback.onContentFailed(true);
            return;
        }

        new AsyncTask<Void, Void, State>() {

            @Override
            protected State doInBackground(Void... params) {
                retrieveContent();
                return mCurrentState;
            }

            @Override
            protected void onPostExecute(State current) {
                if (callback != null) {
                    callback.onContentReady(current == State.INITIALIZED);
                    callback.onContentFailed(current == State.FAILED);
                }
            }
        }.execute();
    }

    private synchronized void retrieveContent() {
        try {
            if (mCurrentState == State.NON_INITIALIZED) {
                mCurrentState = State.INITIALIZING;

                contentList = db.selectContentByQuery(mQuery, mPage, mQty, mOrder);
            }

            mCurrentState = State.INITIALIZED;
        } catch (Exception e) {
            LogHelper.e(TAG, "Could not load data from db: ", e);
        } finally {
            if (mCurrentState != State.INITIALIZED) {
                // Something bad happened!
                LogHelper.w(TAG, "Failed...");
                mCurrentState = State.FAILED;
            }
        }
    }

    enum State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED, FAILED
    }

    public interface Callback {
        void onContentReady(boolean success);

        void onContentFailed(boolean failure);
    }
}