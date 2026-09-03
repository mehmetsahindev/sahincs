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
 * inline script tarafından çalışma anında `<host>/master.m3u8?<token>` olarak kuruluyor.
 * Host ayrıca Cloudflare arkasında ve Referer'sız isteklere 403 dönüyor.
 *
 * Bu yüzden sayfayı WebView'de açıp oynatıcının kendi m3u8 isteğini yakalıyoruz.
 *
 * ! Kritik ayrıntı: oynatıcı istek atmadan önce tıklama bekliyor. Sayfayı öylece
 * ! yüklemek yetmiyor — hiçbir istek doğmuyor ve çözümleme timeout'a kadar asılı kalıyor.
 * ! Bu yüzden [BASLAT_SCRIPTI] ile oynatmayı tetikliyoruz.
 */
open class SelcukPlayer(override val mainUrl: String) : ExtractorApi() {
    override val name            = "SelcukPlayer"
    override val requiresReferer = true

    companion object {
        private const val KAYIT = "SLC_Player"

        /**
         * Oynatıcının üzerindeki oynat düğmesi bir SVG; doğrudan seçici yerine sayfanın
         * ortasındaki elemana gerçek fare olayı dizisi gönderiyoruz. Oynatıcı geç
         * kurulabildiği için kısa aralıklarla tekrar deniyor, video oluşunca duruyor.
         */
        private const val BASLAT_SCRIPTI = """
            (function () {
                var deneme = 0;
                var zamanlayici = setInterval(function () {
                    deneme++;
                    try {
                        var eleman = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
                        if (eleman) {
                            ['mousedown', 'mouseup', 'click'].forEach(function (tip) {
                                eleman.dispatchEvent(new MouseEvent(tip, { bubbles: true, cancelable: true, view: window }));
                            });
                        }
                        var video = document.querySelector('video');
                        if (video) { video.play(); clearInterval(zamanlayici); }
                    } catch (e) {}
                    if (deneme > 20) clearInterval(zamanlayici);
                }, 500);
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
                interceptUrl = Regex("""\.m3u8"""),
                script       = BASLAT_SCRIPTI,
                timeout      = 20_000L
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
