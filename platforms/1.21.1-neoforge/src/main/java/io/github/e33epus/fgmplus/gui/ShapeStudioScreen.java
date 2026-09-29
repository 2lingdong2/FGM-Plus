package io.github.e33epus.fgmplus.gui;

import com.wildfire.gui.WildfireButton;
import com.wildfire.gui.WildfireSlider;
import com.wildfire.gui.screen.BaseWildfireScreen;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.WildfireHelper;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Per-player breast shape editor (three-axis scale + perkiness + offsets +
 * roundness), composed like the accepted forge 1.20.1 studio (user ruling
 * 2026-09-29, after the 1.21.11-style central panel was rejected on live
 * acceptance): right-hand translucent panel with a title band, two 77px slider
 * columns plus full-width perkiness/roundness rows, 20px-tall buttons, the 9x9
 * X button top-right, the status line under the buttons and the large
 * fixed-angle player preview on the left — drawn through FGM 3.2.2's own
 * preview path (WildfireHelper.withEntityAngles + the vanilla 8-arg
 * InventoryScreen.renderEntityInInventory) with the exact constants the 3.2.2
 * customization screen bakes in (ANGLE=-0.5: bodyRot 180+a*20, yRot 180+a*40,
 * xRot a*20, camera rotateX(a*20°), flip rotateZ(π), anchor x-102/y+75, scale 200).
 *
 * <p>1.21.1 notes: Screen#render calls renderBackground itself, so render()
 * must NOT call it again; renderBackground carries the (GuiGraphics, int, int,
 * float) signature. Unlike the forge screen, {@link #onClose()} is overridden
 * so ESC returns to the wardrobe (3.2.x BaseWildfireScreen has no onClose and
 * the vanilla default drops out of the wardrobe entirely). The
 * WildfireButton/WildfireSlider plain constructor API is the 3.2.2 one
 * (WildfireSlider: x, y, w, h, min, max, initial, valueUpdate, messageUpdate,
 * onSave — verified in the jar bytecode).</p>
 */
public class ShapeStudioScreen extends BaseWildfireScreen {

    private static final int PANEL_FILL = 0x55000000;

    //FGM 3.2.2's fixed studio camera angle and its derived quaternions (decoded
    //from the customization screen <clinit>/preview lambda), so this preview is
    //pixel-identical to the one FGM itself draws on this platform
    private static final float fp$PREVIEW_ANGLE = -0.5F;
    private static final Quaternionf fp$PREVIEW_FLIP = new Quaternionf().rotateZ((float) Math.PI);
    private static final Quaternionf fp$PREVIEW_CAMERA = new Quaternionf().rotateX(fp$PREVIEW_ANGLE * 20.0F * Mth.DEG_TO_RAD);

    //Cached at init and refreshed on button presses: getStatusText() stats the disk,
    //which must not run every render frame
    private Component hurtSoundStatus;

    public ShapeStudioScreen(Component title, Screen parent, UUID uuid) {
        super(title, parent, uuid);
    }

    @Override
    public void init() {
        int y = this.height / 2;
        int bx = this.width / 2 + 30;

        //Initial slider positions only; callbacks re-resolve the player so edits
        //always land on the live CACHED entry, never a captured reference
        ShapeData shape = ((ShapeHolder) resolvePlayer()).fgmplus$getShape();

        this.addRenderableWidget(new WildfireButton(this.width / 2 + 174, y - 81, 9, 9, Component.literal("X"),
                button -> Minecraft.getInstance().setScreen(parent)));

        //Live preview: valueUpdate applies to the live shape while dragging (no persist,
        //no sync); onSave on release writes to disk and flags for sync
        this.addRenderableWidget(new WildfireSlider(bx, y - 52, 77, 20,
                ShapeData.MIN_SCALE, ShapeData.MAX_SCALE, shape.getScaleX(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setScaleX(value),
                value -> Component.translatable("fgmplus.studio.scale", "X", String.format(Locale.ROOT, "%.2f", value)),
                value -> persist("scaleX", value)));

        this.addRenderableWidget(new WildfireSlider(bx, y - 32, 77, 20,
                ShapeData.MIN_SCALE, ShapeData.MAX_SCALE, shape.getScaleY(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setScaleY(value),
                value -> Component.translatable("fgmplus.studio.scale", "Y", String.format(Locale.ROOT, "%.2f", value)),
                value -> persist("scaleY", value)));

        this.addRenderableWidget(new WildfireSlider(bx, y - 12, 77, 20,
                ShapeData.MIN_SCALE, ShapeData.MAX_SCALE, shape.getScaleZ(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setScaleZ(value),
                value -> Component.translatable("fgmplus.studio.scale", "Z", String.format(Locale.ROOT, "%.2f", value)),
                value -> persist("scaleZ", value)));

        this.addRenderableWidget(new WildfireSlider(bx + 81, y - 52, 77, 20,
                ShapeData.MIN_OFFSET, ShapeData.MAX_OFFSET, shape.getOffsetX(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setOffsetX(value),
                value -> Component.translatable("fgmplus.studio.pos", "X", String.format(Locale.ROOT, "%+.2f", value)),
                value -> persist("offsetX", value)));

        this.addRenderableWidget(new WildfireSlider(bx + 81, y - 32, 77, 20,
                ShapeData.MIN_OFFSET, ShapeData.MAX_OFFSET, shape.getOffsetY(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setOffsetY(value),
                value -> Component.translatable("fgmplus.studio.pos", "Y", String.format(Locale.ROOT, "%+.2f", value)),
                value -> persist("offsetY", value)));

        //UI shows the inverted value so that dragging RIGHT protrudes (user's
        //expected direction): stored offsetZ is positive toward the torso back
        //(front = -z, user-measured), so the slider negates on both read and write
        WildfireSlider offsetZ = new WildfireSlider(bx + 81, y - 12, 77, 20,
                ShapeData.MIN_OFFSET, ShapeData.MAX_OFFSET, -shape.getOffsetZ(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setOffsetZ(-value),
                value -> Component.translatable("fgmplus.studio.pos", "Z", String.format(Locale.ROOT, "%+.2f", value)),
                value -> persist("offsetZ", -value));
        offsetZ.setTooltip(Tooltip.create(Component.translatable("fgmplus.studio.pos_z_tip")));
        this.addRenderableWidget(offsetZ);

        this.addRenderableWidget(new WildfireSlider(bx, y + 8, 158, 20,
                ShapeData.MIN_PERK, ShapeData.MAX_PERK, shape.getPerkiness(),
                value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setPerkiness(value),
                value -> Component.translatable("fgmplus.studio.perkiness", String.format(Locale.ROOT, "%+.0f", value)),
                value -> persist("perkiness", value)));

        //Roundness: 0 = FGM's flat box (the "triangle" silhouette), 1 = full
        //superellipsoid; displayed as a percentage. Syncs to everyone like the
        //rest of the shape payload
        WildfireSlider roundness = new WildfireSlider(bx, y + 28, 158, 20,
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
        this.addRenderableWidget(new WildfireButton(bx, y + 48, 77, 20,
                Component.translatable("fgmplus.studio.sound_folder"), button -> {
                    HurtSoundManager.openFolder();
                    this.hurtSoundStatus = HurtSoundManager.getStatusText();
                }));
        this.addRenderableWidget(new WildfireButton(bx + 81, y + 48, 77, 20,
                Component.translatable("fgmplus.studio.preview"), button -> {
                    HurtSoundManager.reloadThenPlay();
                    this.hurtSoundStatus = HurtSoundManager.getStatusText();
                },
                Tooltip.create(Component.translatable("fgmplus.studio.preview_tip"))));

        this.addRenderableWidget(new WildfireButton(bx, y + 68, 77, 20,
                Component.translatable("fgmplus.studio.reset"), button -> {
                    ((ShapeHolder) resolvePlayer()).fgmplus$setShape(new ShapeData());
                    persist("reset", 0.0F);
                    //Re-init so the sliders re-read from the fresh default shape
                    this.rebuildWidgets();
                }));
        //Diagnosis: shows the computed torso-back plane; if it hugs the back from
        //every angle the transform chain behind the flatten clamp is proven
        this.addRenderableWidget(new WildfireButton(bx + 81, y + 68, 77, 20,
                Component.translatable(ShapeRenderState.debugPlane ? "fgmplus.studio.debug_on" : "fgmplus.studio.debug_off"), button -> {
                    ShapeRenderState.debugPlane = !ShapeRenderState.debugPlane;
                    button.setMessage(Component.translatable(ShapeRenderState.debugPlane ? "fgmplus.studio.debug_on" : "fgmplus.studio.debug_off"));
                },
                Tooltip.create(Component.translatable("fgmplus.studio.debug_tip"))));

        super.init();
    }

    @Override
    public void onClose() {
        //3.2.x BaseWildfireScreen has no onClose, so ESC would fall through to
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
     * forge composition: the world stays visible through the vanilla dim behind a
     * right-hand translucent panel (main slab + 1px-inset title band), exactly the
     * fills the forge 1.20.1 studio draws.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.width / 2;
        int y = this.height / 2;
        graphics.fill(x + 28, y - 85, x + 190, y + 98, PANEL_FILL);
        graphics.fill(x + 29, y - 84, x + 189, y - 60, PANEL_FILL);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        //NO manual renderBackground here: vanilla Screen#render already calls it on 1.21.1
        super.render(graphics, mouseX, mouseY, delta);

        int x = this.width / 2;
        int y = this.height / 2;
        graphics.drawString(this.font, this.title, x + 32, y - 81, 0xFFFFFF, false);

        //Large live preview of the actual player through FGM 3.2.2's own preview
        //path and constants; the model renders through the same GenderLayer
        //pipeline, so live slider edits show up here
        if (this.minecraft != null && this.minecraft.level != null) {
            Player ent = this.minecraft.level.getPlayerByUUID(this.playerUUID);
            if (ent != null) {
                GuiGraphics gfx = graphics;
                WildfireHelper.withEntityAngles(ent,
                        180.0F + fp$PREVIEW_ANGLE * 20.0F,
                        180.0F + fp$PREVIEW_ANGLE * 40.0F,
                        fp$PREVIEW_ANGLE * 20.0F,
                        e -> InventoryScreen.renderEntityInInventory(gfx,
                                x - 102, y + 75, 200,
                                new Vector3f(0.0F, e.getBbHeight() / 2.0F, 0.0F),
                                fp$PREVIEW_FLIP, fp$PREVIEW_CAMERA, e));
            }
        }

        int cx = x + 109; //center of the translucent panel (x+28..x+190)
        //Status text can exceed the 162 px panel (long file names); wrap it to the
        //panel width, at most two lines with an ellipsis tail
        int ly = y + 88;
        for (String line : wrapStatus(this.hurtSoundStatus.getString(), this.font, 150)) {
            graphics.drawCenteredString(this.font, line, cx, ly, 0xE0E0E0);
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
