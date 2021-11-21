package me.devsaki.hentoid.retrofit;

import io.reactivex.Single;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.json.UpdateInfo;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.GET;

public class UpdateServer {

    /*
    public static final Api API = new Retrofit.Builder()
            .baseUrl(BuildConfig.UPDATE_URL)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(MoshiConverterFactory.create().asLenient())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("update.json")
        Single<UpdateInfo> getUpdateInfo();
    }
    */

}
