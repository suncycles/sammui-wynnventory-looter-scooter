package com.wynnventory.unid;

import com.wynntils.core.components.Models;
import com.wynntils.models.gear.type.GearInfo;
import com.wynntils.models.gear.type.GearTier;
import com.wynntils.models.items.items.game.GearBoxItem;
import com.wynnventory.api.service.TrademarketService;
import com.wynnventory.model.item.trademarket.TrademarketItemSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * What selling an unidentified gear box's unid is expected to net, if every possible gear is equally likely to drop.
 */
public record GearBoxValue(int possible, int priced, List<GearInfo> hits, double expectedValue, Instant computedAt) {
    // Slots re-render every frame while prices arrive asynchronously; a second-old answer is fresh enough.
    private static final Duration MAX_AGE = Duration.ofSeconds(1);
    private static final Map<GearBoxItem, GearBoxValue> CACHE = new HashMap<>();

    public static GearBoxValue of(GearBoxItem box) {
        GearBoxValue cached = CACHE.get(box);
        if (cached != null && cached.computedAt.isAfter(Instant.now().minus(MAX_AGE))) return cached;

        GearBoxValue value = compute(Models.Gear.getPossibleGears(box));
        CACHE.put(box, value);
        return value;
    }

    private static GearBoxValue compute(List<GearInfo> gears) {
        double total = 0;
        int priced = 0;
        List<GearInfo> hits = new ArrayList<>();

        for (GearInfo info : gears) {
            // A blacklisted gear is worth nothing to the player, so it still counts in the average, at 0.
            if (UnidSettings.get().isBlacklisted(info.name())) continue;

            TrademarketItemSnapshot snapshot = TrademarketService.INSTANCE.getItem(info.name(), false);
            if (snapshot == null || UnidValue.unidPrice(snapshot) == null) continue;

            boolean mythic = info.tier() == GearTier.MYTHIC;
            priced++;
            total += UnidValue.expectedSale(snapshot, mythic);
            if (UnidValue.isHighValue(snapshot, mythic)) hits.add(info);
        }

        // Gears without an unid price add 0: no market (or not fetched yet), which `priced` makes visible.
        double expectedValue = gears.isEmpty() ? 0 : total / gears.size();
        return new GearBoxValue(gears.size(), priced, hits, expectedValue, Instant.now());
    }

    public List<Component> tooltipLines() {
        int color = UnidSettings.get().getHighlightColor();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Box EV (net): " + UnidValue.formatEmeralds((int) expectedValue) + " (" + priced
                        + "/" + possible + " priced, " + hits.size() + " hits)")
                .withStyle(ChatFormatting.AQUA));
        for (GearInfo hit : hits) {
            lines.add(Component.literal("★ ")
                    .withColor(color)
                    .append(Component.literal(hit.name()).withStyle(hit.tier().getChatFormatting())));
        }

        return lines;
    }
}
