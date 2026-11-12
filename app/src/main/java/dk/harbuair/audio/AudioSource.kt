package dk.harbuair.audio

/**
 * Pluggable PCM producer. The AirPlay engine only ever sees interleaved
 * signed-16-bit STEREO frames, so a future USB capture source (AudioRecord on a
 * USB device) implements this same interface and nothing in engine/ changes.
 *
 * Contract: fill [out] with up to [maxFrames] stereo frames (out.size >= 2*maxFrames),
 * return frames written. Must not downmix to mono.
 */
interface AudioSource {
    val sampleRate: Int
    val channels: Int get() = 2
    fun read(out: ShortArray, maxFrames: Int): Int
    fun close() {}
}

enum class ToneChannels(val label: String) {
    BOTH("Both (L+R)"), LEFT("Left only"), RIGHT("Right only")
}

/** Generated sine test tone. Phase-continuous; no I/O. */
class ToneSource(
    override val sampleRate: Int = 44_100,
    private val frequencyHz: Double = 440.0,
    private val amplitudeDbfs: Double = -20.0,
    private val channelsMode: ToneChannels = ToneChannels.BOTH,
) : AudioSource {
    private var phase = 0.0
    private val step = 2.0 * Math.PI * frequencyHz / sampleRate
    private val amp = Math.pow(10.0, amplitudeDbfs / 20.0) * Short.MAX_VALUE

    override fun read(out: ShortArray, maxFrames: Int): Int {
        for (i in 0 until maxFrames) {
            val v = (Math.sin(phase) * amp).toInt().toShort()
            phase += step
            if (phase >= 2.0 * Math.PI) phase -= 2.0 * Math.PI
            out[2 * i] = if (channelsMode == ToneChannels.RIGHT) 0 else v
            out[2 * i + 1] = if (channelsMode == ToneChannels.LEFT) 0 else v
        }
        return maxFrames
    }
}
