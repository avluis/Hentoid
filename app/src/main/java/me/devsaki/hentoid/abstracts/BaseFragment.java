package me.devsaki.hentoid.abstracts;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import org.greenrobot.eventbus.EventBus;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.listener.DownloadEvent;

/**
 * Created by avluis on 04/10/2016.
 * Basic Fragment Abstract Class
 * Implementations receive an onBackPressed handled by the hosting activity.
 */
public abstract class BaseFragment extends Fragment {

    private static HentoidDB db;

    private BackInterface backInterface;

    protected static HentoidDB getDB() {
        return db;
    }

    public abstract boolean onBackPressed();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context mContext = HentoidApp.getAppContext();

        db = HentoidDB.getInstance(mContext);

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

        EventBus.getDefault().register(this);
        backInterface.setSelectedFragment(this);
    }

    @Override
    public void onStop() {
        super.onStop();

        EventBus.getDefault().unregister(this);
    }

    public abstract void onDownloadEvent(DownloadEvent event);

    public interface BackInterface {
        void setSelectedFragment(BaseFragment baseFragment);
    }
}