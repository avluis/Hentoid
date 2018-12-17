package me.devsaki.hentoid.retrofit;

import io.reactivex.Single;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ASMHentai.ASMContent;
import pl.droidsonroids.retrofit2.JspoonConverterFactory;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

public class ASMHentaiServer {

    public static final Api API = new Retrofit.Builder()
            .baseUrl(Site.ASMHENTAI.getUrl())
//            .client(your_okhttp_client)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(JspoonConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("/g/{id}")
        Single<ASMContent> getGalleryMetadata(@Path("id") String contentId);
    }
}
