package me.devsaki.hentoid.util;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import me.devsaki.hentoid.HentoidApp;
import timber.log.Timber;

public class AppLifeCycleListener implements LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private void onMoveToForeground() {
        Timber.d("Returning to foreground");
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private void onMoveToBackground() {
        Timber.d("Moving to background");
        if (!Preferences.getAppLockPin().isEmpty() && Preferences.isLockOnAppRestore()) HentoidApp.setUnlocked(false);
    }

}