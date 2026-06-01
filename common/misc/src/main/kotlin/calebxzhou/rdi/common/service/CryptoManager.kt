package calebxzhou.rdi.common.service

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * calebxzhou @ 2026-02-09 21:39
 */

object CryptoManager {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private val KEY = stringToKey("rdiRDIrdi")
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    // 1. Generate a secure key (Store this safely!)
    fun generateKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256) // Use 256-bit AES
        return keyGen.generateKey()
    }
    fun stringToKey(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = password.toByteArray(Charsets.UTF_8)
        val keyBytes = digest.digest(bytes) // This will always be 32 bytes
        return SecretKeySpec(keyBytes, "AES")
    }
    // 2. Encrypt a string
    fun encrypt(data: String, secretKey: SecretKey =KEY): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv // Get the randomly generated IV
        val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        // Combine IV and CipherText so we have the IV for decryption later
        val combined = iv + encryptedBytes
        return Base64.getEncoder().encodeToString(combined)
    }

    // 3. Decrypt a string
    fun decrypt(encryptedData: String, secretKey: SecretKey =KEY): String {
        val combined = Base64.getDecoder().decode(encryptedData)

        // Extract IV from the start of the array
        val iv = combined.sliceArray(0 until IV_LENGTH_BYTE)
        // Extract the actual encrypted content
        val encryptedBytes = combined.sliceArray(IV_LENGTH_BYTE until combined.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
    }
}
