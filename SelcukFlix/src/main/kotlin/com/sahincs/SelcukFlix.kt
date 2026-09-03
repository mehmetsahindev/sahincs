// ! Bu araç sahincs tarafından yazılmıştır.

package com.sahincs

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import java.net.URI
import java.net.URLEncoder

class SelcukFlix : MainAPI() {
    override var mainUrl              = "https://selcukflix.com"
    override var name                 = "SelcukFlix"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    companion object {
        private const val KAYIT        = "SLC"
        private const val SAYFA_BOYUTU = 24
    }

    // ! Site 50+ alan döndürüyor, bilinmeyen alanlarda patlamasın
    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    // ? "uçNokta|sıralama|kategoriSlug" — kategori boşsa tüm arşiv
    override val mainPage = mainPageOf(
        "findSeries|date_desc|"                          to "Yeni Diziler",
        "findMovies|date_desc|"                          to "Yeni Filmler",
        "findSeries|imdb_desc|"                          to "IMDb'ye Göre Diziler",
        "findMovies|imdb_desc|"                          to "IMDb'ye Göre Filmler",
        "findSeries|view_desc|"                          to "Çok İzlenen Diziler",
        "findMovies|view_desc|"                          to "Çok İzlenen Filmler",
        "findMovies|date_desc|film-kategori/aksiyon"     to "Aksiyon Filmleri",
        "findMovies|date_desc|film-kategori/komedi"      to "Komedi Filmleri",
        "findMovies|date_desc|film-kategori/dram"        to "Dram Filmleri",
        "findMovies|date_desc|film-kategori/korku"       to "Korku Filmleri",
        "findMovies|date_desc|film-kategori/bilim-kurgu" to "Bilim Kurgu Filmleri",
        "findMovies|date_desc|film-kategori/animasyon"   to "Animasyon Filmleri",
        "findMovies|date_desc|film-kategori/gerilim"     to "Gerilim Filmleri",
        "findMovies|date_desc|film-kategori/romantik"    to "Romantik Filmler"
    )

    // ══════════════════════════════ İstek katmanı ══════════════════════════════

    /**
     * `/api/bg/` uç noktaları POST + query string ile çalışıp şifreli JSON döndürüyor.
     * Yanıt: {"response": "<base64 AES>"} → çözülünce {"state", "code", "result", ...}
     */
    private suspend fun bgIstek(ucNokta: String, parametreler: Map<String, String>): JsonNode {
        val sorgu = parametreler.entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

        val yanit = app.post(
            "${mainUrl}/api/bg/${ucNokta}?${sorgu}",
            headers = mapOf(
                "Accept"           to "application/json, text/plain, */*",
                "X-Requested-With" to "XMLHttpRequest"
            ),
            referer = "${mainUrl}/"
        ).text

        // ! Domain değişince 301 query string'i düşürüyor; API o zaman JSON yerine düz metin uyarı döndürüyor.
        // ! Bunu erken yakalamazsak Jackson'ın anlaşılmaz "Unrecognized token" hatası çıkıyor.
        if (!yanit.trimStart().startsWith("{")) {
            throw ErrorLoadingException("${ucNokta} » JSON yerine düz yanıt geldi, domain değişmiş olabilir: ${yanit.take(120)}")
        }

        val sifreli = mapper.readTree(yanit).path("response").takeIf { it.isTextual }?.asText()
            ?: throw ErrorLoadingException("${ucNokta} » şifreli yanıt alınamadı")

        return mapper.readTree(SelcukCrypto.coz(sifreli))
    }

    /**
     * Sayfa HTML'i Next.js SSR — tüm veri `__NEXT_DATA__` içindeki `secureData` alanında şifreli.
     */
    private suspend fun sayfaVerisi(adres: String): JsonNode {
        val belge = app.get(adres, referer = "${mainUrl}/").document

        val ham = belge.selectFirst("script#__NEXT_DATA__")?.data()
            ?: throw ErrorLoadingException("__NEXT_DATA__ bulunamadı » ${adres}")

        val sifreli = mapper.readTree(ham).path("props").path("pageProps").path("secureData")
            .takeIf { it.isTextual }?.asText()
            ?: throw ErrorLoadingException("secureData bulunamadı » ${adres}")

        return mapper.readTree(SelcukCrypto.coz(sifreli))
    }

