// ! Bu araç sahincs tarafından yazılmıştır.

package com.sahincs

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class SelcukFlixPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(SelcukFlix())

        // ! Oynatıcı host'ları sık değişiyor — yenisini buraya eklemen yeterli
        registerExtractorAPI(SelcukPlayer("https://pichive.online"))
        registerExtractorAPI(SelcukPlayer("https://four.pichive.online"))
        registerExtractorAPI(SelcukPlayer("https://sn.dplayer82.site"))
    }
}
