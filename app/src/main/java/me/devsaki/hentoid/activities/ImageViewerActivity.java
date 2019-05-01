package me.devsaki.hentoid.activities;

import android.Manifest;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;

import java.util.List;

import me.devsaki.hentoid.fragments.ImagePagerFragment;
import me.devsaki.hentoid.util.BundleManager;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;


public class ImageViewerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Preferences.isViewerKeepScreenOn())
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent intent = getIntent();
        if (intent != null) {
            BundleManager manager = new BundleManager(intent.getExtras());
            List<String> uris = manager.getUrisStr();

            if (null == uris) {
                throw new RuntimeException("Initialization failed");
            }

            ImageViewerViewModel viewModel = ViewModelProviders.of(this)
                    .get(ImageViewerViewModel.class);

            viewModel.setImages(uris);

            if (Preferences.isViewerResumeLastLeft()) {
                viewModel.setInitialPosition(manager.getOpenPageIndex());
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE};
            this.requestPermissions(permissions, 165498);
        }

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
}
