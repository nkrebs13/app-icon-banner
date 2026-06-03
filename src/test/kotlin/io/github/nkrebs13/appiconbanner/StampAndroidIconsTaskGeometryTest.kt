package io.github.nkrebs13.appiconbanner

import io.github.nkrebs13.appiconbanner.android.StampAndroidIconsTask
import io.github.nkrebs13.appiconbanner.android.adaptiveSafeWidthPctInternal
import io.github.nkrebs13.appiconbanner.android.asSafeWidthPct
import io.github.nkrebs13.appiconbanner.android.roundSafeWidthPctInternal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StampAndroidIconsTaskGeometryTest {

    // --- roundSafeWidthPctInternal ---

    @Test
    fun `roundSafeWidthPct nominal — bottomInset=10 height=18 yields ~78 percent`() {
        val result = roundSafeWidthPctInternal(heightPct = 18, bottomInsetPct = 10)
        // ycFrac = (10+9)/100 = 0.19; safeWidthFrac = sqrt(1-(0.62)^2) = sqrt(0.6156) ≈ 0.785
        assertTrue(result in 77..79, "expected ~78%, got $result%")
    }

    @Test
    fun `roundSafeWidthPct original zero-inset — bottomInset=0 height=18 yields ~57 percent`() {
        val result = roundSafeWidthPctInternal(heightPct = 18, bottomInsetPct = 0)
        // ycFrac = 0.09; safeWidthFrac = sqrt(1-(0.82)^2) = sqrt(0.3276) ≈ 0.572
        assertTrue(result in 56..58, "expected ~57%, got $result%")
    }

    @Test
    fun `roundSafeWidthPct band centered at midpoint yields 100 percent`() {
        // ycFrac = 0.5, oneMinusTwiceYc = 0, safe = sqrt(1) = 1.0 → 100%
        val result = roundSafeWidthPctInternal(heightPct = 0, bottomInsetPct = 50)
        assertEquals(100, result)
    }

    @Test
    fun `roundSafeWidthPct Math-max guard fires when band center above midpoint — returns 0`() {
        // ycFrac = (50+5)/100 = 0.55, oneMinusTwiceYc = -0.10, 1 - 0.01 = 0.99 — still valid
        // For a clearly above-center case: ycFrac = 0.75, oneMinusTwiceYc = -0.5
        // 1 - 0.25 = 0.75 — still valid; for degenerate case bottomInsetPct=100, heightPct=0:
        // ycFrac=1.0, oneMinusTwiceYc=-1.0, 1-1=0, sqrt(0)=0
        val result = roundSafeWidthPctInternal(heightPct = 0, bottomInsetPct = 100)
        assertEquals(0, result)
    }

    @Test
    fun `roundSafeWidthPct heightPct=0 bottomInsetPct=0 yields 0`() {
        // ycFrac=0, oneMinusTwiceYc=1.0, 1-1=0, sqrt(0)=0
        val result = roundSafeWidthPctInternal(heightPct = 0, bottomInsetPct = 0)
        assertEquals(0, result)
    }

    // --- adaptiveSafeWidthPctInternal ---

    @Test
    fun `adaptiveSafeWidthPct nominal — height=22 inset=20 yields ~48 percent`() {
        val result = adaptiveSafeWidthPctInternal(heightPct = 22, bottomInsetPct = 20)
        // bandCenter=31% from bottom, dist from 50% center = 19% of 108dp = 20.52dp
        // safeHalf = sqrt(33^2 - 20.52^2) = 25.84dp; safe% = 25.84*2/108*100 ≈ 47.9
        assertTrue(result in 46..50, "expected ~48%, got $result%")
    }

    @Test
    fun `adaptiveSafeWidthPct band at canvas center yields 61 percent (full safe zone)`() {
        // bandCenter=50% from bottom → dist=0 → safeHalf=33dp → safe%=33*2/108*100=61.1
        val result = adaptiveSafeWidthPctInternal(heightPct = 0, bottomInsetPct = 50)
        assertTrue(result in 60..62, "expected ~61%, got $result%")
    }

    @Test
    fun `adaptiveSafeWidthPct Math-max guard fires when band outside safe circle — returns 0`() {
        // bandCenter=100% from bottom, dist from 50% center = 50% of 108dp = 54dp > 33dp → inside sqrt is negative
        val result = adaptiveSafeWidthPctInternal(heightPct = 0, bottomInsetPct = 100)
        assertEquals(0, result)
    }

    @Test
    fun `adaptiveSafeWidthPct at very bottom — inset=0 height=0 — returns 0`() {
        // bandCenter=0%, dist=54dp > 33dp → 33^2 - 54^2 < 0 → Math.max guard → 0
        val result = adaptiveSafeWidthPctInternal(heightPct = 0, bottomInsetPct = 0)
        assertEquals(0, result)
    }

    // --- asSafeWidthPct extension ---

    @Test
    fun `asSafeWidthPct coerces values over 1-0 to 100`() {
        assertEquals(100, 1.5.asSafeWidthPct())
        assertEquals(100, 2.0.asSafeWidthPct())
    }

    @Test
    fun `asSafeWidthPct converts fractional double correctly`() {
        assertEquals(78, 0.785.asSafeWidthPct())
        assertEquals(61, 0.611.asSafeWidthPct())
        assertEquals(0, 0.0.asSafeWidthPct())
    }

    // --- BOLD_FONT_CANDIDATES composition ---

    @Test
    fun `BOLD_FONT_CANDIDATES prepends bold-specifics before regular fallbacks`() {
        val boldSpecific = StampAndroidIconsTask.BOLD_SPECIFIC_CANDIDATES
        val regular = StampAndroidIconsTask.FONT_CANDIDATES
        val bold = StampAndroidIconsTask.BOLD_FONT_CANDIDATES

        assertEquals(boldSpecific + regular, bold,
            "BOLD_FONT_CANDIDATES must be BOLD_SPECIFIC_CANDIDATES + FONT_CANDIDATES in that order")
        assertEquals(boldSpecific.size + regular.size, bold.size)
    }
}
