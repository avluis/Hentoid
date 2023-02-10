package me.devsaki.hentoid.util.download

import org.isomorphism.util.TokenBucket
import org.isomorphism.util.TokenBuckets
import java.util.concurrent.TimeUnit

object DownloadRateLimiter {
    var bucket: TokenBucket? = null

    fun setRateLimit(perSecond: Long) {
        bucket = if (perSecond <= 0) null
        else
            TokenBuckets.builder()
                .withCapacity(perSecond)
                .withFixedIntervalRefillStrategy(perSecond, 1, TimeUnit.SECONDS)
                .build()
    }

    fun take(): Boolean {
        if (null == bucket) return true

        bucket?.consume(1)
        return true
    }
}