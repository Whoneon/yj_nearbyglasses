package ch.pocketpc.nearbyglasses.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartGlassesDetectorTest {

    @Test
    fun `detect returns match for known company id`() {
        val result = SmartGlassesDetector.detect(
            companyId = SmartGlassesDetector.META_COMPANY_ID1,
            deviceName = null
        )

        assertTrue(result.isSmartGlasses)
        val reason = result.reasons.single() as SmartGlassesDetector.Reason.CompanyIdMatch
        assertEquals(SmartGlassesDetector.META_COMPANY_ID1, reason.companyId)
        assertEquals(SmartGlassesDetector.CompanyKey.META, reason.companyKey)
    }

    @Test
    fun `detect returns match for supported name token`() {
        val result = SmartGlassesDetector.detect(
            companyId = null,
            deviceName = "Ray-Ban Smart"
        )

        assertTrue(result.isSmartGlasses)
        val reason = result.reasons.single() as SmartGlassesDetector.Reason.NameContains
        assertEquals("ray-ban", reason.token)
    }

    @Test
    fun `detect returns no match for unknown signals`() {
        val result = SmartGlassesDetector.detect(
            companyId = 0x1234,
            deviceName = "Headphones"
        )

        assertFalse(result.isSmartGlasses)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun `company key lookup and formatting are stable`() {
        assertEquals(
            SmartGlassesDetector.CompanyKey.SNAP,
            SmartGlassesDetector.companyKeyFor(SmartGlassesDetector.SNAP_COMPANY_ID)
        )
        assertEquals("0x03C2", SmartGlassesDetector.formatCompanyId(SmartGlassesDetector.SNAP_COMPANY_ID))
        assertEquals(null, SmartGlassesDetector.companyKeyFor(0x9999))
    }
}
