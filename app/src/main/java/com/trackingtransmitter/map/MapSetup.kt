package com.trackingtransmitter.map

import android.content.Context
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager

const val AMBIENT_CACHE_BYTES = 200L * 1024 * 1024

object MapSetup {
    /** Call once from Application.onCreate: tile requests carry our User-Agent and viewed tiles are cached. */
    fun init(context: Context, client: OkHttpClient) {
        MapLibre.getInstance(context)
        HttpRequestUtil.setOkHttpClient(client)
        OfflineManager.getInstance(context).setMaximumAmbientCacheSize(
            AMBIENT_CACHE_BYTES,
            object : OfflineManager.FileSourceCallback {
                override fun onSuccess() = Unit
                override fun onError(message: String) = Unit
            },
        )
    }
}
