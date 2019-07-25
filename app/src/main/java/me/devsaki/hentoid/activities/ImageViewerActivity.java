package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;

import java.security.AccessControlException;

import me.devsaki.hentoid.activities.bundles.ImageViewerActivityBundle;
import me.devsaki.hentoid.fragments.viewer.ImagePagerFragment;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.PermissionUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;


public class ImageViewerActivity extends AppCompatActivity {

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

        ImageViewerViewModel viewModel = ViewModelProviders.of(this).get(ImageViewerViewModel.class);
        Bundle searchParams = parser.getSearchParams();
        if (searchParams != null) viewModel.loadFromSearchParams(contentId, searchParams);
        else viewModel.loadFromContent(contentId);

        if (!PermissionUtil.requestExternalStoragePermission(this, ConstsImport.RQST_STORAGE_PERMISSION)) {
            ToastUtil.toast("Storage permission denied - cannot open the viewer");
            throw new AccessControlException("Storage permission denied - cannot open the viewer");
        }

        // Allows an full recolor of the status bar with the custom color defined in the activity's theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }

        if (null == savedInstanceState) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(android.R.id.content, new ImagePagerFragment())
                    .commit();
        }
    }
}
