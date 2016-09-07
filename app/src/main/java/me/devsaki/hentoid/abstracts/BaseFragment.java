package me.devsaki.hentoid.abstracts;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

import org.greenrobot.eventbus.EventBus;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.events.DownloadEvent;

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
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        backInterface.addBackInterface(this);
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    // Implementations must annotate method with:
    // @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public abstract void onDownloadEvent(DownloadEvent event);

    public interface BackInterface {
        void addBackInterface(BaseFragment fragment);
    }
}
