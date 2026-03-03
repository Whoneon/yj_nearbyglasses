package ch.pocketpc.nearbyglasses.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CliParserTest {

    @Test
    fun `parse default config`() {
        val config = CliParser.parse(emptyArray())

        assertEquals(ScannerMode.AUTO, config.scannerMode)
        assertEquals(-75, config.rssiThreshold)
        assertEquals(10_000L, config.cooldownMs)
        assertTrue(config.overrideCompanyIds.isEmpty())
    }

    @Test
    fun `parse explicit settings`() {
        val config = CliParser.parse(
            arrayOf(
                "--scanner", "linux",
                "--rssi-threshold", "-65",
                "--cooldown-ms", "5000",
                "--override-company-ids", "0x01AB,0x058E,1234",
                "--debug",
                "--no-notify"
            )
        )

        assertEquals(ScannerMode.LINUX, config.scannerMode)
        assertEquals(-65, config.rssiThreshold)
        assertEquals(5_000L, config.cooldownMs)
        assertEquals(setOf(0x01AB, 0x058E, 1234), config.overrideCompanyIds)
        assertTrue(config.debug)
        assertEquals(false, config.notificationsEnabled)
    }
}
