package me.devsaki.hentoid.activities;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.TextView;

import com.github.paolorotolo.appintro.AppIntro2;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.fragments.BaseSlide;
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

    private void showSkipButton(boolean showButton) {
        this.skipButtonEnabled = showButton;
        setButtonState(skipButton, showButton);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addSlide(BaseSlide.newInstance(R.layout.intro_slide_01));
        if (Helper.isAtLeastAPI(Build.VERSION_CODES.M)) {
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

        if (pager.getCurrentItem() == IMPORT_SLIDE) {
            setProgressButtonEnabled(false);

            TextView defaultTv = (TextView) findViewById(R.id.tv_library_default);
            TextView customTv = (TextView) findViewById(R.id.tv_library_custom);

            defaultTv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    LogHelper.d(TAG, "Default Library Button Clicked.");
                }
            });

            customTv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    LogHelper.d(TAG, "Custom Library Button Clicked.");
                }
            });
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
}
