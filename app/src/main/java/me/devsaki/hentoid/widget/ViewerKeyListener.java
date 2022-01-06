package me.devsaki.hentoid.widget;

import android.view.KeyEvent;
import android.view.View;

import me.devsaki.hentoid.util.Preferences;

public final class ViewerKeyListener implements View.OnKeyListener {

    private Runnable onVolumeDownListener;

    private Runnable onVolumeUpListener;

    private Runnable onKeyLeftListener;

    private Runnable onKeyRightListener;

    private Runnable onBackListener;


    private int cooldown = 1000;

    private int turboCooldown = 500;

    private boolean isTurboEnabled = true;

    private long nextNotifyTime;


    public ViewerKeyListener setOnVolumeDownListener(Runnable onVolumeDownListener) {
        this.onVolumeDownListener = onVolumeDownListener;
        return this;
    }

    public ViewerKeyListener setOnVolumeUpListener(Runnable onVolumeUpListener) {
        this.onVolumeUpListener = onVolumeUpListener;
        return this;
    }

    public ViewerKeyListener setOnKeyLeftListener(Runnable onKeyLeftListener) {
        this.onKeyLeftListener = onKeyLeftListener;
        return this;
    }

    public ViewerKeyListener setOnKeyRightListener(Runnable onKeyRightListener) {
        this.onKeyRightListener = onKeyRightListener;
        return this;
    }

    public ViewerKeyListener setOnBackListener(Runnable onBackListener) {
        this.onBackListener = onBackListener;
        return this;
    }

    public ViewerKeyListener setCooldown(int cooldown) {
        this.cooldown = cooldown;
        return this;
    }

    public ViewerKeyListener setTurboCooldown(int turboCooldown) {
        this.turboCooldown = turboCooldown;
        return this;
    }

    public ViewerKeyListener setTurboEnabled(boolean isTurboEnabled) {
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
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && Preferences.isViewerKeyboardToTurn()) {
            listener = onKeyLeftListener;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && Preferences.isViewerKeyboardToTurn()) {
            listener = onKeyRightListener;
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
        onKeyLeftListener = null;
        onKeyRightListener = null;
        onBackListener = null;
    }
}
