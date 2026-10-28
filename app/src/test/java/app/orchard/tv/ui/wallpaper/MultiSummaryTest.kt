package app.orchard.tv.ui.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiSummaryTest {
    @Test fun `none chosen means everything, and so does all chosen`() {
        assertEquals("All", multiSummary(emptyList(), total = 5, all = "All"))
        assertEquals("All", multiSummary(listOf("a", "b", "c", "d", "e"), total = 5, all = "All"))
    }

    @Test fun `a few are named`() {
        assertEquals("Nature", multiSummary(listOf("Nature"), 5, "All"))
        assertEquals("Nature, Sea", multiSummary(listOf("Nature", "Sea"), 5, "All"))
    }

    @Test fun `more are counted`() {
        assertEquals("Nature, Sea +2", multiSummary(listOf("Nature", "Sea", "City", "Space"), 5, "All"))
    }
}
