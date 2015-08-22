package com.thin.downloadmanager;

import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadRequestQueue {

	/** Specifies default number of download dispatcher threads. */
	private static final int DEFAULT_DOWNLOAD_THREAD_POOL_SIZE = 1;

	/**
	 * The set of all requests currently being processed by this RequestQueue. A Request will be in this set if it is waiting in any queue or currently being processed by any dispatcher.
	 */
	private Set<DownloadRequest> mCurrentRequests = new HashSet<DownloadRequest>();

	/** The queue of requests that are actually going out to the network. */
	private PriorityBlockingQueue<DownloadRequest> mDownloadQueue = new PriorityBlockingQueue<DownloadRequest>();

	/** The download dispatchers */
	private DownloadDispatcher[] mDownloadDispatchers;

	/** Used for generating monotonically-increasing sequence numbers for requests. */
	private AtomicInteger mSequenceGenerator = new AtomicInteger();

	private CallBackDelivery mDelivery;

	/**
	 * Delivery class to delivery the call back to call back registrar in main thread.
	 */
	class CallBackDelivery {

		/** Used for posting responses, typically to the main thread. */
		private final Executor mCallBackExecutor;

		/**
		 * Constructor taking a handler to main thread.
		 */
		public CallBackDelivery(final Handler handler) {
			// Make an Executor that just wraps the handler.
			mCallBackExecutor = new Executor() {
				@Override
				public void execute(Runnable command) {
					handler.post(command);
				}
			};
		}

		public void postDownloadComplete(final DownloadRequest request) {
			mCallBackExecutor.execute(new Runnable() {
				public void run() {
					request.getDownloadListener().onDownloadComplete(request.getDownloadId());
				}
			});
		}

		public void postDownloadFailed(final DownloadRequest request, final int errorCode, final String errorMsg) {
			mCallBackExecutor.execute(new Runnable() {
				public void run() {
					request.getDownloadListener().onDownloadFailed(request.getDownloadId(), errorCode, errorMsg);
				}
			});
		}

		public void postProgressUpdate(final DownloadRequest request, final long totalBytes, final long downloadedBytes, final int progress) {
			mCallBackExecutor.execute(new Runnable() {
				public void run() {
					request.getDownloadListener().onProgress(request.getDownloadId(), totalBytes, downloadedBytes, progress);
				}
			});
		}
	}

	/**
	 * Default constructor.
	 */
	public DownloadRequestQueue() {
		mDownloadDispatchers = new DownloadDispatcher[DEFAULT_DOWNLOAD_THREAD_POOL_SIZE];
		mDelivery = new CallBackDelivery(new Handler(Looper.getMainLooper()));
	}

	/**
	 * Creates the download dispatchers workers pool.
	 */
	public DownloadRequestQueue(int threadPoolSize) {
		mDelivery = new CallBackDelivery(new Handler(Looper.getMainLooper()));
		if (threadPoolSize > 0 && threadPoolSize <= 4) {
			mDownloadDispatchers = new DownloadDispatcher[threadPoolSize];
		} else {
			mDownloadDispatchers = new DownloadDispatcher[DEFAULT_DOWNLOAD_THREAD_POOL_SIZE];
		}
	}

	public void start() {
		stop(); // Make sure any currently running dispatchers are stopped.

		// Create download dispatchers (and corresponding threads) up to the pool size.
		for (int i = 0; i < mDownloadDispatchers.length; i++) {
			DownloadDispatcher downloadDispatcher = new DownloadDispatcher(mDownloadQueue, mDelivery);
			mDownloadDispatchers[i] = downloadDispatcher;
			downloadDispatcher.start();
		}
	}

	// Package-Private methods.
	/**
	 * Generates a download id for the request and adds the download request to the download request queue for the dispatchers pool to act on immediately.
	 * 
	 * @param request
	 * @return downloadId
	 */
	int add(DownloadRequest request) {
		int downloadId = getDownloadId();
		// Tag the request as belonging to this queue and add it to the set of current requests.
		request.setDownloadRequestQueue(this);

		synchronized (mCurrentRequests) {
			mCurrentRequests.add(request);
		}

		// Process requests in the order they are added.
		request.setDownloadId(downloadId);
		mDownloadQueue.add(request);

		return downloadId;
	}

	/**
	 * Returns the current download state for a download request.
	 * 
	 * @param downloadId
	 * @return
	 */
	int query(int downloadId) {
		synchronized (mCurrentRequests) {
			for (DownloadRequest request : mCurrentRequests) {
				if (request.getDownloadId() == downloadId) {
					return request.getDownloadState();
				}
			}
		}
		return DownloadManager.STATUS_NOT_FOUND;
	}

	/**
	 * Cancel all the dispatchers in work and also stops the dispatchers.
	 */
	void cancelAll() {

		synchronized (mCurrentRequests) {
			for (DownloadRequest request : mCurrentRequests) {
				request.cancel();
			}

			// Remove all the requests from the queue.
			mCurrentRequests.clear();
		}
	}

	/**
	 * Cancel a particular download in progress. Returns 1 if the download Id is found else returns 0.
	 * 
	 * @param downloadId
	 * @return int
	 */
	int cancel(int downloadId) {
		synchronized (mCurrentRequests) {
			for (DownloadRequest request : mCurrentRequests) {
				if (request.getDownloadId() == downloadId) {
					request.cancel();
					return 1;
				}
			}
		}

		return 0;
	}

	void finish(DownloadRequest request) {
		if (mCurrentRequests != null) {//if finish and release are called together it throws NPE
			// Remove from the queue.
			synchronized (mCurrentRequests) {
				mCurrentRequests.remove(request);
			}
		}
	}

	/**
	 * Cancels all the pending & running requests and releases all the dispatchers.
	 */
	void release() {
		if (mCurrentRequests != null) {
			synchronized (mCurrentRequests) {
				mCurrentRequests.clear();
				mCurrentRequests = null;
			}
		}

		if (mDownloadQueue != null) {
			mDownloadQueue = null;
		}

		if (mDownloadDispatchers != null) {
			stop();
			
			for (int i = 0; i < mDownloadDispatchers.length; i++) {
				mDownloadDispatchers[i] = null;
			}
			mDownloadDispatchers = null;
		}

	}

	// Private methods.

	/**
	 * Stops download dispatchers.
	 */
	private void stop() {
		for (int i = 0; i < mDownloadDispatchers.length; i++) {
			if (mDownloadDispatchers[i] != null) {
				mDownloadDispatchers[i].quit();
			}
		}
	}

	/**
	 * Gets a sequence number.
	 */
	private int getDownloadId() {
		return mSequenceGenerator.incrementAndGet();
	}
}
