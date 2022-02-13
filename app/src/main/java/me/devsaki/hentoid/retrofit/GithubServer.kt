package me.devsaki.hentoid.retrofit

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import io.reactivex.Single
import me.devsaki.hentoid.json.GithubRelease
import me.devsaki.hentoid.util.network.OkHttpClientSingleton
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.*

object GithubServer {

    private const val GITHUB_BASE_URL = "https://api.github.com/repos/avluis/Hentoid/"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(Date::class.java, Rfc3339DateJsonAdapter())
            .build()
    }

    lateinit var api: Api

    init {
        init()
    }

    fun init() {
        api = Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(Api::class.java)
    }

    interface Api {
        @get:GET("releases")
        val releases: Single<List<GithubRelease>>

        @get:GET("releases/latest")
        val latestRelease: Single<GithubRelease>
    }
}