    // ══════════════════════════════ Yardımcılar ══════════════════════════════

    private fun JsonNode.metin(alan: String): String? =
        this.path(alan).takeIf { it.isTextual && it.asText().isNotBlank() }?.asText()

    private fun JsonNode.tamsayi(alan: String): Int? =
        this.path(alan).takeIf { it.isNumber }?.asInt()

    private fun JsonNode.ondalik(alan: String): Double? =
        this.path(alan).takeIf { it.isNumber }?.asDouble()

    private fun <T> dizi(dugum: JsonNode, sinif: Class<Array<T>>): List<T> =
        if (dugum.isArray) mapper.treeToValue(dugum, sinif).toList() else emptyList()

    /** Görseller AMP proxy'si üzerinden geliyor, kaynağa çeviriyoruz. */
    private fun gorsel(adres: String?): String? = adres
        ?.takeIf { it.isNotBlank() }
        ?.replace("images-macellan-online.cdn.ampproject.org/i/s/", "")

    /** `used_slug` her zaman göreli: "dizi/reacher" / "film/xyz/izle" */
    private fun tamAdres(yol: String) = "${mainUrl}/${yol.trimStart('/')}"

    private fun diziMi(yol: String) = yol.startsWith("dizi/") || yol.contains("/dizi/")

    private fun Icerik.toSearchResponse(): SearchResponse? {
        val baslik = this.baslik ?: return null
        val yol    = this.yol    ?: return null
        val adres  = tamAdres(yol)
        val afis   = gorsel(this.afis ?: this.kapak)

        return if (diziMi(yol)) {
            newTvSeriesSearchResponse(baslik, adres, TvType.TvSeries) {
                this.posterUrl = afis
                this.year      = this@toSearchResponse.yil
            }
        } else {
            newMovieSearchResponse(baslik, adres, TvType.Movie) {
                this.posterUrl = afis
                this.year      = this@toSearchResponse.yil
            }
        }
    }

    private fun AramaSonucu.toSearchResponse(): SearchResponse? {
        val baslik = this.baslik ?: return null
        val yol    = this.yol    ?: return null
        val adres  = tamAdres(yol)
        val afis   = gorsel(this.afis)

        return if (this.tur == "Series" || diziMi(yol)) {
            newTvSeriesSearchResponse(baslik, adres, TvType.TvSeries) {
                this.posterUrl = afis
                this.year      = this@toSearchResponse.yil
            }
        } else {
            newMovieSearchResponse(baslik, adres, TvType.Movie) {
                this.posterUrl = afis
                this.year      = this@toSearchResponse.yil
            }
        }
    }

    // ══════════════════════════════ Ana sayfa ══════════════════════════════

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val parcalar = request.data.split("|")
        val ucNokta  = parcalar.getOrElse(0) { "findSeries" }
        val siralama = parcalar.getOrElse(1) { "date_desc" }
        val kategori = parcalar.getOrElse(2) { "" }

        val yanit = bgIstek(ucNokta, mapOf(
            "releaseYearStart"   to "-1",
            "releaseYearEnd"     to "-1",
            "imdbPointMin"       to "-1",
            "imdbPointMax"       to "-1",
            "categoryIdsComma"   to "",
            "countryIdsComma"    to "",
            "orderType"          to siralama,
            "languageId"         to "-1",
            "currentPage"        to page.toString(),
            "currentPageCount"   to SAYFA_BOYUTU.toString(),
            "queryStr"           to "",
            "categorySlugsComma" to kategori,
            "countryCodesComma"  to ""
        ))

