// ! Bu araç sahincs tarafından yazılmıştır.

package com.sahincs

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class SelcukFlixPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(SelcukFlix())

        // ! Oynatıcı çözücüsü KAYDETMİYORUZ: CloudStream'in kendi Pichive / FourPichive
        // ! extractor'ları (ContentX ailesi) bu host'ları zaten çözüyor. Buraya kendi
        // ! çözücümüzü kaydetmek onları gölgeleyip oynatmayı tamamen bozuyordu.
    }
}
