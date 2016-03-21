package me.devsaki.hentoid.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.github.paolorotolo.appintro.AppIntro2;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.fragments.BaseSlide;
import me.devsaki.hentoid.util.ConstantsPreferences;

/**
 * Created by avluis on 03/20/2016.
 * Introduction activity
 */
public class IntroSlideActivity extends AppIntro2 {

    @Override
    public void init(@Nullable Bundle savedInstanceState) {
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_01));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addSlide(BaseSlide.newInstance(R.layout.intro_slide_02));
            // Ask Storage permission in the second slide
            askForPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
        } else {
            addSlide(BaseSlide.newInstance(R.layout.intro_slide_02_alt));
        }
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_03));
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_04));
        addSlide(BaseSlide.newInstance(R.layout.intro_slide_05));
    }

    @Override
    public void onNextPressed() {
        // Do something when users tap on Next button.
    }

    @Override
    public void onDonePressed() {

        String firstRun = "done";

        SharedPreferences.Editor editor = HentoidApplication
                .getAppPreferences().edit();
        editor.putString(ConstantsPreferences.PREF_FIRST_RUN, firstRun);
        editor.apply();

        final String appLock = HentoidApplication.getAppPreferences()
                .getString(ConstantsPreferences.PREF_APP_LOCK, "");

        if (appLock.isEmpty()) {
            Intent intent = new Intent(this, DownloadsActivity.class);
            startActivity(intent);
            finish();
        } else {
            Intent intent = new Intent(this, AppLockActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public void onSlideChanged() {
        // Do something when the slide changes.
    }
}
