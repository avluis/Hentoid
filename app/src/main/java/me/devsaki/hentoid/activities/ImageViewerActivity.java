package me.devsaki.hentoid.activities;

import static me.devsaki.hentoid.util.PermissionHelper.RQST_STORAGE_PERMISSION;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import me.devsaki.hentoid.activities.bundles.ImageViewerActivityBundle;
import me.devsaki.hentoid.fragments.viewer.ViewerGalleryFragment;
import me.devsaki.hentoid.fragments.viewer.ViewerPagerFragment;
import me.devsaki.hentoid.util.PermissionHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.widget.ViewerKeyListener;


public class ImageViewerActivity extends BaseActivity {

    private static boolean isRunning = false;

    private ViewerKeyListener viewerKeyListener = null;
    private ImageViewerViewModel viewModel = null;


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

        ImageViewerActivityBundle.Parser parser = new ImageViewerActivityBundle.Parser(intent.getExtras());
        long contentId = parser.getContentId();
        if (0 == contentId) throw new IllegalArgumentException("Incorrect ContentId");
        int pageNumber = parser.getPageNumber();


        ViewModelFactory vmFactory = new ViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, vmFactory).get(ImageViewerViewModel.class);

        viewModel.observeDbImages(this);

        if (null == viewModel.getContent().getValue()) { // ViewModel hasn't loaded anything yet (fresh start)
            Bundle searchParams = parser.getSearchParams();
            if (searchParams != null)
                viewModel.loadFromSearchParams(contentId, pageNumber, searchParams);
            else viewModel.loadFromContent(contentId, pageNumber);
        }

        if (!PermissionHelper.requestExternalStorageReadPermission(this, RQST_STORAGE_PERMISSION)) {
            ToastHelper.toast("Storage permission denied - cannot open the viewer");
            return;
        }

        // Allows an full recolor of the status bar with the custom color defined in the activity's theme
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        if (null == savedInstanceState) {
            Fragment fragment;
            if (Preferences.isViewerOpenBookInGalleryMode() || parser.isForceShowGallery())
                fragment = new ViewerGalleryFragment();
            else fragment = new ViewerPagerFragment();

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
        if (viewerKeyListener != null) return viewerKeyListener.onKey(null, keyCode, event);
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

    public void registerKeyListener(ViewerKeyListener listener) {
        takeKeyEvents(true);
        this.viewerKeyListener = listener;
    }

    public void unregisterKeyListener() {
        if (viewerKeyListener != null) viewerKeyListener.clear();
        viewerKeyListener = null;
    }
}
