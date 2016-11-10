package me.devsaki.hentoid.database;

import android.content.Context;
import android.os.AsyncTask;

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
    private final String mQuery;
    private final int mPage;
    private final int mQty;
    private final boolean mOrder;
    private volatile State mCurrentState = State.NON_INIT;
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

    public void retrieveResults(final ContentListener listener) {
        Timber.d("Retrieving results.");

        if (mCurrentState == State.READY) {
            listener.onContentReady(true);
            listener.onContentFailed(false);
            return;
        } else if (mCurrentState == State.FAILED) {
            listener.onContentReady(false);
            listener.onContentFailed(true);
            return;
        }

        mCurrentState = State.INIT;

        new AsyncTask<Void, Void, State>() {

            @Override
            protected State doInBackground(Void... params) {
                retrieveContent();
                return mCurrentState;
            }

            @Override
            protected void onPostExecute(State current) {
                if (listener != null) {
                    listener.onContentReady(current == State.READY);
                    listener.onContentFailed(current == State.FAILED);
                }
            }
        }.execute();
    }

    private synchronized void retrieveContent() {
        Timber.d("Retrieving content.");
        try {
            if (mCurrentState == State.INIT) {
                mCurrentState = State.DONE;

                contentList = db.selectContentByQuery(mQuery, mPage, mQty, mOrder);
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
    }

    private enum State {
        NON_INIT, INIT, DONE, READY, FAILED
    }

    public interface ContentListener {
        void onContentReady(boolean success);

        void onContentFailed(boolean failure);
    }
}
