package me.devsaki.hentoid.retrofit.sources;

import io.reactivex.Single;
import me.devsaki.hentoid.json.sources.EHentaiGalleryQuery;
import me.devsaki.hentoid.json.sources.ExHentaiGalleriesMetadata;
import me.devsaki.hentoid.util.OkHttpClientSingleton;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public class ExHentaiServer {

    private static final String API_URL = "https://exhentai.org/";

    public static final Api API = new Retrofit.Builder()
            .baseUrl(API_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @POST("api.php")
        Single<ExHentaiGalleriesMetadata> getGalleryMetadata(@Body EHentaiGalleryQuery query, @Header("cookie") String cookies);
    }
}
