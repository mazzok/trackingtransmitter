package com.trackingtransmitter

import android.app.Application
import com.trackingtransmitter.data.TreasureStore
import com.trackingtransmitter.map.MapSetup
import okhttp3.OkHttpClient
import java.io.File

class SchatzApp : Application() {
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()) }
            .build()
    }

    val treasures: TreasureStore by lazy { TreasureStore(File(filesDir, "treasures.json")) }

    override fun onCreate() {
        super.onCreate()
        MapSetup.init(this, httpClient)
    }

    companion object {
        const val USER_AGENT = "trackingtransmitter/${BuildConfig.VERSION_NAME} (github.com/mazzok/trackingtransmitter)"
    }
}
