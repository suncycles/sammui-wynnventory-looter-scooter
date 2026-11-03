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
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/** Loads the thresholds and registers /unidhighlight to change them; the listeners join via WynntilsModMixin. */
public final class UnidAddon implements ClientModInitializer {
    private static final int EMERALDS_PER_LE = 4096;

    @Override
    public void onInitializeClient() {
        UnidSettings.load();

        LiteralArgumentBuilder<FabricClientCommandSource> metric = literal("metric");
        for (UnidSettings.PriceMetric value : UnidSettings.PriceMetric.values()) {
            metric.then(literal(value.name().toLowerCase(Locale.ROOT))
                    .executes(ctx -> update(ctx.getSource(), s -> s.setUnidPriceMetric(value))));
        }

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(literal(
                        "unidhighlight")
                .executes(ctx -> showSettings(ctx.getSource()))
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
                                                ctx.getSource(), StringArgumentType.getString(ctx, "item"))))))));

        KeyBindingHelper.registerKeyBinding(BlacklistFeature.TOGGLE_KEY);
        KeyBindingHelper.registerKeyBinding(BlacklistFeature.MARK_PREVIOUS_KEY);
        KeyBindingHelper.registerKeyBinding(BlacklistFeature.MARK_NEXT_KEY);
    }

    private static String keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }

    private static int addToBlacklist(FabricClientCommandSource source, String input) {
        // Store the item's own spelling, and refuse typos that would never match anything.
        Optional<String> name =
                BlacklistFeature.itemNames().filter(input::equalsIgnoreCase).findFirst();
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
                .then(argument("le", DoubleArgumentType.doubleArg(0, 100_000))
                        .executes(ctx -> update(
                                ctx.getSource(),
                                s -> setter.accept(s, (int)
                                        Math.round(DoubleArgumentType.getDouble(ctx, "le") * EMERALDS_PER_LE)))));
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
                + "\nIngredients: lowest price ≥ " + UnidValue.formatEmeralds(settings.getIngredientMinPrice())
                + "\nBlacklist: " + settings.getBlacklist().size() + " items (/unidhighlight blacklist)"));
        return 1;
    }
}
