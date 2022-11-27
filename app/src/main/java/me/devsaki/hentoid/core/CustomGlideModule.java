package me.devsaki.hentoid.core;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;

import me.devsaki.hentoid.util.network.OkHttpClientSingleton;
import okhttp3.OkHttpClient;

/**
 * Startup class to enable Glide over OkHttp
 */
@GlideModule
public final class CustomGlideModule extends AppGlideModule {

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        OkHttpClient client = OkHttpClientSingleton.getInstance();

        OkHttpUrlLoader.Factory factory = new OkHttpUrlLoader.Factory(client);

        registry.replace(GlideUrl.class, InputStream.class, factory);
    }
}