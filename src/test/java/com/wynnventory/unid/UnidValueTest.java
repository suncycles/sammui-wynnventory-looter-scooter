package com.wynnventory.unid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.wynnventory.model.item.trademarket.TrademarketItemSnapshot;
import com.wynnventory.model.item.trademarket.TrademarketItemSummary;
import org.junit.jupiter.api.Test;

public class UnidValueTest {
    @Test
    void lowerMedianOutvotesOneOutlier() {
        assertEquals(100.0, UnidValue.lowerMedian(10.0, 100.0, 110.0, 5000.0));
        assertEquals(100.0, UnidValue.lowerMedian(null, 100.0, null, 5000.0));
        assertNull(UnidValue.lowerMedian(null, null));
    }

    @Test
    void robustPriceIgnoresALoneOverpricedListing() {
        // One listing: the live floor and median are both 900, while the EMA and the archive say about 100.
        TrademarketItemSummary live = new TrademarketItemSummary();
        live.setUnidentifiedLowestPrice(900);
        live.setUnidentifiedMedian(900.0);
        live.setUnidentifiedMovingMedian(100.0);
        TrademarketItemSummary historic = new TrademarketItemSummary();
        historic.setUnidentifiedMedian(110.0);

        assertEquals(110.0, UnidValue.robustPrice(new TrademarketItemSnapshot(live, historic)));
    }

    @Test
    void metricPicksItsPriceAndFallsBackToTheArchive() {
        TrademarketItemSummary live = new TrademarketItemSummary();
        live.setUnidentifiedLowestPrice(80);
        live.setUnidentifiedMedian(100.0);
        TrademarketItemSummary historic = new TrademarketItemSummary();
        historic.setUnidentifiedAveragePrice(120.0);
        TrademarketItemSnapshot snapshot = new TrademarketItemSnapshot(live, historic);

        assertEquals(80.0, UnidValue.unidPrice(snapshot, UnidSettings.PriceMetric.LOWEST));
        assertEquals(100.0, UnidValue.unidPrice(snapshot, UnidSettings.PriceMetric.MEDIAN));
        // Nothing listed live has an average, so the archived one is used.
        assertEquals(120.0, UnidValue.unidPrice(snapshot, UnidSettings.PriceMetric.AVERAGE));
        assertNull(UnidValue.unidPrice(new TrademarketItemSnapshot(null, null), UnidSettings.PriceMetric.MEDIAN));
    }

    @Test
    void listingsPerDayAveragesOverArchivedDays() {
        assertEquals(3, UnidValue.listingsPerDay(snapshotWithCounts(21, 7)));
        assertEquals(0, UnidValue.listingsPerDay(snapshotWithCounts(21, 0)));
        assertEquals(0, UnidValue.listingsPerDay(new TrademarketItemSnapshot(null, null)));
    }

    @Test
    void liquidityScalesUpToTheThreshold() {
        assertEquals(1.0 / 3, UnidValue.liquidity(1, 3, false));
        assertEquals(1, UnidValue.liquidity(10, 3, false));
        assertEquals(0, UnidValue.liquidity(0, 3, false));
        assertEquals(1, UnidValue.liquidity(0, 0, false));
    }

    @Test
    void mythicsAlwaysCountAsLiquid() {
        assertEquals(1, UnidValue.liquidity(0, 3, true));
    }

    @Test
    void netSaleTakesOffTheBuyerFee() {
        assertEquals(100, UnidValue.netSale(105.0, 1), 1e-9);
        assertEquals(50, UnidValue.netSale(105.0, 0.5), 1e-9);
        assertEquals(0, UnidValue.netSale(null, 1));
    }

    @Test
    void expectedSaleIsZeroUnderTheMinPrice() {
        int minPrice = UnidSettings.get().getUnidMinPrice();

        // Mythics skip the liquidity check, so only the price decides.
        assertEquals(0, UnidValue.expectedSale(snapshotPricedAt(minPrice - 1), true));
        assertEquals(minPrice / 1.05, UnidValue.expectedSale(snapshotPricedAt(minPrice), true), 1e-9);
    }

    private static TrademarketItemSnapshot snapshotPricedAt(int price) {
        TrademarketItemSummary live = new TrademarketItemSummary();
        live.setUnidentifiedLowestPrice(price);
        live.setUnidentifiedMedian((double) price);
        live.setUnidentifiedMovingMedian((double) price);
        return new TrademarketItemSnapshot(live, null);
    }

    private static TrademarketItemSnapshot snapshotWithCounts(int unidentified, int days) {
        return new TrademarketItemSnapshot(null, new CountedSummary(unidentified, days));
    }

    // Stands in for TrademarketItemSummaryMixin, which only applies in game.
    private static final class CountedSummary extends TrademarketItemSummary implements ListingCounts {
        private final int unidentified;
        private final int days;

        private CountedSummary(int unidentified, int days) {
            this.unidentified = unidentified;
            this.days = days;
        }

        @Override
        public int wvunid$unidentifiedCount() {
            return unidentified;
        }

        @Override
        public int wvunid$documentCount() {
            return days;
        }
    }
}
