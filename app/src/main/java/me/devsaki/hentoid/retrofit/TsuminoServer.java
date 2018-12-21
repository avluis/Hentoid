package me.devsaki.hentoid.retrofit;

import io.reactivex.Single;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.TsuminoContent;
import me.devsaki.hentoid.util.OkHttpClientSingleton;
import pl.droidsonroids.retrofit2.JspoonConverterFactory;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

public class TsuminoServer {

    public static final Api API = new Retrofit.Builder()
            .baseUrl(Site.TSUMINO.getUrl())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(JspoonConverterFactory.create())
            .client(OkHttpClientSingleton.getInstance())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("/Book/Info/{id1}")
        Single<TsuminoContent> getGalleryMetadata(@Path("id1") String contentId1);
    }
}
