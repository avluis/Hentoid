package me.devsaki.hentoid.ui;

import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

public class BlinkAnimation extends AlphaAnimation {

    public BlinkAnimation() {
        super(0.0f, 1.0f);
        setDuration(500);
        setStartOffset(100);
        setRepeatMode(Animation.REVERSE);
        setRepeatCount(Animation.INFINITE);
    }
}
