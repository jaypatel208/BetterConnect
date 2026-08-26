package dev.jay.betterconnect.core.protocol

import java.util.Locale

/**
 * A distance as the cluster stores it: a unit flag plus two independent uint16 values.
 *
 * The single most commonly mis-guessed part of this format is that whole and fraction
 * are *not* one scaled integer. They are two separate little-endian uint16 fields.
 */
data class DistanceField(
    val isMetres: Boolean,
    val whole: Int,
    val fraction: Int,
) {
    /**
     * Best-effort reconstruction in metres. Lossy above the kilometre threshold, because
     * the wire format only keeps two decimal places: 12 345 m encodes as 12.35 km and
     * comes back as 12 350 m. Round-trip tests must compare fields, not this value.
     */
    val approxMetres: Int
        get() = if (isMetres) whole else whole * 1000 + fraction * 10
}

object DistanceCodec {

    /**
     * The original app compares against 999, not 1000, so 999 m falls into the kilometre
     * branch and renders as "1.00 km". Reproduced by default so we can confirm our frames
     * match the official app's byte-for-byte; set [strictBoundary] to correct it.
     */
    const val LEGACY_METRE_LIMIT: Int = 999
    const val STRICT_METRE_LIMIT: Int = 1000

    fun encode(metres: Int, strictBoundary: Boolean = false): DistanceField {
        val safe = metres.coerceAtLeast(0)
        val limit = if (strictBoundary) STRICT_METRE_LIMIT else LEGACY_METRE_LIMIT

        if (safe < limit) return DistanceField(isMetres = true, whole = safe, fraction = 0)

        // Locale.ROOT matters: a locale using ',' as the decimal separator would break the split.
        val parts = String.format(Locale.ROOT, "%.2f", safe / 1000.0).split(".")
        return DistanceField(
            isMetres = false,
            whole = parts[0].toInt() and 0xFFFF,
            fraction = (parts.getOrNull(1)?.toIntOrNull() ?: 0) and 0xFFFF,
        )
    }

    /** Writes fraction then whole, each little-endian, at [offset]. Occupies 4 bytes. */
    fun writeTo(field: DistanceField, buffer: ByteArray, offset: Int) {
        buffer[offset] = (field.fraction and 0xFF).toByte()
        buffer[offset + 1] = ((field.fraction shr 8) and 0xFF).toByte()
        buffer[offset + 2] = (field.whole and 0xFF).toByte()
        buffer[offset + 3] = ((field.whole shr 8) and 0xFF).toByte()
    }

    fun readFrom(buffer: ByteArray, offset: Int, isMetres: Boolean): DistanceField =
        DistanceField(
            isMetres = isMetres,
            fraction = (buffer[offset].toInt() and 0xFF) or ((buffer[offset + 1].toInt() and 0xFF) shl 8),
            whole = (buffer[offset + 2].toInt() and 0xFF) or ((buffer[offset + 3].toInt() and 0xFF) shl 8),
        )
}
