package me.devsaki.hentoid.retrofit;

import com.squareup.moshi.Moshi;
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter;

import java.util.Date;
import java.util.List;

import io.reactivex.Single;
import me.devsaki.hentoid.util.OkHttpClientSingleton;
import me.devsaki.hentoid.viewholders.GitHubReleaseItem;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.GET;

public class GithubServer {

    private static final String GITHUB_BASE_URL = "https://api.github.com/repos/avluis/Hentoid/";

    private static final Moshi moshi = new Moshi.Builder()
            .add(Date.class, new Rfc3339DateJsonAdapter())
            .build();

    public static final Api API = new Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(Api.class);

    public interface Api {

        @GET("releases")
        Single<List<GitHubReleaseItem.Struct>> getReleases();

        @GET("releases/latest")
        Single<GitHubReleaseItem.Struct> getLatestRelease();
    }
}
