package com.pocket.xiaoli.core.academic

import okhttp3.Interceptor

/**
 * 网络日志出口。
 *
 * Android 把 `NetworkLogInterceptor` 挂进来，桌面端不挂（桌面没有「网络日志」页）。
 * 共享客户端只负责「有就装上」，不依赖任何平台存储。
 */
object NetworkLogBridge {
    @Volatile
    var interceptor: Interceptor? = null
}
