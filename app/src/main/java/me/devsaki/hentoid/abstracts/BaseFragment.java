package me.devsaki.hentoid.abstracts;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.database.HentoidDB;

/**
 * Created by avluis on 04/10/2016.
 * Basic Fragment Abstract Class
 * <p/>
 * Implementations receive an onBackPressed
 * handled by the hosting activity.
 */
public abstract class BaseFragment extends Fragment {

    private static HentoidDB db;

    protected BackInterface backInterface;

    protected static HentoidDB getDB() {
        return db;
    }

    public abstract boolean onBackPressed();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context mContext = HentoidApplication.getAppContext();

        db = new HentoidDB(mContext);

        if (!(getActivity() instanceof BackInterface)) {
            throw new ClassCastException(
                    "Hosting activity must implement BackInterface.");
        } else {
            backInterface = (BackInterface) getActivity();
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        backInterface.setSelectedFragment(this);
    }

    public interface BackInterface {
        void setSelectedFragment(BaseFragment baseFragment);
    }
}