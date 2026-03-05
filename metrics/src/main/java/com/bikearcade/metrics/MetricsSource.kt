package com.bikearcade.metrics

import kotlinx.coroutines.flow.Flow

/**
 * Sealed interface representing a source of bike metric data.
 * Each implementation connects to a specific hardware interface
 * and emits raw [BikeMetrics] via a Kotlin Flow.
 */
sealed interface MetricsSource {

    /** Human-readable name for this source. */
    val name: String

    /** Whether this source is currently connected and producing data. */
    val isConnected: Boolean

    /** Flow of raw bike metrics from this source. */
    val metricsFlow: Flow<BikeMetrics>

    /** Connect to the data source. Suspends until connection is established or fails. */
    suspend fun connect()

    /** Disconnect and release resources. */
    suspend fun disconnect()

    /**
     * BLE FTMS source — connects to a Fitness Machine via Bluetooth Low Energy
     * using the FTMS (Fitness Machine Service) profile.
     */
    interface BleFtms : MetricsSource {
        /** BLE device address (MAC). */
        val deviceAddress: String?
        /** Scan for FTMS devices and connect to the first one found (or specified address). */
        suspend fun scanAndConnect(targetAddress: String? = null)
    }

    /**
     * TCP Serial source — connects to the Bowflex/Velocore serial controller
     * via a TCP bridge (typically on localhost:9999).
     */
    interface TcpSerial : MetricsSource {
        /** Host address of the TCP serial bridge. */
        val host: String
        /** Port of the TCP serial bridge. */
        val port: Int
    }
}
