package com.example.walletconnect.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.solana.publickey.SolanaPublicKey
import timber.log.Timber
import java.security.KeyPairGenerator
import java.security.SecureRandom
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters

/**
 * VaultManager - обеспечивает генерацию и безопасное хранение ключей в Android Keystore.
 * Использует EncryptedSharedPreferences для шифрования данных.
 * 
 * Для Solana генерирует пары ключей Ed25519.
 */
object VaultManager {
    private const val PREFS_NAME = "secure_vault_solana"

    // КРИТИЧЕСКАЯ ОПТИМИЗАЦИЯ: Кешируем SharedPreferences
    // Без кеша каждое обращение создает новый MasterKey → обращение к Android Keystore → лаги!
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    /**
     * Возвращает закешированный экземпляр зашифрованных SharedPreferences.
     * Использует double-checked locking для thread-safety.
     */
    private fun getSharedPrefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: createSharedPrefs(context).also { cachedPrefs = it }
        }
    }

    /**
     * Создает новый экземпляр EncryptedSharedPreferences.
     * Обернуто в try-catch для предотвращения вылетов на эмуляторах с проблемным Keystore.
     */
    private fun createSharedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isKeystoreAvailable(context: Context): Boolean {
        return try {
            getSharedPrefs(context)
            true
        } catch (e: Exception) {
            Timber.e(e, "Keystore unavailable")
            false
        }
    }

    /**
     * Генерирует новую пару ключей Ed25519 (Solana) и сохраняет их.
     * Формат: Ключ = Base58 публичный ключ, Значение = Hex приватный ключ (64 bytes seed + public).
     * @return Пара (PublicKey Base58, PrivateKey Hex)
     */
    fun generateAndSaveKeyPair(context: Context): Pair<String, String> {
        return try {
            // Генерируем Ed25519 ключевую пару с помощью BouncyCastle
            val keyPairGenerator = Ed25519KeyPairGenerator()
            keyPairGenerator.init(Ed25519KeyGenerationParameters(SecureRandom()))
            val keyPair = keyPairGenerator.generateKeyPair()
            
            val privateKey = keyPair.private as Ed25519PrivateKeyParameters
            val publicKey = keyPair.public as Ed25519PublicKeyParameters
            
            // Получаем байты ключей
            val privateKeyBytes = privateKey.encoded  // 32 bytes seed
            val publicKeyBytes = publicKey.encoded    // 32 bytes public key
            
            // Формируем полный keypair (64 bytes: seed + public)
            val fullKeypair = privateKeyBytes + publicKeyBytes
            val privateKeyHex = fullKeypair.joinToString("") { "%02x".format(it) }
            
            // Конвертируем публичный ключ в Base58 (формат Solana адреса)
            val publicKeyBase58 = SolanaPublicKey(publicKeyBytes).base58()

            getSharedPrefs(context).edit().apply {
                // Сохраняем: PublicKey (Base58) -> PrivateKey (Hex)
                putString(publicKeyBase58, privateKeyHex)
                apply()
            }

            Timber.d("🔐 Сгенерированы и сохранены ключи Solana: Address=$publicKeyBase58")
            Pair(publicKeyBase58, privateKeyHex)
        } catch (e: Exception) {
            Timber.e(e, "🚨 Ошибка генерации или сохранения ключей")
            Pair("Error", "Ошибка: ${e.message}")
        }
    }

    /**
     * Возвращает список всех сохраненных адресов (публичных ключей в Base58).
     */
    fun getAllAddresses(context: Context): List<String> {
        return getSharedPrefs(context).all.keys.toList()
    }

    /**
     * Возвращает приватный ключ (hex) для конкретного публичного ключа (base58).
     */
    fun getPrivateKey(context: Context, address: String): String? {
        return getSharedPrefs(context).getString(address, null)
    }

    /**
     * Возвращает полный keypair (64 bytes) для подписи.
     * @return ByteArray из 64 байт (seed + public key) или null
     */
    fun getKeypairBytes(context: Context, address: String): ByteArray? {
        val privateKeyHex = getPrivateKey(context, address) ?: return null
        return try {
            privateKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            Timber.e(e, "Ошибка преобразования ключа")
            null
        }
    }

    /**
     * Подписывает сообщение с помощью Ed25519.
     * @param context Контекст приложения
     * @param address Публичный ключ (Base58)
     * @param message Сообщение для подписи
     * @return Подпись (64 bytes) или null
     */
    fun sign(context: Context, address: String, message: ByteArray): ByteArray? {
        val privateKeyHex = getPrivateKey(context, address) ?: return null
        return try {
            val keypairBytes = privateKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val seed = keypairBytes.take(32).toByteArray()
            
            val privateKey = Ed25519PrivateKeyParameters(seed, 0)
            val signer = org.bouncycastle.crypto.signers.Ed25519Signer()
            signer.init(true, privateKey)
            signer.update(message, 0, message.size)
            signer.generateSignature()
        } catch (e: Exception) {
            Timber.e(e, "Ошибка подписи")
            null
        }
    }
}

