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
public class TestFragment2 extends BaseFragment {
    private static final String TAG = LogHelper.makeLogTag(TestFragment2.class);

    public static TestFragment2 newInstance() {
        return new TestFragment2();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LogHelper.d(TAG, "onCreate: pager");
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
