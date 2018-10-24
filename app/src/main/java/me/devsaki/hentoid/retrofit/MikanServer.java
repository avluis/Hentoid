package me.devsaki.hentoid.retrofit;

import java.io.IOException;
import java.util.Map;

import io.reactivex.Single;
import me.devsaki.hentoid.collection.mikan.MikanAttributeResponse;
import me.devsaki.hentoid.collection.mikan.MikanContentResponse;
import me.devsaki.hentoid.util.Consts;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;

import static java.util.concurrent.TimeUnit.SECONDS;

public class MikanServer {

    private final static String MIKAN_BASE_URL = "https://api.initiate.host/v1/";
    
    private final static int TIMEOUT_S = 20;

    public static final Api API = new Retrofit.Builder()
            .baseUrl(MIKAN_BASE_URL)
            .client(getClient())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api.class);

    private static OkHttpClient getClient() {
        return new OkHttpClient.Builder()
                .addInterceptor(MikanServer::onIntercept)
                .connectTimeout(TIMEOUT_S, SECONDS)
                .readTimeout(TIMEOUT_S, SECONDS)
                .writeTimeout(TIMEOUT_S, SECONDS)
                .build();
    }

    private static okhttp3.Response onIntercept(Interceptor.Chain chain) throws IOException {
        Request request = chain.request()
                .newBuilder()
                .header("User-Agent", Consts.USER_AGENT)
                .header("Data-type", "application/json")
                .build();
        return chain.proceed(request);
    }

    public interface Api {

        @GET("{source}")
        Single<MikanContentResponse> getRecent(@Path("source") String source, @QueryMap Map<String, String> options);

        @GET("{source}/{id}/pages")
        Single<MikanContentResponse> getPages(@Path("source") String source, @Path("id") String contentId);

        @GET("{source}{suffix}")
        Single<MikanContentResponse> search(@Path("source") String source, @Path("suffix") String suffix, @QueryMap Map<String, String> options);

        // Forced HITOMI until the endpoint moves to root URL
        @GET("hitomi.la/info/{endpoint}")
        Single<Response<MikanAttributeResponse>> getMasterData(@Path("endpoint") String endpoint);
    }
}
