package me.devsaki.hentoid.util.network;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.threeten.bp.Instant;

import java.util.LinkedList;
import java.util.Queue;

public class DownloadSpeedCalculator {

    private static final int MAX_RECORDS_SIZE = 5;
    private final Queue<ImmutablePair<Long, Long>> usageRecords;

    private float avgSpeedBps = 0;

    public DownloadSpeedCalculator() {
        usageRecords = new LinkedList<>();
    }

    public synchronized void addSampleNow(final long downloadedBytes) {
        long ticksNow = Instant.now().toEpochMilli();
        usageRecords.add(new ImmutablePair<>(ticksNow, downloadedBytes));
        if (usageRecords.size() > MAX_RECORDS_SIZE) usageRecords.poll();
        updateAvgSpeed(ticksNow, downloadedBytes);
    }

    private void updateAvgSpeed(long ticksNow, long downloadedBytes) {
        ImmutablePair<Long, Long> firstRecord = usageRecords.peek();
        if (firstRecord != null)
            avgSpeedBps = ((downloadedBytes - firstRecord.right) * 1f) / ((ticksNow - firstRecord.left) / 1000f);
    }

    public float getAvgSpeedKbps() {
        return avgSpeedBps / 1000f;
    }
}
