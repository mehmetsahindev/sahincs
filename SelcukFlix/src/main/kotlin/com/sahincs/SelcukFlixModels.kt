// ! Bu araç sahincs tarafından yazılmıştır.

package com.sahincs

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * `/api/bg/findSeries` ve `/api/bg/findMovies` sonuç öğeleri.
 * Site 50'den fazla alan döndürüyor, sadece kullandıklarımızı tanımlıyoruz.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Icerik(
    @JsonProperty("original_title") val baslik: String?  = null,
    @JsonProperty("used_slug")      val yol: String?     = null,
    @JsonProperty("poster_url")     val afis: String?    = null,
    @JsonProperty("face_url")       val kapak: String?   = null,
    @JsonProperty("release_year")   val yil: Int?        = null,
    @JsonProperty("imdb_point")     val imdb: Double?    = null
)

/**
 * `/api/bg/searchContent` sonuç öğeleri — arama uç noktası farklı alan adları kullanıyor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AramaSonucu(
    @JsonProperty("object_name")         val baslik: String? = null,
    @JsonProperty("used_slug")           val yol: String?    = null,
    @JsonProperty("used_type")           val tur: String?    = null,
    @JsonProperty("object_poster_url")   val afis: String?   = null,
    @JsonProperty("object_release_year") val yil: Int?       = null
)
