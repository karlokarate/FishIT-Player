package com.chris.m3usuite.telegram

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Generates and stores a random 32‑byte TDLib database encryption key.
 * The random key is wrapped with an Android Keystore AES‑GCM key and persisted in app prefs.
 */
object TelegramKeyStore {
    private const val KS_ALIAS = "tdlib_db_wrapper"
    private const val PREFS = "tg_keystore"
    private const val KEY_ENC = "enc"
    private const val KEY_IV = "iv"
    private const val KEY_INSTALL_ID = "install_id"

    fun getOrCreateDatabaseKey(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enc = prefs.getString(KEY_ENC, null)
        val iv = prefs.getString(KEY_IV, null)
        return try {
            val ksKey = getOrCreateKsKey()
            if (enc != null && iv != null) {
                decrypt(ksKey, enc.decodeBase64(), iv.decodeBase64())
            } else {
                val rnd = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                val sealed = encrypt(ksKey, rnd)
                prefs.edit().putString(KEY_ENC, sealed.first.encodeBase64()).putString(KEY_IV, sealed.second.encodeBase64()).apply()
                rnd
            }
        } catch (_: Throwable) {
            // Fallback: ephemeral key (not persisted) if Keystore fails
            ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        }
    }

    fun getOrCreateInstallId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_INSTALL_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val id = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, id).apply()
        return id
    }

    private fun getOrCreateKsKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getKey(KS_ALIAS, null)?.let { return it as SecretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KS_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).run {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setRandomizedEncryptionRequired(true)
            if (Build.VERSION.SDK_INT >= 28) {
                setIsStrongBoxBacked(false)
            }
            build()
        }
        gen.init(spec)
        return gen.generateKey()
    }

    private fun encrypt(key: SecretKey, plain: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val enc = cipher.doFinal(plain)
        return enc to cipher.iv
    }

    private fun decrypt(key: SecretKey, enc: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(enc)
    }

    private fun String.decodeBase64(): ByteArray = android.util.Base64.decode(this, android.util.Base64.DEFAULT)
    private fun ByteArray.encodeBase64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
}

