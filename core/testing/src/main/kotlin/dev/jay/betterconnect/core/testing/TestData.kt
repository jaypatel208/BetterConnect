package dev.jay.betterconnect.core.testing

import dev.jay.betterconnect.core.model.GattCharacteristic
import dev.jay.betterconnect.core.model.GattDump
import dev.jay.betterconnect.core.model.GattService
import dev.jay.betterconnect.core.model.NavState
import dev.jay.betterconnect.core.model.Symbol
import dev.jay.betterconnect.core.protocol.ClusterProtocol

/** Shared fixtures so every test describes the same cluster. */
object TestData {

    val navLeft500 = NavState(
        symbol = Symbol.LEFT,
        distanceToTurnM = 500,
        distanceLeftM = 12_300,
        etaSeconds = 45 * 60,
        text = "MG ROAD",
    )

    private fun tbtCharacteristic(properties: List<String>) = GattCharacteristic(
        uuid = ClusterProtocol.TBT_INFO_UUID.toString(),
        properties = properties,
        isTbtInfo = true,
    )

    private fun otherCharacteristic(uuid: String) = GattCharacteristic(
        uuid = uuid,
        properties = listOf("WRITE_NO_RESPONSE"),
        isTbtInfo = false,
    )

    /** A cluster that speaks the protocol. */
    fun healthyDump(
        address: String = FakeClusterTransport.ADDRESS,
        mtu: Int = FakeClusterTransport.DEFAULT_MTU,
    ) = GattDump(
        address = address,
        mtu = mtu,
        services = listOf(
            GattService(
                uuid = ClusterProtocol.SERVICE_UUID.toString(),
                characteristics = listOf(
                    tbtCharacteristic(listOf("WRITE_NO_RESPONSE")),
                    otherCharacteristic(ClusterProtocol.GENERAL_UUID.toString()),
                ),
            ),
        ),
    )

    /** A device with no cluster service at all - wrong device, or a different model. */
    fun serviceMissingDump(address: String = FakeClusterTransport.ADDRESS) = GattDump(
        address = address,
        mtu = FakeClusterTransport.DEFAULT_MTU,
        services = listOf(
            GattService(uuid = "0000180a-0000-1000-8000-00805f9b34fb", characteristics = emptyList()),
        ),
    )

    /** The service is present but TBT_INFO is absent: the GENERAL-only gating case. */
    fun characteristicMissingDump(address: String = FakeClusterTransport.ADDRESS) = GattDump(
        address = address,
        mtu = FakeClusterTransport.DEFAULT_MTU,
        services = listOf(
            GattService(
                uuid = ClusterProtocol.SERVICE_UUID.toString(),
                characteristics = listOf(otherCharacteristic(ClusterProtocol.GENERAL_UUID.toString())),
            ),
        ),
    )

    /** TBT_INFO exists but is read-only - present, useless. */
    fun notWritableDump(address: String = FakeClusterTransport.ADDRESS) = GattDump(
        address = address,
        mtu = FakeClusterTransport.DEFAULT_MTU,
        services = listOf(
            GattService(
                uuid = ClusterProtocol.SERVICE_UUID.toString(),
                characteristics = listOf(tbtCharacteristic(listOf("READ"))),
            ),
        ),
    )
}
