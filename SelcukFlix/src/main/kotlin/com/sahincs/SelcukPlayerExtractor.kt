// ! Bu araç sahincs tarafından yazılmıştır.

package com.sahincs

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

/**
 * SelcukFlix'in kullandığı `iframe.php?v=<hash>` tipi PHP oynatıcıları için jenerik çözümleyici.
 *
 * Gözlemlenen host'lar: pichive.online, four.pichive.online, sn.dplayer82.site
 * Bu host'lar sık değişiyor — yenisi çıkarsa [SelcukFlixPlugin] içindeki listeye eklemen yeterli;
 * kayıtlı olmayan host'lar için [SelcukFlix.loadLinks] zaten bu sınıfı doğrudan çağırıyor.
 *
 * Oynatıcı sayfasında akış şu biçimlerden birinde duruyor:
 *   file: "...m3u8" | "file":"...m3u8" | sources:[{file:"..."}] | eval(function(p,a,c,k,e,d){...})
 */
open class SelcukPlayer(override val mainUrl: String) : ExtractorApi() {
    override val name            = "SelcukPlayer"
    override val requiresReferer = true

    companion object {
        private const val KAYIT = "SLC_Player"

        private val AKIS_DESENLERI = listOf(
            Regex("""["']?file["']?\s*[:=]\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']"""),
            Regex("""["']?(?:src|source)["']?\s*[:=]\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']"""),
            Regex("""(https?:(?://|\\/\\/)[^"'\s\\]+\.(?:m3u8|mp4)[^"'\s\\]*)""")
        )
    }

    private fun akisBul(icerik: String): String? {
        for (desen in AKIS_DESENLERI) {
            val bulunan = desen.find(icerik)?.groupValues?.get(1) ?: continue

            return bulunan.replace("\\/", "/").replace("\\", "")
        }

        return null
    }

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val kaynakReferer = referer ?: "${mainUrl}/"
        Log.d(KAYIT, "url » ${url}")

        val istek  = app.get(url, referer = kaynakReferer)
        val icerik = istek.text

        // ? Oynatıcıya gömülü altyazılar
        istek.document.select("track").forEach { parca ->
            val altyaziAdresi = parca.attr("src").takeIf { it.isNotBlank() } ?: return@forEach

            subtitleCallback.invoke(
                SubtitleFile(
                    lang = parca.attr("label").ifBlank { "Türkçe" },
                    url  = fixUrl(altyaziAdresi)
                )
            )
        }

        // ? Önce düz HTML, olmazsa packed (eval) script'i açıp tekrar dene
        val akis = akisBul(icerik)
            ?: runCatching { getAndUnpack(icerik) }.getOrNull()?.let { akisBul(it) }
            ?: throw ErrorLoadingException("${name} » akış bulunamadı: ${url}")

        val tamAkis = if (akis.startsWith("//")) "https:${akis}" else akis
        Log.d(KAYIT, "akış » ${tamAkis}")

        callback.invoke(
            newExtractorLink(this.name, this.name, tamAkis) {
                this.referer = kaynakReferer
                this.quality = Qualities.Unknown.value
                this.type    = if (tamAkis.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            }
        )
    }
}
