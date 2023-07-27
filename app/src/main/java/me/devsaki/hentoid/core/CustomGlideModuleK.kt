package me.devsaki.hentoid.core

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.executor.GlideExecutor
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import me.devsaki.hentoid.util.network.OkHttpClientSingleton
import java.io.InputStream

/**
 * Startup class to enable Glide over OkHttp and limit Glide parallel threads
 */
@GlideModule
class CustomGlideModuleK : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val client = OkHttpClientSingleton.getInstance()
        val factory = OkHttpUrlLoader.Factory(client)

        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java, factory
        )
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder // customize builder
            .setLogLevel(Log.ERROR)
            .setSourceExecutor( // source executor specify the thread it uses to load the image from remote
                GlideExecutor
                    .newSourceBuilder()
                    .setThreadCount(4) // use the builder to set a desired thread amount
                    .build()
            )
    }
}