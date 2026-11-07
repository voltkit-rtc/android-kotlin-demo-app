package `in`.voltkit.livedemo.kotlin.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small secrets (the guest's session token), encrypted with an Android Keystore key, as in VoltKit's own Android example. The
 * key never leaves the Keystore, and backups are off in the manifest.
 */
class SecureStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("secure", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun read(name: String): String? {
        val sealed = prefs.getString(name, null) ?: return null
        return try {
            val (iv, ct) = sealed.split(':').map { Base64.decode(it, Base64.NO_WRAP) }
            val c = Cipher.getInstance(TRANSFORMATION)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(c.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            // A key the phone can no longer read, after a restore: start again.
            prefs.edit().remove(name).apply()
            null
        }
    }

    fun write(name: String, value: String) {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key())
        val sealed = Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit().putString(name, sealed).apply()
    }

    fun delete(name: String) = prefs.edit().remove(name).apply()

    private companion object {
        const val ALIAS = "voltkit_live_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
