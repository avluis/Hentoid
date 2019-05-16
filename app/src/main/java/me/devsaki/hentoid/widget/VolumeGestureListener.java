package me.devsaki.hentoid.widget;

import android.view.KeyEvent;
import android.view.View;

import timber.log.Timber;

public final class VolumeGestureListener implements View.OnKeyListener {

    private Runnable onVolumeDownListener;

    private Runnable onVolumeUpListener;

    private boolean enableTurbo = true;

    private int actionTimeWindow = 1000;

    private long lastKeyDownEventTick = -1;

    private boolean isTurbo = false;

    public VolumeGestureListener setOnVolumeDownListener(Runnable onVolumeDownListener) {
        this.onVolumeDownListener = onVolumeDownListener;
        return this;
    }

    public VolumeGestureListener setOnVolumeUpListener(Runnable onVolumeUpListener) {
        this.onVolumeUpListener = onVolumeUpListener;
        return this;
    }

    public VolumeGestureListener setEnableTurbo(boolean enableTurbo) {
        this.enableTurbo = enableTurbo;
        return this;
    }

    public VolumeGestureListener setActionTimeWindow(int actionTimeWindow) {
        this.actionTimeWindow = actionTimeWindow;
        return this;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            long timeNow = System.currentTimeMillis();
            if (timeNow - lastKeyDownEventTick > actionTimeWindow / (isTurbo ? 2 : 1)) {
                if (-1 == lastKeyDownEventTick) isTurbo = true;
                if (enableTurbo) lastKeyDownEventTick = timeNow;
                notifyListener(keyCode);
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            lastKeyDownEventTick = -1;
            isTurbo = false;
        }
        return true;
    }

    private void notifyListener(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            onVolumeDownListener.run();
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            onVolumeUpListener.run();
        }
    }
}
