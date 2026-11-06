package com.paero.speedbuilder;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;

/**
 * UI themes for chat messages and the countdown / BUILD! titles.
 *
 * default - the normal colours (countdown numbers each get their own colour)
 * dark    - same as default, but yellow text becomes light gray
 * paero   - everything is white-to-gray, countdown fades gray -> white (5 darkest, 1 lightest), BUILD! is a white-to-gray gradient
 *
 * The finish title / finish time / stopwatch colours are user-chosen and are never touched by a theme.
 */
public class Theme {
    public static final String DEFAULT = "default";
    public static final String DARK = "dark";
    public static final String PAERO = "paero";

    private static final int YELLOW = 0xFFFF55;
    private static final int DARK_THEME_REPLACEMENT = 0xAAAAAA;

    // ---------------------------------------------------------------- lookup

    public static String themeOf(UUID uuid) {
        return uuid == null ? DEFAULT : SpeedBuilderConfig.getTheme(uuid);
    }

    public static String themeOf(Player player) {
        return player == null ? DEFAULT : SpeedBuilderConfig.getTheme(player.getUUID());
    }

    public static String themeOf(CommandSourceStack source) {
        return source != null && source.getEntity() instanceof Player p ? themeOf(p) : DEFAULT;
    }

    public static String next(String theme) {
        if (DEFAULT.equals(theme)) return DARK;
        if (DARK.equals(theme)) return PAERO;
        return DEFAULT;
    }

    public static String displayName(String theme) {
        if (DARK.equals(theme)) return "Dark";
        if (PAERO.equals(theme)) return "Paero";
        return "Default";
    }

    // ---------------------------------------------------------------- chat text

    /** A chat component from a string that may contain legacy section-sign colour codes, recoloured for the player's theme. */
    public static MutableComponent lit(Player player, String text) {
        return wrap(themeOf(player), Component.literal(text));
    }

    public static MutableComponent lit(CommandSourceStack source, String text) {
        return wrap(themeOf(source), Component.literal(text));
    }

    public static MutableComponent wrap(Player player, Component component) {
        return wrap(themeOf(player), component);
    }

    public static MutableComponent wrap(CommandSourceStack source, Component component) {
        return wrap(themeOf(source), component);
    }

    public static MutableComponent wrap(UUID uuid, Component component) {
        return wrap(themeOf(uuid), component);
    }

    private static MutableComponent wrap(String theme, Component component) {
        if (DEFAULT.equals(theme)) {
            return component.copy();
        }
        MutableComponent out = Component.empty();
        component.<Boolean>visit((style, text) -> {
            appendLegacy(out, theme, style, text);
            return Optional.<Boolean>empty();
        }, Style.EMPTY);
        return out;
    }

    private static void appendLegacy(MutableComponent out, String theme, Style base, String text) {
        Style current = base;
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                ChatFormatting format = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(i + 1)));
                if (format != null) {
                    flush(out, theme, current, buffer);
                    if (format == ChatFormatting.RESET) {
                        current = base;
                    } else if (format.isColor()) {
                        current = current.withColor(format).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
                    } else {
                        current = current.applyFormat(format);
                    }
                    ++i;
                    continue;
                }
            }
            buffer.append(c);
        }
        flush(out, theme, current, buffer);
    }

    private static void flush(MutableComponent out, String theme, Style style, StringBuilder buffer) {
        if (buffer.length() == 0) {
            return;
        }
        out.append(Component.literal(buffer.toString()).setStyle(mapStyle(theme, style)));
        buffer.setLength(0);
    }

    private static Style mapStyle(String theme, Style style) {
        TextColor color = style.getColor();
        if (color == null) {
            return PAERO.equals(theme) ? style.withColor(TextColor.fromRgb(0xFFFFFF)) : style;
        }
        return style.withColor(TextColor.fromRgb(mapColor(theme, color.getValue())));
    }

    /** Remaps one RGB colour for a theme. */
    public static int mapColor(String theme, int rgb) {
        rgb &= 0xFFFFFF;
        if (DARK.equals(theme)) {
            return rgb == YELLOW ? DARK_THEME_REPLACEMENT : rgb;
        }
        if (PAERO.equals(theme)) {
            // keep brighter colours bright and darker colours darker, but squeeze everything into white..gray
            int r = rgb >> 16 & 0xFF;
            int g = rgb >> 8 & 0xFF;
            int b = rgb & 0xFF;
            double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
            int gray = (int) Math.round(120.0 + luminance * 0.53);
            gray = Math.max(0, Math.min(255, gray));
            return gray << 16 | gray << 8 | gray;
        }
        return rgb;
    }

    // ---------------------------------------------------------------- countdown / titles

    private static final String[] COUNTDOWN_CODES = {"", "\u00a7c", "\u00a76", "\u00a7e", "\u00a7b", "\u00a7a"};
    private static final int[] PAERO_COUNTDOWN = {0, 0xFFFFFF, 0xE6E6E6, 0xCFCFCF, 0xB0B0B0, 0x8C8C8C};

    /** Legacy colour code used for the number n (1-5) in the countdown: 5 green, 4 aqua, 3 yellow, 2 orange, 1 red. */
    public static String countdownCode(int n) {
        return COUNTDOWN_CODES[Math.max(1, Math.min(5, n))];
    }

    /** The big number shown in the middle of the screen: 5, 4, 3, 2, 1. */
    public static Component countdownTitle(Player player, int n) {
        String theme = themeOf(player);
        if (PAERO.equals(theme)) {
            int idx = Math.max(1, Math.min(5, n));
            return Component.literal(String.valueOf(n)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(PAERO_COUNTDOWN[idx])));
        }
        return wrap(theme, Component.literal(countdownCode(n) + n));
    }

    /** The "BUILD!" title: green in Default/Dark, a white-to-gray gradient in Paero. */
    public static Component buildTitle(Player player) {
        String text = "BUILD!";
        if (!PAERO.equals(themeOf(player))) {
            return Component.literal("\u00a7a\u00a7l" + text);
        }
        MutableComponent out = Component.empty();
        int from = 0xFFFFFF;
        int to = 0x8C8C8C;
        for (int i = 0; i < text.length(); ++i) {
            double t = text.length() == 1 ? 0.0 : (double) i / (double) (text.length() - 1);
            int gray = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
            int rgb = gray << 16 | gray << 8 | gray;
            out.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true)));
        }
        return out;
    }
}
