package me.devsaki.hentoid.ui;

import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

public class BlinkAnimation extends AlphaAnimation {

    public BlinkAnimation(long duration, long startOffset) {
        super(0.0f, 1.0f);
        setRepeatMode(Animation.REVERSE);
        setRepeatCount(Animation.INFINITE);
        setDuration(duration);
        setStartOffset(startOffset);
    }
}
