package com.javelin.plugin.ui;

import com.javelin.plugin.ui.JavelinHighlightProvider.SuspicionBand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class SuspicionBandTest {

    @Test
    void rankOneOfTenIsRed() {
        assertEquals(SuspicionBand.RED, SuspicionBand.fromRank(1, 10));
    }

    @Test
    void topTenPercentIsRed() {
        assertEquals(SuspicionBand.RED, SuspicionBand.fromRank(1, 100));
        assertEquals(SuspicionBand.RED, SuspicionBand.fromRank(10, 100));
    }

    @Test
    void topTwentyFivePercentIsOrange() {
        assertEquals(SuspicionBand.ORANGE, SuspicionBand.fromRank(11, 100));
        assertEquals(SuspicionBand.ORANGE, SuspicionBand.fromRank(25, 100));
    }

    @Test
    void topFiftyPercentIsYellow() {
        assertEquals(SuspicionBand.YELLOW, SuspicionBand.fromRank(26, 100));
        assertEquals(SuspicionBand.YELLOW, SuspicionBand.fromRank(50, 100));
    }

    @Test
    void bottomHalfIsYellow() {
        assertEquals(SuspicionBand.YELLOW, SuspicionBand.fromRank(51, 100));
        assertEquals(SuspicionBand.YELLOW, SuspicionBand.fromRank(100, 100));
    }

    @Test
    void maxRankZeroReturnsYellow() {
        assertEquals(SuspicionBand.YELLOW, SuspicionBand.fromRank(1, 0));
    }

    @Test
    void maxRankNegativeReturnsYellow() {
        assertEquals(SuspicionBand.YELLOW, SuspicionBand.fromRank(1, -5));
    }

    @Test
    void fromRankNeverReturnsGreen() {
        for (int maxRank = 1; maxRank <= 200; maxRank++) {
            for (int rank = 1; rank <= maxRank; rank++) {
                assertNotEquals(SuspicionBand.GREEN, SuspicionBand.fromRank(rank, maxRank),
                        "fromRank returned GREEN for rank=" + rank + ", maxRank=" + maxRank);
            }
        }
    }

    @Test
    void singleRankIsRed() {
        assertEquals(SuspicionBand.RED, SuspicionBand.fromRank(1, 1));
    }

    @Test
    void twoRanksProducesRedAndNonRed() {
        assertEquals(SuspicionBand.RED, SuspicionBand.fromRank(1, 2));
        SuspicionBand second = SuspicionBand.fromRank(2, 2);
        assertNotEquals(SuspicionBand.RED, second);
    }

    @ParameterizedTest(name = "rank={0}, maxRank={1} → band exists")
    @CsvSource({
        "1, 5",
        "3, 5",
        "5, 5",
        "1, 20",
        "10, 20",
        "20, 20",
        "1, 1000",
        "500, 1000",
    })
    void neverReturnsNull(int rank, int maxRank) {
        assertNotNull(SuspicionBand.fromRank(rank, maxRank));
    }

    @Test
    void allBandsHaveColors() {
        for (SuspicionBand band : SuspicionBand.values()) {
            assertNotNull(band.color());
        }
    }

    @Test
    void allBandsHaveDescriptions() {
        for (SuspicionBand band : SuspicionBand.values()) {
            assertNotNull(band.description());
            assertFalse(band.description().isBlank());
        }
    }

    @Test
    void bandsAreMonotonicForIncreasingRank() {
        int maxRank = 100;
        SuspicionBand prev = SuspicionBand.fromRank(1, maxRank);
        for (int rank = 2; rank <= maxRank; rank++) {
            SuspicionBand current = SuspicionBand.fromRank(rank, maxRank);
            assertTrue(current.ordinal() >= prev.ordinal(),
                    "Band should not improve (decrease ordinal) as rank increases: " +
                    "rank=" + rank + " got " + current + " after " + prev);
            prev = current;
        }
    }
}
