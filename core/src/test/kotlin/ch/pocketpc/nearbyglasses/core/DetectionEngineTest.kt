package ch.pocketpc.nearbyglasses.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionEngineTest {

    @Test
    fun `evaluate filters out advertisements below threshold`() {
        val engine = DetectionEngine(rssiThreshold = -75)
        val ad = BleAdvertisement(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Ray-Ban",
            rssi = -90,
            companyId = SmartGlassesDetector.META_COMPANY_ID1,
            manufacturerDataHex = null
        )

        val decision = engine.evaluate(ad)
        assertFalse(decision.matched)
    }

    @Test
    fun `evaluate matches by detector`() {
        val engine = DetectionEngine(rssiThreshold = -80)
        val ad = BleAdvertisement(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = null,
            rssi = -60,
            companyId = SmartGlassesDetector.SNAP_COMPANY_ID,
            manufacturerDataHex = null
        )

        val decision = engine.evaluate(ad)
        assertTrue(decision.matched)
        assertFalse(decision.matchedByOverride)
    }

    @Test
    fun `evaluate matches by override when detector does not`() {
        val overrideId = 0x1234
        val engine = DetectionEngine(
            rssiThreshold = -80,
            overrideCompanyIds = setOf(overrideId)
        )
        val ad = BleAdvertisement(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Headphones",
            rssi = -55,
            companyId = overrideId,
            manufacturerDataHex = null
        )

        val decision = engine.evaluate(ad)
        assertTrue(decision.matched)
        assertTrue(decision.matchedByOverride)
    }
}
