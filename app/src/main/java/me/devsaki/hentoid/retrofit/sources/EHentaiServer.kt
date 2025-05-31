package me.devsaki.hentoid.retrofit.sources

import me.devsaki.hentoid.json.sources.EHentaiGalleriesMetadata
import me.devsaki.hentoid.json.sources.EHentaiGalleryQuery
import me.devsaki.hentoid.json.sources.EHentaiImageQuery
import me.devsaki.hentoid.json.sources.EHentaiImageResponse
import me.devsaki.hentoid.util.network.OkHttpClientManager
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

object EHentaiServer {
    private const val EHENTAI_URL = "http://e-hentai.org/"
    private const val EXHENTAI_URL = "https://exhentai.org/"

    lateinit var ehentaiApi: Api
    lateinit var exentaiApi: Api

    init {
        init()
    }

    // Must have a public init method to reset the connexion pool when updating DoH settings
    fun init() {
        ehentaiApi = Retrofit.Builder()
            .baseUrl(EHENTAI_URL)
            .client(OkHttpClientManager.getInstance())
            .addConverterFactory(MoshiConverterFactory.create().asLenient())
            .build()
            .create(Api::class.java)

        exentaiApi = Retrofit.Builder()
            .baseUrl(EXHENTAI_URL)
            .client(OkHttpClientManager.getInstance())
            .addConverterFactory(MoshiConverterFactory.create().asLenient())
            .build()
            .create(Api::class.java)
    }

    interface Api {
        @POST("api.php")
        fun getGalleryMetadata(
            @Body query: EHentaiGalleryQuery,
            @Header("cookie") cookies: String?
        ): Call<EHentaiGalleriesMetadata>

        @POST("api.php")
        fun getImageMetadata(
            @Body query: EHentaiImageQuery,
            @Header("cookie") cookies: String?
        ): Call<EHentaiImageResponse>
    }
}