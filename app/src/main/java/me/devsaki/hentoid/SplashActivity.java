package me.devsaki.hentoid;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import me.devsaki.hentoid.util.ConstantsPreferences;

/**
 * Created by avluis on 1/9/16.
 * Displays a Splash while starting up.
 * Requires a proper theme setup in order to be effective:
 * https://www.bignerdranch.com/blog/splash-screens-the-right-way/
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
}