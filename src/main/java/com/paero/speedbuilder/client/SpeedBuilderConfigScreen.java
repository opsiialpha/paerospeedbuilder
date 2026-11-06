package com.paero.speedbuilder.client;

import com.paero.speedbuilder.FinishDisplay;
import com.paero.speedbuilder.Sfx;
import com.paero.speedbuilder.SpeedBuilderConfig;
import com.paero.speedbuilder.Theme;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/** In-game config screen (Mods list -> PaeroBuilder -> Config). Options are laid out in two columns. */
public class SpeedBuilderConfigScreen extends Screen {
    private static final int COL_W = 150;
    private static final int GAP = 10;
    private static final int ROW_H = 24;

    private final Screen parent;
    private UUID uuid;
    private Button previewButton;
    private Button soundButton;
    private Button signButton;
    private Button timerColorButton;
    private Button finishColorButton;
    private Button finishTimeColorButton;
    private Button difficultyButton;
    private Button themeButton;
    private EditBox finishTextBox;

    public SpeedBuilderConfigScreen(Screen parent) {
        super(Component.literal("PaeroBuilder Config"));
        this.parent = parent;
    }

    private static UUID resolveUuid() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            return mc.player.getUUID();
        }
        return mc.getUser().getProfileId();
    }

    private SpeedBuilderConfig.PlayerSettings settings() {
        return SpeedBuilderConfig.getPlayerSettings(uuid);
    }

    private static Component onOff(String label, boolean on) {
        return Component.literal(label + ": ").append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @Override
    protected void init() {
        this.uuid = resolveUuid();
        int left = this.width / 2 - COL_W - GAP / 2;
        int right = this.width / 2 + GAP / 2;
        int y = 34;

        // Row 1: Build Preview | Preview Time
        previewButton = Button.builder(onOff("Build Preview", settings().previewEnabled), b -> {
            settings().previewEnabled = !settings().previewEnabled;
            SpeedBuilderConfig.saveConfig();
            b.setMessage(onOff("Build Preview", settings().previewEnabled));
        }).bounds(left, y, COL_W, 20).build();
        this.addRenderableWidget(previewButton);
        this.addRenderableWidget(new PreviewTimeSlider(right, y, COL_W, 20, SpeedBuilderConfig.getPreviewTime(uuid)));
        y += ROW_H;

        // Row 2: Sound Track | Sign Edit Suppression
        soundButton = Button.builder(soundLabel(), b -> {
            int next = SpeedBuilderConfig.getSoundTrack(uuid) == 1 ? 2 : 1;
            SpeedBuilderConfig.setSoundTrack(uuid, next);
            b.setMessage(soundLabel());
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(Sfx.finishSound(next), 1.0f, 1.0f));
        }).bounds(left, y, COL_W, 20)
            .tooltip(Tooltip.create(Component.literal("Track 1: long challenge-complete fanfare on finish.\nTrack 2: the shorter build-start tune also plays on finish.\nClick to switch and hear the finish sound.")))
            .build();
        this.addRenderableWidget(soundButton);
        signButton = Button.builder(onOff("Sign Edit Block", settings().signTextSuppression), b -> {
            settings().signTextSuppression = !settings().signTextSuppression;
            SpeedBuilderConfig.saveConfig();
            b.setMessage(onOff("Sign Edit Block", settings().signTextSuppression));
        }).bounds(right, y, COL_W, 20)
            .tooltip(Tooltip.create(Component.literal("Closes the sign editor right after it opens, so placing signs doesn't interrupt a build.")))
            .build();
        this.addRenderableWidget(signButton);
        y += ROW_H;

        // Row 3: Stopwatch Color | Finish Title Color
        timerColorButton = colorButton(left, y, "Stopwatch Color", () -> SpeedBuilderConfig.getTimerColor(uuid), c -> SpeedBuilderConfig.setTimerColor(uuid, c));
        finishColorButton = colorButton(right, y, "Finish Title Color", () -> SpeedBuilderConfig.getFinishColor(uuid), c -> SpeedBuilderConfig.setFinishColor(uuid, c));
        y += ROW_H;

        // Row 4: Finish Time Color | Finish Text
        finishTimeColorButton = colorButton(left, y, "Finish Time Color", () -> SpeedBuilderConfig.getFinishTimeColor(uuid), c -> SpeedBuilderConfig.setFinishTimeColor(uuid, c));
        finishTextBox = new EditBox(this.font, right, y, COL_W, 20, Component.literal("Finish Text"));
        finishTextBox.setMaxLength(FinishDisplay.MAX_TEXT_LENGTH);
        finishTextBox.setHint(Component.literal("Finish text (&c codes ok)"));
        finishTextBox.setValue(SpeedBuilderConfig.getFinishTitle(uuid));
        finishTextBox.setResponder(text -> settings().finishTitle = text.isEmpty() ? "Superb!" : text);
        this.addRenderableWidget(finishTextBox);
        y += ROW_H;

        // Row 5: Finish Title Time | Difficulty
        this.addRenderableWidget(new FinishTimeSlider(left, y, COL_W, 20, SpeedBuilderConfig.getFinishTitleTime(uuid)));
        difficultyButton = Button.builder(difficultyLabel(), b -> {
            boolean toPaero = !SpeedBuilderConfig.isPaero(uuid);
            SpeedBuilderConfig.setDifficulty(uuid, toPaero ? SpeedBuilderConfig.DIFFICULTY_PAERO : SpeedBuilderConfig.DIFFICULTY_DEFAULT);
            b.setMessage(difficultyLabel());
        }).bounds(right, y, COL_W, 20)
            .tooltip(Tooltip.create(Component.literal("Default: starting a build clears your inventory and gives you the blocks.\nPaero: starting a build never touches your inventory (nothing cleared, nothing given).")))
            .build();
        this.addRenderableWidget(difficultyButton);
        y += ROW_H;

        // Row 6: UI Theme (Default -> Dark -> Paero)
        themeButton = Button.builder(themeLabel(), b -> {
            SpeedBuilderConfig.setTheme(uuid, Theme.next(SpeedBuilderConfig.getTheme(uuid)));
            b.setMessage(themeLabel());
        }).bounds(this.width / 2 - COL_W / 2, y, COL_W, 20)
            .tooltip(Tooltip.create(Component.literal("Default: normal colours, every countdown number has its own colour.\nDark: yellow chat text becomes light gray.\nPaero: everything in white-to-gray, with a gradient BUILD! title.")))
            .build();
        this.addRenderableWidget(themeButton);

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
            .bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    private Component difficultyLabel() {
        boolean paero = SpeedBuilderConfig.isPaero(uuid);
        return Component.literal("Difficulty: ").append(Component.literal(paero ? "Paero" : "Default").withStyle(paero ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));
    }

    private Component themeLabel() {
        return Component.literal("UI Theme: ").append(Component.literal(Theme.displayName(SpeedBuilderConfig.getTheme(uuid))).withStyle(ChatFormatting.AQUA));
    }

    private Component soundLabel() {
        return Component.literal("Sound Track: ").append(Component.literal("Track " + SpeedBuilderConfig.getSoundTrack(uuid)).withStyle(ChatFormatting.AQUA));
    }

    private Button colorButton(int x, int y, String label, Supplier<String> current, Consumer<String> save) {
        Button button = Button.builder(Component.literal(label), b ->
            this.minecraft.setScreen(new ColorPickerScreen(this, Component.literal(label), current.get(), save)))
            .bounds(x, y, COL_W, 20).build();
        this.addRenderableWidget(button);
        return button;
    }

    private static int rgbOf(String color, int fallback) {
        TextColor parsed = FinishDisplay.parseColor(color);
        return parsed != null ? parsed.getValue() : fallback;
    }

    private void swatch(GuiGraphics g, Button b, String color, int fallback) {
        int sx = b.getX() + b.getWidth() - 18;
        int sy = b.getY() + 3;
        g.fill(sx - 1, sy - 1, sx + 15, sy + 15, 0xFF000000);
        g.fill(sx, sy, sx + 14, sy + 14, 0xFF000000 | rgbOf(color, fallback));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        swatch(g, timerColorButton, SpeedBuilderConfig.getTimerColor(uuid), 0xFFFF55);
        swatch(g, finishColorButton, SpeedBuilderConfig.getFinishColor(uuid), 0x55FF55);
        swatch(g, finishTimeColorButton, SpeedBuilderConfig.getFinishTimeColor(uuid), 0xFFFFFF);

        // live preview
        int py = 34 + ROW_H * 6 + 2;
        g.drawCenteredString(this.font, Component.literal("Preview").withStyle(ChatFormatting.GRAY), this.width / 2, py, 0xFFFFFF);
        g.drawCenteredString(this.font, FinishDisplay.timerComponent(uuid, "12.34s"), this.width / 2, py + 11, 0xFFFFFF);
        g.drawCenteredString(this.font, FinishDisplay.buildTitle(uuid), this.width / 2, py + 22, 0xFFFFFF);
        g.drawCenteredString(this.font, FinishDisplay.finishTimeComponent(uuid, "12.34s"), this.width / 2, py + 33, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        SpeedBuilderConfig.saveConfig();
        this.minecraft.setScreen(parent);
    }

    private class FinishTimeSlider extends AbstractSliderButton {
        private static final int MIN = SpeedBuilderConfig.MIN_FINISH_TIME;
        private static final int MAX = SpeedBuilderConfig.MAX_FINISH_TIME;

        FinishTimeSlider(int x, int y, int w, int h, int seconds) {
            super(x, y, w, h, Component.empty(), (seconds - MIN) / (double) (MAX - MIN));
            updateMessage();
        }

        private int seconds() {
            return MIN + (int) Math.round(this.value * (MAX - MIN));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal("Finish Title Time: " + seconds() + "s"));
        }

        @Override
        protected void applyValue() {
            settings().finishTitleTime = seconds();
        }
    }

    private class PreviewTimeSlider extends AbstractSliderButton {
        PreviewTimeSlider(int x, int y, int w, int h, int seconds) {
            super(x, y, w, h, Component.empty(), seconds / 60.0);
            updateMessage();
        }

        private int seconds() {
            return (int) Math.round(this.value * 60.0);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal("Preview Time: " + seconds() + "s"));
        }

        @Override
        protected void applyValue() {
            settings().previewTime = seconds();
        }
    }
}
