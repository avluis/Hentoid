package me.devsaki.hentoid.util.image

import android.content.Context
import android.view.View
import android.widget.ImageView
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.target
import com.github.penfeizhou.animation.apng.APNGDrawable
import com.github.penfeizhou.animation.io.ByteBufferReader
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.util.getContentHeaders
import okio.BufferedSource
import timber.log.Timber
import java.io.InputStream
import java.nio.ByteBuffer


fun clearCoilCache(context: Context, memory: Boolean = true, file: Boolean = true) {
    val imageLoader = context.imageLoader
    if (memory) imageLoader.memoryCache?.clear()
    if (file) imageLoader.diskCache?.clear()
}

fun ImageView.loadCover(content: Content) {
    // TODO  If animated, only load frame zero as a plain bitmap
    val thumbLocation = content.cover.usableUri
    if (thumbLocation.isEmpty()) {
        this.visibility = View.INVISIBLE
        return
    }
    this.visibility = View.VISIBLE

    // Use content's cookies to load image (useful for ExHentai when viewing queue screen)
    val networkHeaders = if (thumbLocation.startsWith("http")) {
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
        .target(this)
        .httpHeaders(networkHeaders)

    SingletonImageLoader.get(this.context).enqueue(request.build())
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
            Timber.i("what do we have here?")
            val stream = result.source.source().peek().inputStream()
            return if (isApng(stream)) {
                Timber.i("we got an APNG")
                AnimatedPngDecoder(result.source)
            } else {
                null
            }
        }
    }
}