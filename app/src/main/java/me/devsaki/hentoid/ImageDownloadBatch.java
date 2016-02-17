package me.devsaki.hentoid;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Created by Shiro on 2/17/2016.
 * Handles batch operations on ImageDownloadTask objects
 * and manages blocking queues for finished tasks
 */
public class ImageDownloadBatch {

    private final ImageDownloadBatch lock;
    private final ExecutorCompletionService<Void> completionService;
    private final List<Future> futures = new ArrayList<>();
    private int inProgress = 0;

    public ImageDownloadBatch(final ExecutorService executorService) {
        completionService = new ExecutorCompletionService<>(executorService);
        lock = this;
    }

    public void addTask(File dir, String filename, String imageUrl) {
        futures.add(
                completionService.submit(
                        new ImageDownloadTask(dir, filename, imageUrl)
                                .registerObserver(new Observer())
                )
        );
    }

    public void waitForCompletedTask() throws Exception {
        completionService.take().get();
    }

    public void cancel() {
        for (Future future : futures) {
            future.cancel(false);
        }

        synchronized (lock) {
            Log.d("SHIRO", "WAIT");
            while (inProgress != 0) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    //Do not interrupt this wait until condition is met
                }
            }
        }
        Log.d("SHIRO", "RELEASED");
    }

    class Observer {
        void taskStarted() {
            synchronized (lock) {
                Log.d("SHIRO", "START");
                inProgress++;
            }
        }

        void taskFinished() {
            synchronized (lock) {
                Log.d("SHIRO", "FINISH");
                inProgress--;
                lock.notifyAll();
            }
        }
    }
}
