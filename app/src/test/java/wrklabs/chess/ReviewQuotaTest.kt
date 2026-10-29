package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wrklabs.chess.billing.ReviewQuota

class ReviewQuotaTest {
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun fiveFreeReviewsAWeek() {
        assertTrue(ReviewQuota.allowed(false, listOf(1, 2, 3, 4)))
        assertFalse(ReviewQuota.allowed(false, listOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun proHasNoLimit() {
        assertTrue(ReviewQuota.allowed(true, List(50) { it.toLong() }))
    }

    @Test
    fun theNextOneComesBackAWeekAfterTheOldestOfTheLastFive() {
        assertNull(ReviewQuota.nextFreeAt(listOf(1, 2, 3, 4)))
        val times = listOf(day, 2 * day, 3 * day, 4 * day, 5 * day)
        assertEquals(day + ReviewQuota.WEEK_MS, ReviewQuota.nextFreeAt(times))
    }

    @Test
    fun anOldReviewDroppingOutFreesASlot() {
        // Seven reviews in the list (older ones not yet filtered): the fifth newest decides.
        val times = List(7) { (it + 1) * day }
        assertEquals(3 * day + ReviewQuota.WEEK_MS, ReviewQuota.nextFreeAt(times))
    }
}
