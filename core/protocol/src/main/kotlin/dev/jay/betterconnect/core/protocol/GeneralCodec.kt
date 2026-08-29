package dev.jay.betterconnect.core.protocol

import dev.jay.betterconnect.core.model.ControlAcks
import dev.jay.betterconnect.core.model.GeneralState
import dev.jay.betterconnect.core.model.GeneralVersion
import kotlin.math.min

/**
 * Builds the `GENERAL` (`0210`) packet - phone to cluster, on a 1000 ms timer and on demand
 * at connect / MTU change / call-state change. Rebuilt fresh at transmission time so the
 * heartbeat advances per actual send, not per enqueue. See `docs/PROTOCOL.md` §6.
 *
 * **The acknowledgement sub-mapping inside bytes 4-17 is `[inferred]`.** The doc gives the
 * two fixed points it confirmed from the native code - byte 13 is `missedCallCount`, byte 15
 * is `smsCount`, both counts rather than acks - and says the remaining span is "the
 * acknowledgement block, see §5" without publishing an exact per-field offset table. The
 * mapping below fills the other twelve bytes with the eleven acked `CONTROL` fields in the
 * same relative order §5 lists them, which is a reasonable placement but **not confirmed on
 * hardware**. If a re-test shows a specific rider action failing to acknowledge, this mapping
 * is the first thing to re-derive.
 */
object GeneralEncoder {

    const val SIZE_V1: Int = 55
    const val SIZE_V2: Int = 89
    const val CALLER_NAME_LEN: Int = 32
    const val PHONE_NAME_LEN: Int = 32

    fun encode(
        state: GeneralState,
        acks: ControlAcks,
        heartbeat: Int,
        version: GeneralVersion,
    ): ByteArray {
        val size = if (version == GeneralVersion.V1) SIZE_V1 else SIZE_V2
        val buffer = ByteArray(size)

        buffer[0] = ((state.volume and 0x0F) or (boolBit(state.headsetConnected, 4)) or 0xC0).toByte()
        buffer[1] = (
            boolBit(state.doNotDisturb, 7) or
                ((min(state.batteryBars, 3) and 0x03) shl 4) or
                (state.callState and 0x0F)
            ).toByte()
        buffer[2] = ((state.signalBars and 0x07)).toByte()
        buffer[3] = 0

        writeAckBlock(buffer, acks, state)

        buffer[18] = if (state.callerPresent) 1 else 0
        val callerBytes = state.callerName.toByteArray(Charsets.UTF_8).copyOf(CALLER_NAME_LEN)
        buffer[19] = min(state.callerName.toByteArray(Charsets.UTF_8).size, CALLER_NAME_LEN).toByte()
        callerBytes.copyInto(buffer, destinationOffset = 20)

        if (version == GeneralVersion.V1) {
            // [inferred] - the doc does not state the v1 width of callProgressCount; a
            // single byte is assumed so the heartbeat lands at 53 as documented.
            buffer[52] = (state.callProgressCount and 0xFF).toByte()
            buffer[53] = (heartbeat and 0xFF).toByte()
            // buffer[54] reserved, left zero.
        } else {
            buffer[52] = (state.callProgressCount and 0xFF).toByte()
            buffer[53] = ((state.callProgressCount shr 8) and 0xFF).toByte()
            buffer[54] = (heartbeat and 0xFF).toByte()
            val phoneBytes = state.phoneName.toByteArray(Charsets.UTF_8).copyOf(PHONE_NAME_LEN)
            buffer[55] = min(state.phoneName.toByteArray(Charsets.UTF_8).size, PHONE_NAME_LEN).toByte()
            phoneBytes.copyInto(buffer, destinationOffset = 56)
            buffer[CHECKSUM_INDEX_V2] = Checksum.compute(buffer, upTo = CHECKSUM_INDEX_V2).toByte()
        }

        return buffer
    }

