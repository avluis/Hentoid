package me.devsaki.hentoid.activities;

import android.Manifest;
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

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.fragments.BaseSlide;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.ConstantsImport;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 03/20/2016.
 * Welcome (Intro Slide) Activity
 * Presents required permissions, then calls the proper activity to:
 * Set storage directory and library import
 */
public class IntroSlideActivity extends AppIntro2 {
    private static final String TAG = LogHelper.makeLogTag(IntroSlideActivity.class);

    private static final int REQUEST_IMPORT_RESULTS = ConstantsImport.REQUEST_IMPORT_RESULTS;
    private static final int REQUEST_APP_SETTINGS = ConstantsImport.REQUEST_APP_SETTINGS;
    private static final int IMPORT_SLIDE = 4;
    private static final String resultKey = ConstantsImport.RESULT_KEY;

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
        pager.setPagingEnabled(false);
    }

    @Override
    public void onNextPressed() {
        if (pager.getCurrentItem() >= 1) {
            setTitle(R.string.app_name);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (pager.getCurrentItem() >= 1) {
            setTitle(R.string.app_name);
        }
    }

    @Override
    public void onDonePressed() {
        HentoidApplication.setDonePressed(true);
        AndroidHelper.commitFirstRun(false);
        Intent intent = new Intent(this, DownloadsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (pager.getCurrentItem() == IMPORT_SLIDE + 1) {
            // DO NOT ALLOW
            LogHelper.d(TAG, "You can't leave just yet!");
        } else {
            super.onBackPressed();
        }
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
        if (!HentoidApplication.hasImportStarted()) {
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {

                public void run() {
                    Intent selectFolder = new Intent(
                            getApplicationContext(), ImportActivity.class);
                    startActivityForResult(selectFolder, REQUEST_IMPORT_RESULTS);
                }
            }, 200);
            handler.removeCallbacks(null);
        }
        HentoidApplication.setBeginImport(true);
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
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_RESULTS) {
            LogHelper.d(TAG, "REQUEST RESULT RECEIVED");
            if (data != null) {
                if (data.getStringExtra(resultKey) != null) {
                    String result = data.getStringExtra(resultKey);
                    if (resultCode == RESULT_OK) {

                        // If we get RESULT_OK, then:
                        LogHelper.d(TAG, "RESULT_OK: ");
                        LogHelper.d(TAG, result);

                        if (result.equals(ConstantsImport.PERMISSION_GRANTED)) {
                            LogHelper.d(TAG, "Permission Allowed, resetting.");

                            HentoidApplication.setBeginImport(false);
                            setProgressButtonEnabled(false);
                            pager.setCurrentItem(IMPORT_SLIDE - 1);

                            Snackbar.make(pager, R.string.permission_granted,
                                    Snackbar.LENGTH_SHORT).show();

                            Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {

                                public void run() {
                                    pager.setCurrentItem(IMPORT_SLIDE);
                                }
                            }, 2000);
                        } else {
                            // Disallow swiping back
                            setSwipeLock(true);
                            // If result passes validation, then we move to next slide
                            pager.setCurrentItem(IMPORT_SLIDE + 1);
                            setProgressButtonEnabled(true);

                            // Auto push to DownloadActivity after 10 seconds
                            Handler doneHandler = new Handler();
                            doneHandler.postDelayed(new Runnable() {

                                public void run() {
                                    if (!HentoidApplication.isDonePressed()) {
                                        onDonePressed();
                                    }
                                }
                            }, 10000);
                            doneHandler.removeCallbacks(null);
                        }
                    }
                    if (resultCode == RESULT_CANCELED) {
                        switch (result) {
                            case ConstantsImport.PERMISSION_DENIED:
                                LogHelper.d(TAG, "Permission Denied by User");

                                pager.setCurrentItem(IMPORT_SLIDE - 3);
                                Snackbar.make(pager, R.string.permission_denied,
                                        Snackbar.LENGTH_LONG).show();
                                setProgressButtonEnabled(true);
                                break;
                            case ConstantsImport.PERMISSION_DENIED_FORCED:
                                LogHelper.d(TAG, "Permission Denied (Forced) by User/Policy");

                                setProgressButtonEnabled(false);
                                setSwipeLock(true);
                                pager.setCurrentItem(IMPORT_SLIDE - 3);

                                Snackbar.make(pager, R.string.permission_denied_forced,
                                        Snackbar.LENGTH_INDEFINITE)
                                        .setAction(R.string.open_app_settings,
                                                new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(View v) {
                                                        openAppSettings();
                                                    }
                                                })
                                        .show();
                                break;
                            case ConstantsImport.EXISTING_LIBRARY_FOUND:
                                LogHelper.d(TAG, "Existing Library Found");

                                pager.setCurrentItem(IMPORT_SLIDE - 2);
                                Snackbar.make(pager, R.string.existing_library_error,
                                        Snackbar.LENGTH_LONG).show();
                                setProgressButtonEnabled(true);
                                break;
                            default:
                                LogHelper.d(TAG, "RESULT_CANCELED");

                                pager.setCurrentItem(IMPORT_SLIDE - 2);
                                setProgressButtonEnabled(true);
                                break;
                        }
                        HentoidApplication.setBeginImport(false);
                    }
                } else {
                    LogHelper.d(TAG, "Error: Data not received! Bad resultKey.");
                    // TODO: Log to Analytics
                    // Try again!
                    initImport();
                }
            } else {
                LogHelper.d(TAG, "Data is null!");
                // TODO: Log to Analytics
                // Try again!
                initImport();
            }
        } else if (requestCode == REQUEST_APP_SETTINGS) {
            // Back from app settings
            HentoidApplication.setBeginImport(false);
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {

                public void run() {
                    setProgressButtonEnabled(true);
                    pager.setCurrentItem(IMPORT_SLIDE - 2);
                }
            }, 100);
            handler.removeCallbacks(null);
        } else {
            LogHelper.d(TAG, "Unknown result code!");
        }
    }
}