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
import com.github.penfeizhou.animation.apng.decode.APNGParser
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.util.getContentHeaders


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
        return DecodeResult(
            image = APNGDrawable.fromFile(source.file().toString()).asImage(),
            isSampled = false
        )
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            return if (APNGParser.isAPNG(result.source.file().toString())) {
                AnimatedPngDecoder(result.source)
            } else {
                null
            }
        }
    }
}