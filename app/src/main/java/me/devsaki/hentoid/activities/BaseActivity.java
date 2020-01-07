package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
    }

    @Override
    protected void onStop() {
        super.onStop(); // TODO prevent Android from taking a screenshot of the screen ?
    }

    @Override
    protected void onRestart() {
        // If locked and PIN enabled, display the PIN
        if (!HentoidApp.isUnlocked() && !Preferences.getAppLockPin().isEmpty() && Preferences.isLockOnAppRestore()) {
            Intent intent = new Intent(this, UnlockActivity.class);
            startActivity(intent);
        }
        super.onRestart();
    }
}
