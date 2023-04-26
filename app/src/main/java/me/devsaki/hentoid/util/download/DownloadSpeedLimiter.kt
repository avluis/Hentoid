package me.devsaki.hentoid.util.download

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.BlockingBucket
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import me.devsaki.hentoid.util.Preferences
import java.time.Duration
import kotlin.math.round

object DownloadSpeedLimiter {
    private var bucket: BlockingBucket? = null

    fun setSpeedLimitKbps(value: Int) {
        bucket = if (value <= 0) null
        else {
            val limit = Bandwidth.classic(
                round(value * 1.1 * 1000).toLong(),
                Refill.intervally(value * 1000L, Duration.ofSeconds(1))
            )
            Bucket.builder().addLimit(limit).build().asBlocking()
        }
    }

    fun take(bytes: Long): Boolean {
        if (null == bucket) return true

        bucket?.consume(bytes)
        return true
    }

    fun prefsSpeedCapToKbps(value: Int): Int {
        return when (value) {
            Preferences.Constant.DL_SPEED_CAP_100 -> 100
            Preferences.Constant.DL_SPEED_CAP_200 -> 200
            Preferences.Constant.DL_SPEED_CAP_400 -> 400
            Preferences.Constant.DL_SPEED_CAP_800 -> 800
            else -> -1
        }
    }
}