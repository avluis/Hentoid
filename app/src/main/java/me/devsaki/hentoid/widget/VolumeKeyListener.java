package me.devsaki.hentoid.widget;

import android.view.KeyEvent;
import android.view.View;

import java.util.Calendar;

public final class VolumeKeyListener implements View.OnKeyListener {

    private static int ACTION_DELAY_MS = 1000;

    private Runnable onVolumeDownKeyListener;

    private Runnable onVolumeUpKeyListener;

    private long lastKeyDownEventTick = -1;
    private boolean isTurbo = false;

    public VolumeKeyListener setOnVolumeDownKeyListener(Runnable onVolumeDownKeyListener) {
        this.onVolumeDownKeyListener = onVolumeDownKeyListener;
        return this;
    }

    public VolumeKeyListener setOnVolumeUpKeyListener(Runnable onVolumeUpKeyListener) {
        this.onVolumeUpKeyListener = onVolumeUpKeyListener;
        return this;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                long timeNow = Calendar.getInstance().getTimeInMillis();
                if (timeNow - lastKeyDownEventTick > ACTION_DELAY_MS / (isTurbo ? 2 : 1)) {
                    if (-1 == lastKeyDownEventTick) isTurbo = true;
                    lastKeyDownEventTick = timeNow;
                    onVolumeDownKeyListener.run();
                }
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                lastKeyDownEventTick = -1;
                isTurbo = false;
            }
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                long timeNow = Calendar.getInstance().getTimeInMillis();
                if (timeNow - lastKeyDownEventTick > ACTION_DELAY_MS / (isTurbo ? 2 : 1)) {
                    if (-1 == lastKeyDownEventTick) isTurbo = true;
                    lastKeyDownEventTick = timeNow;
                    onVolumeUpKeyListener.run();
                }
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                lastKeyDownEventTick = -1;
                isTurbo = false;
            }
        }
        return false;
    }
}
