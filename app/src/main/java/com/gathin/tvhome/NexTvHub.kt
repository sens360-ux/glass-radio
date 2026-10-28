package com.gathin.tvhome

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** NexTV Hub pairing and radio API; tokens are encrypted with Android Keystore. */
internal class NexTvHub(private val context: Context) {
    private val base = "https://hub.nexdata.my"
    private val prefs = context.getSharedPreferences("nextv_hub", Context.MODE_PRIVATE)
    private val alias = "glass_tv_nextv_hub_token"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun token(): String? = runCatching {
        val raw = Base64.decode(prefs.getString("token", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    }.getOrNull()
    fun saveToken(value: String): Boolean = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit().putString("token", encoded).commit()
    }.getOrDefault(false)
    fun clearToken() { prefs.edit().remove("token").apply() }
    private fun installationId(): String {
        prefs.getString("installation_id", null)?.let { return it }
        return UUID.randomUUID().toString().also { prefs.edit().putString("installation_id", it).apply() }
    }
    private fun request(path: String, method: String = "GET", body: JSONObject? = null, bearer: String? = null): Pair<Int, String> {
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 12000
            setRequestProperty("Accept", "application/json")
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            val status = conn.responseCode
            status to (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally { conn.disconnect() }
    }
    fun requestPairing(): Pair<String, Int> {
        val body = JSONObject().put("installation_id", installationId())
            .put("name", "Glass TV").put("model", Build.MODEL ?: "Android TV")
        val (status, raw) = request("/api/v1/devices/pairings", "POST", body)
        if (status != 201) error("Hub pairing HTTP $status")
        val json = JSONObject(raw)
        return json.getString("code") to json.getInt("expires_in")
    }
    fun pollPairing(code: String): String? {
        require(Regex("^[A-Z0-9-]{1,32}$").matches(code))
        val (status, raw) = request("/api/v1/devices/pairings/$code")
        if (status == 202) return null
        if (status != 200) error("Hub pairing HTTP $status")
        val json = JSONObject(raw)
        return if (json.optString("status") == "approved") json.optString("token").takeIf { it.isNotBlank() } else null
    }
    fun radio(): List<NexTvStation> {
        val bearer = token() ?: error("Pairing required")
        val (status, raw) = request("/api/v1/tv/radio", bearer = bearer)
        if (status == 401 || status == 403) { clearToken(); error("Pairing expired. Pair again.") }
        if (status != 200) error("Hub radio HTTP $status")
        val json = JSONObject(raw)
        if (!json.optBoolean("available")) error("Radio not available on Hub")
        val rows = json.optJSONArray("stations") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { i ->
            val s = rows.optJSONObject(i) ?: return@mapNotNull null
            val stream = s.optString("stream")
            if (!stream.startsWith("https://") && !stream.startsWith("http://")) return@mapNotNull null
            val name = s.optString("name").trim()
            if (name.isBlank()) return@mapNotNull null
            NexTvStation(
                id = s.optString("id").ifBlank { stream },
                name = name, stream = stream,
                tagline = s.optString("tagline"),
                logo = s.optString("logo").takeIf { it.startsWith("http") },
                group = s.optString("group"),
                bitrate = s.optInt("bitrate").takeIf { it > 0 },
            )
        }
    }
}
