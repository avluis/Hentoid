package me.devsaki.hentoid.util.download

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.BlockingBucket
import io.github.bucket4j.Bucket
import java.time.Duration


object DownloadRateLimiter {
    private var bucket: BlockingBucket? = null

    fun setRateLimit(perSecond: Long) {
        bucket = if (perSecond <= 0) null
        else {
            val limit =
                Bandwidth.builder()
                    .capacity(perSecond)
                    .refillIntervally(1, Duration.ofSeconds(1))
                    .build()
            Bucket.builder().addLimit(limit).build().asBlocking()
        }
    }

    fun take(): Boolean {
        if (null == bucket) return true

        bucket?.consume(1)
        return true
    }
}