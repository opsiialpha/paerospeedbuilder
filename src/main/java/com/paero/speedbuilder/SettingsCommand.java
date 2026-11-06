package com.paero.speedbuilder;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /pb config finishtime <seconds>      - how long the finish title stays on screen
 * /pb config difficulty <default|paero> - difficulty setting
 * /pb config theme <default|dark|paero> - UI theme
 * (merged into the existing /pb config node by Brigadier)
 */
public class SettingsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pb").then(Commands.literal("config")
            .then(Commands.literal("finishtime").then(Commands.argument("seconds",
                IntegerArgumentType.integer(SpeedBuilderConfig.MIN_FINISH_TIME, SpeedBuilderConfig.MAX_FINISH_TIME)).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                SpeedBuilderConfig.setFinishTitleTime(player.getUUID(), seconds);
                player.displayClientMessage(Theme.lit(player, "\u00a7aFinish title time set to " + seconds + " seconds"), false);
                commands.showConfig(ctx.getSource(), player);
                return 1;
            })))
            .then(Commands.literal("difficulty")
                .then(Commands.literal("default").executes(ctx -> setDifficulty(ctx.getSource(), SpeedBuilderConfig.DIFFICULTY_DEFAULT)))
                .then(Commands.literal("paero").executes(ctx -> setDifficulty(ctx.getSource(), SpeedBuilderConfig.DIFFICULTY_PAERO))))
            .then(Commands.literal("theme")
                .then(Commands.literal("default").executes(ctx -> setTheme(ctx.getSource(), Theme.DEFAULT)))
                .then(Commands.literal("dark").executes(ctx -> setTheme(ctx.getSource(), Theme.DARK)))
                .then(Commands.literal("paero").executes(ctx -> setTheme(ctx.getSource(), Theme.PAERO))))));
    }

    private static int setTheme(CommandSourceStack source, String theme) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SpeedBuilderConfig.setTheme(player.getUUID(), theme);
        player.displayClientMessage(Theme.lit(player, "\u00a7aUI theme set to " + Theme.displayName(theme)), false);
        commands.showConfig(source, player);
        return 1;
    }

    private static int setDifficulty(CommandSourceStack source, String difficulty) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SpeedBuilderConfig.setDifficulty(player.getUUID(), difficulty);
        player.displayClientMessage(Theme.lit(player, "\u00a7aDifficulty set to " + (SpeedBuilderConfig.DIFFICULTY_PAERO.equals(difficulty) ? "Paero" : "Default")), false);
        commands.showConfig(source, player);
        return 1;
    }
}
