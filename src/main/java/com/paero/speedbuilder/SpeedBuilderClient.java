package com.paero.speedbuilder;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.UUID;
import com.paero.speedbuilder.client.SpeedBuilderConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

// Client-only half of the mod (keybinds + sign-editor suppression).
@Mod(value = SpeedBuilderMod.MODID, dist = Dist.CLIENT)
public class SpeedBuilderClient {
    private static KeyMapping resetKeybind;
    private static KeyMapping restartKeybind;
    private static KeyMapping configKeybind;

    public SpeedBuilderClient(IEventBus modEventBus, ModContainer container) {
        // Adds the "Config" button for this mod in the Mods list screen.
        container.registerExtensionPoint(IConfigScreenFactory.class, (modContainer, modListScreen) -> new SpeedBuilderConfigScreen(modListScreen));
        modEventBus.addListener(SpeedBuilderClient::onRegisterKeys);
        NeoForge.EVENT_BUS.addListener(SpeedBuilderClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(SpeedBuilderClient::onScreenInit);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        resetKeybind = new KeyMapping("key.paerospeedbuilder.reset", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, "key.categories.paerospeedbuilder");
        restartKeybind = new KeyMapping("key.paerospeedbuilder.restart", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "key.categories.paerospeedbuilder");
        event.register(resetKeybind);
        configKeybind = new KeyMapping("key.paerospeedbuilder.config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.paerospeedbuilder");
        event.register(restartKeybind);
        event.register(configKeybind);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (resetKeybind != null && resetKeybind.consumeClick() && mc.player != null) {
            mc.player.connection.sendCommand("sb reset");
        }
        if (restartKeybind != null && restartKeybind.consumeClick() && mc.player != null) {
            mc.player.connection.sendCommand("sb restart");
        }
        if (configKeybind != null && configKeybind.consumeClick() && mc.screen == null) {
            mc.setScreen(new SpeedBuilderConfigScreen(null));
        }
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getScreen() instanceof AbstractSignEditScreen && mc.player != null) {
            try {
                UUID playerUuid = mc.player.getUUID();
                if (SpeedBuilderConfig.isSignSuppressionEnabled(playerUuid)) {
                    mc.execute(() -> mc.execute(() -> {
                        if (mc.screen instanceof AbstractSignEditScreen) {
                            mc.setScreen(null);
                        }
                    }));
                }
            } catch (Exception ignored) {
            }
        }
    }
}
