package me.devsaki.hentoid.database;

import android.content.Context;
import android.os.AsyncTask;
import android.support.v4.app.Fragment;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.fragments.DownloadsFragment;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 04/12/2016.
 * Initial Implementation:
 * Grabs content from db with provided query
 * TODO: Implement a callback (and/or convert to a service)
 */
public class SearchContent extends AsyncTask<Void, Void, List<Content>> {

    private static final String TAG = LogHelper.makeLogTag(SearchContent.class);

    private final HentoidDB db;

    private final String mQuery;
    private final int mPage;
    private final int mQty;
    private final boolean mOrder;
    private List<Content> contents;
    private DownloadsFragment fragment;

    public SearchContent(final Context context, String query, int page, int qty, boolean order,
                         DownloadsFragment fragment) {
        Context mContext = context.getApplicationContext();
        mQuery = query;
        mPage = page;
        mQty = qty;
        mOrder = order;
        this.fragment = fragment;

        db = new HentoidDB(mContext);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected List<Content> doInBackground(Void... params) {
        if (this.isCancelled()) {
            return null;
        }
        // TODO: Implement null result
        while (contents == null) {
            try {
                contents = db.selectContentByQuery(mQuery, mPage, mQty, mOrder);
            } catch (Exception e) {
                LogHelper.e(TAG, "Could not load data from db: ", e);
            }
        }
        return contents;
    }

    @Override
    protected void onPostExecute(List<Content> contents) {
        super.onPostExecute(contents);
        fragment.setListContent(contents);
    }
}