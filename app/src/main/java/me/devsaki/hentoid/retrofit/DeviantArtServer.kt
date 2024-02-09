package me.devsaki.hentoid.retrofit

import me.devsaki.hentoid.util.network.OkHttpClientSingleton
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

object DeviantArtServer {
    private val SERVER_URL = "https://www.deviantart.com/"

    val API = Retrofit.Builder()
        .baseUrl(SERVER_URL)
        .client(OkHttpClientSingleton.getInstance())
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(Api::class.java)


    interface Api {
        /*
        @GET("api/v1/me")
        fun getUser(
            @Header("Authorization") authorization: String?
        ): RedditUser

        @GET("user/{username}/saved")
        fun getUserSavedPosts(
            @Path("username") username: String?,
            @Header("Authorization") authorization: String?
        ): RedditUserSavedPosts
         */
    }

}