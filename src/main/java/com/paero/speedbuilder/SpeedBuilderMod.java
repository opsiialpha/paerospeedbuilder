package com.paero.speedbuilder;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.slf4j.Logger;

@Mod(SpeedBuilderMod.MODID)
public class SpeedBuilderMod {
    public static final String MODID = "paerospeedbuilder";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpeedBuilderMod(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(SpeedBuilderMod::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(SpeedBuilderMod::onServerChat);
        buildMode.registerFluidEvents();
        buildMode.registerEvents();
        buildLoader.registerScheduler();
        SpeedBuilderConfig.loadConfig();
        buildLoader.registerAccuracyPoller();
        LOGGER.info("PaeroBuilder initialized successfully!");
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        commands.register(dispatcher);
        FinishCommand.register(dispatcher);
        SettingsCommand.register(dispatcher);
        worldSetup.registerCommands(dispatcher);
    }

    private static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!SpeedBuilderConfig.isPendingCustomInput(player.getUUID())) {
            return;
        }
        try {
            int seconds = Integer.parseInt(event.getRawText());
            if (seconds >= 0 && seconds <= 60) {
                SpeedBuilderConfig.setPreviewTime(player.getUUID(), seconds);
                SpeedBuilderConfig.setPendingCustomInput(player.getUUID(), false);
                player.displayClientMessage(Theme.lit(player, "\u00a7aPreview time set to " + seconds + " seconds!"), false);
                commands.showConfig(player.createCommandSourceStack(), player);
            } else {
                player.displayClientMessage(Theme.lit(player, "\u00a7cInvalid time! Enter a number between 0 and 60."), false);
            }
        } catch (NumberFormatException e) {
            player.displayClientMessage(Theme.lit(player, "\u00a7cInvalid number! Enter a number between 0 and 60."), false);
        }
    }
}
