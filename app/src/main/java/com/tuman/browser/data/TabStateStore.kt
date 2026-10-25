package com.tuman.browser.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedTab(val id: String, val url: String, val title: String, val state: String?)
data class SavedTabs(val activeId: String?, val tabs: List<SavedTab>)

/** Persisted navigation history is encrypted; cookies stay in Gecko private memory. */
class TabStateStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "tabs.v1.enc"))
    private val alias = "tuman.tabs.aes.v1"
    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    fun clear() { file.delete() }
    fun read(): SavedTabs {
        if (!file.baseFile.exists()) return SavedTabs(null, emptyList())
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size in 29..16_777_216) { "Повреждён файл вкладок" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        val list = json.getJSONArray("tabs")
        val tabs = (0 until list.length().coerceAtMost(100)).map { index ->
            val tab = list.getJSONObject(index)
            SavedTab(tab.getString("id"), tab.getString("url"), tab.getString("title"),
                if (tab.isNull("state")) null else tab.getString("state"))
        }.filter { UrlPolicy.allowsNavigation(it.url) }.distinctBy { it.id }
        return SavedTabs(json.optString("active").takeIf { it.isNotBlank() }, tabs)
    }
    fun write(snapshot: SavedTabs) {
        val list = JSONArray()
        snapshot.tabs.forEach { tab ->
            list.put(JSONObject().put("id", tab.id).put("url", tab.url)
                .put("title", tab.title).put("state", tab.state ?: JSONObject.NULL))
        }
        val bytes = JSONObject().put("active", snapshot.activeId).put("tabs", list)
            .toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 16_000_000) { "Превышен размер сохранённых вкладок" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(bytes)
        val stream = file.startWrite()
        try { stream.write(payload); file.finishWrite(stream) }
        catch (failure: Throwable) { file.failWrite(stream); throw failure }
    }
}
