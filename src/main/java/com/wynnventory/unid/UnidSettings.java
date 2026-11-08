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

    /** Drawn over the slot's edge, or across it. */
    public enum Shape {
        NONE,
        FRAME,
        CORNERS,
        DOUBLE_FRAME,
        DASHED_FRAME,
        SLASH
    }

    /** Drawn behind the item, except DIM, which darkens the item itself. */
    public enum Fill {
        NONE,
        FAINT,
        HALF,
        PULSE,
        DIM
    }

    /** A glyph in the slot's top-left corner, in its own color unless the look sets one. */
    public enum Marker {
        NONE("", 0),
        STAR("★", 0xFFFFFF55),
        DIAMOND("◆", 0xFFFFC800),
        CROSS("✕", 0xFFFF4A4A);

        public final String glyph;
        public final int color;

        Marker(String glyph, int color) {
            this.glyph = glyph;
            this.color = color;
        }
    }

    /** How one kind of slot is drawn; the shape and fill use its color, the marker its own. */
    public static final class Look {
        private Shape shape;
        private Fill fill;
        private Marker marker;
        private int color;
        // Null until set: the marker's own color.
        private Integer markerColor;

        Look(Shape shape, Fill fill, Marker marker, int color, Integer markerColor) {
            this.shape = shape;
            this.fill = fill;
            this.marker = marker;
            this.color = color;
            this.markerColor = markerColor;
        }

        // Gson reads a misspelled value in the file as null.
        public Shape getShape() {
            return shape == null ? Shape.NONE : shape;
        }

        public void setShape(Shape shape) {
            this.shape = shape;
        }

        public Fill getFill() {
            return fill == null ? Fill.NONE : fill;
        }

        public void setFill(Fill fill) {
            this.fill = fill;
        }

        public Marker getMarker() {
            return marker == null ? Marker.NONE : marker;
        }

        public void setMarker(Marker marker) {
            this.marker = marker;
        }

        /** The color as 0xRRGGBB. */
        public int getColor() {
            return color & 0xFFFFFF;
        }

        public void setColor(int color) {
            this.color = color & 0xFFFFFF;
        }

        /** The marker color as 0xRRGGBB. */
        public int getMarkerColor() {
            return markerColor != null ? markerColor & 0xFFFFFF : getMarker().color & 0xFFFFFF;
        }

        public void setMarkerColor(int markerColor) {
            this.markerColor = markerColor & 0xFFFFFF;
        }
    }

    private boolean highlightItems = true;
    // Boxes and items start as the frame and star they always had, in WynnVentory's default teal.
    private Look boxLook = new Look(Shape.FRAME, Fill.FAINT, Marker.STAR, 0x00FFCC, null);
    private Look itemLook = new Look(Shape.FRAME, Fill.FAINT, Marker.STAR, 0x00FFCC, null);
    private Look blacklistLook = new Look(Shape.NONE, Fill.DIM, Marker.CROSS, 0xFF4A4A, null);
    private Look favoriteLook = new Look(Shape.FRAME, Fill.FAINT, Marker.STAR, 0xFF55FF, 0xFFFFFF);
    private PriceMetric unidPriceMetric = PriceMetric.ROBUST;
    private int unidMinPrice = 8192; // 2 LE
    private int minUnidListingsPerDay = 3;
    private int boxMinEv = 4096; // 1 LE
    private boolean zeroUnderMinPrice = true;
    private int ingredientMinPrice = 2048; // 32 EB
    private List<String> blacklist = new ArrayList<>();
    private List<String> favorites = new ArrayList<>();

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

    public Look getBoxLook() {
        return boxLook;
    }

    public Look getItemLook() {
        return itemLook;
    }

    /** For blacklisted gear and ingredients, which are never highlighted as valuable. */
    public Look getBlacklistLook() {
        return blacklistLook;
    }

    /** For favorite gear and ingredients, and boxes that can drop them, highlighted whatever they're worth. */
    public Look getFavoriteLook() {
        return favoriteLook;
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
        return contains(blacklist, name);
    }

    /** Returns false if the item was already blacklisted; a blacklisted item stops being a favorite. */
    public boolean addToBlacklist(String name) {
        removeFrom(favorites, name);
        return addTo(blacklist, name);
    }

    /** Returns false if the item wasn't blacklisted. */
    public boolean removeFromBlacklist(String name) {
        return removeFrom(blacklist, name);
    }

    public List<String> getFavorites() {
        return favorites;
    }

    public boolean isFavorite(String name) {
        return contains(favorites, name);
    }

    /** Returns false if the item was already a favorite; a favorite stops being blacklisted. */
    public boolean addToFavorites(String name) {
        removeFrom(blacklist, name);
        return addTo(favorites, name);
    }

    /** Returns false if the item wasn't a favorite. */
    public boolean removeFromFavorites(String name) {
        return removeFrom(favorites, name);
    }

    private static boolean contains(List<String> names, String name) {
        return names.stream().anyMatch(name::equalsIgnoreCase);
    }

    private static boolean addTo(List<String> names, String name) {
        if (contains(names, name)) return false;

        names.add(name);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return true;
    }

    private static boolean removeFrom(List<String> names, String name) {
        return names.removeIf(name::equalsIgnoreCase);
    }
}
