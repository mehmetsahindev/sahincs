// ! Bu araç sahincs tarafından yazılmıştır.

package com.sahincs

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*

/**
 * SelcukFlix'in `iframe.php?v=<hash>` tipi oynatıcıları (pichive.online, dplayer82.site).
 *
 * Tarayıcıda adım adım çözülen akış:
 *   1. `iframe.php` sayfasında tırnak içinde ~3700 karakterlik base64 blob duruyor
 *   2. Oynatıcı bunu `source2.php?v=<blob>` adresine gönderiyor
 *   3. Dönen JSON: playlist[0].sources[0].file → gerçek HLS manifesti (#EXTM3U)
 *   4. Manifest Referer'sız istekte 404 veriyor, herhangi bir Referer yeterli
 *
 * Neden WebView: host Cloudflare arkasında ve düz HTTP istemcisini doğru Referer'la
 * bile 403'lüyor (aynı ağdan tarayıcı geçiyor, curl geçmiyor → istemci parmak izi).
 * Bu yüzden istek tarayıcı bağlamından gitmek zorunda.
 *
 * ! useOkhttp = false: varsayılan (true) WebView isteklerini OkHttp'ye yönlendiriyor,
 * ! bu da Cloudflare'e takılan istemciye geri dönmek demek.
 */
open class SelcukPlayer(override val mainUrl: String) : ExtractorApi() {
    override val name            = "SelcukPlayer"
    override val requiresReferer = true

    companion object {
        private const val KAYIT = "SLC_Player"

        private val mapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

        /**
         * Blob'u sayfadan çıkarıp `source2.php`yi sayfanın kendi bağlamından çağırır.
         * Böylece Cloudflare sorun olmuyor ve oynatıcıya tıklamaya gerek kalmıyor.
         * Yedek olarak oynatmayı da tetikliyor (m3u8 isteği de yakalanabilsin diye).
         */
        private const val COZUM_SCRIPTI = """
            (function () {
                try {
                    var html = document.documentElement.innerHTML;
                    var eslesme = html.match(/["']([A-Za-z0-9+/=]{1000,})["']/);

                    if (eslesme) {
                        var istek = new XMLHttpRequest();
                        istek.open('GET', '/source2.php?v=' + encodeURIComponent(eslesme[1]), true);
                        istek.send();
                    }
                } catch (e) {}

                // ? yedek yol: oynatıcıyı tetikle, m3u8 isteği de doğsun
                try {
                    var tikla = function (el) {
                        ['mousedown', 'mouseup', 'click'].forEach(function (tip) {
                            el.dispatchEvent(new MouseEvent(tip, { bubbles: true, cancelable: true, view: window }));
                        });
                    };
                    var hedefler = document.querySelectorAll('#Player, .jw-display-icon-container');
                    for (var i = 0; i < hedefler.length; i++) tikla(hedefler[i]);
                } catch (e) {}

                return 'ok';
            })();
        """
    }

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val kaynakReferer = referer ?: "${mainUrl}/"
        Log.d(KAYIT, "url » ${url}")

        val yanit = app.get(
            url,
            referer     = kaynakReferer,
            interceptor = WebViewResolver(
                interceptUrl = Regex("""source2\.php|\.m3u8"""),
                useOkhttp    = false,
                script       = COZUM_SCRIPTI,
                timeout      = 25_000L
            )
        )

        val yakalanan = yanit.url
        Log.d(KAYIT, "yakalanan » ${yakalanan}")

        val akis = when {
            // ? doğrudan akış yakalandıysa onu kullan
            yakalanan.contains(".m3u8") -> yakalanan

            // ? source2.php yakalandıysa gövdesinden file alanını çıkar
            yakalanan.contains("source2.php") -> {
                val govde = runCatching { yanit.text }.getOrDefault("")

                runCatching {
                    mapper.readTree(govde)
                        .path("playlist").path(0)
                        .path("sources").path(0)
                        .path("file").takeIf { it.isTextual }?.asText()
                }.getOrNull()
            }

            else -> null
        } ?: throw ErrorLoadingException("${name} » akış yakalanamadı (yakalanan: ${yakalanan.take(80)})")

        Log.d(KAYIT, "akış » ${akis}")

        callback.invoke(
            newExtractorLink(this.name, this.name, akis) {
                this.referer = "${mainUrl}/"   // ! Referer'sız istek 404 dönüyor
                this.quality = Qualities.Unknown.value
                this.type    = ExtractorLinkType.M3U8
            }
        )
    }
}
