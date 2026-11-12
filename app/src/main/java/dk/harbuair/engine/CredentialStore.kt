package dk.harbuair.engine

import android.content.Context
import java.security.SecureRandom

/**
 * App-private storage (allowBackup=false) for pairing credentials and the
 * per-install sender identity. Values are never logged.
 * Hardening hypothesis (not done in Phase 1): wrap with Android Keystore encryption.
 */
class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("harbuair_pairing", Context.MODE_PRIVATE)

    fun get(receiverId: String): String = prefs.getString("creds:$receiverId", "") ?: ""
    fun put(receiverId: String, json: String) = prefs.edit().putString("creds:$receiverId", json).apply()
    fun forget(receiverId: String) = prefs.edit().remove("creds:$receiverId").apply()

    /** Random locally-administered MAC-style id: avoids sending the real hardware address. */
    fun senderMac(): String {
        prefs.getString("sender_mac", null)?.let { return it }
        val b = ByteArray(6).also { SecureRandom().nextBytes(it) }
        b[0] = ((b[0].toInt() or 0x02) and 0xFE).toByte()   // locally administered, unicast
        val mac = b.joinToString(":") { "%02X".format(it) }
        prefs.edit().putString("sender_mac", mac).apply()
        return mac
    }
}