    private fun writeAckBlock(buffer: ByteArray, acks: ControlAcks, state: GeneralState) {
        buffer[4] = acks.callAccept.toByte()
        buffer[5] = acks.callReject.toByte()
        buffer[6] = acks.callRejectWithSms.toByte()
        buffer[7] = acks.skipToNext.toByte()
        buffer[8] = acks.skipToPrev.toByte()
        buffer[9] = acks.missedCallGet.toByte()
        buffer[10] = acks.alertGet.toByte()
        buffer[11] = acks.resumeSong.toByte()
        buffer[12] = acks.pauseSong.toByte()
        buffer[13] = (state.missedCallCount and 0xFF).toByte()
        buffer[14] = acks.stopSong.toByte()
        buffer[15] = (state.smsCount and 0xFF).toByte()
        buffer[16] = acks.launchMediaPlayer.toByte()
        // buffer[17] reserved, left zero.
    }

    private fun boolBit(value: Boolean, shift: Int): Int = if (value) (1 shl shift) else 0

    private const val CHECKSUM_INDEX_V2 = 88
}

data class DecodedGeneral(
    val volume: Int,
    val headsetConnected: Boolean,
    val doNotDisturb: Boolean,
    val batteryBars: Int,
    val callState: Int,
    val signalBars: Int,
    val acks: ControlAcks,
    val missedCallCount: Int,
    val smsCount: Int,
    val callerPresent: Boolean,
    val callerName: String,
    val callProgressCount: Int,
    val heartbeat: Int,
    val phoneName: String,
    val version: GeneralVersion,
)

/** Test-only round-trip partner for [GeneralEncoder]. Nothing in production reads GENERAL back. */
object GeneralDecoder {
    fun decode(bytes: ByteArray): DecodedGeneral {
        val version = when (bytes.size) {
            GeneralEncoder.SIZE_V1 -> GeneralVersion.V1
            GeneralEncoder.SIZE_V2 -> GeneralVersion.V2
            else -> error(
                "GENERAL frame must be ${GeneralEncoder.SIZE_V1} or ${GeneralEncoder.SIZE_V2} bytes",
            )
        }
        val callerLen = (bytes[19].toInt() and 0xFF).coerceAtMost(GeneralEncoder.CALLER_NAME_LEN)
        val callerName = String(bytes, 20, callerLen, Charsets.UTF_8)

        val callProgressCount: Int
        val heartbeat: Int
        var phoneName = ""
        if (version == GeneralVersion.V1) {
            callProgressCount = bytes[52].toInt() and 0xFF
            heartbeat = bytes[53].toInt() and 0xFF
        } else {
            callProgressCount = (bytes[52].toInt() and 0xFF) or ((bytes[53].toInt() and 0xFF) shl 8)
            heartbeat = bytes[54].toInt() and 0xFF
            val phoneLen = (bytes[55].toInt() and 0xFF).coerceAtMost(GeneralEncoder.PHONE_NAME_LEN)
            phoneName = String(bytes, 56, phoneLen, Charsets.UTF_8)
        }

        return DecodedGeneral(
            volume = bytes[0].toInt() and 0x0F,
            headsetConnected = (bytes[0].toInt() and 0x10) != 0,
            doNotDisturb = (bytes[1].toInt() and 0x80) != 0,
            batteryBars = (bytes[1].toInt() shr 4) and 0x03,
            callState = bytes[1].toInt() and 0x0F,
            signalBars = bytes[2].toInt() and 0x07,
            acks = ControlAcks(
                callAccept = bytes[4].toInt() and 0xFF,
                callReject = bytes[5].toInt() and 0xFF,
                callRejectWithSms = bytes[6].toInt() and 0xFF,
                skipToNext = bytes[7].toInt() and 0xFF,
                skipToPrev = bytes[8].toInt() and 0xFF,
                missedCallGet = bytes[9].toInt() and 0xFF,
                alertGet = bytes[10].toInt() and 0xFF,
                resumeSong = bytes[11].toInt() and 0xFF,
                pauseSong = bytes[12].toInt() and 0xFF,
                stopSong = bytes[14].toInt() and 0xFF,
                launchMediaPlayer = bytes[16].toInt() and 0xFF,
            ),
            missedCallCount = bytes[13].toInt() and 0xFF,
            smsCount = bytes[15].toInt() and 0xFF,
            callerPresent = bytes[18].toInt() != 0,
            callerName = callerName,
            callProgressCount = callProgressCount,
            heartbeat = heartbeat,
            phoneName = phoneName,
            version = version,
        )
    }
}
