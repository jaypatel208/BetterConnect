package dev.jay.betterconnect.core.protocol

/** Plain 8-bit additive checksum over bytes 0 until [ClusterProtocol.CHECKSUM_INDEX]. */
object Checksum {

    fun compute(buffer: ByteArray, upTo: Int = ClusterProtocol.CHECKSUM_INDEX): Int {
        var sum = 0
        for (i in 0 until upTo) sum = (sum + (buffer[i].toInt() and 0xFF)) and 0xFF
        return sum
    }

    fun apply(buffer: ByteArray) {
        buffer[ClusterProtocol.CHECKSUM_INDEX] = compute(buffer).toByte()
    }

    fun isValid(buffer: ByteArray): Boolean =
        buffer.size == ClusterProtocol.TBT_SIZE &&
            compute(buffer) == (buffer[ClusterProtocol.CHECKSUM_INDEX].toInt() and 0xFF)
}
