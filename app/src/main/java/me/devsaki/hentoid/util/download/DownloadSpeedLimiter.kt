package me.devsaki.hentoid.util.download

import org.isomorphism.util.TokenBucket
import org.isomorphism.util.TokenBuckets
import java.util.concurrent.TimeUnit
import kotlin.math.round

object DownloadSpeedLimiter {
    var bucket: TokenBucket? = null

    fun setSpeedLimitKbps(value: Int) {
        if (value < 0) bucket = null

        bucket = TokenBuckets.builder()
            .withCapacity(round(value * 1.1 * 1000).toLong())
            .withFixedIntervalRefillStrategy(value * 1000L, 1, TimeUnit.SECONDS)
            .build()
    }

    fun take(bytes: Long): Boolean {
        if (null == bucket) return true

        bucket?.consume(bytes)
        return true
    }
}