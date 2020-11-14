package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.appintro.AppIntro2;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.fragments.intro.EndIntroFragment;
import me.devsaki.hentoid.fragments.intro.ImportIntroFragment;
import me.devsaki.hentoid.fragments.intro.PermissionIntroFragment;
import me.devsaki.hentoid.fragments.intro.SourcesIntroFragment;
import me.devsaki.hentoid.fragments.intro.ThemeIntroFragment;
import me.devsaki.hentoid.fragments.intro.WelcomeIntroFragment;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;

/**
 * Created by avluis on 03/20/2016.
 * Maintained by wightwulf1944 on 06/23/2018
 * Welcome (Intro Slide) Activity
 * Presents required permissions, then calls the proper activity to:
 * Set storage directory and library import
 */
public class IntroActivity extends AppIntro2 {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addSlide(new WelcomeIntroFragment());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addSlide(new PermissionIntroFragment());
        }
        addSlide(new ImportIntroFragment());
        addSlide(new ThemeIntroFragment());
        addSlide(new SourcesIntroFragment());
        addSlide(new EndIntroFragment());

        setTitle(R.string.app_name);
        setWizardMode(true); // Replaces skip button with back button
        setSystemBackButtonLocked(true);
        setIndicatorEnabled(true);
        setSwipeLock(true);

        setBackgroundResource(R.drawable.bg_pin_dialog);
    }

    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        super.onSlideChanged(oldFragment, newFragment);
        if (oldFragment instanceof SourcesIntroFragment)
            setSourcePrefs(((SourcesIntroFragment) oldFragment).getSelection());
        boolean isProgressButtonEnabled = !(newFragment instanceof ImportIntroFragment);
        setButtonsEnabled(isProgressButtonEnabled);
    }

    public void onPermissionGranted() {
        goToNextSlide(false);
    }

    public void nextStep() {
        goToNextSlide(false);
        setButtonsEnabled(false);
    }

    public void setThemePrefs(int pref) {
        Preferences.setColorTheme(pref);
        ThemeHelper.applyTheme(this);
        goToNextSlide(false);
    }

    public void setSourcePrefs(List<Site> sources) {
        Preferences.setActiveSites(sources);
    }

    @Override
    public void onDonePressed(Fragment currentFragment) {
        Preferences.setIsFirstRun(false);
        Intent intent = new Intent(this, LibraryActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
}
