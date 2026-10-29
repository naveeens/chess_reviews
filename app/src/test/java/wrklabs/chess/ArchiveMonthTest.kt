package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Test
import wrklabs.chess.importer.OnlineFetcher

class ArchiveMonthTest {
    @Test
    fun aTimeIsFiledUnderItsUtcMonthTheWayChessComNamesThem() {
        assertEquals("2026/10", OnlineFetcher.monthKey(1793199773000L))
        // The last second of September and the first of October, in UTC.
        assertEquals("2026/09", OnlineFetcher.monthKey(1790812799000L))
        assertEquals("2026/10", OnlineFetcher.monthKey(1790812800000L))
        assertEquals("2026/09" < "2026/10", true)
    }
}
