// ! Bu araç sahincs tarafından yazılmıştır.

package com.sahincs

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*

/**
 * SelcukFlix'in `iframe.php?v=<hash>` tipi oynatıcıları (pichive.online, dplayer82.site).
 *
 * Bu oynatıcılar **Playerjs** kullanıyor ve akış adresini sayfada tutmuyor:
 * HTML'de yalnızca çıplak `master.m3u8` metni var, tam adres obfuscate edilmiş
 * inline script tarafından çalışma anında `<host>/master.m3u8?<token>` şeklinde kuruluyor.
 * Ayrıca host Cloudflare arkasında ve Referer'sız isteklere 403 dönüyor.
 *
 * Bu yüzden statik regex ile çözmek mümkün değil; sayfayı WebView'de açıp
 * oynatıcının kendi attığı m3u8 isteğini yakalıyoruz.
 */
open class SelcukPlayer(override val mainUrl: String) : ExtractorApi() {
    override val name            = "SelcukPlayer"
    override val requiresReferer = true

    companion object {
        private const val KAYIT = "SLC_Player"
    }

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val kaynakReferer = referer ?: "${mainUrl}/"
        Log.d(KAYIT, "url » ${url}")

        val yanit = app.get(
            url,
            referer     = kaynakReferer,
            interceptor = WebViewResolver(
                interceptUrl = Regex("""\.m3u8"""),
                timeout      = 30_000L
            )
        )

        val akis = yanit.url.takeIf { it.contains(".m3u8") }
            ?: throw ErrorLoadingException("${name} » akış yakalanamadı: ${url}")

        Log.d(KAYIT, "akış » ${akis}")

        callback.invoke(
            newExtractorLink(this.name, this.name, akis) {
                this.referer = "${mainUrl}/"   // ! oynatıcı host'u Referer bekliyor
                this.quality = Qualities.Unknown.value
                this.type    = ExtractorLinkType.M3U8
            }
        )
    }
}
