package io.github.e33epus.fgmplus.gui;

import com.wildfire.gui.WildfireButton;
import com.wildfire.gui.WildfireSlider;
import com.wildfire.gui.screen.BaseWildfireScreen;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.entitydata.PlayerConfig;
import io.github.e33epus.fgmplus.FgmPlusMod;
import io.github.e33epus.fgmplus.shape.ShapeData;
import io.github.e33epus.fgmplus.shape.ShapeHolder;
import io.github.e33epus.fgmplus.shape.ShapeRenderState;
import io.github.e33epus.fgmplus.shape.ShapeStore;
import io.github.e33epus.fgmplus.sound.HurtSoundManager;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player breast shape editor (three-axis scale + perkiness + offsets +
 * roundness), composed like the accepted 1.21.11 baseline (FGM 5 style): a
 * centered translucent panel over a dimmed backdrop, FGM's 166px customization
 * grid (two 81px halves), 15px-tall buttons, the title centered at the wardrobe
 * title height and a live player preview in a scissored window left of the grid.
 *
 * <p>1.21.1 notes: Screen#render calls renderBackground itself, so render()
 * must NOT call it again; renderTransparentBackground dims without the menu
 * blur (the blur-every-frame change is 1.21.2+, but the translucent look matches
 * the baseline either way). FGM 3.2.2's BaseWildfireScreen has no
 * renderPlayerInFrame and no onClose, so the preview is hand-rolled with a
 * scissor + InventoryScreen entity render, and {@link #onClose()} is added to
 * return to the wardrobe (ESC used to drop out of the wardrobe entirely). The
 * 3.2.2 WildfireButton/WildfireSlider keep the plain constructor API
 * (WildfireSlider: x, y, w, h, min, max, initial, valueUpdate, messageUpdate,
 * onSave — verified in the jar bytecode).</p>
 */
public class ShapeStudioScreen extends BaseWildfireScreen {

    //The accepted baseline's grid: 166px of content, split into two 81px halves
    private static final int FULL_WIDTH = 166;
    private static final int HALF_WIDTH = 81;
    private static final int PANEL_FILL = 0x55000000;
    //FGM's buttons are 15px tall (its sliders stay 20px)
    private static final int FGM_BUTTON_HEIGHT = 15;

    //Diagnosis toggle switch (a field reference so the label can be flipped via
    //setMessage — 3.2.x WildfireButton has no message supplier)
    private WildfireButton debugPlaneButton;
    //Cached at init and refreshed on button presses: getStatusText() stats the disk,
    //which must not run every render frame
    private Component hurtSoundStatus;

    public ShapeStudioScreen(Component title, Screen parent, UUID uuid) {
        super(title, parent, uuid);
    }

    @Override
    public void init() {
        int y = this.height / 2 - 11; //baseline row anchor
        final int left = this.width / 2 - 36; //same column the baseline's sliders live in
        final int right = left + HALF_WIDTH + 4;

        //Initial slider positions only; callbacks re-resolve the player so edits
        //always land on the live CACHED entry, never a captured reference
        ShapeData shape = ((ShapeHolder) resolvePlayer()).fgmplus$getShape();

        //Live preview: valueUpdate applies to the live shape while dragging (no persist,
        //no sync); onSave on release writes to disk and flags for sync
        this.addRenderableWidget(slider(left, y - 24,
                ShapeData.MIN_SCALE, ShapeData.MAX_SCALE, shape.getScaleX(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setScaleX(value),
                value -> Component.translatable("fgmplus.studio.scale", "X", String.format(Locale.ROOT, "%.2f", value)),
                value -> persist("scaleX", value)));

        this.addRenderableWidget(slider(left, y - 4,
                ShapeData.MIN_SCALE, ShapeData.MAX_SCALE, shape.getScaleY(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setScaleY(value),
                value -> Component.translatable("fgmplus.studio.scale", "Y", String.format(Locale.ROOT, "%.2f", value)),
                value -> persist("scaleY", value)));

        this.addRenderableWidget(slider(left, y + 16,
                ShapeData.MIN_SCALE, ShapeData.MAX_SCALE, shape.getScaleZ(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setScaleZ(value),
                value -> Component.translatable("fgmplus.studio.scale", "Z", String.format(Locale.ROOT, "%.2f", value)),
                value -> persist("scaleZ", value)));

        this.addRenderableWidget(slider(right, y - 24,
                ShapeData.MIN_OFFSET, ShapeData.MAX_OFFSET, shape.getOffsetX(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setOffsetX(value),
                value -> Component.translatable("fgmplus.studio.pos", "X", String.format(Locale.ROOT, "%+.2f", value)),
                value -> persist("offsetX", value)));

        this.addRenderableWidget(slider(right, y - 4,
                ShapeData.MIN_OFFSET, ShapeData.MAX_OFFSET, shape.getOffsetY(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setOffsetY(value),
                value -> Component.translatable("fgmplus.studio.pos", "Y", String.format(Locale.ROOT, "%+.2f", value)),
                value -> persist("offsetY", value)));

        //UI shows the inverted value so that dragging RIGHT protrudes (user's
        //expected direction): stored offsetZ is positive toward the torso back
        //(front = -z, user-measured), so the slider negates on both read and write
        WildfireSlider offsetZ = slider(right, y + 16,
                ShapeData.MIN_OFFSET, ShapeData.MAX_OFFSET, -shape.getOffsetZ(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setOffsetZ(-value),
                value -> Component.translatable("fgmplus.studio.pos", "Z", String.format(Locale.ROOT, "%+.2f", value)),
                value -> persist("offsetZ", -value));
        offsetZ.setTooltip(Tooltip.create(Component.translatable("fgmplus.studio.pos_z_tip")));
        this.addRenderableWidget(offsetZ);

        this.addRenderableWidget(slider(left, y + 36,
                ShapeData.MIN_PERK, ShapeData.MAX_PERK, shape.getPerkiness(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setPerkiness(value),
                value -> Component.translatable("fgmplus.studio.perkiness", String.format(Locale.ROOT, "%+.0f", value)),
                value -> persist("perkiness", value)));

        //Roundness: 0 = FGM's flat box (the "triangle" silhouette), 1 = full
        //superellipsoid; displayed as a percentage. Syncs to everyone like the
        //rest of the shape payload
        WildfireSlider roundness = slider(right, y + 36,
                ShapeData.MIN_ROUNDNESS, ShapeData.MAX_ROUNDNESS, shape.getRoundness(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setRoundness(value),
                value -> Component.translatable("fgmplus.studio.roundness", String.format(Locale.ROOT, "%.0f", value * 100f)),
                value -> persist("roundness", value));
        roundness.setTooltip(Tooltip.create(Component.translatable("fgmplus.studio.roundness_tip")));
        this.addRenderableWidget(roundness);

        //Custom hurt sound controls; every *.ogg in the folder plays (random pick,
        //same as FGM's own damage oggs). Preview plays instantly when the loaded
        //set matches the directory and only chains a full resource reload when the
        //files changed, so newly dropped/renamed files are picked up without restart
        this.hurtSoundStatus = HurtSoundManager.getStatusText();
        this.addRenderableWidget(new WildfireButton(left, y + 56, HALF_WIDTH, FGM_BUTTON_HEIGHT,
                Component.translatable("fgmplus.studio.sound_folder"), button -> {
                    HurtSoundManager.openFolder();
                    this.hurtSoundStatus = HurtSoundManager.getStatusText();
                }));
        this.addRenderableWidget(new WildfireButton(right, y + 56, HALF_WIDTH, FGM_BUTTON_HEIGHT,
                Component.translatable("fgmplus.studio.preview"), button -> {
                    HurtSoundManager.reloadThenPlay();
                    this.hurtSoundStatus = HurtSoundManager.getStatusText();
                },
                Tooltip.create(Component.translatable("fgmplus.studio.preview_tip"))));

        this.addRenderableWidget(new WildfireButton(left, y + 76, HALF_WIDTH, FGM_BUTTON_HEIGHT,
                Component.translatable("fgmplus.studio.reset"), button -> {
                    ((ShapeHolder) resolvePlayer()).fgmplus$setShape(new ShapeData());
                    persist("reset", 0.0F);
                    //Re-init so the sliders re-read from the fresh default shape
                    this.rebuildWidgets();
                }));
        //Diagnosis: shows the computed torso-back plane; if it hugs the back from
        //every angle the transform chain behind the flatten clamp is proven
        this.debugPlaneButton = new WildfireButton(right, y + 76, HALF_WIDTH, FGM_BUTTON_HEIGHT,
                Component.translatable(ShapeRenderState.debugPlane ? "fgmplus.studio.debug_on" : "fgmplus.studio.debug_off"), button -> {
                    ShapeRenderState.debugPlane = !ShapeRenderState.debugPlane;
                    button.setMessage(Component.translatable(ShapeRenderState.debugPlane ? "fgmplus.studio.debug_on" : "fgmplus.studio.debug_off"));
                },
                Tooltip.create(Component.translatable("fgmplus.studio.debug_tip")));
        this.addRenderableWidget(this.debugPlaneButton);

        super.init();
    }

    private static WildfireSlider slider(int x, int y, float min, float max, float current,
            it.unimi.dsi.fastutil.floats.FloatConsumer update,
            it.unimi.dsi.fastutil.floats.Float2ObjectFunction<Component> message,
            it.unimi.dsi.fastutil.floats.FloatConsumer save) {
        return new WildfireSlider(x, y, HALF_WIDTH, 20, min, max, current, update, message, save);
    }

    @Override
    public void onClose() {
        //3.2.x BaseWildfireScreen has no onClose, so ESC used to fall through to
        //the vanilla default (null screen) instead of returning to the wardrobe
        this.minecraft.setScreen(parent);
    }

    /** Re-reads the sound status text; called after the async preview reload finishes. */
    public void refreshSoundStatus() {
        this.hurtSoundStatus = HurtSoundManager.getStatusText();
    }

    private PlayerConfig resolvePlayer() {
        PlayerConfig plr = WildfireGender.getPlayerById(this.playerUUID);
        //getPlayerById returns null when no entry exists yet
        return plr != null ? plr : WildfireGender.getOrAddPlayerById(this.playerUUID);
    }

    private void persist(String key, float value) {
        PlayerConfig plr = resolvePlayer();
        if (plr == null) {
            //No FGM entry yet: persisting a default shape here would DELETE the
            //player's saved shape file (ShapeStore deletes on isDefault)
            return;
        }
        ShapeStore.save(this.playerUUID, ((ShapeHolder) plr).fgmplus$getShape());
        //FGM's own tick loop detects this flag and sends the C2S sync packet;
        //the shape payload piggybacks on that send (WildfireSyncClientMixin)
        plr.needsSync = true;
        //Observability for the "slider edits stop applying" report class
        FgmPlusMod.LOGGER.debug("FGM Plus shape edit: uuid={}, {}={}", this.playerUUID, key, value);
    }

    /**
     * Baseline composition: dim the world, the translucent panel over the grid,
     * the title centered at the wardrobe title height, and the live player
     * preview in a scissored window left of the sliders (FGM 3.2.x has no
     * renderPlayerInFrame, so the window is hand-rolled).
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);

        int x = this.width / 2;
        int y = this.height / 2;
        //Same translucent panel style as the baseline, sized over the grid
        graphics.fill(x - 40, y - 32, x + 136, y + 113, PANEL_FILL);

        //Title centered at FGM's wardrobe title height
        graphics.drawString(this.font, this.title, x - this.font.width(this.title) / 2, y - 82, 0xFFFFFF, false);

        //Large live preview of the actual player; the model renders through the
        //same GenderLayer pipeline, so live slider edits show up here
        Minecraft minecraft = this.minecraft;
        if (minecraft != null && minecraft.level != null) {
            Player ent = minecraft.level.getPlayerByUUID(this.playerUUID);
            if (ent != null) {
                graphics.enableScissor(x - 128, y - 35, x - 52, y + 53);
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                        x - 128, y - 35, x - 52, y + 113, 70, 0.0F, mouseX, mouseY + 35, ent);
                graphics.disableScissor();
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        //NO manual renderBackground here: vanilla Screen#render already calls it on 1.21.1
        super.render(graphics, mouseX, mouseY, delta);

        int x = this.width / 2;
        int y = this.height / 2;
        //Status text (loaded sound files) below the 15px button rows; can exceed the
        //162 px panel (long file names), so wrap to at most two lines with a tail
        int ly = y + 94;
        for (String line : wrapStatus(this.hurtSoundStatus.getString(), this.font, FULL_WIDTH - 4)) {
            graphics.drawCenteredString(this.font, line, x + 48, ly, 0xE0E0E0);
            ly += 10;
        }
    }

    /** Greedy character-wrap to at most 2 lines of {@code maxWidth} px. */
    private static java.util.List<String> wrapStatus(String text, Font font, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String piece = new String(Character.toChars(codePoint));
            if (current.length() > 0 && font.width(current + piece) > maxWidth) {
                lines.add(current.toString());
                current.setLength(0);
            }
            current.append(piece);
            i += Character.charCount(codePoint);
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        if (lines.size() > 2) {
            String second = lines.get(1);
            while (!second.isEmpty() && font.width(second + "…") > maxWidth) {
                second = second.substring(0, second.length() - 1);
            }
            return java.util.List.of(lines.get(0), second + "…");
        }
        return lines;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int state) {
        //Ensure all sliders are saved even when the mouse comes up outside the widget
        for (var child : this.children()) {
            if (child instanceof WildfireSlider slider) {
                slider.save();
            }
        }
        return super.mouseReleased(mouseX, mouseY, state);
    }
}
