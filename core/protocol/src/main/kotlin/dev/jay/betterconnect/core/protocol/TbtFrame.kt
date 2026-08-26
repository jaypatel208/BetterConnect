package dev.jay.betterconnect.core.protocol

/**
 * A 48-byte frame with content-based equality.
 *
 * A bare ByteArray compares by identity, which makes it useless as Compose state and
 * as a StateFlow value - every emission would look like a change.
 */
class TbtFrame(val bytes: ByteArray) {

    init {
        require(bytes.size == ClusterProtocol.TBT_SIZE) {
            "TBT frame must be exactly ${ClusterProtocol.TBT_SIZE} bytes, was ${bytes.size}"
        }
    }

    fun decode(): DecodeResult = TbtDecoder.decode(bytes)

    fun toHex(): String = bytes.toHex()

    fun toHexGroups(perLine: Int = 16): List<String> = bytes.toHexGroups(perLine)

    /** Byte indices that differ from [other]; drives the changed-byte highlight in the UI. */
    fun diffIndices(other: TbtFrame?): Set<Int> {
        if (other == null) return emptySet()
        return bytes.indices.filterTo(mutableSetOf()) { bytes[it] != other.bytes[it] }
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is TbtFrame && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "TbtFrame(${toHex()})"

    companion object {
        fun endNavigation(): TbtFrame = TbtFrame(TbtEncoder.endNavigationFrame())
    }
}
