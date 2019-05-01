package me.devsaki.hentoid.widget;

import android.view.KeyEvent;
import android.view.View;

public final class VolumeKeyListener implements View.OnKeyListener {

    private Runnable onVolumeDownKeyListener;

    private Runnable onVolumeUpKeyListener;

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
            onVolumeDownKeyListener.run();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            onVolumeUpKeyListener.run();
            return true;
        }
        return false;
    }
}
