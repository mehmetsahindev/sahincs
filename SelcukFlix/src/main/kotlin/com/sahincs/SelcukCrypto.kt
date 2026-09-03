// ! Bu araç sahincs tarafından yazılmıştır.

package com.sahincs

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * SelcukFlix, hem sayfa içindeki `__NEXT_DATA__` verisini hem de `/api/bg/` altındaki
 * uç nokta yanıtlarını AES ile şifreleyip base64 olarak gönderiyor.
 *
 * Şema, sitenin `_app.js` paketindeki 379 numaralı modülden çıkarıldı:
 *   anahtar = base64(sha256(GIZLI)).substring(0, 32)
 *   iv      = 16 bayt sıfır
 *   şifre   = AES-256-CBC / PKCS5
 *
 * ! GIZLI değişirse (site güncellemesi) tek düzeltmen gereken yer burası.
 */
object SelcukCrypto {
    private const val GIZLI = "!!22xx!!90!!"

    private val anahtar: ByteArray by lazy {
        val ozet = MessageDigest.getInstance("SHA-256").digest(GIZLI.toByteArray(Charsets.UTF_8))

        Base64.encodeToString(ozet, Base64.NO_WRAP).substring(0, 32).toByteArray(Charsets.UTF_8)
    }

    private val iv = ByteArray(16)

    fun coz(sifreli: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(anahtar, "AES"), IvParameterSpec(iv))

        return String(cipher.doFinal(Base64.decode(sifreli, Base64.DEFAULT)), Charsets.UTF_8)
    }
}
