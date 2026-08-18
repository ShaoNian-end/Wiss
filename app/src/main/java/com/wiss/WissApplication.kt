package com.wiss

import android.app.Application
import coil.Coil
import coil.ImageLoader

/**
 * 应用入口：初始化内置 Hosts，并让 Coil 图片加载走同一套自定义 DNS，
 * 使图片请求也能命中 Hosts 加速。
 */
class WissApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Hosts.init(this)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(Hosts.okHttpClient)
                .build()
        )
    }
}
