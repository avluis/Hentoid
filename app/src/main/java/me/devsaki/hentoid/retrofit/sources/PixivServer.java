package me.devsaki.hentoid.retrofit.sources;

import com.squareup.moshi.Moshi;
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter;

import java.util.Date;

import me.devsaki.hentoid.json.sources.PixivGalleryMetadata;
import me.devsaki.hentoid.json.sources.PixivIllustMetadata;
import me.devsaki.hentoid.json.sources.PixivSeriesContentMetadata;
import me.devsaki.hentoid.json.sources.PixivSeriesMetadata;
import me.devsaki.hentoid.util.network.OkHttpClientSingleton;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

public class PixivServer {

    private static final String API_URL = "https://www.pixiv.net/";

    private static final Moshi moshi = new Moshi.Builder()
            .add(Date.class, new Rfc3339DateJsonAdapter())
            .build();

    public static final Api API = new Retrofit.Builder()
            .baseUrl(API_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("touch/ajax/illust/details")
        Call<PixivIllustMetadata> getIllustMetadata(@Query("illust_id") String id, @Header("cookie") String cookies);

        @GET("ajax/illust/{id}/pages")
        Call<PixivGalleryMetadata> getIllustPages(@Query("id") String id, @Header("cookie") String cookies);

        @GET("touch/ajax/illust/series/{id}")
        Call<PixivSeriesMetadata> getSeriesMetadata(@Path("id") String id, @Header("cookie") String cookies);

        @GET("touch/ajax/illust/series_content/{id}")
        Call<PixivSeriesContentMetadata> getSeriesIllust(@Path("id") String id, @Query("limit") int limit, @Query("last_order") int lastorder, @Header("cookie") String cookies);
    }
}
