// ! Bu araç sahincs tarafından yazılmıştır.

package com.sahincs

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*

/**
 * SelcukFlix'in `iframe.php?v=<hash>` tipi oynatıcıları (pichive.online, dplayer82.site).
 *
 * Oynatıcı **Playerjs**; akış adresini sayfada tutmuyor. HTML'de yalnızca çıplak
 * `master.m3u8` metni var, tam adres obfuscate edilmiş inline script tarafından
 * çalışma anında `<host>/master.m3u8?<token>` olarak kuruluyor. Bu yüzden sayfayı
 * WebView'de açıp oynatıcının kendi isteğini yakalıyoruz.
 *
 * Tarayıcıda ölçülen iki kritik davranış:
 *  1. Oynatıcı, tıklama gelmeden hiçbir istek atmıyor → [BASLAT_SCRIPTI] ile tetikliyoruz.
 *  2. Akış adresi Referer'sız isteklere 404 dönüyor; herhangi bir Referer yeterli.
 */
open class SelcukPlayer(override val mainUrl: String) : ExtractorApi() {
    override val name            = "SelcukPlayer"
    override val requiresReferer = true

    companion object {
        private const val KAYIT = "SLC_Player"

        /**
         * Oynat düğmesi bir SVG ve konteyner `#Player` / `.jw-display-icon-container`.
         *
         * ! Koordinat tabanlı `elementFromPoint` kullanmıyoruz: CloudStream'in WebView'i
         * ! ekranda görünmediği için viewport 0 olabiliyor ve o zaman hiçbir eleman dönmüyor.
         * ! Seçici tabanlı tıklama tarayıcıda koordinatsız olarak doğrulandı.
         *
         * Oynatıcı geç kurulabildiğinden kısa aralıklarla tekrar deneniyor; video
         * oluşunca duruyor. Dönen metin [WebViewResolver] scriptCallback'ine gidiyor.
         */
        private const val BASLAT_SCRIPTI = """
            (function () {
                function tikla(el) {
                    ['mousedown', 'mouseup', 'click'].forEach(function (tip) {
                        try { el.dispatchEvent(new MouseEvent(tip, { bubbles: true, cancelable: true, view: window })); } catch (e) {}
                    });
                }

                function dene() {
                    var hedefler = [].slice.call(
                        document.querySelectorAll('#Player, .jw-display-icon-container, #Player svg, #Player path, video')
                    );
                    try {
                        var orta = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
                        if (orta) hedefler.push(orta);
                    } catch (e) {}

                    hedefler.forEach(tikla);

                    var video = document.querySelector('video');
                    if (video) { try { video.play(); } catch (e) {} }

                    return hedefler.length;
                }

                var hedefSayisi = dene();
                var deneme = 0;
                var zamanlayici = setInterval(function () {
                    deneme++;
                    var bulundu = dene();
                    if (document.querySelector('video') || deneme > 30) clearInterval(zamanlayici);
                }, 400);

                return 'vp=' + window.innerWidth + 'x' + window.innerHeight + ' hedef=' + hedefSayisi;
            })();
        """
    }

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val kaynakReferer = referer ?: "${mainUrl}/"
        Log.d(KAYIT, "url » ${url}")

        // ? Script'in ilk turdaki çıktısı; çözümleme tutmazsa hata mesajına koyuyoruz
        var tani = "-"

        val yanit = app.get(
            url,
            referer     = kaynakReferer,
            interceptor = WebViewResolver(
                interceptUrl   = Regex("""\.m3u8"""),
                script         = BASLAT_SCRIPTI,
                scriptCallback = { sonuc -> tani = sonuc },
                timeout        = 20_000L
            )
        )

        Log.d(KAYIT, "tanı » ${tani}")

        val akis = yanit.url.takeIf { it.contains(".m3u8") }
            ?: throw ErrorLoadingException("${name} » akış yakalanamadı [${tani}] » ${url}")

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
