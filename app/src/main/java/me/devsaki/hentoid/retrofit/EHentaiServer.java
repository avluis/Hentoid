package me.devsaki.hentoid.retrofit;

import java.io.IOException;
import java.util.Map;

import io.reactivex.Single;
import me.devsaki.hentoid.collection.mikan.MikanAttributeResponse;
import me.devsaki.hentoid.collection.mikan.MikanContentResponse;
import me.devsaki.hentoid.parsers.EHentai.EHentaiGalleriesMetadata;
import me.devsaki.hentoid.parsers.EHentai.EHentaiGalleryMetadata;
import me.devsaki.hentoid.parsers.EHentai.EHentaiGalleryQuery;
import me.devsaki.hentoid.util.Consts;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;

import static java.util.concurrent.TimeUnit.SECONDS;

public class EHentaiServer {

    private final static String API_URL = "http://e-hentai.org/";

    public static final Api API = new Retrofit.Builder()
            .baseUrl(API_URL)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @POST("api.php")
        Single<EHentaiGalleriesMetadata> getGalleryMetadata(@Body EHentaiGalleryQuery query);
    }
}
