package com.wynnventory.unid;

import com.wynnventory.unid.UnidSettings.Fill;
import com.wynnventory.unid.UnidSettings.Look;
import com.wynnventory.unid.UnidSettings.Marker;
import com.wynnventory.unid.UnidSettings.PriceMetric;
import com.wynnventory.unid.UnidSettings.Shape;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/** Every setting on one screen, opened by /sls or /unidhighlight and saved when it closes. */
public class UnidSettingsScreen extends OptionsSubScreen {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 20;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int ERROR_COLOR = 0xFFFF5555;
    private static final DecimalFormat LE = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final List<Preset> PRESETS = List.of(
            new Preset("Red #F00", 0xFF0000),
            new Preset("Green #0F0", 0x00FF00),
            new Preset("Blue #00F", 0x0000FF),
            new Preset("Teal #0FC", 0x00FFCC));

    private record Preset(String name, int color) {}

    public UnidSettingsScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.literal("sammui wynnventory looter scooter"));
    }

    @Override
    protected void addOptions() {
        UnidSettings settings = UnidSettings.get();
        list.addSmall(
                CycleButton.onOffBuilder(settings.isHighlightItems())
                        .create(Component.literal("Highlighting"), (button, on) -> settings.setHighlightItems(on)),
                CycleButton.builder(UnidSettingsScreen::enumName, settings.getUnidPriceMetric())
                        .withValues(PriceMetric.values())
                        .withTooltip(metric -> Tooltip.create(metricTooltip(metric)))
                        .create(
                                Component.literal("Unid price"),
                                (button, metric) -> settings.setUnidPriceMetric(metric)));

        addLook("Box look", settings.getBoxLook());
        addLook("Item look", settings.getItemLook());
        addLook("Favorite look", settings.getFavoriteLook());
        addLook("Blacklisted look", settings.getBlacklistLook());

        list.addHeader(Component.literal("Unidentified gear"));
        list.addSmall(label("Min unid price (LE)"), priceBox(settings.getUnidMinPrice(), settings::setUnidMinPrice));
        list.addSmall(
                label("Min listings per day"),
                numberBox(
                        String.valueOf(settings.getMinUnidListingsPerDay()),
                        "\\d*",
                        value -> settings.setMinUnidListingsPerDay(Math.min(Integer.parseInt(value), 50))));

        list.addHeader(Component.literal("Gear boxes"));
        list.addSmall(label("Min box EV (LE)"), priceBox(settings.getBoxMinEv(), settings::setBoxMinEv));
        list.addSmall(List.of(CycleButton.onOffBuilder(settings.isZeroUnderMinPrice())
                .withTooltip(on -> Tooltip.create(Component.literal(
                        "Box EV counts possible items priced under the min unid price as worth nothing,"
                                + " since you'd throw them away.")))
                .create(Component.literal("Cheap items worth 0"), (button, on) -> settings.setZeroUnderMinPrice(on))));

        list.addHeader(Component.literal("Ingredients"));
        list.addSmall(
                label("Min lowest price (LE)"),
                priceBox(settings.getIngredientMinPrice(), settings::setIngredientMinPrice));

        addNameList("Favorites", settings.getFavorites(), settings::addToFavorites, settings::removeFromFavorites);
        addNameList("Blacklist", settings.getBlacklist(), settings::addToBlacklist, settings::removeFromBlacklist);
    }

    @Override
    public void removed() {
        super.removed();
        UnidSettings.save();
    }

    /** An add box, then every name on the list with a remove button. */
    private void addNameList(String title, List<String> names, Consumer<String> add, Consumer<String> remove) {
        list.addHeader(Component.literal(title));
        EditBox nameBox = new EditBox(font, 0, 0, WIDTH, HEIGHT, Component.literal("Item name"));
        nameBox.setMaxLength(64);
        nameBox.setHint(Component.literal("Exact item name"));
        nameBox.setResponder(value -> nameBox.setTextColor(TEXT_COLOR));
        list.addSmall(
                nameBox,
                Button.builder(Component.literal("Add"), button -> addName(nameBox, add))
                        .width(WIDTH)
                        .build());
        for (String item : names) {
            list.addSmall(
                    label(item),
                    Button.builder(Component.literal("Remove"), button -> {
                                remove.accept(item);
                                refresh();
                            })
                            .width(WIDTH)
                            .build());
        }
    }

    private void addName(EditBox nameBox, Consumer<String> add) {
        Optional<String> name = BlacklistFeature.resolveItemName(nameBox.getValue());
        if (name.isEmpty()) {
            nameBox.setTextColor(ERROR_COLOR);
            return;
        }

        add.accept(name.get());
        refresh();
    }

    // Reopened so the lists match the settings. Not rebuildWidgets(): that adds everything to the same layout again,
    // stacking a second list and Done button over the first.
    private void refresh() {
        double scroll = list.scrollAmount();
        UnidSettingsScreen screen = new UnidSettingsScreen(lastScreen);
        minecraft.setScreen(screen);
        screen.list.setScrollAmount(scroll);
    }

    private void addLook(String title, Look look) {
        list.addHeader(Component.literal(title));
        list.addSmall(
                cycle("Shape", Shape.values(), look.getShape(), look::setShape),
                cycle("Fill", Fill.values(), look.getFill(), look::setFill));
        EditBox hexBox = colorBox(look::getColor, look::setColor, "Color as hex, like #F00 or #00FFCC");
        Preset current = PRESETS.stream()
                .filter(preset -> preset.color() == look.getColor())
                .findFirst()
                .orElse(PRESETS.getFirst());
        list.addSmall(
                CycleButton.builder(
                                (Preset preset) ->
                                        Component.literal(preset.name()).withColor(preset.color()),
                                current)
                        .withValues(PRESETS)
                        .create(
                                Component.literal("Color preset"),
                                (button, preset) -> hexBox.setValue(hex(preset.color()))),
                hexBox);
        list.addSmall(
                cycle("Marker", Marker.values(), look.getMarker(), look::setMarker),
                colorBox(look::getMarkerColor, look::setMarkerColor, "Marker color as hex, like #FF5 or #FFFFFF"));
    }

    private static <T extends Enum<T>> CycleButton<T> cycle(String label, T[] values, T value, Consumer<T> setter) {
        return CycleButton.builder(UnidSettingsScreen::enumName, value)
                .withValues(values)
                .create(Component.literal(label), (button, chosen) -> setter.accept(chosen));
    }

    /** A hex color box, with a swatch of the color at its right end. */
    private EditBox colorBox(IntSupplier getter, IntConsumer setter, String tooltip) {
        EditBox box = new EditBox(font, 0, 0, WIDTH, HEIGHT, Component.literal(tooltip)) {
            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                super.renderWidget(graphics, mouseX, mouseY, partialTick);
                graphics.fill(
                        getRight() - 16, getY() + 4, getRight() - 4, getBottom() - 4, 0xFF000000 | getter.getAsInt());
            }
        };
        box.setMaxLength(7);
        box.setValue(hex(getter.getAsInt()));
        box.setFilter(text -> text.matches("#?[0-9a-fA-F]{0,6}"));
        box.setTooltip(Tooltip.create(Component.literal(tooltip)));
        // Red until the text is a whole color; the last whole color stays in use meanwhile.
        box.setResponder(text -> {
            Integer color = UnidSettings.parseHexColor(text);
            box.setTextColor(color == null ? ERROR_COLOR : TEXT_COLOR);
            if (color != null) setter.accept(color);
        });
        return box;
    }

    private static String hex(int color) {
        return String.format("#%06X", color);
    }

    private StringWidget label(String text) {
        return new StringWidget(WIDTH, HEIGHT, Component.literal(text), font);
    }

    /** A box for a price in LE, so 0.5 is 32 EB. */
    private EditBox priceBox(int emeralds, IntConsumer setter) {
        return numberBox(
                LE.format(emeralds / (double) UnidAddon.EMERALDS_PER_LE),
                "\\d*\\.?\\d*",
                value -> setter.accept(UnidAddon.toEmeralds(Double.parseDouble(value))));
    }

    private EditBox numberBox(String value, String pattern, Consumer<String> onValid) {
        EditBox box = new EditBox(font, 0, 0, WIDTH, HEIGHT, Component.empty());
        box.setMaxLength(9);
        box.setValue(value);
        box.setFilter(text -> text.matches(pattern));
        // An empty box, or a lone ".", keeps the last value until a number is typed.
        box.setResponder(text -> {
            if (text.matches(".*\\d.*")) onValid.accept(text);
        });
        return box;
    }

    private static Component enumName(Enum<?> value) {
        String name = value.name().replace('_', ' ');
        return Component.literal(name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT));
    }

    private static Component metricTooltip(PriceMetric metric) {
        return Component.literal(
                switch (metric) {
                    case ROBUST ->
                        "The middle of the live lowest, live median, moving median and archived median,"
                                + " so one odd listing can't swing it.";
                    case MEDIAN -> "The live unid median, or the archived one when nothing is listed.";
                    case LOWEST -> "The live unid lowest price, or the archived one when nothing is listed.";
                    case AVERAGE -> "The live unid average, or the archived one when nothing is listed.";
                });
    }
}
