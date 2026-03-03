package ch.pocketpc.nearbyglasses.desktop.scanner

import ch.pocketpc.nearbyglasses.core.BleAdvertisement

interface PlatformBleScanner {
    fun start(onAdvertisement: (BleAdvertisement) -> Unit)
    fun stop()
}
