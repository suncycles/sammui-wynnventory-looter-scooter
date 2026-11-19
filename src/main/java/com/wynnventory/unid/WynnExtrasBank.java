package com.wynnventory.unid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads everything WynnExtras' "All Characters Mode" sees: the account bank, every character's bank and saved
 * inventory, the misc bucket and the tome bookshelf. Reflection keeps WynnExtras optional: without it, or if it renames
 * these classes, there is just no data.
 */
final class WynnExtrasBank {
    private static final Logger LOGGER = LoggerFactory.getLogger("wynnventory_unid");
    private static final String SEARCH_CLASS = "julianh06.wynnextras.features.inventory.data.CrossClassBankSearch";

    private static volatile boolean broken;

    private WynnExtrasBank() {}

    /**
     * Every item WynnExtras holds for any character, read off the render thread, leaving out the current character's
     * inventory: WynnExtras only saves that now and then, so it is better counted live. Empty without WynnExtras.
     */
    static Optional<CompletableFuture<List<ItemStack>>> everythingButCurrentInventory() {
        if (broken || !FabricLoader.getInstance().isModLoaded("wynnextras")) return Optional.empty();

        try {
            Class<?> search = Class.forName(SEARCH_CLASS);
            // An empty query over all pages, the current character and the account bank included.
            Object request = search.getMethod(
                            "createRequest", String.class, boolean.class, boolean.class, boolean.class)
                    .invoke(null, "", true, true, true);
            Object currentCharacter =
                    request.getClass().getMethod("currentCharacterId").invoke(request);
            CompletableFuture<?> results = (CompletableFuture<?>)
                    search.getMethod("searchAsync", request.getClass()).invoke(null, request);
            return Optional.of(results.thenApply(list -> items((List<?>) list, currentCharacter)));
        } catch (ReflectiveOperationException | ClassCastException e) {
            giveUp(e);
            return Optional.empty();
        }
    }

    private static List<ItemStack> items(List<?> results, Object currentCharacter) {
        List<ItemStack> items = new ArrayList<>();
        for (Object result : results) {
            boolean inventory = "PLAYER_INVENTORY".equals(String.valueOf(field(result, "type")));
            if (inventory && Objects.equals(currentCharacter, field(result, "characterId"))) continue;

            addItems(items, field(result, "pageItems"));
            addItems(items, field(result, "armorItems"));
        }
        return items;
    }

    private static void addItems(List<ItemStack> items, Object stacks) {
        if (!(stacks instanceof List<?> list)) return;
        for (Object stack : list) {
            if (stack instanceof ItemStack item && !item.isEmpty()) items.add(item);
        }
    }

    private static Object field(Object result, String name) {
        try {
            return result.getClass().getField(name).get(result);
        } catch (ReflectiveOperationException e) {
            giveUp(e);
            throw new IllegalStateException("WynnExtras search results have no " + name, e);
        }
    }

    private static void giveUp(Exception e) {
        if (broken) return;
        broken = true;
        LOGGER.warn("Couldn't read WynnExtras' All Characters data; counting the open page and inventory only", e);
    }
}
