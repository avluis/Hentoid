package me.devsaki.hentoid.widget;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.annimon.stream.function.Consumer;

import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.Preferences;

public final class ReaderKeyListener implements View.OnKeyListener {

    private Consumer<Boolean> onVolumeDownListener;

    private Consumer<Boolean> onVolumeUpListener;

    private Consumer<Boolean> onKeyLeftListener;

    private Consumer<Boolean> onKeyRightListener;

    private Consumer<Boolean> onBackListener;

    // Parameters
    private static final int COOLDOWN = 1000;
    private static final int TURBO_COOLDOWN = 500;
    private static final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

    // Internal variables
    private long nextNotifyTime = Long.MAX_VALUE;
    private final Debouncer<Consumer<Boolean>> simpleTapDebouncer;


    public ReaderKeyListener(Context context) {
        simpleTapDebouncer = new Debouncer<>(context, LONG_PRESS_TIMEOUT, consumer -> consumer.accept(false));
    }

    public ReaderKeyListener setOnVolumeDownListener(Consumer<Boolean> onVolumeDownListener) {
        this.onVolumeDownListener = onVolumeDownListener;
        return this;
    }

    public ReaderKeyListener setOnVolumeUpListener(Consumer<Boolean> onVolumeUpListener) {
        this.onVolumeUpListener = onVolumeUpListener;
        return this;
    }

    public ReaderKeyListener setOnKeyLeftListener(Consumer<Boolean> onKeyLeftListener) {
        this.onKeyLeftListener = onKeyLeftListener;
        return this;
    }

    public ReaderKeyListener setOnKeyRightListener(Consumer<Boolean> onKeyRightListener) {
        this.onKeyRightListener = onKeyRightListener;
        return this;
    }

    public ReaderKeyListener setOnBackListener(Consumer<Boolean> onBackListener) {
        this.onBackListener = onBackListener;
        return this;
    }

    private boolean isTurboEnabled() {
        return !Preferences.isViewerVolumeToSwitchBooks();
    }

    private boolean isDetectLongPress() {
        return Preferences.isViewerVolumeToSwitchBooks();
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

        Consumer<Boolean> listener;
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

        if (event.getRepeatCount() == 0) { // Simple down
            if (isDetectLongPress()) {
                simpleTapDebouncer.submit(listener);
                nextNotifyTime = event.getEventTime() + LONG_PRESS_TIMEOUT;
            } else {
                listener.accept(false);
                nextNotifyTime = event.getEventTime() + COOLDOWN;
            }
        } else if (event.getEventTime() >= nextNotifyTime) { // Long down
            simpleTapDebouncer.clear();
            listener.accept(true);
            nextNotifyTime = event.getEventTime() + (isTurboEnabled() ? TURBO_COOLDOWN : COOLDOWN);
        }
        return true;
    }

    public void clear() {
        onVolumeDownListener = null;
        onVolumeUpListener = null;
        onKeyLeftListener = null;
        onKeyRightListener = null;
        onBackListener = null;
        simpleTapDebouncer.clear();
    }
}
