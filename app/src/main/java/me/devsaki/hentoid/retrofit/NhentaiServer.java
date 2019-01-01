package me.devsaki.hentoid.retrofit;

import io.reactivex.Single;
import me.devsaki.hentoid.parsers.content.NhentaiContent;
import me.devsaki.hentoid.util.OkHttpClientSingleton;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

// NHentai API reference : https://github.com/NHMoeDev/NHentai-android/issues/27
public class NhentaiServer {

    private final static String API_URL = "https://nhentai.net/api/";

    public static final Api API = new Retrofit.Builder()
            .baseUrl(API_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("gallery/{id}")
        Single<NhentaiContent> getGalleryMetadata(@Path("id") String contentId);
    }
}
