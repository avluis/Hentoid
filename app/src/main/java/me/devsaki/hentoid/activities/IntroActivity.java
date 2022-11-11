package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.appintro.AppIntro2;

import org.jetbrains.annotations.NotNull;

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
 * Welcome (Intro Slide) Activity
 * Presents required permissions, then calls the proper activity to:
 * Set storage directory and library import
 */
public class IntroActivity extends AppIntro2 {

    private Handler autoEndHandler = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addSlide(new WelcomeIntroFragment());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
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

        // Set default color theme, in case user skips the slide
        Preferences.setColorTheme(Preferences.Default.COLOR_THEME);

        setBackgroundResource(R.drawable.bg_pin_dialog);
    }

    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        super.onSlideChanged(oldFragment, newFragment);
        if (oldFragment instanceof SourcesIntroFragment)
            setSourcePrefs(((SourcesIntroFragment) oldFragment).getSelection());

        boolean canProgress = !(newFragment instanceof ImportIntroFragment);
        setSwipeLock(!canProgress);
        if (!canProgress) setButtonsEnabled(false);

        // Reset folder selection when coming back to that screen
        if (newFragment instanceof ImportIntroFragment) {
            ((ImportIntroFragment) newFragment).reset();
        }

        // Auto-validate the last screen after 2 seconds of inactivity
        if (newFragment instanceof EndIntroFragment) {
            autoEndHandler = new Handler(Looper.getMainLooper());
            autoEndHandler.postDelayed(() -> onDonePressed(newFragment), 2000);
        } else { // Stop auto-validate if user goes back
            if (autoEndHandler != null) autoEndHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull @NotNull String[] permissions, @NonNull @NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Next slide is the import slide
        setSwipeLock(true);
        setButtonsEnabled(false);
    }

    public void onPermissionGranted() {
        goToNextSlide(false);
    }

    public void nextStep() {
        goToNextSlide(false);
    }

    public void setThemePrefs(int pref) {
        Preferences.setColorTheme(pref);
        ThemeHelper.applyTheme(this);
        goToNextSlide(false);
    }

    public void setSourcePrefs(List<Site> sources) {
        Preferences.setActiveSites(sources);
    }

    // Validation of the final step of the wizard
    @Override
    public void onDonePressed(Fragment currentFragment) {
        autoEndHandler.removeCallbacksAndMessages(null);

        Preferences.setIsFirstRun(false);
        // Need to do that to avoid a useless reloading of the library screen upon loading prefs for the first time
        Preferences.setLibraryDisplay(Preferences.Default.LIBRARY_DISPLAY);

        // Load library screen
        Intent intent = new Intent(this, LibraryActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
}
