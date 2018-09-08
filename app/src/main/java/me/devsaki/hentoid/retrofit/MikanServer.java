package me.devsaki.hentoid.retrofit;

import java.util.Map;

import io.reactivex.Single;
import me.devsaki.hentoid.collection.mikan.MikanAttributeResponse;
import me.devsaki.hentoid.collection.mikan.MikanContentResponse;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;

public class MikanServer {

    private final static String MIKAN_BASE_URL = "https://api.initiate.host/v1/";

    public static final Api API = new Retrofit.Builder()
            .baseUrl(MIKAN_BASE_URL)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @Headers({
                "Data-type: application/json"
        })
        @GET("{source}")
        Single<MikanContentResponse> getRecent(@Path("source") String source, @QueryMap Map<String, String> options, @Header("User-Agent") String userAgent);

        @Headers({
                "Data-type: application/json"
        })
        @GET("{source}/{id}/pages")
        Single<MikanContentResponse> getPages(@Path("source") String source, @Path("id") String contentId, @Header("User-Agent") String userAgent);

        @Headers({
                "Data-type: application/json"
        })
        @GET("{source}{suffix}")
        Single<MikanContentResponse> search(@Path("source") String source, @Path("suffix") String suffix, @QueryMap Map<String, String> options, @Header("User-Agent") String userAgent);

        @Headers({
                "Data-type: application/json"
        })
        @GET("hitomi.la/info/{endpoint}") // Forced HITOMI until the endpoint moves to root URL
        Single<Response<MikanAttributeResponse>> getMasterData(@Path("endpoint") String endpoint, @Header("User-Agent") String userAgent);
    }
}
