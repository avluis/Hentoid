package me.devsaki.hentoid.activities;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;

import java.util.List;

import me.devsaki.hentoid.activities.bundles.ImageViewerActivityBundle;
import me.devsaki.hentoid.fragments.viewer.ImagePagerFragment;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.PermissionUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;


public class ImageViewerActivity extends AppCompatActivity {

    private ImageViewerViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Preferences.isViewerKeepScreenOn())
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            ImageViewerActivityBundle.Parser parser = new ImageViewerActivityBundle.Parser(intent.getExtras());
            List<String> uris = parser.getUrisStr();

            if (null == uris) {
                throw new RuntimeException("Initialization failed");
            }

            viewModel = ViewModelProviders.of(this).get(ImageViewerViewModel.class);
            viewModel.setImages(uris);
            viewModel.setContentId(parser.getContentId());
        }

        PermissionUtil.requestExternalStoragePermission(this, ConstsImport.RQST_STORAGE_PERMISSION);

        // Allows an full recolor of the status bar with the custom color defined in the activity's theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        }

        if (null == savedInstanceState) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(android.R.id.content, new ImagePagerFragment())
                    .commit();
        }
    }

    @Override
    public void onBackPressed() {
        viewModel.saveCurrentPosition();
        super.onBackPressed();
    }

}
