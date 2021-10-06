package me.devsaki.hentoid.retrofit.sources;

import me.devsaki.hentoid.json.sources.PixivIllustMetadata;
import me.devsaki.hentoid.json.sources.PixivIllustPagesMetadata;
import me.devsaki.hentoid.json.sources.PixivSeriesIllustMetadata;
import me.devsaki.hentoid.json.sources.PixivSeriesMetadata;
import me.devsaki.hentoid.json.sources.PixivUserIllustMetadata;
import me.devsaki.hentoid.json.sources.PixivUserMetadata;
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

    public static final Api API = new Retrofit.Builder()
            .baseUrl(API_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addConverterFactory(MoshiConverterFactory.create().asLenient())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("touch/ajax/illust/details")
        Call<PixivIllustMetadata> getIllustMetadata(@Query("illust_id") String id, @Header("cookie") String cookies);

        @GET("ajax/illust/{id}/pages")
        Call<PixivIllustPagesMetadata> getIllustPages(@Query("id") String id, @Header("cookie") String cookies);

        @GET("touch/ajax/illust/series/{id}")
        Call<PixivSeriesMetadata> getSeriesMetadata(@Path("id") String id, @Header("cookie") String cookies);

        @GET("touch/ajax/illust/series_content/{id}")
        Call<PixivSeriesIllustMetadata> getSeriesIllusts(@Path("id") String id, @Query("limit") int limit, @Query("last_order") int lastorder, @Header("cookie") String cookies);

        @GET("touch/ajax/illust/user_illusts")
        Call<PixivUserIllustMetadata> getUserIllusts(@Query("user_id") String id, @Header("cookie") String cookies);

        @GET("touch/ajax/user/details")
        Call<PixivUserMetadata> getUserMetadata(@Query("id") String id, @Header("cookie") String cookies);
    }
}
