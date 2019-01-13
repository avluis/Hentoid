package me.devsaki.hentoid.retrofit;

import io.reactivex.Single;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.NhentaiContent;
import me.devsaki.hentoid.util.OkHttpClientSingleton;
import pl.droidsonroids.retrofit2.JspoonConverterFactory;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

// NHentai API reference : https://github.com/NHMoeDev/NHentai-android/issues/27
public class NhentaiServer {

    public static final Api API = new Retrofit.Builder()
            .baseUrl(Site.NHENTAI.getUrl())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(JspoonConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("/g/{id}/")
        Single<NhentaiContent> getGalleryMetadata(@Path("id") String contentId);
    }
}
