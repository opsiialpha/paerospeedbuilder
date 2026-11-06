package com.paero.speedbuilder;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /pb finish ... : choose the text + colour shown in the middle of the screen when a build is completed. */
public class FinishCommand {
    private static final List<String> COLOR_NAMES = List.of(
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
        "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pb").then(Commands.literal("finish")
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                player.displayClientMessage(Theme.lit(player, "\u00a76Finish screen: ").append(FinishDisplay.buildTitle(player.getUUID())), false);
                player.displayClientMessage(Theme.lit(player, "\u00a77/pb finish text <text>  \u00a78(use &c, &l etc. for extra formatting)"), false);
                player.displayClientMessage(Theme.lit(player, "\u00a77/pb finish color <name|#RRGGBB>"), false);
                player.displayClientMessage(Theme.lit(player, "\u00a77/pb finish test  \u00a78| \u00a77/pb finish reset  \u00a78| \u00a77/pb finish debug"), false);
                return 1;
            })
            .then(Commands.literal("text").then(Commands.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                String text = StringArgumentType.getString(ctx, "text");
                if (text.length() > FinishDisplay.MAX_TEXT_LENGTH) {
                    text = text.substring(0, FinishDisplay.MAX_TEXT_LENGTH);
                }
                SpeedBuilderConfig.setFinishTitle(player.getUUID(), text);
                FinishDisplay.show(player, 12340L);
                return 1;
            })))
            .then(Commands.literal("color").then(Commands.argument("color", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(COLOR_NAMES, builder))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String color = StringArgumentType.getString(ctx, "color");
                    if (FinishDisplay.parseColor(color) == null) {
                        ctx.getSource().sendFailure(Theme.lit(ctx.getSource(), "Unknown colour. Use a name like gold / red / light_purple or a hex code like #FF8800"));
                        return 0;
                    }
                    SpeedBuilderConfig.setFinishColor(player.getUUID(), color);
                    FinishDisplay.show(player, 12340L);
                    return 1;
                })))
            .then(Commands.literal("reset").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                SpeedBuilderConfig.setFinishTitle(player.getUUID(), "Superb!");
                SpeedBuilderConfig.setFinishColor(player.getUUID(), "green");
                FinishDisplay.show(player, 12340L);
                return 1;
            }))
            .then(Commands.literal("debug").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                spbAccuracy.BuildSession session = spbAccuracy.ACTIVE_SESSIONS.get(player.getUUID());
                if (session == null) {
                    player.displayClientMessage(Theme.lit(player, "\u00a7cNo active build session - start a build first, then run this while building."), false);
                    return 0;
                }
                for (String line : FinishScan.report(player.serverLevel(), session)) {
                    player.displayClientMessage(Theme.lit(player, line), false);
                }
                return 1;
            }))
            .then(Commands.literal("test").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                FinishDisplay.show(player, 12340L);
                return 1;
            }))));
    }
}
