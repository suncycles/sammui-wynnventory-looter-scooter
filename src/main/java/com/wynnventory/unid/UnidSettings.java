package com.wynnventory.unid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Thresholds for unid highlighting, kept in config/wynnventory-unid.json. */
public final class UnidSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger("wynnventory_unid");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static UnidSettings instance = new UnidSettings();

    /** Which unid price the thresholds and box EV use. */
    public enum PriceMetric {
        ROBUST,
        MEDIAN,
        LOWEST,
        AVERAGE
    }

    private boolean highlightItems = true;
    private PriceMetric unidPriceMetric = PriceMetric.ROBUST;
    private int unidMinPrice = 8192; // 2 LE
    private int minUnidListingsPerDay = 3;
    private int boxMinEv = 4096; // 1 LE
    private int ingredientMinPrice = 2048; // 32 EB
    private List<String> blacklist = new ArrayList<>();

    public static UnidSettings get() {
        return instance;
    }

    static void load() {
        Path file = file();
        if (!Files.exists(file)) return;

        try (Reader reader = Files.newBufferedReader(file)) {
            UnidSettings loaded = GSON.fromJson(reader, UnidSettings.class);
            if (loaded != null) instance = loaded;
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Could not read {}, using defaults", file, e);
        }
    }

    static void save() {
        try {
            Files.writeString(file(), GSON.toJson(instance));
        } catch (IOException e) {
            LOGGER.warn("Could not save {}", file(), e);
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("wynnventory-unid.json");
    }

    public boolean isHighlightItems() {
        return highlightItems;
    }

    public void setHighlightItems(boolean highlightItems) {
        this.highlightItems = highlightItems;
    }

    public int getUnidMinPrice() {
        return unidMinPrice;
    }

    public void setUnidMinPrice(int unidMinPrice) {
        this.unidMinPrice = unidMinPrice;
    }

    public int getMinUnidListingsPerDay() {
        return minUnidListingsPerDay;
    }

    public void setMinUnidListingsPerDay(int minUnidListingsPerDay) {
        this.minUnidListingsPerDay = minUnidListingsPerDay;
    }

    public PriceMetric getUnidPriceMetric() {
        // Gson reads a misspelled metric in the file as null.
        return unidPriceMetric == null ? PriceMetric.ROBUST : unidPriceMetric;
    }

    public void setUnidPriceMetric(PriceMetric unidPriceMetric) {
        this.unidPriceMetric = unidPriceMetric;
    }

    public int getBoxMinEv() {
        return boxMinEv;
    }

    public void setBoxMinEv(int boxMinEv) {
        this.boxMinEv = boxMinEv;
    }

    public int getIngredientMinPrice() {
        return ingredientMinPrice;
    }

    public void setIngredientMinPrice(int ingredientMinPrice) {
        this.ingredientMinPrice = ingredientMinPrice;
    }

    public List<String> getBlacklist() {
        return blacklist;
    }

    public boolean isBlacklisted(String name) {
        return blacklist.stream().anyMatch(name::equalsIgnoreCase);
    }

    /** Returns false if the item was already blacklisted. */
    public boolean addToBlacklist(String name) {
        if (isBlacklisted(name)) return false;

        blacklist.add(name);
        blacklist.sort(String.CASE_INSENSITIVE_ORDER);
        return true;
    }

    /** Returns false if the item wasn't blacklisted. */
    public boolean removeFromBlacklist(String name) {
        return blacklist.removeIf(name::equalsIgnoreCase);
    }
}
