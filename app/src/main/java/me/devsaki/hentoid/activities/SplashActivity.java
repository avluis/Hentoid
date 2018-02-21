package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.AssetsCache;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;

/**
 * Created by avluis on 1/9/16.
 * Displays a Splash while starting up.
 * Nothing but a splash/activity selection should be defined here.
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AssetsCache.init(HentoidApp.getAppContext());

        if (Preferences.isFirstRun()) {
            Intent intent = new Intent(this, IntroActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        } else {
            Helper.launchMainActivity(this);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        }
    }
}
