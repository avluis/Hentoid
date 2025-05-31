package me.devsaki.hentoid.customssiv.decoder

import android.app.ActivityManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ColorSpace
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import androidx.annotation.Keep
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * <p>
 * An implementation of {@link ImageRegionDecoder} using a pool of {@link BitmapRegionDecoder}s,
 * to provide true parallel loading of tiles. This is only effective if parallel loading has been
 * enabled in the view by calling me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView.setExecutor(Executor)
 * with a multi-threaded {@link Executor} instance.
 * </p><p>
 * One decoder is initialised when the class is initialised. This is enough to decode base layer tiles.
 * Additional decoders are initialised when a subregion of the image is first requested, which indicates
 * interaction with the view. Creation of additional encoders stops when {@link #allowAdditionalDecoder(int, long)}
 * returns false. The default implementation takes into account the file size, number of CPU cores,
 * low memory status and a hard limit of 4. Extend this class to customise this.
 * </p><p>
 * <b>WARNING:</b> This class is highly experimental and not proven to be stable on a wide range of
 * devices. You are advised to test it thoroughly on all available devices, and code your app to use
 * {@link SkiaImageRegionDecoder} on old or low powered devices you could not test.
 * </p>
 */
private const val FILE_PREFIX = "file://"
private const val ASSET_PREFIX = "$FILE_PREFIX/android_asset/"
private const val RESOURCE_PREFIX = ContentResolver.SCHEME_ANDROID_RESOURCE + "://"

internal class SkiaPooledImageRegionDecoder(private val bitmapConfig: Bitmap.Config) : ImageRegionDecoder {
    private var debug: Boolean = false

    private var decoderPool: DecoderPool? = DecoderPool()
    private val decoderLock: ReadWriteLock = ReentrantReadWriteLock(true)

    private var context: Context? = null
    private var uri: Uri? = null

    private var fileLength = Long.MAX_VALUE
    private val imageDimensions = Point(0, 0)
    private val lazyInited = AtomicBoolean(false)

    /**
     * Controls logging of debug messages. All instances are affected.
     *
     * @param debug true to enable debug logging, false to disable.
     */
    @Keep
    @Suppress("unused")
    fun setDebug(debug: Boolean) {
        this.debug = debug
    }

    /**
     * Initialises the decoder pool. This method creates one decoder on the current thread and uses
     * it to decode the bounds, then spawns an independent thread to populate the pool with an
     * additional three decoders. The thread will abort if [.recycle] is called.
     */
    @Throws(Exception::class)
    override fun init(context: Context, uri: Uri): Point {
        this.context = context
        this.uri = uri
        initialiseDecoder()
        return this.imageDimensions
    }

    /**
     * Initialises extra decoders for as long as [.allowAdditionalDecoder] returns
     * true and the pool has not been recycled.
     */
    private fun lazyInit() {
        if (lazyInited.compareAndSet(false, true) && fileLength < Long.MAX_VALUE) {
            debug("Starting lazy init of additional decoders")
            val thread: Thread = object : Thread() {
                override fun run() {
                    while (decoderPool != null && allowAdditionalDecoder(
                            decoderPool!!.size(),
                            fileLength
                        )
                    ) {
                        // New decoders can be created while reading tiles but this read lock prevents
                        // them being initialised while the pool is being recycled.
                        try {
                            if (decoderPool != null) {
                                val start = System.currentTimeMillis()
                                debug("Starting decoder")
                                initialiseDecoder()
                                val end = System.currentTimeMillis()
                                debug("Started decoder, took " + (end - start) + "ms")
                            }
                        } catch (e: Exception) {
                            // A decoder has already been successfully created so we can ignore this
                            debug("Failed to start decoder: " + e.message)
                        }
                    }
                }
            }
            thread.start()
        }
    }

    /**
     * Initialises a new [BitmapRegionDecoder] and adds it to the pool, unless the pool has
     * been recycled while it was created.
     */
    @Throws(IOException::class, PackageManager.NameNotFoundException::class)
    private fun initialiseDecoder() {
        val uriString = uri.toString()
        var decoder: BitmapRegionDecoder?
        var localFileLength = Long.MAX_VALUE
        if (uriString.startsWith(RESOURCE_PREFIX)) {
            val id = getResourceId(context!!, uri!!)
            try {
                context!!.resources.openRawResourceFd(id).use { descriptor ->
                    localFileLength = descriptor.length
                }
            } catch (e: Exception) {
                // Pooling disabled
            }
            decoder =
                BitmapRegionDecoder.newInstance(context!!.resources.openRawResource(id), false)
        } else if (uriString.startsWith(ASSET_PREFIX)) {
            val assetName = uriString.substring(ASSET_PREFIX.length)
            try {
                context!!.assets.openFd(assetName).use { descriptor ->
                    localFileLength = descriptor.length
                }
            } catch (e: Exception) {
                // Pooling disabled
            }
            decoder = BitmapRegionDecoder.newInstance(
                context!!.assets.open(
                    assetName,
                    AssetManager.ACCESS_RANDOM
                ), false
            )
        } else if (uriString.startsWith(FILE_PREFIX)) {
            decoder =
                BitmapRegionDecoder.newInstance(uriString.substring(FILE_PREFIX.length), false)
            try {
                val file = File(uriString)
                if (file.exists()) {
                    localFileLength = file.length()
                }
            } catch (e: Exception) {
                // Pooling disabled
            }
        } else {
            val contentResolver = context!!.contentResolver
            contentResolver.openInputStream(uri!!).use { input ->
                if (input == null) throw RuntimeException("Content resolver returned null stream. Unable to initialise with uri.")
                decoder = BitmapRegionDecoder.newInstance(input, false)
                try {
                    contentResolver.openAssetFileDescriptor(uri!!, "r").use { descriptor ->
                        if (descriptor != null) {
                            localFileLength = descriptor.length
                        }
                    }
                } catch (e: Exception) {
                    // Stick with MAX_LENGTH
                }
            }
        }

        this.fileLength = localFileLength
        imageDimensions[decoder!!.width] = decoder!!.height
        decoderLock.writeLock().lock()
        try {
            if (decoderPool != null) {
                decoderPool!!.add(decoder)
            }
        } finally {
            decoderLock.writeLock().unlock()
        }
    }

    /**
     * Acquire a read lock to prevent decoding overlapping with recycling, then check the pool still
     * exists and acquire a decoder to load the requested region. There is no check whether the pool
     * currently has decoders, because it's guaranteed to have one decoder after [.init]
     * is called and be null once [.recycle] is called. In practice the view can't call this
     * method until after [.init], so there will be no blocking on an empty pool.
     */
    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
        debug("Decode region " + sRect + " on thread " + Thread.currentThread().name)
        if (sRect.width() < imageDimensions.x || sRect.height() < imageDimensions.y) {
            lazyInit()
        }
        decoderLock.readLock().lock()
        try {
            if (decoderPool != null) {
                val decoder = decoderPool!!.acquire()
                try {
                    // Decoder can't be null or recycled in practice
                    if (decoder != null && !decoder.isRecycled) {
                        val options = BitmapFactory.Options()
                        options.inSampleSize = sampleSize
                        options.inPreferredConfig = bitmapConfig
                        // If that is not set, some PNGs are read with a ColorSpace of code "Unknown" (-1),
                        // which makes resizing buggy (generates a black picture)
                        options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)

                        val bitmap = decoder.decodeRegion(sRect, options)
                            ?: throw RuntimeException("Skia image decoder returned null bitmap - image format may not be supported")
                        return bitmap
                    }
                } finally {
                    if (decoder != null) {
                        decoderPool!!.release(decoder)
                    }
                }
            }
            throw IllegalStateException("Cannot decode region after decoder has been recycled")
        } finally {
            decoderLock.readLock().unlock()
        }
    }

    /**
     * Holding a read lock to avoid returning true while the pool is being recycled, this returns
     * true if the pool has at least one decoder available.
     */
    @Synchronized
    override fun isReady(): Boolean {
        return !(decoderPool?.isEmpty ?: true)
    }

    /**
     * Wait until all read locks held by [.decodeRegion] are released, then recycle
     * and destroy the pool. Elsewhere, when a read lock is acquired, we must check the pool is not null.
     */
    @Synchronized
    override fun recycle() {
        decoderLock.writeLock().lock()
        try {
            if (decoderPool != null) {
                decoderPool!!.recycle()
                decoderPool = null
                context = null
                uri = null
            }
        } finally {
            decoderLock.writeLock().unlock()
        }
    }

    /**
     * Called before creating a new decoder. Based on number of CPU cores, available memory, and the
     * size of the image file, determines whether another decoder can be created. Subclasses can
     * override and customise this.
     *
     * @param numberOfDecoders the number of decoders that have been created so far
     * @param fileLength       the size of the image file in bytes. Creating another decoder will use approximately this much native memory.
     * @return true if another decoder can be created.
     */
    protected fun allowAdditionalDecoder(numberOfDecoders: Int, fileLength: Long): Boolean {
        if (numberOfDecoders >= 4) {
            debug("No additional decoders allowed, reached hard limit (4)")
            return false
        } else if (numberOfDecoders * fileLength > 20 * 1024 * 1024) {
            debug("No additional encoders allowed, reached hard memory limit (20Mb)")
            return false
        } else if (numberOfDecoders >= getNumberOfCores()) {
            debug("No additional encoders allowed, limited by CPU cores (" + getNumberOfCores() + ")")
            return false
        } else if (isLowMemory()) {
            debug("No additional encoders allowed, memory is low")
            return false
        }
        debug("Additional decoder allowed, current count is " + numberOfDecoders + ", estimated native memory " + ((fileLength * numberOfDecoders) / (1024 * 1024)) + "Mb")
        return true
    }


    /**
     * A simple pool of [BitmapRegionDecoder] instances, all loading from the same source.
     */
    private class DecoderPool {
        private val available = Semaphore(0, true)
        private val decoders: MutableMap<BitmapRegionDecoder?, Boolean> = ConcurrentHashMap()

        @get:Synchronized
        val isEmpty: Boolean
            /**
             * Returns false if there is at least one decoder in the pool.
             */
            get() = decoders.isEmpty()

        /**
         * Returns number of encoders.
         */
        @Synchronized
        fun size(): Int {
            return decoders.size
        }

        /**
         * Acquire a decoder. Blocks until one is available.
         */
        fun acquire(): BitmapRegionDecoder? {
            available.acquireUninterruptibly()
            return nextAvailable
        }

        /**
         * Release a decoder back to the pool.
         */
        fun release(decoder: BitmapRegionDecoder) {
            if (markAsUnused(decoder)) {
                available.release()
            }
        }

        /**
         * Adds a newly created decoder to the pool, releasing an additional permit.
         */
        @Synchronized
        fun add(decoder: BitmapRegionDecoder?) {
            decoders[decoder] = false
            available.release()
        }

        /**
         * While there are decoders in the map, wait until each is available before acquiring,
         * recycling and removing it. After this is called, any call to [.acquire] will
         * block forever, so this call should happen within a write lock, and all calls to
         * [.acquire] should be made within a read lock so they cannot end up blocking on
         * the semaphore when it has no permits.
         */
        @Synchronized
        fun recycle() {
            while (decoders.isNotEmpty()) {
                val decoder = acquire()
                if (decoder != null) {
                    decoder.recycle()
                    decoders.remove(decoder)
                }
            }
        }

        @get:Synchronized
        private val nextAvailable: BitmapRegionDecoder?
            get() {
                for (entry in decoders.entries) {
                    if (!entry.value) {
                        entry.setValue(true)
                        return entry.key
                    }
                }
                return null
            }

        @Synchronized
        private fun markAsUnused(decoder: BitmapRegionDecoder): Boolean {
            for (entry in decoders.entries) {
                if (decoder == entry.key) {
                    if (entry.value) {
                        entry.setValue(false)
                        return true
                    } else {
                        return false
                    }
                }
            }
            return false
        }
    }

    private fun getNumberOfCores(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    private fun isLowMemory(): Boolean {
        val activityManager =
            context!!.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.lowMemory
    }

    private fun debug(message: String) {
        if (debug) Timber.d(message)
    }
}