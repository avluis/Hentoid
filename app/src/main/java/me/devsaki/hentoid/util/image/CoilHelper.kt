package me.devsaki.hentoid.util.image

import android.content.Context
import android.graphics.Point
import android.view.View
import android.widget.ImageView
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.target
import coil3.serviceLoaderEnabled
import com.awxkee.jxlcoder.coil.JxlDecoder
import com.github.penfeizhou.animation.apng.APNGDrawable
import com.github.penfeizhou.animation.io.ByteBufferReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.util.getContentHeaders
import okio.BufferedSource
import okio.Path.Companion.toOkioPath
import java.io.InputStream
import java.nio.ByteBuffer

private val stillImageLoader: ImageLoader by lazy { initStillImageLoader() }

suspend fun clearCoilCache(context: Context, memory: Boolean = true, file: Boolean = true) {
    withContext(Dispatchers.IO) {
        context.imageLoader.apply {
            if (memory) memoryCache?.clear()
            if (file) diskCache?.clear()
        }
        stillImageLoader.apply {
            if (memory) memoryCache?.clear()
            if (file) diskCache?.clear()
        }
    }
}

private fun initStillImageLoader(): ImageLoader {
    HentoidApp.getInstance().let { context ->
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.10)
                    .build()
            }
            .components {
                add(JxlDecoder.Factory())
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(50 * 1024 * 1024) // 50 MB
                    .build()
            }
            .serviceLoaderEnabled(false)
            .build()
    }
}

fun ImageView.loadStill(data: String) {
    val request = ImageRequest.Builder(context)
        .data(data)
        .memoryCacheKey(data)
        .diskCacheKey(data)
        .target(this)

    stillImageLoader.enqueue(request.build())
}

fun ImageView.loadCover(content: Content, disableAnimation: Boolean = false) {
    val thumbLocation = content.cover.usableUri
    if (thumbLocation.isEmpty()) {
        this.visibility = View.INVISIBLE
        return
    }
    this.visibility = View.VISIBLE

    // Use content's cookies to load image (useful for ExHentai when viewing queue screen)
    val isOnline = thumbLocation.startsWith("http")
    val networkHeaders = if (isOnline) {
        val headers = NetworkHeaders.Builder()
        getContentHeaders(content).forEach {
            headers.add(it.first, it.second)
        }
        headers.build()
    } else {
        NetworkHeaders.EMPTY
    }

    val request = ImageRequest.Builder(context)
        .data(thumbLocation)
        .memoryCacheKey(thumbLocation)
        .diskCacheKey(thumbLocation)
        .target(this)
        .httpHeaders(networkHeaders)

    // TODO https://github.com/coil-kt/coil/issues/2629
    val loader = if (disableAnimation && !isOnline) stillImageLoader
    else SingletonImageLoader.get(this.context)
    loader.enqueue(request.build())
}

suspend fun getDimensions(context: Context, data: String): Point = withContext(Dispatchers.IO) {
    val request = ImageRequest.Builder(context)
        .data(data)
        .memoryCacheKey(data)
        .diskCacheKey(data)

    val result = stillImageLoader.execute(request.build())
    result.image?.let { img ->
        return@withContext Point(img.width, img.height)
    }
    return@withContext Point(0, 0)
}

class AnimatedPngDecoder(private val source: ImageSource) : Decoder {

    override suspend fun decode(): DecodeResult {
        // We must buffer the source into memory as APNGDrawable decodes
        // the image lazily at draw time which is prohibited by Coil.
        val buffer = source.source().squashToDirectByteBuffer()
        return DecodeResult(
            image = APNGDrawable { ByteBufferReader(buffer) }.asImage(),
            isSampled = false,
        )
    }

    private fun BufferedSource.squashToDirectByteBuffer(): ByteBuffer {
        // Squash bytes to BufferedSource inner buffer then we know total byteCount.
        request(Long.MAX_VALUE)

        val byteBuffer = ByteBuffer.allocateDirect(buffer.size.toInt())
        while (!buffer.exhausted()) buffer.read(byteBuffer)
        byteBuffer.flip()
        return byteBuffer
    }

    class Factory : Decoder.Factory {

        private fun isApng(input: InputStream): Boolean {
            val data = ByteArray(1000)
            input.read(data, 0, 1000)
            return getMimeTypeFromPictureBinary(data, 1000) == MIME_IMAGE_APNG
        }

        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            val stream = result.source.source().peek().inputStream()
            return if (isApng(stream)) {
                AnimatedPngDecoder(result.source)
            } else {
                null
            }
        }
    }
}