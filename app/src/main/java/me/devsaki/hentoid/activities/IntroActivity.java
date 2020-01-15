package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.paolorotolo.appintro.AppIntro2;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.fragments.intro.EndIntroFragment;
import me.devsaki.hentoid.fragments.intro.ImportIntroFragment;
import me.devsaki.hentoid.fragments.intro.PermissionIntroFragment;
import me.devsaki.hentoid.fragments.intro.SourcesIntroFragment;
import me.devsaki.hentoid.fragments.intro.ThemeIntroFragment;
import me.devsaki.hentoid.fragments.intro.WelcomeIntroFragment;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import timber.log.Timber;

import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_INDEFINITE;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_SHORT;
import static me.devsaki.hentoid.util.ConstsImport.RESULT_KEY;

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
        showSkipButton(false);
        setGoBackLock(true);
        showPagerIndicator(false);
        setSwipeLock(true);

        backgroundFrame.setBackgroundColor(ThemeHelper.getColor(this, R.color.window_background_light));
    }

    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        super.onSlideChanged(oldFragment, newFragment);
        if (oldFragment instanceof SourcesIntroFragment)
            setSourcePrefs(((SourcesIntroFragment) oldFragment).getSelection());
        boolean isProgressButtonEnabled = !(newFragment instanceof ImportIntroFragment);
        setProgressButtonEnabled(isProgressButtonEnabled);
    }

    public void onPermissionGranted() {
        getPager().goToNextSlide();
    }

    public void onDefaultStorageSelected() {
        Intent defaultDir = new Intent(this, ImportActivity.class);
        defaultDir.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(defaultDir, ConstsImport.RQST_IMPORT_RESULTS);
    }

    public void onCustomStorageSelected() {
        Intent customDir = new Intent(this, ImportActivity.class);
        startActivityForResult(customDir, ConstsImport.RQST_IMPORT_RESULTS);
    }

    public void setThemePrefs(int pref) {
        Preferences.setColorTheme(pref);
        ThemeHelper.applyTheme(this);
        getPager().goToNextSlide();
    }

    public void setSourcePrefs(List<Site> sources) {
        Preferences.setActiveSites(sources);
    }

    @Override
    public void onDonePressed(Fragment currentFragment) {
        Preferences.setIsFirstRun(false);
        Intent intent = new Intent(this, LibraryActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    // Callback from the directory chooser
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ConstsImport.RQST_IMPORT_RESULTS) {
            Timber.d("REQUEST RESULT RECEIVED");
            if (data != null && data.getStringExtra(RESULT_KEY) != null) {
                String result = data.getStringExtra(RESULT_KEY);
                resultHandler(resultCode, result);
            }
        }
    }

    private void resultHandler(int resultCode, String result) {
        if (resultCode == RESULT_OK) {
            Timber.d("RESULT_OK: %s", result);

            if (result.equals(ConstsImport.PERMISSION_GRANTED)) {
                Timber.d("Permission Allowed, resetting.");
                Snackbar.make(pager, R.string.permission_granted, LENGTH_SHORT).show();
            } else {
                // If result passes validation, then we move to next slide
                getPager().goToNextSlide();
                setButtonState(nextButton, false);
            }
        } else {
            switch (result) {
                case ConstsImport.PERMISSION_DENIED:
                    Timber.d("Permission Denied by User");
                    Snackbar.make(pager, R.string.permissioncomplaint_snackbar, LENGTH_LONG).show();
                    break;
                case ConstsImport.PERMISSION_DENIED_FORCED:
                    Timber.d("Permission Denied (Forced) by User/Policy");
                    Snackbar.make(pager, R.string.permissioncomplaint_snackbar_manual, LENGTH_INDEFINITE)
                            .setAction(android.R.string.ok, v -> openAppSettings())
                            .show();
                    break;
                case ConstsImport.RESULT_CANCELED:
                case ConstsImport.EXISTING_LIBRARY_FOUND:
                    Snackbar.make(pager, R.string.import_canceled, LENGTH_LONG).show();
                    break;
                default:
                    // Other cases should fail silently
            }
        }
    }

    private void openAppSettings() {
        Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
        Intent appSettings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
        startActivityForResult(appSettings, ConstsImport.RQST_APP_SETTINGS);
    }
}
