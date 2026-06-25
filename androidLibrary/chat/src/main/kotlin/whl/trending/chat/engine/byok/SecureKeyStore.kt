package whl.trending.chat.engine.byok

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 敏感凭证加密存储：仅承载 BYOK 的 apiKey 一项。
 * 底层用 [EncryptedSharedPreferences]（Android Keystore 托管的 MasterKey 加密落盘）。
 *
 * 由 App 在启动时 [init]（提供 applicationContext）；薄接口便于将来替换实现，调用方零改动。
 */
object SecureKeyStore {

    private const val FILE = "byok_secure_prefs"
    private const val KEY_API_KEY = "byok_api_key"
    private const val TAG = "SecureKeyStore"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.onFailure { Log.e(TAG, "init EncryptedSharedPreferences failed", it) }.getOrNull()
    }

    /** 未初始化或无值时返回空串。 */
    fun getApiKey(): String = prefs?.getString(KEY_API_KEY, "").orEmpty()

    fun setApiKey(value: String) {
        prefs?.edit()?.putString(KEY_API_KEY, value)?.apply()
    }

    fun clear() {
        prefs?.edit()?.remove(KEY_API_KEY)?.apply()
    }
}
