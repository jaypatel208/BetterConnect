package dev.jay.betterconnect.core.protocol

/**
 * Street/instruction text handling, reproducing the original encoder.
 *
 * Everything outside `[0-9a-zA-Z.]` becomes a space, which destroys hyphens and slashes
 * in road names: "Sarkhej-Gandhinagar Hwy" ships as "Sarkhej Gandhinagar Hwy". Whether
 * the cluster could render more is untested - the official app never gives it the chance.
 */
object TextCodec {

    fun sanitise(raw: String): String {
        val mapped = buildString(raw.length) {
            for (c in raw) {
                val keep = (c in '0'..'9') || (c in 'a'..'z') || (c in 'A'..'Z') || c == '.'
                append(if (keep) c else ' ')
            }
        }.trim()

        // The original slices to 31 and appends '.', then writes min(length, 31) bytes -
        // so the appended dot is always truncated away. Net effect is a plain take(31).
        return mapped.take(ClusterProtocol.MAX_TEXT_LEN)
    }

    fun writeTo(text: String, buffer: ByteArray) {
        val clipped = text.take(ClusterProtocol.MAX_TEXT_LEN)
        buffer[14] = clipped.length.toByte()
        for (i in clipped.indices) {
            buffer[ClusterProtocol.TEXT_OFFSET + i] = clipped[i].code.toByte()
        }
    }

    fun readFrom(buffer: ByteArray): String {
        val len = (buffer[14].toInt() and 0xFF).coerceAtMost(ClusterProtocol.MAX_TEXT_LEN)
        val bytes = ByteArray(len) { buffer[ClusterProtocol.TEXT_OFFSET + it] }
        return String(bytes.map { (it.toInt() and 0xFF).toChar() }.toCharArray())
    }
}
