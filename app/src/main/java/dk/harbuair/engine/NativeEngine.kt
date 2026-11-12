package dk.harbuair.engine

/** Thin JNI wrapper. One instance == one native engine (sender + loop thread + PCM ring). */
class NativeEngine(private val listener: Listener) {

    interface Listener {
        fun onLaunched(ok: Boolean, error: String)
        fun onClosedByReceiver()
        fun onPinRequired(deviceName: String)
        /** Pairing material. Persist in app-private storage; never log. */
        fun onCredentials(receiverId: String, credsJson: String)
        fun onNativeLog(warn: Boolean, line: String)
    }

    enum class Auth(val ordinal_: Int, val label: String) {
        AIRPLAY1(0, "AirPlay 1 (no auth)"),
        HAP_TRANSIENT(4, "AirPlay 2 transient (HomePod/Mac)"),
        HAP_PIN(5, "AirPlay 2 PIN (Apple TV)"),
    }

    private var handle: Long = nativeCreate(this)

    // Called from the native loop thread. Keep in sync with EventType in native_engine.cpp.
    @Suppress("unused")
    fun onNativeEvent(type: Int, a: String, b: String) {
        when (type) {
            0 -> listener.onLaunched(true, "")
            1 -> listener.onLaunched(false, a)
            2 -> listener.onClosedByReceiver()
            3 -> listener.onPinRequired(a)
            4 -> listener.onCredentials(a, b)
            5 -> listener.onNativeLog(false, a)
            6 -> listener.onNativeLog(true, a)
        }
    }

    fun start(
        host: String, port: Int, receiverName: String, auth: Auth, airplay2: Boolean,
        receiverId: String, credsJson: String, sampleRate: Int, strict: Boolean,
        senderName: String, senderMac: String,
    ) = nativeStart(handle, host, port, receiverName, auth.ordinal_, airplay2, receiverId,
        credsJson, sampleRate, strict, senderName, senderMac)

    fun stop() = nativeStop(handle)
    fun submitPin(pin: String) = nativeSubmitPin(handle, pin)
    fun setVolume(pct: Double) = nativeSetVolume(handle, pct)

    /** @return frames accepted (all-or-nothing); 0 when the ring is full. */
    fun pushPcm(pcm: ShortArray, frames: Int): Int = nativePushPcm(handle, pcm, frames)
    fun ringFreeFrames(): Int = nativeRingFreeFrames(handle)

    /** Sends TEARDOWN (best effort), joins the loop thread. Blocking (~300 ms): call off the UI thread. */
    fun destroy() {
        val h = handle
        handle = 0
        if (h != 0L) nativeDestroy(h)
    }

    private external fun nativeCreate(listener: NativeEngine): Long
    private external fun nativeStart(h: Long, host: String, port: Int, name: String, authMode: Int,
        airplay2: Boolean, receiverId: String, credsJson: String, sampleRate: Int, strict: Boolean,
        senderName: String, senderMac: String)
    private external fun nativeStop(h: Long)
    private external fun nativeSubmitPin(h: Long, pin: String)
    private external fun nativeSetVolume(h: Long, pct: Double)
    private external fun nativePushPcm(h: Long, pcm: ShortArray, frames: Int): Int
    private external fun nativeRingFreeFrames(h: Long): Int
    private external fun nativeDestroy(h: Long)

    companion object {
        init { System.loadLibrary("harbuair_native") }
    }
}
