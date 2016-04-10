package me.devsaki.hentoid.abstracts;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.database.HentoidDB;

/**
 * Created by avluis on 04/10/2016.
 */
public abstract class BaseFragment extends Fragment {

    private static HentoidDB db;

    protected static HentoidDB getDB() {
        return db;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context mContext = HentoidApplication.getAppContext();

        db = new HentoidDB(mContext);
    }
}