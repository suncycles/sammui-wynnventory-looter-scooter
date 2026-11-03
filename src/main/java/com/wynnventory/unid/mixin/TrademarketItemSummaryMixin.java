package com.wynnventory.unid.mixin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wynnventory.model.item.trademarket.TrademarketItemSummary;
import com.wynnventory.unid.ListingCounts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TrademarketItemSummary.class)
public abstract class TrademarketItemSummaryMixin implements ListingCounts {
    // The API sends these and Jackson fills them, but WynnVentory itself has no field for them.
    @Unique
    @JsonProperty("unidentified_count")
    private int unidentifiedCount;

    @Unique
    @JsonProperty("document_count")
    private int documentCount;

    @Override
    public int wvunid$unidentifiedCount() {
        return unidentifiedCount;
    }

    @Override
    public int wvunid$documentCount() {
        return documentCount;
    }
}
