package me.devsaki.hentoid.retrofit;

import io.reactivex.Single;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.FakkuContent;
import me.devsaki.hentoid.util.OkHttpClientSingleton;
import pl.droidsonroids.retrofit2.JspoonConverterFactory;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

public class FakkuServer {

    public static final Api API = new Retrofit.Builder()
            .baseUrl(Site.FAKKU2.getUrl())
            .client(OkHttpClientSingleton.getInstance())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(JspoonConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("/hentai/{id1}")
        Single<FakkuContent> getGalleryMetadata(@Path("id1") String contentId1, @Header("cookie") String cookie);
    }
}
