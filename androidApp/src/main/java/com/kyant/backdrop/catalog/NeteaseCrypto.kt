package com.kyant.backdrop.catalog

import android.util.Base64
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Netease web/eapi payload encryption.
 *
 * The official app endpoints accept two shapes:
 *  - weapi: double AES-CBC + RSA-wrapped random secret (music.163.com/weapi/...)
 *  - eapi : AES-ECB over "path-36cd479b6b5-json-36cd479b6b5-md5" (interface.music.163.com/eapi/...)
 *
 * Implementing them locally lets the app talk to music.163.com directly, so a
 * phone no longer needs a PC-side Node proxy (and adb reverse) to log in.
 */
internal object NeteaseCrypto {

    private const val IV = "0102030405060708"
    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val PUB_EXP = "010001"
    private const val MODULUS =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa" +
            "76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b" +
            "7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462" +
            "db0a22b8e7"

    private fun aesCbcBase64(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(IV.toByteArray(Charsets.UTF_8))
        )
        return Base64.encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun aesEcbHex(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(text.toByteArray(Charsets.UTF_8)).joinToString("") {
            "%02X".format(it.toInt() and 0xFF)
        }
    }

    fun md5Hex(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    data class Weapi(val params: String, val encSecKey: String)

    fun weapi(json: String): Weapi {
        val secret = buildString { repeat(16) { append(BASE62[Random.nextInt(BASE62.length)]) } }
        val params = aesCbcBase64(aesCbcBase64(json, PRESET_KEY), secret)
        val reversed = secret.reversed().toByteArray(Charsets.UTF_8)
        val message = BigInteger(1, reversed)
        val encrypted = message.modPow(
            BigInteger(PUB_EXP, 16),
            BigInteger(MODULUS, 16)
        )
        val hex = encrypted.toString(16).padStart(256, '0')
        return Weapi(params, hex)
    }

    fun eapi(path: String, json: String): String {
        val digest = md5Hex("nobody${path}use${json}md5forencrypt")
        val data = "$path-36cd479b6b5-$json-36cd479b6b5-$digest"
        return aesEcbHex(data, EAPI_KEY)
    }
}
