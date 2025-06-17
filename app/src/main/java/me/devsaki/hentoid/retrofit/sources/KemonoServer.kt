package me.devsaki.hentoid.retrofit.sources

import me.devsaki.hentoid.json.sources.kemono.KemonoArtist
import me.devsaki.hentoid.json.sources.kemono.KemonoGallery
import me.devsaki.hentoid.util.network.OkHttpClientManager
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

object KemonoServer {
    private const val KEMONO_URL = "https://kemono.su/api/v1/"

    lateinit var api: Api

    init {
        init()
    }

    // Must have a public init method to reset the connexion pool when updating DoH settings
    fun init() {
        api = Retrofit.Builder()
            .baseUrl(KEMONO_URL)
            .client(OkHttpClientManager.getInstance())
            .addConverterFactory(MoshiConverterFactory.create().asLenient())
            .build()
            .create(Api::class.java)
    }

    interface Api {
        @GET("{service}/user/{id}/profile")
        fun getArtist(
            @Query("service") service: String,
            @Query("id") id: String,
            @Header("cookie") cookies: String,
            @Header("accept") accept: String,
            @Header("user-agent") userAgent: String
        ): Call<KemonoArtist>

        @GET("{service}/user/{user_id}/post/{id}")
        fun getGallery(
            @Query("service") service: String,
            @Query("user_id") userId: String,
            @Query("id") id: String,
            @Header("cookie") cookies: String,
            @Header("accept") accept: String,
            @Header("user-agent") userAgent: String
        ): Call<KemonoGallery>
    }
}