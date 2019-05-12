package me.devsaki.hentoid.widget;

import android.view.KeyEvent;
import android.view.View;

import java.util.Calendar;

public class KeyInputDetector implements View.OnKeyListener {

    public interface OnKeyEventListener {
        void onEvent(int keyCode);
    }

    // PARAMETERS
    private final int actionTimeFrame;
    private final OnKeyEventListener listener;
    private boolean enableTurbo = true;

    // INTERNALS
    private long lastKeyDownEventTick = -1;
    private boolean isTurbo = false;


    public KeyInputDetector(OnKeyEventListener listener, int actionTimeFrame) {
        this.actionTimeFrame = actionTimeFrame;
        this.listener = listener;
    }

    public void setEnableTurbo(boolean enableTurbo) {
        this.enableTurbo = enableTurbo;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            long timeNow = Calendar.getInstance().getTimeInMillis();
            if (timeNow - lastKeyDownEventTick > actionTimeFrame / (isTurbo ? 2 : 1)) {
                if (-1 == lastKeyDownEventTick) isTurbo = true;
                if (enableTurbo) lastKeyDownEventTick = timeNow;
                listener.onEvent(keyCode);
            }
            return true;
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            lastKeyDownEventTick = -1;
            isTurbo = false;
        }
        return false;
    }
}
