package com.bikearcade.metrics

import kotlinx.coroutines.flow.Flow

/**
 * Sealed interface representing a source of bike metric data.
 * Each implementation connects to a specific hardware interface
 * and emits raw [BikeMetrics] via a Kotlin Flow.
 */
sealed interface MetricsSource {

    val name: String
    val isConnected: Boolean
    val metricsFlow: Flow<BikeMetrics>
    suspend fun connect()
    suspend fun disconnect()

    /** BLE FTMS source — connects via Bluetooth Low Energy FTMS profile. */
    interface BleFtms : MetricsSource {
        val deviceAddress: String?
        suspend fun scanAndConnect(targetAddress: String? = null)
    }

    /** Session source — connects via VeloLauncher's WorkoutSessionClient (AIDL). */
    interface Session : MetricsSource {
        fun setResistance(level: Int): Boolean
        fun requestStart(): Boolean
        fun requestStop(): Boolean
    }
}
