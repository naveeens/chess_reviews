package wrklabs.chess

import org.junit.Assert.assertEquals
import org.junit.Test
import wrklabs.chess.ui.accuracyChip

class AccuracyChipTest {
    @Test
    fun aChipRoundsToTheNearestWholeNumberLikeTheTableDoesToOneDecimal() {
        assertEquals("90%", accuracyChip(89.96))
        assertEquals("90.0", String.format("%.1f", 89.96))
        assertEquals("89%", accuracyChip(89.4))
        assertEquals("100%", accuracyChip(99.5))
    }
}
