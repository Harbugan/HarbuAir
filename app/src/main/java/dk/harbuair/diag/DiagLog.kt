package dk.harbuair.diag

import android.util.Log
import java.util.ArrayDeque

/**
 * In-memory diagnostic log (last 300 lines). Never persisted, never sent anywhere.
 * Lines are passed through [redact] as a safety net: long hex/base64-like tokens
 * (possible keys / pairing material) are masked. Callers must still not log secrets.
 */
object DiagLog {
    private const val MAX = 300
    private val lines = ArrayDeque<String>()
    private val listeners = mutableListOf<() -> Unit>()

    private val secretLike = Regex("""[A-Fa-f0-9]{32,}|[A-Za-z0-9+/=_-]{40,}""")

    fun redact(s: String): String = secretLike.replace(s, "<redacted>")

    @Synchronized
    fun add(tag: String, msg: String) {
        val line = "[$tag] ${redact(msg)}"
        Log.i("HarbuAir", line)
        if (lines.size >= MAX) lines.removeFirst()
        lines.addLast(line)
        listeners.toList().forEach { it() }
    }

    @Synchronized fun snapshot(): String = lines.joinToString("\n")
    @Synchronized fun addListener(l: () -> Unit) { listeners += l }
    @Synchronized fun removeListener(l: () -> Unit) { listeners -= l }
}
