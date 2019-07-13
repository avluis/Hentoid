package me.devsaki.hentoid.widget;

import android.view.KeyEvent;
import android.view.View;

public final class VolumeGestureListener implements View.OnKeyListener {

    private Runnable onVolumeDownListener;

    private Runnable onVolumeUpListener;

    private Runnable onBackListener;

    private int cooldown = 1000;

    private int turboCooldown = 500;

    private boolean isTurboEnabled = true;

    private long nextNotifyTime;

    public VolumeGestureListener setOnVolumeDownListener(Runnable onVolumeDownListener) {
        this.onVolumeDownListener = onVolumeDownListener;
        return this;
    }

    public VolumeGestureListener setOnVolumeUpListener(Runnable onVolumeUpListener) {
        this.onVolumeUpListener = onVolumeUpListener;
        return this;
    }

    public VolumeGestureListener setOnBackListener(Runnable onBackListener) {
        this.onBackListener = onBackListener;
        return this;
    }

    public VolumeGestureListener setCooldown(int cooldown) {
        this.cooldown = cooldown;
        return this;
    }

    public VolumeGestureListener setTurboCooldown(int turboCooldown) {
        this.turboCooldown = turboCooldown;
        return this;
    }

    public VolumeGestureListener setTurboEnabled(boolean isTurboEnabled) {
        this.isTurboEnabled = isTurboEnabled;
        return this;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        Runnable listener;
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            listener = onVolumeDownListener;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
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
}
