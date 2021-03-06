package me.devsaki.hentoid.retrofit.sources;

import java.util.Map;

import me.devsaki.hentoid.json.sources.LusciousBookMetadata;
import me.devsaki.hentoid.json.sources.LusciousGalleryMetadata;
import me.devsaki.hentoid.util.network.OkHttpClientSingleton;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.QueryMap;

public class LusciousServer {

    private static final String API_URL = "https://members.luscious.net/";

    public static final Api API = new Retrofit.Builder()
            .baseUrl(API_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addConverterFactory(MoshiConverterFactory.create().asLenient())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("graphql/nobatch/")
        Call<LusciousBookMetadata> getBookMetadata(@QueryMap Map<String, String> options);

        @GET("graphql/nobatch/")
        Call<LusciousGalleryMetadata> getGalleryMetadata(@QueryMap Map<String, String> options);
    }
}
