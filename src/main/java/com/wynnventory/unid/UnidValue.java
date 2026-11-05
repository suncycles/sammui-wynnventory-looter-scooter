package com.wynnventory.unid;

import com.wynntils.models.emeralds.type.EmeraldUnits;
import com.wynnventory.model.item.trademarket.PriceType;
import com.wynnventory.model.item.trademarket.TrademarketItemSnapshot;
import com.wynnventory.util.EmeraldUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Unid price math on top of WynnVentory's trade market snapshots. */
public final class UnidValue {
    // Listed prices include the 5% fee the trade market adds for the buyer; the seller never receives it.
    private static final double BUYER_FEE = 1.05;

    private UnidValue() {}

    /** The unid price picked by the price metric setting. */
    public static Double unidPrice(TrademarketItemSnapshot snapshot) {
        return unidPrice(snapshot, UnidSettings.get().getUnidPriceMetric());
    }

    static Double unidPrice(TrademarketItemSnapshot snapshot, UnidSettings.PriceMetric metric) {
        return switch (metric) {
            case ROBUST -> robustPrice(snapshot);
            case MEDIAN -> liveOrHistoric(PriceType.UNID_MEDIAN, snapshot);
            case LOWEST -> liveOrHistoric(PriceType.UNID_LOWEST, snapshot);
            case AVERAGE -> liveOrHistoric(PriceType.UNID_AVG, snapshot);
        };
    }

    /** Ingredients go by their lowest listed price; they have no unid form or listing threshold. */
    public static boolean isValuableIngredient(TrademarketItemSnapshot snapshot) {
        Double lowest = liveOrHistoric(PriceType.LOWEST, snapshot);
        return lowest != null && lowest >= UnidSettings.get().getIngredientMinPrice();
    }

    /** The live price, or the archived one when nothing is listed right now. */
    private static Double liveOrHistoric(PriceType type, TrademarketItemSnapshot snapshot) {
        Double live = type.getValue(snapshot.live());
        return live != null ? live : type.getValue(snapshot.historic());
    }

    /**
     * Unid price that one bad signal can't move: the lower median of the live floor, live median, 7-day EMA median
     * and archived median. A lowball floor or a lone overpriced listing (floor == median) is outvoted by the history.
     */
    static Double robustPrice(TrademarketItemSnapshot snapshot) {
        return lowerMedian(
                PriceType.UNID_LOWEST.getValue(snapshot.live()),
                PriceType.UNID_MEDIAN.getValue(snapshot.live()),
                PriceType.UNID_MOVING_MEDIAN.getValue(snapshot.live()),
                PriceType.UNID_MEDIAN.getValue(snapshot.historic()));
    }

    /** Lower median of the non-null values (the smaller one for two), null when there are none. */
    static Double lowerMedian(Double... values) {
        List<Double> sorted =
                Arrays.stream(values).filter(Objects::nonNull).sorted().toList();
        return sorted.isEmpty() ? null : sorted.get((sorted.size() - 1) / 2);
    }

    /**
     * Average unid listings seen per archived day. The trade market exposes no sales, so listing volume is the
     * closest proxy for how often an item trades.
     */
    static double listingsPerDay(TrademarketItemSnapshot snapshot) {
        if (!(snapshot.historic() instanceof ListingCounts counts) || counts.wvunid$documentCount() <= 0) return 0;
        return (double) counts.wvunid$unidentifiedCount() / counts.wvunid$documentCount();
    }

    /**
     * How sellable the unid is, from 0 to 1: listings/day as a share of the threshold. Mythics always sell, so they
     * count as fully liquid however rarely they are listed.
     */
    static double liquidity(double listingsPerDay, int minListingsPerDay, boolean mythic) {
        if (mythic || minListingsPerDay <= 0) return 1;
        return Math.min(1, listingsPerDay / minListingsPerDay);
    }

    public static boolean isHighValue(TrademarketItemSnapshot snapshot, boolean mythic) {
        Double price = unidPrice(snapshot);
        return price != null && price >= UnidSettings.get().getUnidMinPrice() && liquidity(snapshot, mythic) >= 1;
    }

    /** Expected proceeds of selling the unid: the seller's share of its price, weighted by liquidity. */
    public static double expectedSale(TrademarketItemSnapshot snapshot, boolean mythic) {
        Double price = unidPrice(snapshot);
        // Unids under the min price get thrown away, so they're worth nothing.
        if (price == null) return 0;
        UnidSettings settings = UnidSettings.get();
        if (settings.isZeroUnderMinPrice() && price < settings.getUnidMinPrice()) return 0;
        return netSale(price, liquidity(snapshot, mythic));
    }

    static double netSale(Double price, double liquidity) {
        return price == null ? 0 : price / BUYER_FEE * liquidity;
    }

    private static double liquidity(TrademarketItemSnapshot snapshot, boolean mythic) {
        return liquidity(listingsPerDay(snapshot), UnidSettings.get().getMinUnidListingsPerDay(), mythic);
    }

    static String formatEmeralds(int emeralds) {
        // The formatter returns an empty string for 0.
        return emeralds > 0 ? EmeraldUtils.getFormattedString(emeralds, false) : "0" + EmeraldUnits.EMERALD.getSymbol();
    }
}
