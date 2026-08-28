package dev.jay.betterconnect.core.protocol

import dev.jay.betterconnect.core.model.SymbolCatalog

/**
 * Wire-level truth read back out of a frame.
 *
 * Distances are exposed as their encoded [DistanceField]s rather than as metres, because
 * kilometre mode keeps only two decimals and is therefore lossy. Comparing these fields is
 * how round-trip tests stay honest.
 */
data class DecodedFrame(
    val symbolCode: Int,
    val turn: DistanceField,
    val total: DistanceField,
    val etaHour12: Int,
    val etaMinute: Int,
    val isPm: Boolean,
    val roundaboutExit: Int,
    val gpsActive: Boolean,
    val text: String,
    val checksum: Int,
    val reservedByte13: Int,
    val reservedByte46: Int,
    val constantBitSet: Boolean,
) {
    val symbolChar: Char get() = symbolCode.toChar()

    /** Lowercase is the blink form; arrival is the documented exception, using 'H'. */
    val blinking: Boolean get() = symbolChar.isLowerCase() || symbolCode == 'H'.code

    val symbolLabel: String get() = SymbolCatalog.labelFor(symbolChar)

    val etaHour24: Int get() = when {
        isPm && etaHour12 != 12 -> etaHour12 + 12
        !isPm && etaHour12 == 12 -> 0
        else -> etaHour12
    }
}

sealed interface DecodeResult {
    data class Valid(val frame: DecodedFrame) : DecodeResult
    data class BadChecksum(val frame: DecodedFrame, val expected: Int, val actual: Int) : DecodeResult
    data class BadSize(val size: Int) : DecodeResult

    val frameOrNull: DecodedFrame?
        get() = when (this) {
            is Valid -> frame
            is BadChecksum -> frame
            is BadSize -> null
        }
}

/**
 * The inverse of [TbtEncoder].
 *
 * This exists for two reasons that both come from the link having no return channel:
 * it lets tests assert an encode/decode round trip instead of guessing, and it drives the
 * in-app virtual cluster, which renders the bytes actually about to go on the wire.
 */
object TbtDecoder {

    fun decode(buffer: ByteArray): DecodeResult {
        if (buffer.size != ClusterProtocol.TBT_SIZE) return DecodeResult.BadSize(buffer.size)

        val flags = buffer[0].toInt() and 0xFF
        val flags2 = buffer[12].toInt() and 0xFF
        val byte7 = buffer[7].toInt() and 0xFF

        val frame = DecodedFrame(
            symbolCode = buffer[1].toInt() and 0xFF,
            turn = DistanceCodec.readFrom(buffer, 2, isMetres = flags and TbtEncoder.FLAG_TURN_METRES != 0),
            total = DistanceCodec.readFrom(
                buffer,
                8,
                isMetres = flags2 and TbtEncoder.FLAG2_TOTAL_METRES != 0,
            ),
            etaHour12 = byte7 and 0x0F,
            etaMinute = buffer[6].toInt() and 0xFF,
            isPm = flags and TbtEncoder.FLAG_PM != 0,
            roundaboutExit = (byte7 shr 4) and 0x0F,
            gpsActive = flags2 and TbtEncoder.FLAG2_GPS_ACTIVE != 0,
            text = TextCodec.readFrom(buffer),
            checksum = buffer[ClusterProtocol.CHECKSUM_INDEX].toInt() and 0xFF,
            reservedByte13 = buffer[13].toInt() and 0xFF,
            reservedByte46 = buffer[46].toInt() and 0xFF,
            constantBitSet = flags and TbtEncoder.FLAG_CONSTANT != 0,
        )

        val expected = Checksum.compute(buffer)
        return if (expected == frame.checksum) {
            DecodeResult.Valid(frame)
        } else {
            DecodeResult.BadChecksum(frame, expected = expected, actual = frame.checksum)
        }
    }
}

/** Hex rendering for the diagnostic log and the on-screen frame view. */
fun ByteArray.toHexGroups(perLine: Int = 16): List<String> = toList().chunked(perLine).map { line ->
    line.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
