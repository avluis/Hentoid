package me.devsaki.hentoid.retrofit

import me.devsaki.hentoid.json.sources.DeviantArtDeviation
import me.devsaki.hentoid.json.sources.DeviantArtGallection
import me.devsaki.hentoid.util.network.OkHttpClientSingleton
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

object DeviantArtServer {
    private const val SERVER_URL = "https://www.deviantart.com/"

    lateinit var API: Api

    init {
        init()
    }

    // Must have a public init method to reset the connexion pool when updating DoH settings
    fun init() {
        API = Retrofit.Builder()
            .baseUrl(SERVER_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(Api::class.java)
    }


    interface Api {
        @GET("_puppy/dadeviation/init")
        fun getDeviation(
            @Query("deviationid") id: String,
            @Query("username") username: String,
            @Query("type") type: String,
            @Query("include_session") includeSession: String,
            @Query("csrf_token") token: String,
            @Query("expand") expand: String,
            @Query("da_minor_version") daMinorVersion: String,
            @Header("cookie") cookieStr: String,
            @Header("user-agent") userAgent: String,
        ): Call<DeviantArtDeviation>

        @GET("_puppy/dashared/gallection/contents")
        fun getUserGallection(
            @Query("username") username: String,
            @Query("type") type: String,
            @Query("offset") offset: String,
            @Query("limit") limit: String,
            @Query("folderid") folderid: String,
            @Query("csrf_token") token: String,
            @Query("da_minor_version") daMinorVersion: String,
            @Header("cookie") cookieStr: String,
            @Header("user-agent") userAgent: String,
        ): Call<DeviantArtGallection>
    }
}