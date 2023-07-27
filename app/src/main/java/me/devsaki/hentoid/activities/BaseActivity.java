package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.ContentView;
import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.time.Instant;

import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.events.CommunicationEvent;
import me.devsaki.hentoid.util.LocaleHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.ToastHelper;

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
        // Change locale if set manually
        LocaleHelper.convertLocaleToEnglish(this);

        ThemeHelper.applyTheme(this);
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    @Override
    protected void onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    protected void onRestart() {
        // If locked and PIN enabled, display the PIN
        if (!HentoidApp.Companion.isUnlocked() && !Preferences.getAppLockPin().isEmpty() && Preferences.isLockOnAppRestore()) {
            // Evaluate if any set delay has passed; if so, the app gets locked
            int lockDelayCode = Preferences.getLockTimer();
            int lockDelaySec = switch (lockDelayCode) {
                case Preferences.Constant.LOCK_TIMER_10S -> 10;
                case Preferences.Constant.LOCK_TIMER_30S -> 30;
                case Preferences.Constant.LOCK_TIMER_1M -> 60;
                case Preferences.Constant.LOCK_TIMER_2M -> 120;
                default -> 0;
            };
            if ((Instant.now().toEpochMilli() - HentoidApp.Companion.getLockInstant()) / 1000 > lockDelaySec) {
                Intent intent = new Intent(this, UnlockActivity.class);
                startActivity(intent);
            } else {
                HentoidApp.Companion.setUnlocked(true); // Auto-unlock when we're back to the app under the delay
            }
        }
        super.onRestart();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCommunicationEvent(CommunicationEvent event) {
        if (event.getRecipient() != CommunicationEvent.RC_ALL || event.getType() != CommunicationEvent.EV_BROADCAST || null == event.getMessage())
            return;
        // Make sure current activity is active (=eligible to display that toast)
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
        ToastHelper.toast(event.getMessage());
    }
}
