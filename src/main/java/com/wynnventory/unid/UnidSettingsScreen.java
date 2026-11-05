package com.wynnventory.unid;

import com.wynnventory.unid.UnidSettings.HighlightStyle;
import com.wynnventory.unid.UnidSettings.PriceMetric;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
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

        list.addHeader(Component.literal("Highlight"));
        EditBox hexBox = colorBox();
        list.addSmall(
                CycleButton.builder(UnidSettingsScreen::enumName, settings.getHighlightStyle())
                        .withValues(HighlightStyle.values())
                        .withTooltip(style -> Tooltip.create(styleTooltip(style)))
                        .create(Component.literal("Style"), (button, style) -> settings.setHighlightStyle(style)),
                hexBox);
        list.addSmall(List.of(
                preset("Red #F00", 0xFF0000, hexBox),
                preset("Green #0F0", 0x00FF00, hexBox),
                preset("Blue #00F", 0x0000FF, hexBox),
                preset("Teal #0FC", 0x00FFCC, hexBox)));

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

        list.addHeader(Component.literal("Blacklist"));
        EditBox nameBox = new EditBox(font, 0, 0, WIDTH, HEIGHT, Component.literal("Item name"));
        nameBox.setMaxLength(64);
        nameBox.setHint(Component.literal("Exact item name"));
        nameBox.setResponder(value -> nameBox.setTextColor(TEXT_COLOR));
        list.addSmall(
                nameBox,
                Button.builder(Component.literal("Add"), button -> addToBlacklist(nameBox))
                        .width(WIDTH)
                        .build());
        for (String item : settings.getBlacklist()) {
            list.addSmall(
                    label(item),
                    Button.builder(Component.literal("Remove"), button -> {
                                settings.removeFromBlacklist(item);
                                refresh();
                            })
                            .width(WIDTH)
                            .build());
        }
    }

    @Override
    public void removed() {
        super.removed();
        UnidSettings.save();
    }

    private void addToBlacklist(EditBox nameBox) {
        Optional<String> name = BlacklistFeature.resolveItemName(nameBox.getValue());
        if (name.isEmpty()) {
            nameBox.setTextColor(ERROR_COLOR);
            return;
        }

        UnidSettings.get().addToBlacklist(name.get());
        refresh();
    }

    // Rebuilt so the list matches the blacklist; without restoring it, the scroll position jumps to the top.
    private void refresh() {
        double scroll = list.scrollAmount();
        rebuildWidgets();
        list.setScrollAmount(scroll);
    }

    /** A hex box for the highlight color, with a swatch of the color at its right end. */
    private EditBox colorBox() {
        EditBox box = new EditBox(font, 0, 0, WIDTH, HEIGHT, Component.literal("Highlight color")) {
            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                super.renderWidget(graphics, mouseX, mouseY, partialTick);
                int color = 0xFF000000 | UnidSettings.get().getHighlightColor();
                graphics.fill(getRight() - 16, getY() + 4, getRight() - 4, getBottom() - 4, color);
            }
        };
        box.setMaxLength(7);
        box.setValue(String.format("#%06X", UnidSettings.get().getHighlightColor()));
        box.setFilter(text -> text.matches("#?[0-9a-fA-F]{0,6}"));
        box.setTooltip(Tooltip.create(Component.literal("Highlight color as hex, like #F00 or #00FFCC")));
        // Red until the text is a whole color; the last whole color stays in use meanwhile.
        box.setResponder(text -> {
            Integer color = UnidSettings.parseHexColor(text);
            box.setTextColor(color == null ? ERROR_COLOR : TEXT_COLOR);
            if (color != null) UnidSettings.get().setHighlightColor(color);
        });
        return box;
    }

    private Button preset(String name, int color, EditBox hexBox) {
        return Button.builder(
                        Component.literal(name).withColor(color),
                        button -> hexBox.setValue(String.format("#%06X", color)))
                .width(WIDTH)
                .build();
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
        String name = value.name();
        return Component.literal(name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT));
    }

    private static Component styleTooltip(HighlightStyle style) {
        return Component.literal(
                switch (style) {
                    case FRAME -> "A solid border around the slot over a faint fill.";
                    case FILL -> "A half-transparent fill behind the item.";
                    case PULSE -> "A fill that fades in and out.";
                });
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
