package com.paero.speedbuilder;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/** Finish screen (custom title + time subtitle) and stopwatch formatting. */
public class FinishDisplay {
    public static final int MAX_TEXT_LENGTH = 64;

    /** 12.34s, or 1:05.34 once past a minute. */
    public static String formatTime(long ms) {
        long totalSec = ms / 1000L;
        long hund = (ms % 1000L) / 10L;
        if (totalSec >= 60L) {
            return (totalSec / 60L) + ":" + String.format("%02d", totalSec % 60L) + "." + String.format("%02d", hund);
        }
        return totalSec + "." + String.format("%02d", hund) + "s";
    }

    /** "&a" style codes -> section-sign codes, so any text can be formatted. */
    public static String colorize(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(text.charAt(i + 1)) >= 0) {
                sb.append('\u00a7');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Accepts #RRGGBB (or RRGGBB) or a Minecraft colour name like gold, dark_red, light_purple. Null if invalid. */
    public static TextColor parseColor(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase();
        String hex = s.startsWith("#") ? s.substring(1) : s;
        if (hex.length() == 6 && hex.matches("[0-9a-f]{6}")) {
            return TextColor.fromRgb(Integer.parseInt(hex, 16));
        }
        ChatFormatting fmt = ChatFormatting.getByName(s);
        if (fmt != null && fmt.isColor()) {
            return TextColor.fromLegacyFormat(fmt);
        }
        return null;
    }

    private static TextColor colorOr(String value, int fallbackRgb) {
        TextColor color = parseColor(value);
        return color != null ? color : TextColor.fromRgb(fallbackRgb);
    }

    /** Stopwatch text shown above the hotbar, in the player's chosen colour (not bold). */
    public static MutableComponent timerComponent(UUID uuid, String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(colorOr(SpeedBuilderConfig.getTimerColor(uuid), 0xFFFF55)));
    }

    /** The time shown under the finish title, in the player's chosen colour. */
    public static MutableComponent finishTimeComponent(UUID uuid, String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(colorOr(SpeedBuilderConfig.getFinishTimeColor(uuid), 0xFFFFFF)));
    }

    public static MutableComponent buildTitle(UUID uuid) {
        TextColor color = parseColor(SpeedBuilderConfig.getFinishColor(uuid));
        if (color == null) color = TextColor.fromLegacyFormat(ChatFormatting.GREEN);
        String text = colorize(SpeedBuilderConfig.getFinishTitle(uuid));
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withBold(true));
    }

    /** Big custom title in the middle of the screen, final time as the subtitle under it. */
    public static void show(ServerPlayer player, long elapsedMs) {
        Component title = buildTitle(player.getUUID());
        Component subtitle = finishTimeComponent(player.getUUID(), formatTime(elapsedMs));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, SpeedBuilderConfig.getFinishTitleTime(player.getUUID()) * 20, 15));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }
}
