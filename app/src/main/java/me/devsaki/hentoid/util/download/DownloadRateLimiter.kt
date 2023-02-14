package me.devsaki.hentoid.util.download

import org.isomorphism.util.TokenBucket
import org.isomorphism.util.TokenBuckets
import java.util.concurrent.TimeUnit

object DownloadRateLimiter {
    private var bucket: TokenBucket? = null

    fun setRateLimit(perSecond: Long) {
        bucket = if (perSecond <= 0) null
        else
            TokenBuckets.builder()
                .withCapacity(perSecond)
                .withFixedIntervalRefillStrategy(perSecond, 1, TimeUnit.SECONDS)
                .build()
    }

    fun setRateLimit2(perSecond: Long) {
        bucket = if (perSecond <= 0) null
        else
            TokenBuckets.builder()
                .withCapacity(perSecond)
                .withFixedIntervalRefillStrategy(perSecond, 1300, TimeUnit.MILLISECONDS)
                .build()
    }

    fun take(): Boolean {
        if (null == bucket) return true

        bucket?.consume(1)
        return true
    }
}