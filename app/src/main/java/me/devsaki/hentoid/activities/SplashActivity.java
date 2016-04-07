package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import me.devsaki.hentoid.util.AndroidHelper;

/**
 * Created by avluis on 1/9/16.
 * Displays a Splash while starting up.
 * <p/>
 * Nothing but a splash/activity selection should be defined here.
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (AndroidHelper.isFirstRun()) {
            Intent intent = new Intent(this, IntroSlideActivity.class);
            startActivity(intent);
            finish();
        } else {
            AndroidHelper.launchMainActivity(this);
            finish();
        }
    }
}