        val icerikler = dizi(yanit.path("result"), Array<Icerik>::class.java)
        val liste     = icerikler.mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(request.name, liste, hasNext = icerikler.size >= SAYFA_BOYUTU)
    }

    // ══════════════════════════════ Arama ══════════════════════════════

    override suspend fun search(query: String): List<SearchResponse> {
        val yanit    = bgIstek("searchContent", mapOf("searchterm" to query))
        val sonuclar = dizi(yanit.path("result"), Array<AramaSonucu>::class.java)

        // ! "MovieSeries" (seri-filmler/...) bir koleksiyon sayfası, doğrudan oynatılamıyor
        return sonuclar
            .filterNot { it.tur == "MovieSeries" || it.yol.orEmpty().startsWith("seri-filmler/") }
            .mapNotNull { it.toSearchResponse() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // ══════════════════════════════ Detay ══════════════════════════════

    override suspend fun load(url: String): LoadResponse? {
        val veri     = sayfaVerisi(url)
        val icerik   = veri.path("contentItem")
        val iliskili = veri.path("RelatedResults")

        val baslik   = icerik.metin("original_title") ?: icerik.metin("used_title") ?: return null
        val afis     = gorsel(icerik.metin("poster_url") ?: icerik.metin("face_url"))
        val ozet     = icerik.metin("description") ?: icerik.metin("used_content_body")
        val yil      = icerik.tamsayi("release_year")
        val puan     = icerik.ondalik("imdb_point")?.let { Score.from10(it) }   // ? IMDb 0-10 skalası
        val sure     = icerik.tamsayi("total_minutes")
        val etiketler = icerik.metin("categories")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        val fragman  = icerik.metin("trailer")

        val oyuncular = (dizi(iliskili.path("getSerieCastsById").path("result"), Array<JsonNode>::class.java) +
                         dizi(iliskili.path("getMovieCastsById").path("result"), Array<JsonNode>::class.java))
            .mapNotNull { oyuncu ->
                val ad = oyuncu.metin("name") ?: return@mapNotNull null
                Actor(ad, gorsel(oyuncu.metin("cast_image")))
            }

        val oneriler = (dizi(iliskili.path("getRelatedSeries").path("result"), Array<Icerik>::class.java) +
                        dizi(iliskili.path("getRelatedMovies").path("result"), Array<Icerik>::class.java))
            .mapNotNull { it.toSearchResponse() }

        val sezonlar = iliskili.path("getSerieSeasonAndEpisodes").path("result")

        // ! Sezon/bölüm ağacı varsa dizi, yoksa film
        if (sezonlar.isArray && sezonlar.size() > 0) {
            val bolumler = sezonlar.flatMap { sezon ->
                sezon.path("episodes").mapNotNull { bolum ->
                    val yol = bolum.metin("used_slug") ?: return@mapNotNull null

                    newEpisode(tamAdres(yol)) {
                        this.name        = bolum.metin("episode_subtitle") ?: bolum.metin("episode_text")
                        this.season      = bolum.tamsayi("season_no")
                        this.episode     = bolum.tamsayi("episode_no")
                        this.description = bolum.metin("episode_description")
                    }
                }
            }

            return newTvSeriesLoadResponse(baslik, url, TvType.TvSeries, bolumler) {
                this.posterUrl       = afis
                this.plot            = ozet
                this.year            = yil
                this.score           = puan
                this.duration        = sure
                this.tags            = etiketler
                this.recommendations = oneriler
                addActors(oyuncular)
                addTrailer(fragman)
            }
        }

        return newMovieLoadResponse(baslik, url, TvType.Movie, url) {
            this.posterUrl       = afis
            this.plot            = ozet
            this.year            = yil
            this.score           = puan
            this.duration        = sure
            this.tags            = etiketler
            this.recommendations = oneriler
            addActors(oyuncular)
            addTrailer(fragman)
        }
    }

    // ══════════════════════════════ Video kaynakları ══════════════════════════════

    /** `source_content` bir <iframe> HTML parçası; src'yi çıkarıp protokolü tamamlıyoruz. */
    private fun iframeAdresi(kaynakIcerik: String?): String? {
        val ham   = kaynakIcerik ?: return null
        val adres = Regex("""src=["']([^"']+)["']""").find(ham)?.groupValues?.get(1)?.trim()
            ?: return null

        return when {
            adres.startsWith("//")   -> "https:${adres}"
            adres.startsWith("http") -> adres
            else                     -> fixUrl(adres)
        }
    }

    private fun kokAdres(adres: String): String = runCatching {
        URI(adres).let { "${it.scheme}://${it.host}" }
    }.getOrDefault(mainUrl)

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d(KAYIT, "data » ${data}")

        val iliskili = sayfaVerisi(data).path("RelatedResults")

        // ? Bölümler getEpisodeSources, filmler getMovieSourcesById + getMoviePartSourcesById_<parçaId>
        val kaynaklar = mutableListOf<JsonNode>()
        kaynaklar += iliskili.path("getEpisodeSources").path("result").toList()
        kaynaklar += iliskili.path("getMovieSourcesById").path("result").toList()
        iliskili.fieldNames().forEach { alan ->
            if (alan.startsWith("getMoviePartSourcesById")) {
                kaynaklar += iliskili.path(alan).path("result").toList()
            }
        }

        if (kaynaklar.isEmpty()) {
            Log.d(KAYIT, "kaynak bulunamadı » ${data}")
            return false
        }

        var bulundu = false

        // ! Aynı iframe adresi birden çok dil satırında tekrar ediyor; akış aynı olduğu için
        // ! adrese göre grupluyoruz — WebView çözümlemesi saniyeler sürdüğünden tekrarı göze alamayız
        kaynaklar
            .mapNotNull { kaynak ->
                val adres = iframeAdresi(kaynak.metin("source_content")) ?: return@mapNotNull null

                adres to kaynak
            }
            .groupBy({ it.first }, { it.second })
            .forEach { (adres, grup) ->
                val diller = grup.mapNotNull { it.metin("language_name") }.distinct()
                val etiket = listOfNotNull(
                    grup.firstNotNullOfOrNull { it.metin("source_name") },
                    diller.joinToString(" / ").takeIf { it.isNotBlank() },
                    grup.firstNotNullOfOrNull { it.metin("quality_name") }
                ).joinToString(" • ")

                Log.d(KAYIT, "iframe » ${etiket} » ${adres}")

                // ! callback suspend değil, newExtractorLink ise suspend olabiliyor →
                // ! linkleri önce topla, etiketleyip burada (suspend bağlamda) yeniden yayınla
                val toplanan = mutableListOf<ExtractorLink>()

                // ? Önce kayıtlı çözücüler, olmazsa jenerik oynatıcı çözümleyicimiz.
                // ! Bir kaynağın patlaması diğerlerini düşürmesin diye ikisi de runCatching içinde
                val cozuldu = runCatching {
                    loadExtractor(adres, "${mainUrl}/", subtitleCallback) { toplanan.add(it) }
                }.onFailure { Log.d(KAYIT, "çözücü hatası » ${adres} » ${it.message}") }
                 .getOrDefault(false)

                if (!cozuldu && toplanan.isEmpty()) {
                    runCatching {
                        SelcukPlayer(kokAdres(adres)).getUrl(adres, "${mainUrl}/", subtitleCallback) { toplanan.add(it) }
                    }.onFailure { Log.d(KAYIT, "çözümlenemedi » ${adres} » ${it.message}") }
                }

                toplanan.forEach { link ->
                    bulundu = true
                    callback.invoke(
                        newExtractorLink(link.source, "${etiket} - ${link.name}", link.url) {
                            this.referer       = link.referer
                            this.quality       = link.quality
                            this.type          = link.type
                            this.headers       = link.headers
                            this.extractorData = link.extractorData
                        }
                    )
                }
            }

        return bulundu
    }
}
