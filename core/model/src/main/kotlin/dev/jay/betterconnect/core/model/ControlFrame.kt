package dev.jay.betterconnect.core.model

/**
 * The decoded 20-byte `CONTROL` (`0A10`) frame - cluster to phone. Every rider button press
 * reaches the app through this. See `docs/PROTOCOL.md` §5.
 *
 * There is no sequence number: the cluster holds a *level* per field and changes it on
 * each press. Callers detect a press by comparing against the previously seen [ControlFrame],
 * never by reading a single frame in isolation.
 */
data class ControlFrame(
    val dialSource: Int,
    val volumeToSet: Int,
    val callAccept: Int,
    val callReject: Int,
    val callRejectWithSms: Int,
    val pagePlaylist: Int,
    val newPlaylistReq: Int,
    val takeMeHome: Int,
    val resumeSong: Int,
    val pauseSong: Int,
    val skipToNext: Int,
    val skipToPrev: Int,
    val stopSong: Int,
    val missedCallGet: Int,
    val alertGet: Int,
    val launchMediaPlayer: Int,
    val selectPlaylistSong: Int,
    val selectedPlaylistSong: Int,
    val dialIndex: Int,
    val dialTxn: Int,
)
