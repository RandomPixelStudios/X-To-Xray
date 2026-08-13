package com.xtoxray;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Command registration — isolated MC imports.
 * Loaded ONLY via Class.forName() from XtoXray.
 * If any MC class is missing, this class fails to load and commands are disabled.
 */
public class CommandRegistrar {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                ((LiteralArgumentBuilder) Commands.literal(XtoXray.MOD_ID)
                    .then(Commands.literal("durability")
                        .then(Commands.literal("xblock")
                            .then(Commands.argument("amount", (ArgumentType) IntegerArgumentType.integer(0, 100))
                                .executes(context -> {
                                    int amount = IntegerArgumentType.getInteger((CommandContext) context, "amount");
                                    XrayState.getInstance().setVeinMinerDurability(amount);
                                    ((CommandSourceStack) context.getSource()).sendSuccess(
                                        () -> Component.literal("Durability cost per block set to " + amount), true);
                                    return 1;
                                }))))
                    .then(((LiteralArgumentBuilder) Commands.literal("veinminer")
                        .then(Commands.literal("on").executes(context -> {
                            XrayState.getInstance().setVeinMiner(true);
                            ((CommandSourceStack) context.getSource()).sendSuccess(
                                () -> Component.literal("Vein miner enabled"), true);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(context -> {
                            XrayState.getInstance().setVeinMiner(false);
                            ((CommandSourceStack) context.getSource()).sendSuccess(
                                () -> Component.literal("Vein miner disabled"), true);
                            return 1;
                        }))))
                    .then(Commands.literal("status").executes(context -> {
                        XrayState state = XrayState.getInstance();
                        ((CommandSourceStack) context.getSource()).sendSuccess(
                            () -> Component.literal("Vein miner: " + (state.isVeinMiner() ? "ON" : "OFF")
                                + ", durability per block: " + state.getVeinMinerDurability()), false);
                        return 1;
                    }))));
        });
    }
}
