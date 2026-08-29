package dev.jay.betterconnect.core.protocol

import dev.jay.betterconnect.core.model.ControlFrame

/**
 * Decodes the 20-byte `CONTROL` (`0A10`) frame. Cluster to phone only - see `docs/PROTOCOL.md`
 * §5. Only bytes 0-17 are ever parsed `[dex]`; byte 0 bits 5-4 and bytes 18-19 are
 * **undetermined** and deliberately not exposed.
 */
object ControlDecoder {

    const val SIZE: Int = 20

    fun decode(bytes: ByteArray): ControlFrame {
        require(bytes.size == SIZE) { "CONTROL frame must be $SIZE bytes, was ${bytes.size}" }
        val b0 = bytes[0].toInt() and 0xFF
        return ControlFrame(
            dialSource = (b0 shr 6) and 0x03,
            volumeToSet = b0 and 0x0F,
            callAccept = bytes[1].toInt() and 0xFF,
            callReject = bytes[2].toInt() and 0xFF,
            callRejectWithSms = bytes[3].toInt() and 0xFF,
            pagePlaylist = bytes[4].toInt() and 0x3F,
            newPlaylistReq = (bytes[4].toInt() and 0xFF) shr 6,
            takeMeHome = bytes[5].toInt() and 0xFF,
            resumeSong = bytes[6].toInt() and 0xFF,
            pauseSong = bytes[7].toInt() and 0xFF,
            skipToNext = bytes[8].toInt() and 0xFF,
            skipToPrev = bytes[9].toInt() and 0xFF,
            stopSong = bytes[10].toInt() and 0xFF,
            missedCallGet = bytes[11].toInt() and 0xFF,
            alertGet = bytes[12].toInt() and 0xFF,
            launchMediaPlayer = bytes[13].toInt() and 0xFF,
            selectPlaylistSong = bytes[14].toInt() and 0xFF,
            selectedPlaylistSong = bytes[15].toInt() and 0xFF,
            dialIndex = bytes[16].toInt() and 0xFF,
            dialTxn = bytes[17].toInt() and 0xFF,
        )
    }
}

/**
 * Test-only round-trip partner for [ControlDecoder]. Production never writes to `CONTROL` -
 * this exists so a decode can be verified against a known-good frame rather than a hand-built
 * byte array. See `.claude/rules/testing.md`: "write a decoder alongside every encoder."
 */
object ControlEncoder {
    fun encode(frame: ControlFrame): ByteArray {
        val bytes = ByteArray(ControlDecoder.SIZE)
        bytes[0] = (((frame.dialSource and 0x03) shl 6) or (frame.volumeToSet and 0x0F)).toByte()
        bytes[1] = frame.callAccept.toByte()
        bytes[2] = frame.callReject.toByte()
        bytes[3] = frame.callRejectWithSms.toByte()
        bytes[4] = (((frame.newPlaylistReq and 0x03) shl 6) or (frame.pagePlaylist and 0x3F)).toByte()
        bytes[5] = frame.takeMeHome.toByte()
        bytes[6] = frame.resumeSong.toByte()
        bytes[7] = frame.pauseSong.toByte()
        bytes[8] = frame.skipToNext.toByte()
        bytes[9] = frame.skipToPrev.toByte()
        bytes[10] = frame.stopSong.toByte()
        bytes[11] = frame.missedCallGet.toByte()
        bytes[12] = frame.alertGet.toByte()
        bytes[13] = frame.launchMediaPlayer.toByte()
        bytes[14] = frame.selectPlaylistSong.toByte()
        bytes[15] = frame.selectedPlaylistSong.toByte()
        bytes[16] = frame.dialIndex.toByte()
        bytes[17] = frame.dialTxn.toByte()
        return bytes
    }
}
