package dk.harbuair.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dk.harbuair.audio.AudioSource
import dk.harbuair.audio.ToneChannels
import dk.harbuair.audio.ToneSource
import dk.harbuair.diag.DiagLog
import dk.harbuair.discovery.AirPlayDiscovery
import dk.harbuair.discovery.DeviceType
import dk.harbuair.discovery.Receiver
import dk.harbuair.engine.CredentialStore
import dk.harbuair.engine.NativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ConnState { IDLE, CONNECTING, PIN_REQUIRED, STREAMING, ERROR, CLOSED_BY_RECEIVER }

data class UiState(
    val receivers: List<Receiver> = emptyList(),
    val selected: Receiver? = null,
    val auth: NativeEngine.Auth? = null,          // null = auto from device type
    val conn: ConnState = ConnState.IDLE,
    val pairing: String = "not paired in this session",
    val format: String = "-",
    val error: String? = null,
    val volumePct: Int = 20,
    val tone: ToneChannels = ToneChannels.BOTH,
    val strict: Boolean = false,
) {
    val effectiveAuth: NativeEngine.Auth
        get() = auth ?: when (selected?.type) {
            DeviceType.APPLE_TV -> NativeEngine.Auth.HAP_PIN
            else -> NativeEngine.Auth.HAP_TRANSIENT
        }
}

/**
 * Owns discovery + one streaming session. All streaming is stopped when the UI
 * goes to the background (Activity.onStop) or the ViewModel is cleared: Phase 1
 * deliberately has no foreground service, so nothing can keep running unseen.
 */
class StreamViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val store = CredentialStore(app)
    private val discovery = AirPlayDiscovery(app) { list ->
        _state.update { s ->
            val sel = s.selected?.let { cur -> list.firstOrNull { it.key == cur.key } ?: cur }
            s.copy(receivers = list, selected = sel)
        }
    }
    private var engine: NativeEngine? = null
    private var pumpJob: Job? = null
    private var pendingPinDevice: String? = null

    fun startDiscovery() = discovery.start()
    fun stopDiscovery() = discovery.stop()

    fun select(r: Receiver) {
        _state.update { it.copy(selected = r, error = null) }
        DiagLog.add("ui", "selected '${r.name}' type=${r.type.label} model=${r.model} host=${r.host} port=${r.port} " +
            "gid=${r.groupId} group-leader-flag=${r.groupLeaderFlag} features=${r.features} flags=${r.statusFlags}")
    }
    fun setAuth(a: NativeEngine.Auth?) = _state.update { it.copy(auth = a) }
    fun setTone(t: ToneChannels) = _state.update { it.copy(tone = t) }
    fun setStrict(v: Boolean) = _state.update { it.copy(strict = v) }
    fun forgetPairing() {
        _state.value.selected?.let { store.forget(it.name); DiagLog.add("pairing", "stored credentials for '${it.name}' removed") }
        _state.update { it.copy(pairing = "stored credentials removed") }
    }

    fun setVolume(pct: Int) {
        _state.update { it.copy(volumePct = pct) }
        engine?.setVolume(pct.toDouble())
    }

    fun start() {
        val s = _state.value
        val r = s.selected ?: return fail("No receiver selected")
        val host = r.host ?: return fail("Receiver has no resolved IPv4 address yet")
        val port = r.port ?: return fail("Receiver advertises no port")
        if (engine != null) return
        if (r.passwordRequired) DiagLog.add("warn", "receiver advertises a password (pw=true); password auth is not wired in Phase 1")

        val source: AudioSource = ToneSource(channelsMode = s.tone)
        val auth = s.effectiveAuth
        _state.update { it.copy(conn = ConnState.CONNECTING, error = null,
            format = "${source.sampleRate} Hz, 16-bit, ${source.channels} ch (library resamples to 44100 Hz ALAC)",
            pairing = if (store.get(r.name).isNotEmpty()) "stored credentials found" else "no stored credentials") }
        DiagLog.add("engine", "connecting to ${r.name} @ $host:$port auth=${auth.label} tone=${s.tone.label}")

        val e = NativeEngine(listener(r))
        engine = e
        e.start(host, port, r.name, auth, airplay2 = auth != NativeEngine.Auth.AIRPLAY1,
            receiverId = r.name, credsJson = store.get(r.name), sampleRate = source.sampleRate,
            strict = s.strict, senderName = "HarbuAir", senderMac = store.senderMac())
        pumpJob = viewModelScope.launch(Dispatchers.Default) { pump(e, source) }
    }

    /** Real-time paced producer: keeps ~0.5 s of PCM ahead of the clock, pushes into the native ring. */
    private suspend fun pump(e: NativeEngine, source: AudioSource) {
        val chunk = 1024
        val buf = ShortArray(chunk * 2)
        val t0 = System.nanoTime()
        var produced = 0L
        var dropped = 0L
        try {
            while (currentCoroutineContext().isActive) {
                val elapsed = (System.nanoTime() - t0) / 1e9
                val target = ((elapsed + 0.5) * source.sampleRate).toLong()
                if (produced + chunk > target) { delay(5); continue }
                val n = source.read(buf, chunk)
                if (n <= 0) break
                if (e.pushPcm(buf, n) == 0) { dropped += n; delay(5) } else produced += n
            }
        } finally {
            source.close()
            if (dropped > 0) DiagLog.add("audio", "ring full: dropped $dropped frames")
        }
    }

    fun submitPin(pin: String) {
        engine?.submitPin(pin)
        _state.update { it.copy(conn = ConnState.CONNECTING, pairing = "PIN submitted, pairing...") }
    }

    fun stop() {
        val e = engine ?: return
        engine = null
        pumpJob?.cancel()
        pumpJob = null
        _state.update { it.copy(conn = ConnState.IDLE, format = "-") }
        DiagLog.add("engine", "stopping (TEARDOWN)")
        viewModelScope.launch(Dispatchers.IO) {
            e.destroy()   // blocking ~300 ms: flushes TEARDOWN, joins the native loop thread
            DiagLog.add("engine", "stopped")
        }
    }

    private fun fail(msg: String) { _state.update { it.copy(conn = ConnState.ERROR, error = msg) }; DiagLog.add("error", msg) }

    private fun listener(r: Receiver) = object : NativeEngine.Listener {
        override fun onLaunched(ok: Boolean, error: String) {
            if (ok) {
                _state.update { it.copy(conn = ConnState.STREAMING, error = null, pairing = "session established") }
                engine?.setVolume(_state.value.volumePct.toDouble())
                DiagLog.add("engine", "streaming started")
            } else {
                val hint = if (error.contains("Timed out", true))
                    " (receiver could not reach our UDP ports, or the pairing mode is wrong; see docs/TESTING.md)" else ""
                viewModelScope.launch { stopWithError("Connection failed: $error$hint") }
            }
        }
        override fun onClosedByReceiver() {
            viewModelScope.launch { stopWithError("Session closed by receiver", ConnState.CLOSED_BY_RECEIVER) }
        }
        override fun onPinRequired(deviceName: String) {
            pendingPinDevice = deviceName
            _state.update { it.copy(conn = ConnState.PIN_REQUIRED, pairing = "PIN required (shown on $deviceName)") }
        }
        override fun onCredentials(receiverId: String, credsJson: String) {
            store.put(r.name, credsJson)   // never logged
            _state.update { it.copy(pairing = "paired; credentials stored (app-private)") }
            DiagLog.add("pairing", "long-term credentials obtained and stored (content not logged)")
        }
        override fun onNativeLog(warn: Boolean, line: String) = DiagLog.add(if (warn) "native/warn" else "native", line)
    }

    private fun stopWithError(msg: String, state: ConnState = ConnState.ERROR) {
        DiagLog.add("error", msg)
        val e = engine
        engine = null
        pumpJob?.cancel(); pumpJob = null
        _state.update { it.copy(conn = state, error = msg, format = "-") }
        if (e != null) viewModelScope.launch(Dispatchers.IO) { e.destroy() }
    }

    override fun onCleared() {
        discovery.stop()
        pumpJob?.cancel()
        val e = engine; engine = null
        // viewModelScope is cancelled here; destroy on a plain thread so TEARDOWN still goes out.
        if (e != null) Thread { e.destroy() }.start()
    }
}
