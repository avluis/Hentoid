package me.devsaki.hentoid.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.view.View;

import com.github.paolorotolo.appintro.AppIntro2;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.fragments.BaseSlide;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 03/20/2016.
 * Welcome (Intro Slide) Activity
 * Presents required permissions, then calls the proper activity to:
 * Set storage directory and library import
 */
public class IntroSlideActivity extends AppIntro2 {
    private static final String TAG = LogHelper.makeLogTag(IntroSlideActivity.class);

    private static final int REQUEST_RESULTS = 1;
    private static final int REQUEST_APP_SETTINGS = 2;
    private static final int IMPORT_SLIDE = 4;
    private boolean donePressed;

    @Override
    public void init(@Nullable Bundle savedInstanceState) {
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_01));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addSlide(BaseSlide.newInstance(R.layout.intro_slide_02));
            // Ask Storage permission in the second slide,
            // but only for Android M+ users.
            askForPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
        } else {
            // In order to keep the number of slides the same,
            // we show this info slide for non-M users.
            addSlide(BaseSlide.newInstance(R.layout.intro_slide_02_alt));
        }
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_03));
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_04));
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_05));
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_06));

        setNavBarColor("#2b0202");
        setVibrate(true);
        setVibrateIntensity(30);
    }

    @Override
    public void onNextPressed() {
        // Do something when users tap on Next button.
    }

    @Override
    public void onDonePressed() {
        donePressed = true;
        AndroidHelper.commitFirstRun(true);
        AndroidHelper.launchMainActivity(this);
        finish();
    }

    @Override
    public void onSlideChanged() {
        // Show the import activity just prior to the last slide
        if (pager.getCurrentItem() == IMPORT_SLIDE) {
            setProgressButtonEnabled(false);
            initImport();
        }
    }

    private void initImport() {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {

            public void run() {
                Intent selectFolder = new Intent(
                        getApplicationContext(), ImportActivity.class);
                startActivityForResult(selectFolder, 1);
            }
        }, 200);
    }

    private void openAppSettings() {
        Intent appSettings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        appSettings.addCategory(Intent.CATEGORY_DEFAULT);
        appSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(appSettings, REQUEST_APP_SETTINGS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_RESULTS) {
            String resultKey = ImportActivity.getResultKey();
            setProgressButtonEnabled(true);
            if (data != null) {
                if (data.getStringExtra(resultKey) != null) {
                    String result = data.getStringExtra(resultKey);
                    if (resultCode == Activity.RESULT_OK) {
                        // If we get RESULT_OK, then:
                        LogHelper.d(TAG, "RESULT_OK: ");
                        LogHelper.d(TAG, result);

                        // If result passes validation, then we move to next slide
                        pager.setCurrentItem(IMPORT_SLIDE + 1);
                        // Disallow swiping back
                        setSwipeLock(true);

                        // Auto push to DownloadActivity after 10 seconds
                        Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {

                            public void run() {
                                if (!donePressed) {
                                    onDonePressed();
                                }
                            }
                        }, 10000);
                    }
                    if (resultCode == Activity.RESULT_CANCELED) {
                        switch (result) {
                            case "PERMISSION_DENIED":
                                LogHelper.d(TAG, "Permission Denied by User");

                                pager.setCurrentItem(IMPORT_SLIDE - 3);
                                AndroidHelper.singleSnack(pager,
                                        getString(R.string.permission_denied),
                                        Snackbar.LENGTH_LONG);
                                break;
                            case "PERMISSION_DENIED_FORCED":
                                LogHelper.d(TAG, "Permission Denied (Forced) by User/Policy");

                                setProgressButtonEnabled(false);
                                setSwipeLock(true);
                                pager.setCurrentItem(IMPORT_SLIDE - 3);

                                Snackbar.make(pager, getString(R.string.permission_denied_forced),
                                        Snackbar.LENGTH_INDEFINITE)
                                        .setAction(getString(R.string.open_app_settings),
                                                new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(View v) {
                                                        openAppSettings();
                                                    }
                                                })
                                        .show();
                                break;
                            case "EXISTING_LIBRARY_FOUND":
                                LogHelper.d(TAG, "Existing Library Found");

                                pager.setCurrentItem(IMPORT_SLIDE - 2);
                                AndroidHelper.singleSnack(pager,
                                        getString(R.string.existing_library_found),
                                        Snackbar.LENGTH_LONG);
                                break;
                            default:
                                LogHelper.d(TAG, "RESULT_CANCELED");

                                pager.setCurrentItem(IMPORT_SLIDE - 2);
                                break;
                        }
                    }
                } else {
                    LogHelper.d(TAG, "Error: Data not received! Bad resultKey.");
                    // TODO: Log to Analytics
                    // Try again!
                    initImport();
                }
            } else {
                LogHelper.i(TAG, "Data is null!");
                // TODO: Log to Analytics
                // Try again!
                initImport();
            }
        } else if (requestCode == REQUEST_APP_SETTINGS) {
            // Back from app settings
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {

                public void run() {
                    setProgressButtonEnabled(true);
                    pager.setCurrentItem(IMPORT_SLIDE - 2);
                }
            }, 100);
        } else {
            LogHelper.i(TAG, "Unknown result code!");
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}