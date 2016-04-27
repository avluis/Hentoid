package me.devsaki.hentoid.database;

import android.content.Context;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 04/12/2016.
 * <p/>
 * Grabs content from db with provided query
 */
public class SearchContent {
    private static final String TAG = LogHelper.makeLogTag(SearchContent.class);

    private static int counter = 0;
    private final HentoidDB db;
    private final String mQuery;
    private final int mPage;
    private final int mQty;
    private final boolean mOrder;
    private volatile State mCurrentState = State.NON_INITIALIZED;
    private Context mContext;
    private List<Content> contentList = new ArrayList<>();

    public SearchContent(final Context context, String query, int page, int qty, boolean order) {
        // Rotating the screen should not cause an additional call
        if (mContext == null) {
            counter++;
            LogHelper.d(TAG, "I've been called: " + counter + ((counter > 1) ? " times." : " time."));
        }

        mContext = context.getApplicationContext();
        mQuery = query;
        mPage = page;
        mQty = qty;
        mOrder = order;

        db = new HentoidDB(mContext);
    }

    public List<Content> getContent() {
        return contentList;
    }

    public void retrieveResults(final Callback callback) {

        LogHelper.d(TAG, "retrieveResults called");

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