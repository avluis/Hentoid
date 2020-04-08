package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.security.AccessControlException;

import me.devsaki.hentoid.activities.bundles.ImageViewerActivityBundle;
import me.devsaki.hentoid.fragments.viewer.ImageGalleryFragment;
import me.devsaki.hentoid.fragments.viewer.ImagePagerFragment;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.PermissionUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;


public class ImageViewerActivity extends BaseActivity {

    private View.OnKeyListener keyListener = null;

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

        Bundle searchParams = parser.getSearchParams();
        ImageViewerViewModel viewModel = new ViewModelProvider(this).get(ImageViewerViewModel.class);

        if (searchParams != null) viewModel.loadFromSearchParams(contentId, searchParams);
        else viewModel.loadFromContent(contentId);

        if (!PermissionUtil.requestExternalStorageReadPermission(this, ConstsImport.RQST_STORAGE_PERMISSION)) {
            ToastUtil.toast("Storage permission denied - cannot open the viewer");
            throw new AccessControlException("Storage permission denied - cannot open the viewer");
        }

        // Allows an full recolor of the status bar with the custom color defined in the activity's theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }

        if (null == savedInstanceState) {
            Fragment fragment;
            if (Preferences.isViewerOpenBookInGalleryMode()) fragment = new ImageGalleryFragment();
            else fragment = new ImagePagerFragment();

            getSupportFragmentManager()
                    .beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commit();
        }

        if (!Preferences.getRecentVisibility())
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
    }

    public void registerKeyListener(View.OnKeyListener listener) {
        takeKeyEvents(true);
        this.keyListener = listener;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyListener != null) return keyListener.onKey(null, keyCode, event);
        else return super.onKeyDown(keyCode, event);
    }
}
