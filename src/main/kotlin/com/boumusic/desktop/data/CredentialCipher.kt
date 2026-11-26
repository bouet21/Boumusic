package com.boumusic.desktop.data

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado AES-GCM para almacenar de forma segura las credenciales de servidores
 * en el archivo de configuración local (~/.boumusic/config.json).
 */
object CredentialCipher {
    private const val PREFIX = "enc:v1:"
    private val keyBytes = byteArrayOf(
        0x42, 0x6F, 0x75, 0x6D, 0x75, 0x73, 0x69, 0x63,
        0x53, 0x65, 0x63, 0x75, 0x72, 0x65, 0x4B, 0x31
    )

    fun encrypt(plain: String): String {
        if (plain.isBlank() || plain.startsWith(PREFIX)) return plain
        return try {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val key = SecretKeySpec(keyBytes, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = iv + cipherText
            PREFIX + Base64.getEncoder().encodeToString(combined)
        } catch (_: Exception) {
            plain
        }
    }

    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val raw = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
            if (raw.size < 13) return ""
            val iv = raw.sliceArray(0 until 12)
            val cipherText = raw.sliceArray(12 until raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val key = SecretKeySpec(keyBytes, "AES")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (_: Exception) {
            stored
        }
    }
}
