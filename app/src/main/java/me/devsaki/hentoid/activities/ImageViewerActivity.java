package me.devsaki.hentoid.activities;

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
import me.devsaki.hentoid.widget.VolumeKeyListener;

import static me.devsaki.hentoid.util.PermissionHelper.RQST_STORAGE_PERMISSION;


public class ImageViewerActivity extends BaseActivity {

    public static boolean isRunning = false;

    private VolumeKeyListener volumeKeyListener = null;
    private ImageViewerViewModel viewModel = null;

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
            if (searchParams != null) viewModel.loadFromSearchParams(contentId, pageNumber, searchParams);
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
            if (Preferences.isViewerOpenBookInGalleryMode()) fragment = new ViewerGalleryFragment();
            else fragment = new ViewerPagerFragment();

            getSupportFragmentManager()
                    .beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commit();
        }

        if (!Preferences.getRecentVisibility())
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        isRunning = true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (volumeKeyListener != null) return volumeKeyListener.onKey(null, keyCode, event);
        else return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        if (isFinishing()) { // i.e. the activity is closing for good; not being paused / backgrounded
            if (viewModel != null) viewModel.emptyCacheFolder();
            Preferences.setViewerDeleteAskMode(Preferences.Constant.VIEWER_DELETE_ASK_AGAIN);
            Preferences.setViewerCurrentPageNum(-1);
            Preferences.setViewerCurrentContent(-1);
            isRunning = false;
        }
        super.onStop();
    }

    public void registerKeyListener(VolumeKeyListener listener) {
        takeKeyEvents(true);
        this.volumeKeyListener = listener;
    }

    public void unregisterKeyListener() {
        if (volumeKeyListener != null) volumeKeyListener.clear();
        volumeKeyListener = null;
    }
}
