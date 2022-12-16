package me.devsaki.hentoid.util.download

import me.devsaki.hentoid.util.Preferences
import org.isomorphism.util.TokenBucket
import org.isomorphism.util.TokenBuckets
import java.util.concurrent.TimeUnit
import kotlin.math.round

object DownloadSpeedLimiter {
    var bucket: TokenBucket? = null

    fun setSpeedLimitKbps(value: Int) {
        bucket = if (value <= 0) null
        else
            TokenBuckets.builder()
                .withCapacity(round(value * 1.1 * 1000).toLong())
                .withFixedIntervalRefillStrategy(value * 1000L, 1, TimeUnit.SECONDS)
                .build()
    }

    fun take(bytes: Long): Boolean {
        if (null == bucket) return true

        bucket?.consume(bytes)
        return true
    }

    fun prefsSpeedCapToKbps(value : Int): Int {
        return when (value) {
            Preferences.Constant.DL_SPEED_CAP_100 -> 100
            Preferences.Constant.DL_SPEED_CAP_200 -> 200
            Preferences.Constant.DL_SPEED_CAP_400 -> 400
            Preferences.Constant.DL_SPEED_CAP_800 -> 800
            else -> -1
        }
    }
}