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
import android.support.v4.app.Fragment;
import android.view.View;

import com.github.paolorotolo.appintro.AppIntro2;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.fragments.BaseSlide;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 03/20/2016.
 * Welcome (Intro Slide) Activity
 * Presents required permissions, then calls the proper activity to:
 * Set storage directory and library import
 */
public class IntroActivity extends AppIntro2 {
    private static final String TAG = LogHelper.makeLogTag(IntroActivity.class);

    private static final int IMPORT_SLIDE = 4;
    private Fragment doneFragment;

    private void showSkipButton(boolean showButton) {
        this.skipButtonEnabled = showButton;
        setButtonState(skipButton, showButton);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        doneFragment = BaseSlide.newInstance(R.layout.intro_slide_06);
        addSlide(doneFragment);

        setNavBarColor("#2b0202");
        setVibrate(true);
        setVibrateIntensity(30);
        showSkipButton(false);
        pager.setPagingEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (pager.getCurrentItem() >= 1) {
            setTitle(R.string.app_name);
        }
    }

    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        super.onSlideChanged(oldFragment, newFragment);

        if (pager.getCurrentItem() >= 1) {
            setTitle(R.string.app_name);
        }

        // Show the import activity just prior to the last slide
        if (pager.getCurrentItem() == IMPORT_SLIDE) {
            setProgressButtonEnabled(false);
            initImport();
        }
    }

    @Override
    public void onDonePressed(Fragment currentFragment) {
        HentoidApp.setDonePressed(true);
        Helper.commitFirstRun(false);
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

    private void initImport() {
        if (!HentoidApp.hasImportStarted()) {
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {

                public void run() {
                    Intent selectFolder = new Intent(
                            getApplicationContext(), ImportActivity.class);
                    startActivityForResult(selectFolder, ConstsImport.RQST_IMPORT_RESULTS);
                }
            }, 200);
            handler.removeCallbacks(null);
        }
        HentoidApp.setBeginImport(true);
    }

    private void openAppSettings() {
        Intent appSettings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        appSettings.addCategory(Intent.CATEGORY_DEFAULT);
        appSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(appSettings, ConstsImport.RQST_APP_SETTINGS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ConstsImport.RQST_IMPORT_RESULTS) {
            LogHelper.d(TAG, "REQUEST RESULT RECEIVED");
            if (data != null) {
                if (data.getStringExtra(ConstsImport.RESULT_KEY) != null) {
                    String result = data.getStringExtra(ConstsImport.RESULT_KEY);
                    if (resultCode == RESULT_OK) {
                        resultHandler(true, result);
                    }
                    if (resultCode == RESULT_CANCELED) {
                        resultHandler(false, result);
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
        } else if (requestCode == ConstsImport.RQST_APP_SETTINGS) {
            // Back from app settings
            HentoidApp.setBeginImport(false);
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

    private void resultHandler(boolean success, String result) {
        if (success) {
            // If we get RESULT_OK, then:
            LogHelper.d(TAG, "RESULT_OK: ");
            LogHelper.d(TAG, result);

            if (result.equals(ConstsImport.PERMISSION_GRANTED)) {
                LogHelper.d(TAG, "Permission Allowed, resetting.");

                HentoidApp.setBeginImport(false);
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
                        if (!HentoidApp.isDonePressed() && doneFragment != null) {
                            onDonePressed(doneFragment);
                        }
                    }
                }, 10000);
                doneHandler.removeCallbacks(null);
            }
        } else {
            switch (result) {
                case ConstsImport.PERMISSION_DENIED:
                    LogHelper.d(TAG, "Permission Denied by User");

                    pager.setCurrentItem(IMPORT_SLIDE - 3);
                    Snackbar.make(pager, R.string.permission_denied,
                            Snackbar.LENGTH_LONG).show();
                    setProgressButtonEnabled(true);
                    break;
                case ConstsImport.PERMISSION_DENIED_FORCED:
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
                case ConstsImport.EXISTING_LIBRARY_FOUND:
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
            HentoidApp.setBeginImport(false);
        }
    }
}