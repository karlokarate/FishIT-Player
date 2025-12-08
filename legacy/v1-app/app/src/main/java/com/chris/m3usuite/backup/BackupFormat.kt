package com.chris.m3usuite.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * MSBX backup format with optional AES-256-GCM encryption.
 */
object BackupFormat {
    const val MAGIC = "MSBX"
    const val VERSION: Byte = 1
    private const val FLAG_ENCRYPTED: Byte = 0x1

    @Serializable
    data class Manifest(
        val schema: Int = 1,
        val exportedAtUtc: String,
        val appVersion: String? = null,
        val encrypted: Boolean = false,
    )

    @Serializable
    data class ProfileExport(
        val id: Long,
        val name: String,
        val type: String,
        val avatarFile: String? = null,
    )

    @Serializable
    data class ResumeVodMark(
        val mediaId: Long,
        val positionSecs: Int,
        val updatedAt: Long,
    )

    @Serializable
    data class ResumeEpisodeMark(
        val episodeId: Int,
        val positionSecs: Int,
        val updatedAt: Long,
    )

    @Serializable
    data class Payload(
        val settings: Map<String, String> = emptyMap(),
        val profiles: List<ProfileExport> = emptyList(),
        val resumeVod: List<ResumeVodMark> = emptyList(),
        val resumeEpisodes: List<ResumeEpisodeMark> = emptyList(),
    )

    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    fun zip(
        manifest: Manifest,
        payload: Payload,
        assets: Map<String, ByteArray> = emptyMap(),
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(baos)).use { zos ->
            fun put(
                name: String,
                bytes: ByteArray,
            ) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
            put("manifest.json", json.encodeToString(Manifest.serializer(), manifest).encodeToByteArray())
            put("payload.json", json.encodeToString(Payload.serializer(), payload).encodeToByteArray())
            for ((path, data) in assets) put(path, data)
        }
        return baos.toByteArray()
    }

    fun unzip(bytes: ByteArray): Triple<Manifest, Payload, Map<String, ByteArray>> {
        var man: Manifest? = null
        var pay: Payload? = null
        val assets = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var e = zis.nextEntry
            val buf = ByteArray(16 * 1024)
            while (e != null) {
                val bos = ByteArrayOutputStream()
                var read: Int
                while (zis.read(buf).also { read = it } >= 0) bos.write(buf, 0, read)
                val data = bos.toByteArray()
                when (e.name) {
                    "manifest.json" -> man = json.decodeFromString(Manifest.serializer(), data.decodeToString())
                    "payload.json" -> pay = json.decodeFromString(Payload.serializer(), data.decodeToString())
                    else -> assets[e.name] = data
                }
                e = zis.nextEntry
            }
        }
        return Triple(requireNotNull(man), requireNotNull(pay), assets)
    }

    private fun deriveKey(
        pass: CharArray,
        salt: ByteArray,
        iterations: Int = 120_000,
    ): ByteArray {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pass, salt, iterations, 256)
        return f.generateSecret(spec).encoded
    }

    private fun aesGcmEncrypt(
        zip: ByteArray,
        passphrase: CharArray,
    ): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val enc = cipher.doFinal(zip)
        val out = ByteArrayOutputStream()
        out.write(MAGIC.encodeToByteArray())
        out.write(byteArrayOf(VERSION))
        out.write(byteArrayOf(FLAG_ENCRYPTED))
        out.write(salt)
        out.write(iv)
        out.write(enc)
        return out.toByteArray()
    }

    private fun aesGcmDecrypt(
        envelope: ByteArray,
        passphrase: CharArray,
    ): ByteArray {
        require(envelope.size > 34) { "Invalid MSBX envelope" }
        val magic = envelope.copyOfRange(0, 4).decodeToString()
        require(magic == MAGIC) { "Not an MSBX file" }
        val flags = envelope[5]
        require(flags.toInt() and FLAG_ENCRYPTED.toInt() != 0) { "File not encrypted" }
        val salt = envelope.copyOfRange(6, 22)
        val iv = envelope.copyOfRange(22, 34)
        val payload = envelope.copyOfRange(34, envelope.size)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(payload)
    }

    fun pack(
        manifest: Manifest,
        payload: Payload,
        assets: Map<String, ByteArray>,
        passphrase: CharArray?,
    ): ByteArray {
        val zip = zip(manifest, payload, assets)
        return if (passphrase != null) aesGcmEncrypt(zip, passphrase) else zip
    }

    fun unpack(
        bytes: ByteArray,
        passphrase: CharArray?,
    ): Triple<Manifest, Payload, Map<String, ByteArray>> {
        val isEncrypted =
            bytes.size > 6 && bytes.copyOfRange(0, 4).decodeToString() == MAGIC && bytes[5].toInt() and FLAG_ENCRYPTED.toInt() != 0
        val rawZip = if (isEncrypted) aesGcmDecrypt(bytes, passphrase ?: error("Passphrase required")) else bytes
        return unzip(rawZip)
    }
}
