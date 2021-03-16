package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.ContentView;
import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import org.threeten.bp.Instant;

import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;

public abstract class BaseActivity extends AppCompatActivity {

    protected BaseActivity() {
        super();
    }

    @ContentView
    protected BaseActivity(@LayoutRes int contentLayoutId) {
        super(contentLayoutId);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
    }

    @Override
    protected void onRestart() {
        // If locked and PIN enabled, display the PIN
        if (!HentoidApp.isUnlocked() && !Preferences.getAppLockPin().isEmpty() && Preferences.isLockOnAppRestore()) {
            // Evaluate if any set delay has passed; if so, the app gets locked
            int lockDelayCode = Preferences.getLockTimer();
            int lockDelaySec;
            switch (lockDelayCode) {
                case Preferences.Constant.LOCK_TIMER_10S:
                    lockDelaySec = 10;
                    break;
                case Preferences.Constant.LOCK_TIMER_30S:
                    lockDelaySec = 30;
                    break;
                case Preferences.Constant.LOCK_TIMER_1M:
                    lockDelaySec = 60;
                    break;
                case Preferences.Constant.LOCK_TIMER_2M:
                    lockDelaySec = 120;
                    break;
                default:
                    lockDelaySec = 0;
            }
            if ((Instant.now().toEpochMilli() - HentoidApp.getLockInstant()) / 1000 > lockDelaySec) {
                Intent intent = new Intent(this, UnlockActivity.class);
                startActivity(intent);
            } else {
                HentoidApp.setUnlocked(true); // Auto-unlock when we're back to the app under the delay
            }
        }
        super.onRestart();
    }
}
