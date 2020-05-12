package me.devsaki.hentoid.widget;

import android.view.KeyEvent;
import android.view.View;

import me.devsaki.hentoid.util.Preferences;

public final class VolumeKeyListener implements View.OnKeyListener {

    private Runnable onVolumeDownListener;

    private Runnable onVolumeUpListener;

    private Runnable onBackListener;


    private int cooldown = 1000;

    private int turboCooldown = 500;

    private boolean isTurboEnabled = true;

    private long nextNotifyTime;


    public VolumeKeyListener setOnVolumeDownListener(Runnable onVolumeDownListener) {
        this.onVolumeDownListener = onVolumeDownListener;
        return this;
    }

    public VolumeKeyListener setOnVolumeUpListener(Runnable onVolumeUpListener) {
        this.onVolumeUpListener = onVolumeUpListener;
        return this;
    }

    public VolumeKeyListener setOnBackListener(Runnable onBackListener) {
        this.onBackListener = onBackListener;
        return this;
    }

    public VolumeKeyListener setCooldown(int cooldown) {
        this.cooldown = cooldown;
        return this;
    }

    public VolumeKeyListener setTurboCooldown(int turboCooldown) {
        this.turboCooldown = turboCooldown;
        return this;
    }

    public VolumeKeyListener setTurboEnabled(boolean isTurboEnabled) {
        this.isTurboEnabled = isTurboEnabled;
        return this;
    }

    private boolean isVolumeKey(int keyCode, int targetKeyCode) {
        // Ignore volume keys when disabled in preferences
        if (!Preferences.isViewerVolumeToTurn()) return false;

        if (Preferences.isViewerInvertVolumeRocker()) {
            if (targetKeyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                return (keyCode == KeyEvent.KEYCODE_VOLUME_UP);
            else if (targetKeyCode == KeyEvent.KEYCODE_VOLUME_UP)
                return (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN);
        }
        return (keyCode == targetKeyCode);
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        Runnable listener;
        if (isVolumeKey(keyCode, KeyEvent.KEYCODE_VOLUME_DOWN)) {
            listener = onVolumeDownListener;
        } else if (isVolumeKey(keyCode, KeyEvent.KEYCODE_VOLUME_UP)) {
            listener = onVolumeUpListener;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            listener = onBackListener;
        } else {
            return false;
        }

        if (event.getRepeatCount() == 0) {
            listener.run();
            nextNotifyTime = event.getEventTime() + cooldown;
        } else if (event.getEventTime() >= nextNotifyTime) {
            listener.run();
            nextNotifyTime = event.getEventTime() + (isTurboEnabled ? turboCooldown : cooldown);
        }
        return true;
    }

    public void clear() {
        onVolumeDownListener = null;
        onVolumeUpListener = null;
        onBackListener = null;
    }
}
