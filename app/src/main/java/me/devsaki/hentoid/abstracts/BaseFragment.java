package me.devsaki.hentoid.abstracts;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.greenrobot.eventbus.EventBus;

import me.devsaki.hentoid.events.DownloadEvent;

/**
 * Created by avluis on 04/10/2016.
 * Basic Fragment Abstract Class
 * Implementations receive an onBackPressed handled by the hosting activity.
 */
public abstract class BaseFragment extends Fragment {

    private BackInterface backInterface;

    public abstract boolean onBackPressed();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

//        LeakCanary.installedRefWatcher().watch(this);
    }

    // Implementations must annotate method with:
    // @Subscribe(threadMode = ThreadMode.MAIN)
    public abstract void onDownloadEvent(DownloadEvent event);

    public interface BackInterface {
        void addBackInterface(BaseFragment fragment);
    }
}
