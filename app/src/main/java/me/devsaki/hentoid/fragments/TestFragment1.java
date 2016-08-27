package me.devsaki.hentoid.fragments;

import android.os.Bundle;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import me.devsaki.hentoid.abstracts.BaseFragment;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 08/26/2016.
 */
public class TestFragment1 extends BaseFragment {
    private static final String TAG = LogHelper.makeLogTag(TestFragment1.class);

    public static TestFragment1 newInstance() {
        return new TestFragment1();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LogHelper.d(TAG, "onCreate: endless");
    }

    @Override
    public boolean onBackPressed() {
        // Let the activity handle it.
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        // Ignore
    }
}
