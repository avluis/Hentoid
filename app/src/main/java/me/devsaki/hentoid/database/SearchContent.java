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

    private final HentoidDB db;
    private Context mContext;
    private static int counter = 0;

    private final String mQuery;
    private final int mPage;
    private final int mQty;
    private final boolean mOrder;
    private List<Content> contentList = new ArrayList<>();

    public SearchContent(final Context context, final ContentInterface contentInterface,
                         String query, int page, int qty, boolean order) {
        // Rotating the screen should not cause an additional call
        if (mContext == null) {
            counter++;
            LogHelper.d(TAG, "I've been called: " + counter + ((counter > 1) ? " times." : " time."));
        }

        mContext = context.getApplicationContext();
        //contentInterface.onContentReady(false);

        mQuery = query;
        mPage = page;
        mQty = qty;
        mOrder = order;

        db = new HentoidDB(mContext);

        retrieveResults(contentInterface);
    }

    public List<Content> getContent() {
        return contentList;
    }

    private void retrieveResults(final ContentInterface contentInterface) {
        new AsyncTask<Void, Void, List<Content>>() {

            @Override
            protected List<Content> doInBackground(Void... params) {
                pullContent();
                return contentList;
            }

            @Override
            protected void onPostExecute(List<Content> contents) {
                contentList = contents;
                contentInterface.onContentReady(true);
            }
        }.execute();
    }

    private synchronized void pullContent() {
        try {
            contentList = db.selectContentByQuery(mQuery, mPage, mQty, mOrder);
        } catch (Exception e) {
            LogHelper.e(TAG, "Could not load data from db: ", e);
        }
    }

    public interface ContentInterface {
        void onContentReady(boolean ready);
    }
}