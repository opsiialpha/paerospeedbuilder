package com.paero.speedbuilder.client;

import com.paero.speedbuilder.FinishDisplay;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

/** HSV colour wheel + brightness bar + hex box + the 16 chat colours as presets. */
public class ColorPickerScreen extends Screen {
    private static final int WHEEL = 112;
    private static final int BAR_W = 14;
    private static final int[] PRESETS = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private final Screen parent;
    private final Component pickerTitle;
    private final Consumer<String> onApply;
    private float hue;
    private float sat;
    private float val = 1.0f;
    private ResourceLocation wheelLocation;
    private DynamicTexture wheelTexture;
    private EditBox hexBox;
    private boolean updatingBox;
    private boolean dragWheel;
    private boolean dragBar;
    private int wheelX;
    private int wheelY;
    private int barX;
    private int rightX;

    public ColorPickerScreen(Screen parent, Component title, String initialColor, Consumer<String> onApply) {
        super(title);
        this.parent = parent;
        this.pickerTitle = title;
        this.onApply = onApply;
        TextColor parsed = FinishDisplay.parseColor(initialColor);
        setFromRgb(parsed != null ? parsed.getValue() : 0xFFFFFF);
    }

    @Override
    protected void init() {
        int totalW = WHEEL + 12 + BAR_W + 12 + 84;
        wheelX = (this.width - totalW) / 2;
        wheelY = this.height / 2 - 66;
        barX = wheelX + WHEEL + 12;
        rightX = barX + BAR_W + 12;

        wheelLocation = ResourceLocation.fromNamespaceAndPath("paerospeedbuilder", "color_wheel");
        wheelTexture = new DynamicTexture(WHEEL, WHEEL, false);
        NativeImage img = wheelTexture.getPixels();
        if (img != null) {
            float r = WHEEL / 2.0f;
            for (int y = 0; y < WHEEL; ++y) {
                for (int x = 0; x < WHEEL; ++x) {
                    float dx = x + 0.5f - r;
                    float dy = y + 0.5f - r;
                    float d = (float) Math.sqrt(dx * dx + dy * dy) / r;
                    int argb;
                    if (d <= 1.0f) {
                        float h = (float) (Math.atan2(dy, dx) / (Math.PI * 2.0));
                        if (h < 0) h += 1.0f;
                        argb = 0xFF000000 | hsvToRgb(h, d, 1.0f);
                    } else {
                        argb = 0xFF202020;
                    }
                    int abgr = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
                    img.setPixelRGBA(x, y, abgr);
                }
            }
            wheelTexture.upload();
        }
        this.minecraft.getTextureManager().register(wheelLocation, wheelTexture);

        hexBox = new EditBox(this.font, rightX, wheelY + 20, 84, 18, Component.literal("Hex"));
        hexBox.setMaxLength(7);
        hexBox.setFilter(s -> s.matches("#?[0-9a-fA-F]{0,6}"));
        hexBox.setResponder(text -> {
            if (updatingBox) return;
            String hex = text.startsWith("#") ? text.substring(1) : text;
            if (hex.length() == 6) {
                setFromRgb(Integer.parseInt(hex, 16));
            }
        });
        this.addRenderableWidget(hexBox);
        syncHexBox();

        int btnY = wheelY + WHEEL + 14;
        this.addRenderableWidget(Button.builder(Component.literal("Apply"), b -> {
            onApply.accept(String.format("#%06X", hsvToRgb(hue, sat, val)));
            this.minecraft.setScreen(parent);
        }).bounds(this.width / 2 - 104, btnY, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.minecraft.setScreen(parent))
            .bounds(this.width / 2 + 4, btnY, 100, 20).build());
    }

