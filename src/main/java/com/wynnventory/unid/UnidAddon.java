package com.wynnventory.unid;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * Loads the thresholds and registers /unidhighlight (or /sls) to change them; the listeners join via
 * WynntilsModMixin.
 */
public final class UnidAddon implements ClientModInitializer {
    static final int EMERALDS_PER_LE = 4096;
    private static final double MAX_LE = 100_000;

    @Override
    public void onInitializeClient() {
        UnidSettings.load();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            dispatcher.register(command("unidhighlight"));
            dispatcher.register(command("sls"));
        });

        KeyBindingHelper.registerKeyBinding(BlacklistFeature.TOGGLE_KEY);
        KeyBindingHelper.registerKeyBinding(BlacklistFeature.MARK_PREVIOUS_KEY);
        KeyBindingHelper.registerKeyBinding(BlacklistFeature.MARK_NEXT_KEY);
    }

    static int toEmeralds(double le) {
        return (int) Math.round(Math.min(le, MAX_LE) * EMERALDS_PER_LE);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        LiteralArgumentBuilder<FabricClientCommandSource> metric = literal("metric");
        for (UnidSettings.PriceMetric value : UnidSettings.PriceMetric.values()) {
            metric.then(literal(value.name().toLowerCase(Locale.ROOT))
                    .executes(ctx -> update(ctx.getSource(), s -> s.setUnidPriceMetric(value))));
        }

        return literal(name)
                .executes(ctx -> openSettings())
                .then(literal("on").executes(ctx -> update(ctx.getSource(), s -> s.setHighlightItems(true))))
                .then(literal("off").executes(ctx -> update(ctx.getSource(), s -> s.setHighlightItems(false))))
                .then(metric)
                .then(priceSetting("minprice", UnidSettings::setUnidMinPrice))
                .then(priceSetting("minboxev", UnidSettings::setBoxMinEv))
                .then(priceSetting("miningredient", UnidSettings::setIngredientMinPrice))
                .then(literal("minlistings")
                        .then(argument("perDay", IntegerArgumentType.integer(0, 50))
                                .executes(ctx -> update(
                                        ctx.getSource(),
                                        s -> s.setMinUnidListingsPerDay(
                                                IntegerArgumentType.getInteger(ctx, "perDay"))))))
                .then(literal("blacklist")
                        .executes(ctx -> showBlacklist(ctx.getSource()))
                        .then(literal("add")
                                .then(argument("item", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) ->
                                                SharedSuggestionProvider.suggest(BlacklistFeature.itemNames(), builder))
                                        .executes(ctx -> addToBlacklist(
                                                ctx.getSource(), StringArgumentType.getString(ctx, "item")))))
                        .then(literal("remove")
                                .then(argument("item", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                UnidSettings.get().getBlacklist(), builder))
                                        .executes(ctx -> removeFromBlacklist(
                                                ctx.getSource(), StringArgumentType.getString(ctx, "item"))))));
    }

    private static int openSettings() {
        Minecraft mc = Minecraft.getInstance();
        // Deferred so the chat screen, which closes after the command runs, doesn't close the settings with it.
        mc.schedule(() -> mc.setScreen(new UnidSettingsScreen(null)));
        return 1;
    }

    private static String keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }

    private static int addToBlacklist(FabricClientCommandSource source, String input) {
        Optional<String> name = BlacklistFeature.resolveItemName(input);
        if (name.isEmpty()) {
            source.sendError(Component.literal("No gear or ingredient is called " + input));
            return 0;
        }

        UnidSettings.get().addToBlacklist(name.get());
        UnidSettings.save();
        return showBlacklist(source);
    }

    private static int removeFromBlacklist(FabricClientCommandSource source, String input) {
        if (!UnidSettings.get().removeFromBlacklist(input)) {
            source.sendError(Component.literal(input + " isn't blacklisted"));
            return 0;
        }

        UnidSettings.save();
        return showBlacklist(source);
    }

    /** Lists the blacklist with a clickable [x] that removes each item. */
    private static int showBlacklist(FabricClientCommandSource source) {
        MutableComponent message = Component.literal("Blacklist (hover an item and press "
                + keyName(BlacklistFeature.TOGGLE_KEY) + " to add or remove it; on a box, first mark one of its items"
                + " with " + keyName(BlacklistFeature.MARK_PREVIOUS_KEY) + "/"
                + keyName(BlacklistFeature.MARK_NEXT_KEY) + "):");
        List<String> blacklist = UnidSettings.get().getBlacklist();
        if (blacklist.isEmpty()) message.append("\n  empty");
        for (String name : blacklist) {
            message.append("\n ")
                    .append(Component.literal("[x]").withStyle(style -> style.withColor(ChatFormatting.RED)
                            .withClickEvent(new ClickEvent.RunCommand("/unidhighlight blacklist remove " + name))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Remove " + name)))))
                    .append(" " + name);
        }

        source.sendFeedback(message);
        return 1;
    }

    /** A subcommand taking a price in LE, so 0.5 is 32 EB. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> priceSetting(
            String name, ObjIntConsumer<UnidSettings> setter) {
        return literal(name)
                .then(argument("le", DoubleArgumentType.doubleArg(0, MAX_LE))
                        .executes(ctx -> update(
                                ctx.getSource(),
                                s -> setter.accept(s, toEmeralds(DoubleArgumentType.getDouble(ctx, "le"))))));
    }

    private static int update(FabricClientCommandSource source, Consumer<UnidSettings> change) {
        change.accept(UnidSettings.get());
        UnidSettings.save();
        return showSettings(source);
    }

    private static int showSettings(FabricClientCommandSource source) {
        UnidSettings settings = UnidSettings.get();
        source.sendFeedback(Component.literal("Unid highlight " + (settings.isHighlightItems() ? "on" : "off")
                + "\nGear: unid " + settings.getUnidPriceMetric().name().toLowerCase(Locale.ROOT) + " price ≥ "
                + UnidValue.formatEmeralds(settings.getUnidMinPrice()) + ", " + settings.getMinUnidListingsPerDay()
                + "+ listings/day"
                + "\nBoxes: EV ≥ " + UnidValue.formatEmeralds(settings.getBoxMinEv())
                + (settings.isZeroUnderMinPrice() ? ", items under the min price count as 0" : "")
                + "\nIngredients: lowest price ≥ " + UnidValue.formatEmeralds(settings.getIngredientMinPrice())
                + "\nBlacklist: " + settings.getBlacklist().size() + " items (/unidhighlight blacklist)"));
        return 1;
    }
}
