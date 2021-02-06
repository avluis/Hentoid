package me.devsaki.hentoid.retrofit.sources;

import io.reactivex.Single;
import me.devsaki.hentoid.json.sources.EHentaiGalleriesMetadata;
import me.devsaki.hentoid.json.sources.EHentaiGalleryQuery;
import me.devsaki.hentoid.json.sources.EHentaiImageQuery;
import me.devsaki.hentoid.json.sources.EHentaiImageResponse;
import me.devsaki.hentoid.util.network.OkHttpClientSingleton;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public class EHentaiServer {

    private static final String EHENTAI_URL = "http://e-hentai.org/";
    private static final String EXHENTAI_URL = "https://exhentai.org/";

    public static final EHentaiServer.Api EHENTAI_API = new Retrofit.Builder()
            .baseUrl(EHENTAI_URL)
            .client(OkHttpClientSingleton.getInstance())
//            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(EHentaiServer.Api.class);

    public static final EHentaiServer.Api EXHENTAI_API = new Retrofit.Builder()
            .baseUrl(EXHENTAI_URL)
            .client(OkHttpClientSingleton.getInstance())
  //          .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(EHentaiServer.Api.class);

    public interface Api {
/*
        @POST("api.php")
        Single<EHentaiGalleriesMetadata> getGalleryMetadata(@Body EHentaiGalleryQuery query, @Header("cookie") String cookies);

        @POST("api.php")
        Single<EHentaiImageResponse> getImageMetadata(@Body EHentaiImageQuery query, @Header("cookie") String cookies);
 */

        @POST("api.php")
        Call<EHentaiGalleriesMetadata> getGalleryMetadata(@Body EHentaiGalleryQuery query, @Header("cookie") String cookies);

        @POST("api.php")
        Call<EHentaiImageResponse> getImageMetadata(@Body EHentaiImageQuery query, @Header("cookie") String cookies);
    }
}
