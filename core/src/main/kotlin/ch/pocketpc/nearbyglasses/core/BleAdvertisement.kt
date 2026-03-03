package ch.pocketpc.nearbyglasses.core

data class BleAdvertisement(
    val timestamp: Long = System.currentTimeMillis(),
    val deviceAddress: String,
    val deviceName: String?,
    val rssi: Int,
    val companyId: Int?,
    val manufacturerDataHex: String?
)
