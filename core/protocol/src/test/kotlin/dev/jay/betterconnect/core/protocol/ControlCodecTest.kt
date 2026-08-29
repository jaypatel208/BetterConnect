package dev.jay.betterconnect.core.protocol

import dev.jay.betterconnect.core.model.ControlFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ControlCodecTest {

    private fun sample() = ControlFrame(
        dialSource = 2,
        volumeToSet = 7,
        callAccept = 1,
        callReject = 2,
        callRejectWithSms = 3,
        pagePlaylist = 12,
        newPlaylistReq = 1,
        takeMeHome = 5,
        resumeSong = 9,
        pauseSong = 10,
        skipToNext = 11,
        skipToPrev = 12,
        stopSong = 13,
        missedCallGet = 4,
        alertGet = 6,
        launchMediaPlayer = 8,
        selectPlaylistSong = 3,
        selectedPlaylistSong = 2,
        dialIndex = 1,
        dialTxn = 5,
    )

    @Test
    fun `every field round trips`() {
        val frame = sample()
        val decoded = ControlDecoder.decode(ControlEncoder.encode(frame))
        assertEquals(frame, decoded)
    }

    @Test
    fun `dial source occupies the top two bits, volume the bottom nibble`() {
        val bytes = ControlEncoder.encode(sample().copy(dialSource = 3, volumeToSet = 0x0F))
        assertEquals(0xC0 or 0x0F, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `page playlist and new playlist request share byte 4`() {
        val bytes = ControlEncoder.encode(sample().copy(pagePlaylist = 0x3F, newPlaylistReq = 3))
        assertEquals(0xFF, bytes[4].toInt() and 0xFF)
        val decoded = ControlDecoder.decode(bytes)
        assertEquals(0x3F, decoded.pagePlaylist)
        assertEquals(3, decoded.newPlaylistReq)
    }

    @Test
    fun `wrong size is rejected rather than silently misread`() {
        assertThrows(IllegalArgumentException::class.java) {
            ControlDecoder.decode(ByteArray(19))
        }
    }
}
