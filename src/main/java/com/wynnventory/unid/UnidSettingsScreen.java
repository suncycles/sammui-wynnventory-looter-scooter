package com.wynnventory.unid;

import com.wynnventory.unid.UnidSettings.PriceMetric;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
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
                CycleButton.builder(UnidSettingsScreen::metricName, settings.getUnidPriceMetric())
                        .withValues(PriceMetric.values())
                        .withTooltip(metric -> Tooltip.create(metricTooltip(metric)))
                        .create(
                                Component.literal("Unid price"),
                                (button, metric) -> settings.setUnidPriceMetric(metric)));

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

    private static Component metricName(PriceMetric metric) {
        String name = metric.name();
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
