package me.devsaki.hentoid.util.download

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.BlockingBucket
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import java.time.Duration


object DownloadRateLimiter {
    private var bucket: BlockingBucket? = null

    fun setRateLimit(perSecond: Long) {
        bucket = if (perSecond <= 0) null
        else {
            val limit = Bandwidth.classic(perSecond, Refill.intervally(1, Duration.ofSeconds(1)))
            Bucket.builder().addLimit(limit).build().asBlocking()
        }
    }

    fun take(): Boolean {
        if (null == bucket) return true

        bucket?.consume(1)
        return true
    }
}