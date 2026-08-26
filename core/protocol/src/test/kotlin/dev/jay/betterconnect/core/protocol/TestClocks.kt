package dev.jay.betterconnect.core.protocol

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Fixed clocks so ETA encoding is deterministic. */
object TestClocks {
    fun at(iso: String): Clock = Clock.fixed(Instant.parse(iso), ZoneOffset.UTC)

    /** 10:00 UTC - pairs with an etaSeconds of 2700 to land on 10:45 AM. */
    val TEN_AM: Clock = at("2026-01-01T10:00:00Z")
}
