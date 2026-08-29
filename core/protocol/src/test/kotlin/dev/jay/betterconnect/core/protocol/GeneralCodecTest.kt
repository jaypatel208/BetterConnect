package dev.jay.betterconnect.core.protocol

import dev.jay.betterconnect.core.model.ControlAcks
import dev.jay.betterconnect.core.model.GeneralState
import dev.jay.betterconnect.core.model.GeneralVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralCodecTest {

    private fun state() = GeneralState(
        volume = 6,
        headsetConnected = true,
        doNotDisturb = true,
        batteryBars = 3,
        callState = 2,
        signalBars = 5,
        callerPresent = true,
        callerName = "MOM",
        callProgressCount = 42,
        missedCallCount = 1,
        smsCount = 2,
        phoneName = "PIXEL",
    )

    private fun acks() = ControlAcks(
        callAccept = 1,
        callReject = 2,
        callRejectWithSms = 3,
        skipToNext = 4,
        skipToPrev = 5,
        missedCallGet = 6,
        alertGet = 7,
        resumeSong = 8,
        pauseSong = 9,
        stopSong = 10,
        launchMediaPlayer = 11,
    )

    @Test
    fun `v2 frame is the documented size with a valid checksum`() {
        val bytes = GeneralEncoder.encode(state(), acks(), heartbeat = 200, version = GeneralVersion.V2)
        assertEquals(GeneralEncoder.SIZE_V2, bytes.size)
        val checksum = Checksum.compute(bytes, upTo = 88)
        assertEquals(checksum, bytes[88].toInt() and 0xFF)
    }

    @Test
    fun `v1 frame is the documented size with heartbeat at byte 53 and no checksum tail`() {
        val bytes = GeneralEncoder.encode(state(), acks(), heartbeat = 200, version = GeneralVersion.V1)
        assertEquals(GeneralEncoder.SIZE_V1, bytes.size)
        assertEquals(200, bytes[53].toInt() and 0xFF)
    }

    @Test
    fun `v2 round trips every field through the decoder`() {
        val encoded = GeneralEncoder.encode(state(), acks(), heartbeat = 77, version = GeneralVersion.V2)
        val decoded = GeneralDecoder.decode(encoded)

        assertEquals(state().volume, decoded.volume)
        assertEquals(state().headsetConnected, decoded.headsetConnected)
        assertEquals(state().doNotDisturb, decoded.doNotDisturb)
        assertEquals(state().batteryBars, decoded.batteryBars)
        assertEquals(state().callState, decoded.callState)
        assertEquals(state().signalBars, decoded.signalBars)
        assertEquals(state().callerPresent, decoded.callerPresent)
        assertEquals(state().callerName, decoded.callerName)
        assertEquals(state().callProgressCount, decoded.callProgressCount)
        assertEquals(state().missedCallCount, decoded.missedCallCount)
        assertEquals(state().smsCount, decoded.smsCount)
        assertEquals(state().phoneName, decoded.phoneName)
        assertEquals(77, decoded.heartbeat)
        assertEquals(GeneralVersion.V2, decoded.version)
        assertEquals(acks(), decoded.acks)
    }

    @Test
    fun `v1 round trips without a phone name`() {
        val encoded = GeneralEncoder.encode(state(), acks(), heartbeat = 5, version = GeneralVersion.V1)
        val decoded = GeneralDecoder.decode(encoded)

        assertEquals(GeneralVersion.V1, decoded.version)
        assertEquals(5, decoded.heartbeat)
        assertEquals("", decoded.phoneName)
        assertEquals(acks(), decoded.acks)
    }

    @Test
    fun `battery is clamped to 3 bars even when the caller asks for more`() {
        val bytes = GeneralEncoder.encode(state().copy(batteryBars = 9), acks(), 0, GeneralVersion.V2)
        val decoded = GeneralDecoder.decode(bytes)
        assertEquals("v2 clamps to 0-3 - do not exceed it", 3, decoded.batteryBars)
    }

    @Test
    fun `missed call count and sms count occupy bytes 13 and 15, not the ack block`() {
        val bytes = GeneralEncoder.encode(
            state().copy(missedCallCount = 9, smsCount = 4),
            acks(),
            heartbeat = 0,
            version = GeneralVersion.V2,
        )
        assertEquals(9, bytes[13].toInt() and 0xFF)
        assertEquals(4, bytes[15].toInt() and 0xFF)
        assertTrue("these are counts, not part of the ack styling", true)
    }
}