    @Override
    public void removed() {
        if (wheelLocation != null) {
            this.minecraft.getTextureManager().release(wheelLocation);
            wheelLocation = null;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private void setFromRgb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h;
        if (delta == 0.0f) {
            h = 0.0f;
        } else if (max == r) {
            h = ((g - b) / delta) / 6.0f;
        } else if (max == g) {
            h = (((b - r) / delta) + 2.0f) / 6.0f;
        } else {
            h = (((r - g) / delta) + 4.0f) / 6.0f;
        }
        if (h < 0) h += 1.0f;
        this.hue = h;
        this.sat = max == 0.0f ? 0.0f : delta / max;
        this.val = max;
    }

    private static int hsvToRgb(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6.0f;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - s * f);
        float t = v * (1.0f - s * (1.0f - f));
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    private void syncHexBox() {
        updatingBox = true;
        hexBox.setValue(String.format("#%06X", hsvToRgb(hue, sat, val)));
        updatingBox = false;
    }

    private void updateWheel(double mx, double my) {
        double cx = wheelX + WHEEL / 2.0;
        double cy = wheelY + WHEEL / 2.0;
        double dx = mx - cx;
        double dy = my - cy;
        sat = (float) Math.min(1.0, Math.sqrt(dx * dx + dy * dy) / (WHEEL / 2.0));
        float h = (float) (Math.atan2(dy, dx) / (Math.PI * 2.0));
        hue = h < 0 ? h + 1.0f : h;
        syncHexBox();
    }

    private void updateBar(double my) {
        val = (float) (1.0 - Math.max(0.0, Math.min(1.0, (my - wheelY) / WHEEL)));
        syncHexBox();
    }

    // Preset swatches: 16x16 including the black border, 18 px apart. The 4x4 grid starts under the hex box
    // and its bottom edge lines up with the bottom edge of the colour wheel frame (wheelY + WHEEL + 2),
    // so it can never reach the Apply / Cancel buttons below.
    private static final int PRESET_SIZE = 16;
    private static final int PRESET_PITCH = 18;

    private int presetX(int i) {
        int gridWidth = 3 * PRESET_PITCH + PRESET_SIZE;
        return rightX - 1 + (86 - gridWidth) / 2 + (i % 4) * PRESET_PITCH;
    }

    private int presetY(int i) {
        int gridHeight = 3 * PRESET_PITCH + PRESET_SIZE;
        return wheelY + WHEEL + 2 - gridHeight + (i / 4) * PRESET_PITCH;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            double cx = wheelX + WHEEL / 2.0;
            double cy = wheelY + WHEEL / 2.0;
            double dist = Math.sqrt((mx - cx) * (mx - cx) + (my - cy) * (my - cy));
            if (dist <= WHEEL / 2.0 + 1.0) {
                dragWheel = true;
                updateWheel(mx, my);
                return true;
            }
            if (mx >= barX && mx <= barX + BAR_W && my >= wheelY && my <= wheelY + WHEEL) {
                dragBar = true;
                updateBar(my);
                return true;
            }
            for (int i = 0; i < PRESETS.length; ++i) {
                int px = presetX(i);
                int py = presetY(i);
                if (mx >= px && mx < px + PRESET_SIZE && my >= py && my < py + PRESET_SIZE) {
                    setFromRgb(PRESETS[i]);
                    syncHexBox();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragWheel) {
            updateWheel(mx, my);
            return true;
        }
        if (dragBar) {
            updateBar(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragWheel = false;
        dragBar = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, pickerTitle, this.width / 2, wheelY - 22, 0xFFFFFF);

        // wheel
        g.fill(wheelX - 2, wheelY - 2, wheelX + WHEEL + 2, wheelY + WHEEL + 2, 0xFF000000);
        g.blit(wheelLocation, wheelX, wheelY, 0.0f, 0.0f, WHEEL, WHEEL, WHEEL, WHEEL);
        double angle = hue * Math.PI * 2.0;
        int mx = (int) Math.round(wheelX + WHEEL / 2.0 + Math.cos(angle) * sat * (WHEEL / 2.0));
        int my = (int) Math.round(wheelY + WHEEL / 2.0 + Math.sin(angle) * sat * (WHEEL / 2.0));
        g.fill(mx - 3, my - 3, mx + 4, my + 4, 0xFF000000);
        g.fill(mx - 2, my - 2, mx + 3, my + 3, 0xFFFFFFFF);
        g.fill(mx - 1, my - 1, mx + 2, my + 2, 0xFF000000 | hsvToRgb(hue, sat, val));

        // brightness bar
        g.fill(barX - 1, wheelY - 1, barX + BAR_W + 1, wheelY + WHEEL + 1, 0xFF000000);
        g.fillGradient(barX, wheelY, barX + BAR_W, wheelY + WHEEL, 0xFF000000 | hsvToRgb(hue, sat, 1.0f), 0xFF000000);
        int barMarker = wheelY + Math.round((1.0f - val) * (WHEEL - 1));
        g.fill(barX - 2, barMarker - 1, barX + BAR_W + 2, barMarker + 2, 0xFF000000);
        g.fill(barX - 1, barMarker, barX + BAR_W + 1, barMarker + 1, 0xFFFFFFFF);

        // current colour swatch
        g.fill(rightX - 1, wheelY - 1, rightX + 85, wheelY + 18, 0xFF000000);
        g.fill(rightX, wheelY, rightX + 84, wheelY + 17, 0xFF000000 | hsvToRgb(hue, sat, val));

        // presets
        for (int i = 0; i < PRESETS.length; ++i) {
            int px = presetX(i);
            int py = presetY(i);
            g.fill(px, py, px + PRESET_SIZE, py + PRESET_SIZE, 0xFF000000);
            g.fill(px + 1, py + 1, px + PRESET_SIZE - 1, py + PRESET_SIZE - 1, 0xFF000000 | PRESETS[i]);
        }
    }
}
