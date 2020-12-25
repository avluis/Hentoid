package me.devsaki.hentoid.util.network;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.threeten.bp.Instant;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Calculate average download speed based on samples taken at different moments in time
 */
public class DownloadSpeedCalculator {

    private static final int MAX_SAMPLES_SIZE = 5;
    private final Queue<ImmutablePair<Long, Long>> samples;

    private float avgSpeedBps = 0;

    public DownloadSpeedCalculator() {
        samples = new LinkedList<>();
    }

    /**
     * Record a sample for the current time
     *
     * @param downloadedBytes Total number of downloaded bytes to sample at the current time
     */
    public synchronized void addSampleNow(final long downloadedBytes) {
        long ticksNow = Instant.now().toEpochMilli();
        samples.add(new ImmutablePair<>(ticksNow, downloadedBytes));
        if (samples.size() > MAX_SAMPLES_SIZE) samples.poll();
        updateAvgSpeed(ticksNow, downloadedBytes);
    }

    /**
     * Update average speed by processing recorded samples
     *
     * @param ticksNow        Timestamp to calculate download speed for
     * @param downloadedBytes Total number of downloaded bytes at the given timestamp
     */
    private void updateAvgSpeed(long ticksNow, long downloadedBytes) {
        ImmutablePair<Long, Long> firstRecord = samples.peek();
        if (firstRecord != null)
            avgSpeedBps = ((downloadedBytes - firstRecord.right) * 1f) / ((ticksNow - firstRecord.left) / 1000f);
    }

    /**
     * Get the updated average download speed
     * @return Updated average download speed, in Kbps
     */
    public float getAvgSpeedKbps() {
        return avgSpeedBps / 1000f;
    }
}
