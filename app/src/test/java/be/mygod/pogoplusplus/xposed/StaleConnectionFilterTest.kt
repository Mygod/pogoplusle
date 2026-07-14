package be.mygod.pogoplusplus.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleConnectionFilterTest {
    @Test
    fun suppressesBaselineConnectionWithinWindow() {
        val filter = filter()

        assertTrue(filter.shouldSuppress(true, 1, BASELINE_ADDRESS, 3_999))
    }

    @Test
    fun ignoresNewDeviceAndLateCallback() {
        val filter = filter()

        assertFalse(filter.shouldSuppress(true, 1, "00:11:22:33:44:66", 1_001))
        assertFalse(filter.shouldSuppress(true, 2, BASELINE_ADDRESS, 4_001))
    }

    @Test
    fun matchingDisconnectIsSuppressedOnce() {
        val filter = filter()
        assertTrue(filter.shouldSuppress(true, 1, BASELINE_ADDRESS, 1_001))

        assertTrue(filter.shouldSuppress(false, 1, BASELINE_ADDRESS, 10_000))
        assertFalse(filter.shouldSuppress(false, 1, BASELINE_ADDRESS, 10_001))
    }

    @Test
    fun addressFallbackConsumesOnlyMatchingConnection() {
        val filter = filter()
        assertTrue(filter.shouldSuppress(true, 1, BASELINE_ADDRESS, 1_001))
        assertTrue(filter.shouldSuppress(true, 2, SECOND_BASELINE_ADDRESS, 1_002))

        assertTrue(filter.shouldSuppress(false, 99, BASELINE_ADDRESS, 10_000))
        assertFalse(filter.shouldSuppress(false, 1, BASELINE_ADDRESS, 10_001))
        assertTrue(filter.shouldSuppress(false, 2, SECOND_BASELINE_ADDRESS, 10_002))
    }

    private fun filter() = StaleConnectionFilter(
        registeredAtMillis = 1_000,
        baselineAddresses = setOf(BASELINE_ADDRESS, SECOND_BASELINE_ADDRESS),
        callbackWindowMillis = 3_000,
    )

    private companion object {
        const val BASELINE_ADDRESS = "00:11:22:33:44:55"
        const val SECOND_BASELINE_ADDRESS = "00:11:22:33:44:77"
    }
}
