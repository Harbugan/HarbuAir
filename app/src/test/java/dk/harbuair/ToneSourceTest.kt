package dk.harbuair

import dk.harbuair.audio.ToneChannels
import dk.harbuair.audio.ToneSource
import dk.harbuair.discovery.DeviceType
import dk.harbuair.diag.DiagLog
import org.junit.Assert.*
import org.junit.Test

class ToneSourceTest {
    private fun gen(mode: ToneChannels, frames: Int = 44_100): ShortArray {
        val src = ToneSource(44_100, 440.0, -20.0, mode)
        val out = ShortArray(frames * 2)
        assertEquals(frames, src.read(out, frames))
        return out
    }

    @Test fun bothChannelsIdenticalAndStereoInterleaved() {
        val o = gen(ToneChannels.BOTH)
        for (i in 0 until 44_100) assertEquals(o[2 * i], o[2 * i + 1])
        assertTrue(o.any { it.toInt() != 0 })
    }

    @Test fun leftOnlyLeavesRightSilent() {
        val o = gen(ToneChannels.LEFT)
        for (i in 0 until 44_100) assertEquals(0, o[2 * i + 1].toInt())
        assertTrue((0 until 44_100).any { o[2 * it].toInt() != 0 })
    }

    @Test fun rightOnlyLeavesLeftSilent() {
        val o = gen(ToneChannels.RIGHT)
        for (i in 0 until 44_100) assertEquals(0, o[2 * i].toInt())
        assertTrue((0 until 44_100).any { o[2 * it + 1].toInt() != 0 })
    }

    @Test fun peakIsAboutMinus20Dbfs() {
        val peak = gen(ToneChannels.BOTH).maxOf { kotlin.math.abs(it.toInt()) }
        val dbfs = 20 * kotlin.math.log10(peak / 32767.0)
        assertEquals(-20.0, dbfs, 0.1)
    }

    @Test fun frequencyIs440HzByZeroCrossings() {
        val o = gen(ToneChannels.BOTH)   // exactly 1 second
        var up = 0
        for (i in 1 until 44_100) if (o[2 * (i - 1)] < 0 && o[2 * i] >= 0) up++
        assertEquals(440.0, up.toDouble(), 2.0)
    }

    @Test fun phaseContinuousAcrossReads() {
        val a = ToneSource(); val b = ToneSource()
        val one = ShortArray(2048 * 2); b.read(one, 2048)
        val p1 = ShortArray(1000 * 2); val p2 = ShortArray(1048 * 2)
        a.read(p1, 1000); a.read(p2, 1048)
        for (i in 0 until 1000) assertEquals(one[2 * i], p1[2 * i])
        for (i in 0 until 1048) assertEquals(one[2 * (1000 + i)], p2[2 * i])
    }

    @Test fun deviceTypeFromModel() {
        assertEquals(DeviceType.HOMEPOD, DeviceType.fromModel("AudioAccessory5,1"))
        assertEquals(DeviceType.APPLE_TV, DeviceType.fromModel("AppleTV14,1"))
        assertEquals(DeviceType.OTHER, DeviceType.fromModel(null))
    }

    @Test fun redactionMasksLongSecrets() {
        val hex = "a".repeat(64)
        assertFalse(DiagLog.redact("key=$hex ok").contains(hex))
        assertEquals("pairing ok", DiagLog.redact("pairing ok"))
    }
}
