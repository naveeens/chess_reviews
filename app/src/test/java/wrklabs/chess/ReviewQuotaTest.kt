package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wrklabs.chess.billing.ReviewQuota

class ReviewQuotaTest {
    private val hour = 60L * 60 * 1000

    @Test
    fun twentyFreeReviewsADay() {
        assertTrue(ReviewQuota.allowed(false, List(19) { it.toLong() }))
        assertFalse(ReviewQuota.allowed(false, List(20) { it.toLong() }))
    }

    @Test
    fun proHasNoLimit() {
        assertTrue(ReviewQuota.allowed(true, List(50) { it.toLong() }))
    }

    @Test
    fun theNextOneComesBackADayAfterTheOldestOfTheLastTwenty() {
        assertNull(ReviewQuota.nextFreeAt(List(19) { it.toLong() }))
        val times = List(20) { (it + 1) * hour }
        assertEquals(hour + ReviewQuota.DAY_MS, ReviewQuota.nextFreeAt(times))
    }

    @Test
    fun anOldReviewDroppingOutFreesASlot() {
        // Twenty-two reviews in the list (older ones not yet filtered): the twentieth newest decides.
        val times = List(22) { (it + 1) * hour }
        assertEquals(3 * hour + ReviewQuota.DAY_MS, ReviewQuota.nextFreeAt(times))
    }
}
