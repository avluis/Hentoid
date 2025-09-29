package me.devsaki.hentoid.retrofit

import me.devsaki.hentoid.json.sources.jikan.JikanAnimeRoles
import me.devsaki.hentoid.json.sources.jikan.JikanCharacters
import me.devsaki.hentoid.json.sources.jikan.JikanContents
import me.devsaki.hentoid.json.sources.jikan.JikanMangaRoles
import me.devsaki.hentoid.util.network.OkHttpClientManager
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

object JikanServer {
    private const val SERVER_URL = "https://api.jikan.moe/v4/"

    lateinit var API: Api

    init {
        init()
    }

    // Must have a public init method to reset the connexion pool when updating DoH settings
    fun init() {
        API = Retrofit.Builder()
            .baseUrl(SERVER_URL)
            .client(OkHttpClientManager.getInstance())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(Api::class.java)
    }

    interface Api {
        @GET("characters")
        fun searchCharacters(
            @Query("q") query: String,
            @Query("order_by") orderBy: String = "favorites",
            @Query("sort") sort: String = "desc"
        ): Call<JikanCharacters>

        @GET("characters/{id}/anime")
        fun searchCharacterAnime(
            @Path("id") id: String,
        ): Call<JikanAnimeRoles>

        @GET("characters/{id}/manga")
        fun searchCharacterManga(
            @Path("id") id: String,
        ): Call<JikanMangaRoles>

        @GET("anime")
        fun searchAnime(
            @Query("q") query: String,
            // No order = ordered by best match
            //@Query("order_by") orderBy: String = "favorites", // see also popularity
            //@Query("sort") sort: String = "desc"
        ): Call<JikanContents>

        @GET("manga")
        fun searchManga(
            @Query("q") query: String,
            // No order = ordered by best match
            //@Query("order_by") orderBy: String = "favorites", // see also popularity
            //@Query("sort") sort: String = "desc"
        ): Call<JikanContents>
    }
}