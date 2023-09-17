package me.devsaki.hentoid.ui

import android.view.animation.AlphaAnimation

class BlinkAnimation(duration: Long, startOffset: Long) : AlphaAnimation(0.0f, 1.0f) {
    init {
        repeatMode = REVERSE
        repeatCount = INFINITE
        setDuration(duration)
        setStartOffset(startOffset)
    }
}