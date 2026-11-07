package `in`.voltkit.livedemo.kotlin.gifts

import android.content.Context
import `in`.voltkit.livedemo.kotlin.Config
import `in`.voltkit.livedemo.kotlin.data.Api
import `in`.voltkit.livedemo.kotlin.data.Gift
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** The gift catalog, read once. */
object GiftCatalog {
    private var cached: List<Gift>? = null
    suspend fun load(): List<Gift> = cached ?: Api.gifts().also { cached = it }
}

/**
 * Gift animations on disk. The catalog's are fetched as a room opens, so nothing has to wait for a download when a gift
 * arrives; anything else is fetched when it is played.
 */
object SvgaFiles {
    private fun dir(context: Context) = File(context.cacheDir, "svga").apply { mkdirs() }

    private fun nameOf(url: String) = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".svga"

    suspend fun get(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val file = File(dir(context), nameOf(url))
        if (file.length() > 0) return@withContext file
        Api.http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw java.io.IOException("HTTP ${res.code} for $url")
            val part = File(file.path + ".part")
            part.outputStream().use { res.body.byteStream().copyTo(it) }
            part.renameTo(file)
        }
        file
    }

    suspend fun preload(context: Context) {
        val gifts = runCatching { GiftCatalog.load() }.getOrNull() ?: return
        for (g in gifts) runCatching { get(context, Config.assetUrl(g.svgaUrl)) }
    }
}
