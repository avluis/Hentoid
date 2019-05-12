package me.devsaki.hentoid.widget;

import android.view.KeyEvent;
import android.view.View;

public final class VolumeKeyListener implements KeyInputDetector.OnKeyEventListener {

    private final KeyInputDetector detector;

    private Runnable onVolumeDownKeyListener;
    private Runnable onVolumeUpKeyListener;

    public VolumeKeyListener() {
        detector = new KeyInputDetector(this, 1000);
    }

    public VolumeKeyListener setOnVolumeDownKeyListener(Runnable onVolumeDownKeyListener) {
        this.onVolumeDownKeyListener = onVolumeDownKeyListener;
        return this;
    }

    public VolumeKeyListener setOnVolumeUpKeyListener(Runnable onVolumeUpKeyListener) {
        this.onVolumeUpKeyListener = onVolumeUpKeyListener;
        return this;
    }

    public View.OnKeyListener getListener()
    {
        return detector;
    }

    @Override
    public boolean onEvent(int keyCode) {
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
