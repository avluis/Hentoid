package me.devsaki.hentoid.widget

import android.content.Context
import android.hardware.SensorManager
import android.view.OrientationEventListener
import me.devsaki.hentoid.core.Consumer

enum class DeviceOrientation {
    PORTRAIT,
    LANDSCAPE,
    PORTRAIT_REVERSE,
    LANDSCAPE_REVERSE
}

// Inspired by https://gist.github.com/tuanchauict/6a885779c0940a012b81
class SimpleOrientationListener(
    ctx: Context,
    rate: Int = SensorManager.SENSOR_DELAY_NORMAL,
    onOrientationChange: Consumer<DeviceOrientation>
) :
    OrientationEventListener(ctx, rate) {

    private var lastOrientation = DeviceOrientation.PORTRAIT
    private var onChange: Consumer<DeviceOrientation>? = onOrientationChange

    override fun onOrientationChanged(orientation: Int) {
        if (orientation < 0) {
            return  // Flip screen, Not take account
        }

        val curOrientation = if (orientation <= 45) {
            DeviceOrientation.PORTRAIT
        } else if (orientation <= 135) {
            DeviceOrientation.LANDSCAPE_REVERSE
        } else if (orientation <= 225) {
            DeviceOrientation.PORTRAIT_REVERSE
        } else if (orientation <= 315) {
            DeviceOrientation.LANDSCAPE
        } else {
            DeviceOrientation.PORTRAIT
        }
        if (curOrientation != lastOrientation) {
            onChange?.invoke(curOrientation)
            lastOrientation = curOrientation
        }
    }

    fun clear() {
        onChange = null
    }
}