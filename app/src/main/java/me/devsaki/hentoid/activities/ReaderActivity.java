package me.devsaki.hentoid.activities;

import static me.devsaki.hentoid.util.file.PermissionHelper.RQST_STORAGE_PERMISSION;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ReaderActivityBundle;
import me.devsaki.hentoid.fragments.reader.ReaderGalleryFragment;
import me.devsaki.hentoid.fragments.reader.ReaderPagerFragment;
import me.devsaki.hentoid.util.file.PermissionHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.viewmodels.ReaderViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.widget.ReaderKeyListener;


public class ReaderActivity extends BaseActivity {

    private static boolean isRunning = false;

    private ReaderKeyListener readerKeyListener = null;
    private ReaderViewModel viewModel = null;


    private static synchronized void setRunning(boolean value) {
        isRunning = value;
    }

    public static synchronized boolean isRunning() {
        return isRunning;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Preferences.isViewerKeepScreenOn())
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent intent = getIntent();
        if (null == intent || null == intent.getExtras())
            throw new IllegalArgumentException("Required init arguments not found");

        ReaderActivityBundle parser = new ReaderActivityBundle(intent.getExtras());
        long contentId = parser.getContentId();
        if (0 == contentId) throw new IllegalArgumentException("Incorrect ContentId");
        int pageNumber = parser.getPageNumber();


        ViewModelFactory vmFactory = new ViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, vmFactory).get(ReaderViewModel.class);

        viewModel.observeDbImages(this);

        if (null == viewModel.getContent().getValue()) { // ViewModel hasn't loaded anything yet (fresh start)
            Bundle searchParams = parser.getSearchParams();
            if (searchParams != null)
                viewModel.loadContentFromSearchParams(contentId, pageNumber, searchParams);
            else viewModel.loadContentFromId(contentId, pageNumber);
        }

        if (!PermissionHelper.requestExternalStorageReadPermission(this, RQST_STORAGE_PERMISSION) &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ToastHelper.toast(R.string.storage_permission_denied);
            return;
        }

        // Allows an full recolor of the status bar with the custom color defined in the activity's theme
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        if (null == savedInstanceState) {
            Fragment fragment;
            if (Preferences.isViewerOpenBookInGalleryMode() || parser.isForceShowGallery())
                fragment = new ReaderGalleryFragment();
            else fragment = new ReaderPagerFragment();

            getSupportFragmentManager()
                    .beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commit();
        }

        if (!Preferences.getRecentVisibility())
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setRunning(true);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (readerKeyListener != null) return readerKeyListener.onKey(null, keyCode, event);
        else return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        if (isFinishing()) { // i.e. the activity is closing for good; not being paused / backgrounded
            if (viewModel != null) viewModel.onActivityLeave();
            Preferences.setViewerDeleteAskMode(Preferences.Constant.VIEWER_DELETE_ASK_AGAIN);
            Preferences.setViewerCurrentPageNum(-1);
            Preferences.setViewerCurrentContent(-1);
            setRunning(false);
        }
        super.onStop();
    }

    public void registerKeyListener(ReaderKeyListener listener) {
        takeKeyEvents(true);
        this.readerKeyListener = listener;
    }

    public void unregisterKeyListener() {
        if (readerKeyListener != null) readerKeyListener.clear();
        readerKeyListener = null;
    }
}
