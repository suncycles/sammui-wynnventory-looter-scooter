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

    /** How highlighted slots are drawn; every style also stars them. */
    public enum HighlightStyle {
        FRAME,
        FILL,
        PULSE
    }

    private boolean highlightItems = true;
    private HighlightStyle highlightStyle = HighlightStyle.FRAME;
    private int highlightColor = 0x00FFCC; // WynnVentory's default teal
    private PriceMetric unidPriceMetric = PriceMetric.ROBUST;
    private int unidMinPrice = 8192; // 2 LE
    private int minUnidListingsPerDay = 3;
    private int boxMinEv = 4096; // 1 LE
    private boolean zeroUnderMinPrice = true;
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

    public HighlightStyle getHighlightStyle() {
        // Gson reads a misspelled style in the file as null.
        return highlightStyle == null ? HighlightStyle.FRAME : highlightStyle;
    }

    public void setHighlightStyle(HighlightStyle highlightStyle) {
        this.highlightStyle = highlightStyle;
    }

    /** The highlight color as 0xRRGGBB. */
    public int getHighlightColor() {
        return highlightColor & 0xFFFFFF;
    }

    public void setHighlightColor(int highlightColor) {
        this.highlightColor = highlightColor & 0xFFFFFF;
    }

    /** Reads #RGB or #RRGGBB, with or without the #, as 0xRRGGBB; null for anything else. */
    static Integer parseHexColor(String text) {
        String hex = text.strip();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("[0-9a-fA-F]{3}|[0-9a-fA-F]{6}")) return null;
        if (hex.length() == 3) hex = hex.replaceAll("(.)", "$1$1");
        return Integer.parseInt(hex, 16);
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

    /** Whether box EV counts possible items priced under the min unid price as worth nothing. */
    public boolean isZeroUnderMinPrice() {
        return zeroUnderMinPrice;
    }

    public void setZeroUnderMinPrice(boolean zeroUnderMinPrice) {
        this.zeroUnderMinPrice = zeroUnderMinPrice;
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
