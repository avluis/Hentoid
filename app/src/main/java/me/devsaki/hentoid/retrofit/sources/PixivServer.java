package me.devsaki.hentoid.retrofit.sources;

import com.squareup.moshi.Moshi;
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter;

import java.util.Date;

import me.devsaki.hentoid.json.sources.PixivIllustMetadata;
import me.devsaki.hentoid.util.network.OkHttpClientSingleton;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public class PixivServer {

    private static final String API_URL = "https://www.pixiv.net/touch/ajax/";

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

        @GET("illust/details")
        Call<PixivIllustMetadata> getIllustMetadata(@Query("illust_id") String id, @Header("cookie") String cookies);
    }
}
