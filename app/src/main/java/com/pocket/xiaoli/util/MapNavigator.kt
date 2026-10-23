package com.pocket.xiaoli.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens an external map app searching for [keyword].
 * Tries Amap / Baidu schemes first, then the system geo: intent, then Amap web.
 * No map SDK dependency is required.
 */
internal fun openMapSearch(context: Context, keyword: String) {
    val encoded = Uri.encode(keyword)
    val candidates = listOf(
        "androidamap://poi?sourceApplication=pocket_xiaoli&keywords=$encoded",
        "baidumap://map/geocoder?address=$encoded&src=com.pocket.xiaoli",
        "geo:0,0?q=$encoded"
    )
    for (raw in candidates) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(raw))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (_: Exception) {
            // app for this scheme is unavailable; try the next candidate
        }
    }
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://uri.amap.com/search?keyword=$encoded"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        // no browser available; nothing else to do
    }
}