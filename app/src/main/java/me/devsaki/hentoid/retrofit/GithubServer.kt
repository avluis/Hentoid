package me.devsaki.hentoid.retrofit

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.json.GithubRelease
import me.devsaki.hentoid.util.network.OkHttpClientSingleton
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.*

object GithubServer {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(Date::class.java, Rfc3339DateJsonAdapter())
            .build()
    }

    lateinit var api: Api

    init {
        init()
    }

    // Must have a public init method to reset the connexion pool when updating DoH settings
    fun init() {
        api = Retrofit.Builder()
            .baseUrl(BuildConfig.GITHUB_API_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(Api::class.java)
    }

    interface Api {
        @get:GET("releases")
        val releases: Call<List<GithubRelease>>

        @get:GET("releases/latest")
        val latestRelease: Call<GithubRelease>
    }
}