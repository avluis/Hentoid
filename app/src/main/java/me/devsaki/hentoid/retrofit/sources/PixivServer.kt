package me.devsaki.hentoid.retrofit.sources

import me.devsaki.hentoid.json.sources.*
import me.devsaki.hentoid.util.network.OkHttpClientSingleton
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

object PixivServer {
    private const val API_URL = "https://www.pixiv.net/";

    lateinit var api: Api

    init {
        init()
    }

    fun init() {
        api = Retrofit.Builder()
            .baseUrl(API_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addConverterFactory(MoshiConverterFactory.create().asLenient())
            .build()
            .create(Api::class.java)
    }

    interface Api {
        @GET("touch/ajax/illust/details")
        fun getIllustMetadata(
            @Query("illust_id") id: String,
            @Header("cookie") cookies: String
        ): Call<PixivIllustMetadata>

        @GET("ajax/illust/{id}/pages")
        fun getIllustPages(
            @Query("id") id: String,
            @Header("cookie") cookies: String
        ): Call<PixivIllustPagesMetadata>

        @GET("touch/ajax/illust/series/{id}")
        fun getSeriesMetadata(
            @Path("id") id: String,
            @Header("cookie") cookies: String
        ): Call<PixivSeriesMetadata>

        @GET("touch/ajax/illust/series_content/{id}")
        fun getSeriesIllusts(
            @Path("id") id: String,
            @Query("limit") limit: Int,
            @Query("last_order") lastorder: Int,
            @Header("cookie") cookies: String
        ): Call<PixivSeriesIllustMetadata>

        @GET("touch/ajax/illust/user_illusts")
        fun getUserIllusts(
            @Query("user_id") id: String,
            @Header("cookie") cookies: String
        ): Call<PixivUserIllustMetadata>

        @GET("touch/ajax/user/details")
        fun getUserMetadata(
            @Query("id") id: String,
            @Header("cookie") cookies: String
        ): Call<PixivUserMetadata>
    }
}