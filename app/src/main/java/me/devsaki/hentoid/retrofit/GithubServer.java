package me.devsaki.hentoid.retrofit;

import java.util.List;

import io.reactivex.Single;
import me.devsaki.hentoid.util.OkHttpClientSingleton;
import me.devsaki.hentoid.viewholders.GitHubRelease;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;

public class GithubServer {

    private static final String GITHUB_BASE_URL = "https://api.github.com/repos/avluis/Hentoid/";

    public static final Api API = new Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("releases")
        Single<List<GitHubRelease.Struct>> getReleases();

        @GET("releases/latest")
        Single<GitHubRelease.Struct> getLatestRelease();
    }
}
