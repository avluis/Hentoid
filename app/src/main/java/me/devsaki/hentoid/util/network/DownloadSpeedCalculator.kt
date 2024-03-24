package me.devsaki.hentoid.util.network

import java.time.Instant
import java.util.LinkedList
import java.util.Queue

object DownloadSpeedCalculator {
    private const val MAX_SAMPLES_SIZE = 15
    private val samples: Queue<Pair<Long, Long>> = LinkedList()

    private var avgSpeedBps = 0f


    /**
     * Record a sample for the current time
     *
     * @param downloadedBytes Total number of downloaded bytes to sample at the current time
     */
    @Synchronized
    fun addSampleNow(downloadedBytes: Long) {
        val ticksNow = Instant.now().toEpochMilli()
        samples.add(Pair(ticksNow, downloadedBytes))
        if (samples.size > MAX_SAMPLES_SIZE) samples.poll()
        updateAvgSpeed(ticksNow, downloadedBytes)
    }

    /**
     * Update average speed by processing recorded samples
     *
     * @param ticksNow        Timestamp to calculate download speed for
     * @param downloadedBytes Total number of downloaded bytes at the given timestamp
     */
    private fun updateAvgSpeed(ticksNow: Long, downloadedBytes: Long) {
        val firstRecord = samples.peek()
        if (firstRecord != null) avgSpeedBps =
            (downloadedBytes - firstRecord.second) * 1f / ((ticksNow - firstRecord.first) / 1000f)
    }

    /**
     * Get the updated average download speed
     * @return Updated average download speed, in Kbps
     */
    fun getAvgSpeedKbps(): Float {
        return avgSpeedBps / 1000f
    }
}