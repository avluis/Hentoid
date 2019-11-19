package me.devsaki.hentoid.util;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 Inspired by {@link android.os.AsyncTask}'s THREAD_POOL_EXECUTOR,
 but with a {@link ThreadPoolExecutor.DiscardPolicy} (drop tasks that cannot be processed)
 instead of the default {@link ThreadPoolExecutor.AbortPolicy} (throw an exception)
*/
public class ImageLoaderThreadExecutor extends ThreadPoolExecutor {

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    // We want at least 2 threads and at most 4 threads in the core pool,
    // preferring to have 1 less than the CPU count to avoid saturating
    // the CPU with background work
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE_SECONDS = 30;

    public ImageLoaderThreadExecutor() {
        super(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(128), new ThreadPoolExecutor.DiscardPolicy());
        this.allowCoreThreadTimeOut(true);
    }
}