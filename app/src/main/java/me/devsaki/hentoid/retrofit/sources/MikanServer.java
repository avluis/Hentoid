package me.devsaki.hentoid.retrofit.sources;

import java.util.Map;

import io.reactivex.Single;
import me.devsaki.hentoid.collection.mikan.MikanAttributeResponse;
import me.devsaki.hentoid.collection.mikan.MikanContentResponse;
import me.devsaki.hentoid.util.OkHttpClientSingleton;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;
import retrofit2.http.Url;

public class MikanServer {

    private static final String MIKAN_BASE_URL = "https://api.initiate.host/v1/";

    public static final Api API = new Retrofit.Builder()
            .baseUrl(MIKAN_BASE_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("{source}")
        Single<MikanContentResponse> getRecent(@Path("source") String source, @QueryMap Map<String, String> options);

        @GET("{source}/{id}/pages")
        Single<MikanContentResponse> getPages(@Path("source") String source, @Path("id") String contentId);

        @GET
        Single<MikanContentResponse> search(@Url String url, @QueryMap Map<String, String> options);

        // Forced HITOMI until the endpoint moves to root URL
        @GET("hitomi.la/info/{endpoint}")
        Single<Response<MikanAttributeResponse>> getMasterData(@Path("endpoint") String endpoint);
    }
}
