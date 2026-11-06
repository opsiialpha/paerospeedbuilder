package com.paero.speedbuilder;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import java.io.InputStream;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class commands {
    private static String timeFormat(long ms) {
        return ms / 1000L + "." + String.format("%03d", ms % 1000L).substring(0, 2) + "s";
    }

    private static InputStream getBuildStream(String buildName) {
        InputStream customStream = customBuilds.getCustomBuildStream(buildName);
        if (customStream != null) {
            return customStream;
        }
        return commands.class.getResourceAsStream("/assets/paerospeedbuilder/builds/" + buildName + ".schem");
    }

    public static boolean isValidBuildName(String buildName) {
        if (customBuilds.isCustomBuild(buildName)) {
            return true;
        }
        String resourcePath = "/assets/paerospeedbuilder/builds/" + buildName + ".schem";
        try (InputStream stream = commands.class.getResourceAsStream(resourcePath)) {
            return stream != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static Component makeToggleButton(boolean enabled, boolean enableAction, String command) {
        boolean unneeded;
        boolean bl = unneeded = enabled == enableAction;
        ChatFormatting color = enableAction ? (unneeded ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN) : (unneeded ? ChatFormatting.DARK_GRAY : ChatFormatting.RED);
        String label = enableAction ? "Enable" : "Disable";
        MutableComponent base = Component.literal(("[ " + label + " ]")).withStyle(color);
        if (!unneeded) {
            Style style = base.getStyle().withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
            base = base.setStyle(style);
        }
        return base;
    }

    public static void showConfig(CommandSourceStack source, ServerPlayer player) {
        int pad = 14;
        boolean previewEnabled = SpeedBuilderConfig.isPreviewEnabled(player.getUUID());
        String previewLabel = String.format("%-14s", "Build Preview: ");
        MutableComponent previewStatus = Component.literal((previewEnabled ? "ENABLED" : "DISABLED")).withStyle(s -> s.withColor(previewEnabled ? 65280 : 0xFF0000).withBold(true));
        Component previewEnableBtn = commands.makeToggleButton(previewEnabled, true, "/pb config preview on");
        Component previewDisableBtn = commands.makeToggleButton(previewEnabled, false, "/pb config preview off");
        int currentPreviewTime = SpeedBuilderConfig.getPreviewTime(player.getUUID());
        String previewTimeLabel = String.format("%-14s", "Preview Time: ");
        boolean buildModeEnabled = buildMode.isInBuildMode(player);
        String bmLabel = String.format("%-14s", "Build Mode: ");
        MutableComponent bmStatus = Component.literal((buildModeEnabled ? "ENABLED" : "DISABLED")).withStyle(s -> s.withColor(buildModeEnabled ? 65280 : 0xFF0000).withBold(true));
        Component bmEnableBtn = commands.makeToggleButton(buildModeEnabled, true, "/pb buildmode on");
        Component bmDisableBtn = commands.makeToggleButton(buildModeEnabled, false, "/pb buildmode off");
        MutableComponent divider = Component.literal(("\u00a7b" + "-".repeat(48)));
        MutableComponent header = Component.literal("\u00a76- PaeroBuilder Config -");
        source.sendSuccess(() -> Theme.wrap(player, header), false);
        MutableComponent previewLine = Component.literal(("\u00a7e" + previewLabel)).append(previewStatus).append(Component.literal(" \u00a7f| ")).append(previewEnableBtn).append(Component.literal(" ")).append(previewDisableBtn);
        source.sendSuccess(() -> Theme.wrap(player, previewLine), false);
        MutableComponent previewTimeText = Component.literal(("\u00a7e" + previewTimeLabel));
        for (int i = 0; i <= 10; ++i) {
            int index = i;
            boolean isSelected = index == currentPreviewTime;
            ChatFormatting color = isSelected ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            MutableComponent timeButton = isSelected ? Component.literal(("[" + index + "]")).withStyle(color, ChatFormatting.BOLD).withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pb config previewtime " + index))) : Component.literal(("[" + index + "]")).withStyle(color).withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pb config previewtime " + index)));
            previewTimeText.append(Component.literal(" ")).append(timeButton);
        }
        source.sendSuccess(() -> Theme.wrap(player, previewTimeText), false);
        MutableComponent customInputButton = Component.literal(("\u00a7e" + String.format("%-14s", "Custom Preview Time (0-60 sec): "))).append(Component.literal("[Input]").withStyle(ChatFormatting.LIGHT_PURPLE).withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pb config previewtime input")))).append(Component.literal((" \u00a77Current: \u00a7a" + currentPreviewTime + "s")));
        source.sendSuccess(() -> Theme.wrap(player, customInputButton), false);
        MutableComponent buildModeLine = Component.literal(("\u00a7e" + bmLabel)).append(bmStatus).append(Component.literal(" \u00a7f| ")).append(bmEnableBtn).append(Component.literal(" ")).append(bmDisableBtn);
        source.sendSuccess(() -> Theme.wrap(player, buildModeLine), false);
        MutableComponent finishLine = Theme.lit(player, "\u00a7e" + String.format("%-14s", "Finish Text: ")).append(FinishDisplay.buildTitle(player.getUUID())).append(Theme.wrap(player, Component.literal(" ").append(Component.literal("[Text]").withStyle(ChatFormatting.LIGHT_PURPLE).withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/pb finish text ")))).append(Component.literal(" ")).append(Component.literal("[Color]").withStyle(ChatFormatting.LIGHT_PURPLE).withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/pb finish color "))))));
        source.sendSuccess(() -> finishLine, false);
        int finishTime = SpeedBuilderConfig.getFinishTitleTime(player.getUUID());
        MutableComponent finishTimeLine = Component.literal("\u00a7e" + String.format("%-14s", "Finish Title Time: ")).append(Component.literal(finishTime + "s").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)).append(Component.literal(" ")).append(Component.literal("[Set]").withStyle(ChatFormatting.LIGHT_PURPLE).withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/pb config finishtime "))));
        source.sendSuccess(() -> Theme.wrap(player, finishTimeLine), false);
        boolean paero = SpeedBuilderConfig.isPaero(player.getUUID());
        MutableComponent difficultyLine = Component.literal("\u00a7e" + String.format("%-14s", "Difficulty: "))
            .append(Component.literal("[ Default ]").withStyle(!paero ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN).withStyle(s -> paero ? s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pb config difficulty default")) : s))
            .append(Component.literal(" "))
            .append(Component.literal("[ Paero ]").withStyle(paero ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN).withStyle(s -> !paero ? s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pb config difficulty paero")) : s));
        source.sendSuccess(() -> Theme.wrap(player, difficultyLine), false);
        String theme = SpeedBuilderConfig.getTheme(player.getUUID());
        MutableComponent themeLine = Component.literal("\u00a7e" + String.format("%-14s", "UI Theme: "));
        String[] themeIds = new String[]{Theme.DEFAULT, Theme.DARK, Theme.PAERO};
        for (int t = 0; t < themeIds.length; ++t) {
            final String id = themeIds[t];
            boolean selected = id.equals(theme);
            MutableComponent button = Component.literal("[ " + Theme.displayName(id) + " ]").withStyle(selected ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN);
            if (!selected) {
                button = button.withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pb config theme " + id)));
            }
            themeLine.append(button);
            if (t < themeIds.length - 1) {
                themeLine.append(Component.literal(" "));
            }
        }
        source.sendSuccess(() -> Theme.wrap(player, themeLine), false);
        source.sendSuccess(() -> Theme.wrap(player, divider), false);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((((((((((((((((Commands.literal("pb").then(Commands.literal("help").executes(ctx -> {
            CommandSourceStack source = ctx.getSource();
            MutableComponent header = Component.literal("\u00a76\u00a7l\u2550\u2550\u2550\u2550\u2550\u2550\u2550 PaeroBuilder Commands \u2550\u2550\u2550\u2550\u2550\u2550\u2550");
            MutableComponent divider = Component.literal(("\u00a7b" + "\u2500".repeat(40)));
            Component[] helpLines = new Component[]{Component.literal("\u00a7e/pb config \u00a77- Shows configuration menu with interactive buttons"), Component.literal("\u00a7e/pb <build> \u00a77- Loads a build with preview for speedbuilding practice"), Component.literal("\u00a7e/pb grind <build> \u00a77- Enables grind mode (auto-replenish, no preview)"), Component.literal("\u00a7e/pb view <build> \u00a77- Places complete build for learning purposes"), Component.literal(""), Component.literal("\u00a7e/pb create <name> \u00a77- Create custom build from build area"), Component.literal("\u00a7e/pb list \u00a77- List all custom builds"), Component.literal("\u00a7e/pb delete <name> \u00a77- Delete a custom build"), Component.literal(""), Component.literal("\u00a7e/pb buildmode <on/off> \u00a77- Toggles build mode manually"), Component.literal("\u00a7e/pb time <build> \u00a77- Shows your best recorded time"), Component.literal("\u00a7e/pb time reset <build> \u00a77- Resets your recorded time"), Component.literal(""), Component.literal("\u00a7e/pb stop \u00a77- Stops all sessions and clears area/inventory"), Component.literal("\u00a7e/pb reset \u00a77- Clears build area \u00a78(Default: Grave Key)"), Component.literal("\u00a7e/pb restart \u00a77- Resets area and starts countdown \u00a78(Default: F6)"), Component.literal("\u00a7e/pb setup \u00a77- Builds a 15x15 build platform under your feet (7 blocks of air above)")};
            source.sendSuccess(() -> Theme.wrap(source, header), false);
            source.sendSuccess(() -> Theme.wrap(source, divider), false);
            for (Component line : helpLines) {
                source.sendSuccess(() -> Theme.wrap(source, line), false);
            }
            source.sendSuccess(() -> Theme.wrap(source, divider), false);
            return 1;
        }))).then(Commands.literal("setup").executes(ctx -> worldSetup.setupWorld(ctx.getSource())))).then(Commands.literal("replacenear").then(Commands.argument("radius", IntegerArgumentType.integer(1, 200)).then(Commands.argument("fromBlock", StringArgumentType.word()).then(Commands.argument("toBlock", StringArgumentType.word()).executes(ctx -> {
            ServerPlayer player;
            try {
                player = (ctx.getSource()).getPlayer();
            }
            catch (Exception e) {
                (ctx.getSource()).sendFailure(Theme.lit(ctx.getSource(), "Must be run by a player"));
                return 0;
            }
            int radius = IntegerArgumentType.getInteger(ctx, "radius");
            String fromBlock = StringArgumentType.getString(ctx, "fromBlock");
            String toBlock = StringArgumentType.getString(ctx, "toBlock");
            return worldSetup.replaceNearCommand(ctx.getSource(), player.blockPosition(), radius, fromBlock, toBlock);
        })))))).then(Commands.literal("stop").executes(ctx -> {
            ServerPlayer player = null;
            try {
                player = (ctx.getSource()).getPlayer();
            }
            catch (Exception exception) {
                // empty catch block
            }
            if (player == null) {
                (ctx.getSource()).sendFailure(Theme.lit(ctx.getSource(), "You must be a player to use this command!"));
                return 0;
            }
            buildLoader.cancelPlayerTasks(player);
            ServerLevel world = (ctx.getSource()).getLevel();
            BlockVector3 min = BlockVector3.at(-10, 0, -10);
            BlockVector3 max = BlockVector3.at(10, 50, 10);
            BlockVector3 origin = BlockVector3.at(-3, 0, -3);
            buildLoader.clearDroppedItems(world, min, max, origin);
            spbAccuracy.ACTIVE_SESSIONS.remove(player.getUUID());
            if (buildMode.isInBuildMode(player)) {
                buildMode.disable(player);
            }
            buildLoader.clearInventory(player);
            resetBuild.hardReset(ctx.getSource());
            player.displayClientMessage(Theme.lit(player, SpeedBuilderConfig.isPaero(player.getUUID()) ? "\u00a7cAll build sessions have been stopped. \u00a77Build area reset." : "\u00a7cAll build sessions have been stopped. \u00a77Build area reset and inventory cleared."), false);
            return 1;
        }))).then(Commands.literal("restart").executes(ctx -> {
            ServerPlayer player;
            try {
                player = (ctx.getSource()).getPlayer();
            }
            catch (Exception e) {
                (ctx.getSource()).sendFailure(Theme.lit(ctx.getSource(), "You must be a player to use this command!"));
                return 0;
            }
            resetBuild.hardReset(ctx.getSource());
            ServerLevel world = (ctx.getSource()).getLevel();
            BlockVector3 min = BlockVector3.at(-10, 0, -10);
            BlockVector3 max = BlockVector3.at(10, 50, 10);
            BlockVector3 origin = BlockVector3.at(-3, 0, -3);
            buildLoader.clearDroppedItems(world, min, max, origin);
            spbAccuracy.BuildSession session = spbAccuracy.ACTIVE_SESSIONS.get(player.getUUID());
            if (session == null) {
                player.displayClientMessage(Theme.lit(player, "\u00a7cNo active build session to restart!"), false);
                return 0;
            }
            player.displayClientMessage(Theme.lit(player, "\u00a7eRestarting..."), false);
            buildLoader.cancelPlayerTasks(player);
            buildLoader.clearBuildArea(player.serverLevel(), session.min, session.max, session.worldOrigin);
            buildLoader.clearInventory(player);
            MinecraftServer server = world.getServer();
            buildLoader.schedule(server, 20, () -> {
                player.connection.send(new ClientboundSetTitleTextPacket(Theme.countdownTitle(player, 3)));
                Sfx.countdownTick(player, false);
            }, player.getUUID().toString());
            buildLoader.schedule(server, 40, () -> {
                player.connection.send(new ClientboundSetTitleTextPacket(Theme.countdownTitle(player, 2)));
                Sfx.countdownTick(player, false);
            }, player.getUUID().toString());
            buildLoader.schedule(server, 60, () -> {
                player.connection.send(new ClientboundSetTitleTextPacket(Theme.countdownTitle(player, 1)));
                Sfx.countdownTick(player, true);
            }, player.getUUID().toString());
            buildLoader.schedule(server, 80, () -> {
                player.connection.send(new ClientboundSetTitleTextPacket(Theme.buildTitle(player)));
                Sfx.buildStart(player);
                buildLoader.giveBlocks(player, session.buildName);
                long now = System.currentTimeMillis();
                spbAccuracy.ACTIVE_SESSIONS.put(player.getUUID(), new spbAccuracy.BuildSession(session.clipboard, session.min, session.max, session.worldOrigin, now, session.buildName, session.isGrindSession));
                player.displayClientMessage(Theme.lit(player, "\u00a77blocks replenished"), false);
            }, player.getUUID().toString());
            return 1;
        }))).then((Commands.literal("grind").then(Commands.argument("build", StringArgumentType.word()).executes(ctx -> {
            ServerPlayer player = null;
            try {
                player = (ctx.getSource()).getPlayer();
            }
            catch (Exception exception) {
                // empty catch block
            }
            if (player == null) {
                (ctx.getSource()).sendFailure(Theme.lit(ctx.getSource(), "You must be a player to use this command!"));
                return 0;
            }
            String buildName = StringArgumentType.getString(ctx, "build");
            if (!commands.isValidBuildName(buildName)) {
                player.displayClientMessage(Theme.lit(player, ("\u00a7c'" + buildName + "' is not a valid build name!")), false);
                return 0;
            }
            grindMode.enable(player, buildName);
            return 1;
        }))).then(Commands.literal("stop").executes(ctx -> {
            ServerPlayer player = null;
            try {
                player = (ctx.getSource()).getPlayer();
            }
            catch (Exception exception) {
                // empty catch block
            }
            if (player == null) {
                (ctx.getSource()).sendFailure(Theme.lit(ctx.getSource(), "You must be a player to use this command!"));
                return 0;
            }
            grindMode.disable(player);
            if (buildMode.isInBuildMode(player)) {
                buildMode.disable(player);
            }
            resetBuild.hardReset(ctx.getSource());
            ServerLevel world = (ctx.getSource()).getLevel();
            BlockVector3 min = BlockVector3.at(-10, 0, -10);
            BlockVector3 max = BlockVector3.at(10, 50, 10);
            BlockVector3 origin = BlockVector3.at(-3, 0, -3);
            buildLoader.clearDroppedItems(world, min, max, origin);
            spbAccuracy.ACTIVE_SESSIONS.remove(player.getUUID());
            player.displayClientMessage(Theme.lit(player, "\u00a7cGrind Mode stopped."), false);
            return 1;
        })))).then((Commands.literal("buildmode").then(Commands.literal("on").executes(ctx -> {
            ServerPlayer player = null;
            try {
                player = (ctx.getSource()).getPlayer();
            }
            catch (Exception exception) {
                // empty catch block
            }
            if (player == null) {
                (ctx.getSource()).sendFailure(Theme.lit(ctx.getSource(), "You must be a player to use this command!"));
                return 0;
            }
            buildMode.enable(player);
            player.displayClientMessage(Theme.lit(player, "\u00a7aBuild Mode enabled!"), false);
            commands.showConfig(ctx.getSource(), player);
            return 1;
        }))).then(Commands.literal("off").executes(ctx -> {
            ServerPlayer player = null;
            try {
                player = (ctx.getSource()).getPlayer();
            }
            catch (Exception exception) {
                // empty catch block
            }
            if (player == null) {
                (ctx.getSource()).sendFailure(Theme.lit(ctx.getSource(), "You must be a player to use this command!"));
                return 0;
            }
            buildMode.disable(player);
            player.displayClientMessage(Theme.lit(player, "\u00a7cBuild Mode disabled!"), false);
            commands.showConfig(ctx.getSource(), player);
            return 1;
        })))).then(Commands.literal("view").then(Commands.argument("build", StringArgumentType.word()).executes(ctx -> {
            String buildName = StringArgumentType.getString(ctx, "build");
            if (!commands.isValidBuildName(buildName)) {
                (ctx.getSource()).sendFailure(Theme.lit(ctx.getSource(), ("\u00a7c'" + buildName + "' is not a valid build name!")));
                return 0;
            }
            int result = buildLoader.placeSchematic(ctx.getSource(), buildName);
            if (result == 1) {
                try {
                    ServerPlayer player = (ctx.getSource()).getPlayer();
                    player.displayClientMessage(Theme.lit(player, "\u00a7aViewing build. Clear area with \u00a7e/pb reset \u00a7aor with your keybind"), false);
                }
                catch (Exception e) {
                    (ctx.getSource()).sendSuccess(() -> Theme.lit(ctx.getSource(), "Viewing build. Clear area with \u00a7e/pb reset \u00a7aor with your keybind"), false);
                }
            }
            return result;
        })))).then((Commands.literal("time").then(Commands.literal("reset").then(Commands.argument("build", StringArgumentType.word()).executes(ctx -> {
            ServerPlayer player;
            try {
                player = (ctx.getSource()).getPlayer();
            }
            catch (Exception e) {
                (ctx.getSource()).sendFailure(Theme.lit(ctx.getSource(), "You must be a player to use this command!"));
                return 0;
            }
            String buildName = StringArgumentType.getString(ctx, "build");
            if (!commands.isValidBuildName(buildName)) {
                player.displayClientMessage(Theme.lit(player, ("\u00a7c'" + buildName + "' is not a valid build name!")), false);
                return 0;
            }
            Long previousTime = SpeedBuilderConfig.getBestTime(player.getUUID(), buildName);
            if (previousTime == null) {
                player.displayClientMessage(Theme.lit(player, ("\u00a7eNo recorded time found for: \u00a7b" + buildName)), false);
                return 0;
            }
            SpeedBuilderConfig.resetBestTime(player.getUUID(), buildName);
            player.displayClientMessage(Theme.lit(player, ("\u00a7aReset best time for \u00a7b" + buildName + " \u00a7a(was: \u00a7e" + commands.timeFormat(previousTime) + "\u00a7a)")), false);
            return 1;
        })))).then(Commands.argument("build", StringArgumentType.word()).executes(ctx -> {
            ServerPlayer player = null;
            try {
                player = (ctx.getSource()).getPlayer();
            }
            catch (Exception exception) {
                // empty catch block
            }
            if (player == null) {
                (ctx.getSource()).sendFailure(Theme.lit(ctx.getSource(), "You must be a player to use this command!"));
                return 0;
            }
            String build = StringArgumentType.getString(ctx, "build");
            if (!commands.isValidBuildName(build)) {
                player.displayClientMessage(Theme.lit(player, ("\u00a7c'" + build + "' is not a valid build name!")), false);
                return 0;
            }
            Long best = SpeedBuilderConfig.getBestTime(player.getUUID(), build);
            if (best == null) {
                player.displayClientMessage(Theme.lit(player, ("\u00a7eYou have no recorded time for: \u00a7b" + build)), false);
            } else {
                player.displayClientMessage(Theme.lit(player, ("\u00a7eYour best time for \u00a7b" + build + " \u00a7eis: \u00a7a" + commands.timeFormat(best))), false);
            }
            return 1;
        })))).then(Commands.literal("reset").executes(ctx -> {
            int result = resetBuild.hardReset(ctx.getSource());
            ServerLevel world = (ctx.getSource()).getLevel();
            BlockVector3 min = BlockVector3.at(-10, 0, -10);
            BlockVector3 max = BlockVector3.at(10, 50, 10);
            BlockVector3 origin = BlockVector3.at(-3, 0, -3);
            buildLoader.clearDroppedItems(world, min, max, origin);
            return result;
        }))).then(((Commands.literal("config").executes(ctx -> {
            ServerPlayer player;
            CommandSourceStack source = ctx.getSource();
            try {
                player = source.getPlayer();
            }
            catch (Exception e) {
                source.sendFailure(Theme.lit(source, "You must be a player to use this command!"));
                return 0;
            }
            commands.showConfig(source, player);
            return 1;
        })).then((Commands.literal("preview").then(Commands.literal("on").executes(ctx -> {
            ServerPlayer player = (ctx.getSource()).getPlayer();
            SpeedBuilderConfig.setPreview(player.getUUID(), true);
            commands.showConfig(ctx.getSource(), player);
            return 1;
        }))).then(Commands.literal("off").executes(ctx -> {
            ServerPlayer player = (ctx.getSource()).getPlayer();
            SpeedBuilderConfig.setPreview(player.getUUID(), false);
            commands.showConfig(ctx.getSource(), player);
            return 1;
        })))).then((Commands.literal("previewtime").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 60)).executes(ctx -> {
            ServerPlayer player = (ctx.getSource()).getPlayer();
            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
            SpeedBuilderConfig.setPreviewTime(player.getUUID(), seconds);
            player.displayClientMessage(Theme.lit(player, ("\u00a7aPreview time set to " + seconds + " seconds")), false);
            commands.showConfig(ctx.getSource(), player);
            return 1;
        }))).then(Commands.literal("input").executes(ctx -> {
            ServerPlayer player = (ctx.getSource()).getPlayer();
            SpeedBuilderConfig.setPendingCustomInput(player.getUUID(), true);
            player.displayClientMessage(Theme.lit(player, "\u00a7eInput custom view time in chat in seconds (0-60):"), false);
            return 1;
        }))))).then(Commands.literal("create").then(Commands.argument("buildName", StringArgumentType.word()).executes(ctx -> {
            String buildName = StringArgumentType.getString(ctx, "buildName");
            return customBuilds.createCustomBuild(ctx.getSource(), buildName);
        }))))).then(Commands.literal("delete").then(Commands.argument("buildName", StringArgumentType.word()).executes(ctx -> {
            String buildName = StringArgumentType.getString(ctx, "buildName");
            return customBuilds.deleteCustomBuild(ctx.getSource(), buildName);
        })))).then(Commands.literal("list").executes(ctx -> customBuilds.listCustomBuilds(ctx.getSource())))).then(Commands.argument("build", StringArgumentType.word()).executes(ctx -> {
            String buildName = StringArgumentType.getString(ctx, "build");
            if (!commands.isValidBuildName(buildName)) {
                (ctx.getSource()).sendFailure(Theme.lit(ctx.getSource(), ("\u00a7c'" + buildName + "' is not a valid build name!")));
                return 0;
            }
            try {
                ServerPlayer player = (ctx.getSource()).getPlayer();
                buildLoader.cancelPlayerTasks(player);
                spbAccuracy.ACTIVE_SESSIONS.remove(player.getUUID());
                if (!SpeedBuilderConfig.isPreviewEnabled(player.getUUID())) {
                    player.displayClientMessage(Theme.lit(player, ("\u00a7bBuild selected: " + buildName + " \u00a77(Preview disabled)")), true);
                    buildMode.enable(player);
                    buildLoader.clearInventory(player);
                    buildLoader.giveBlocks(player, buildName);
                    try (InputStream stream = commands.getBuildStream(buildName);){
                        ClipboardFormat format;
                        if (stream != null && (format = ClipboardFormats.findByAlias("schem")) != null) {
                            ClipboardReader reader = format.getReader(stream);
                            Clipboard clipboard = reader.read();
                            BlockVector3 min = clipboard.getMinimumPoint();
                            BlockVector3 max = clipboard.getMaximumPoint();
                            BlockVector3 worldOrigin = BlockVector3.at(-3, 0, -3);
                            long now = System.currentTimeMillis();
                            spbAccuracy.ACTIVE_SESSIONS.put(player.getUUID(), new spbAccuracy.BuildSession(clipboard, min, max, worldOrigin, now, buildName, false));
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    player.displayClientMessage(Theme.lit(player, "\u00a7aReady to build! Preview was skipped."), false);
                    Sfx.buildStart(player);
                    return 1;
                }
                player.displayClientMessage(Theme.lit(player, ("\u00a7bBuild selected: " + buildName)), true);
                buildMode.enable(player);
                if (customBuilds.isCustomBuild(buildName)) {
                    player.displayClientMessage(Theme.lit(player, "\u00a7d[Custom Build]"), false);
                }
            }
            catch (Exception e) {
                (ctx.getSource()).sendFailure(Theme.lit(ctx.getSource(), ("\u00a7cFailed to select build: " + buildName)));
                return 0;
            }
            return buildLoader.previewSchematic(ctx.getSource(), buildName);
        })));
    }
}
