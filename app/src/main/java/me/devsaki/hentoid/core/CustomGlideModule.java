package me.devsaki.hentoid.core;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.engine.executor.GlideExecutor;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;

import me.devsaki.hentoid.util.network.OkHttpClientSingleton;
import okhttp3.OkHttpClient;

/**
 * Startup class to enable Glide over OkHttp and limit Glide parallel threads
 */
@GlideModule
public final class CustomGlideModule extends AppGlideModule {

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        OkHttpClient client = OkHttpClientSingleton.getInstance();

        OkHttpUrlLoader.Factory factory = new OkHttpUrlLoader.Factory(client);

        registry.replace(GlideUrl.class, InputStream.class, factory);
    }

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder                                // customize builder
                .setLogLevel(Log.ERROR)
                .setSourceExecutor(                // source executor specify the thread it uses to load the image from remote
                        GlideExecutor
                                .newSourceBuilder()
                                .setThreadCount(4)         // use the builder to set a desired thread amount
                                .build()
                );
    }
}