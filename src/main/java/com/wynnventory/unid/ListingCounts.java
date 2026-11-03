package com.wynnventory.unid;

/** Listing counts WynnVentory's summaries drop; TrademarketItemSummaryMixin adds them back. */
public interface ListingCounts {
    int wvunid$unidentifiedCount();

    /** Only set on /history/latest: the number of archived days the counts are summed over. */
    int wvunid$documentCount();
}
