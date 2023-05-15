package me.devsaki.hentoid.retrofit

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.json.core.UpdateInfo
import me.devsaki.hentoid.util.network.OkHttpClientSingleton
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.*

object UpdateServer {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(Date::class.java, Rfc3339DateJsonAdapter())
            .build()
    }

    val api: Api

    init {
        api = Retrofit.Builder()
            .baseUrl(BuildConfig.UPDATE_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(Api::class.java)
    }

    interface Api {
        @get:GET("update.json")
        val updateInfo: Call<UpdateInfo>
    }
}