package io.github.e33epus.fgmplus.gui;

import com.wildfire.gui.GuiUtils;
import com.wildfire.gui.WildfireButton;
import com.wildfire.gui.WildfireSlider;
import com.wildfire.gui.screen.BaseWildfireScreen;
import com.wildfire.main.entitydata.PlayerConfig;
import com.wildfire.main.WildfireGender;
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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Per-player breast shape editor (three-axis scale + perkiness + offsets).
 * Mirrors FGM's breast customization screen: a translucent panel on the
 * right and a large live preview of the actual player on the left.
 */
public class ShapeStudioScreen extends BaseWildfireScreen {

	private WildfireSlider scaleXSlider;
	private WildfireSlider scaleYSlider;
	private WildfireSlider scaleZSlider;
	private WildfireSlider offsetXSlider;
	private WildfireSlider offsetYSlider;
	private WildfireSlider offsetZSlider;
	private WildfireSlider perkinessSlider;
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
		//always land on the live cache entry, never a captured reference
		ShapeData shape = ((ShapeHolder) resolvePlayer()).fgmplus$getShape();

		this.addRenderableWidget(new WildfireButton.Builder()
				.message(() -> Component.literal("X"))
				.position(this.width / 2 + 174, y - 81)
				.size(9, 9)
				.onPress(button -> Minecraft.getInstance().setScreen(parent))
				.build());

		//Live preview: update applies to the live shape while dragging (no persist,
		//no sync); save on release writes to disk and flags for sync
		this.addRenderableWidget(this.scaleXSlider = slider(bx, y - 52, 77,
				ShapeData.MIN_SCALE, ShapeData.MAX_SCALE, shape.getScaleX(),
				value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setScaleX(value),
				value -> Component.translatable("fgmplus.studio.scale", "X", String.format(Locale.ROOT, "%.2f", value)),
				value -> persist("scaleX", value)));

		this.addRenderableWidget(this.scaleYSlider = slider(bx, y - 32, 77,
				ShapeData.MIN_SCALE, ShapeData.MAX_SCALE, shape.getScaleY(),
				value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setScaleY(value),
				value -> Component.translatable("fgmplus.studio.scale", "Y", String.format(Locale.ROOT, "%.2f", value)),
				value -> persist("scaleY", value)));

		this.addRenderableWidget(this.scaleZSlider = slider(bx, y - 12, 77,
				ShapeData.MIN_SCALE, ShapeData.MAX_SCALE, shape.getScaleZ(),
				value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setScaleZ(value),
				value -> Component.translatable("fgmplus.studio.scale", "Z", String.format(Locale.ROOT, "%.2f", value)),
				value -> persist("scaleZ", value)));

		this.addRenderableWidget(this.offsetXSlider = slider(bx + 81, y - 52, 77,
				ShapeData.MIN_OFFSET, ShapeData.MAX_OFFSET, shape.getOffsetX(),
				value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setOffsetX(value),
				value -> Component.translatable("fgmplus.studio.pos", "X", String.format(Locale.ROOT, "%+.2f", value)),
				value -> persist("offsetX", value)));

		this.addRenderableWidget(this.offsetYSlider = slider(bx + 81, y - 32, 77,
				ShapeData.MIN_OFFSET, ShapeData.MAX_OFFSET, shape.getOffsetY(),
				value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setOffsetY(value),
				value -> Component.translatable("fgmplus.studio.pos", "Y", String.format(Locale.ROOT, "%+.2f", value)),
				value -> persist("offsetY", value)));

		//UI shows the inverted value so that dragging RIGHT protrudes (user's
		//expected direction): stored offsetZ is positive toward the torso back
		//(front = -z, user-measured), so the slider negates on both read and write
		this.offsetZSlider = slider(bx + 81, y - 12, 77,
				ShapeData.MIN_OFFSET, ShapeData.MAX_OFFSET, -shape.getOffsetZ(),
				value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setOffsetZ(-value),
				value -> Component.translatable("fgmplus.studio.pos", "Z", String.format(Locale.ROOT, "%+.2f", value)),
				value -> persist("offsetZ", -value));
		this.offsetZSlider.setTooltip(Tooltip.create(Component.translatable("fgmplus.studio.pos_z_tip")));
		this.addRenderableWidget(this.offsetZSlider);

		this.addRenderableWidget(this.perkinessSlider = slider(bx, y + 8, 158,
				ShapeData.MIN_PERK, ShapeData.MAX_PERK, shape.getPerkiness(),
				value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setPerkiness(value),
				value -> Component.translatable("fgmplus.studio.perkiness", String.format(Locale.ROOT, "%+.0f", value)),
				value -> persist("perkiness", value)));

		//Custom hurt sound controls; every *.ogg in the folder plays (random pick,
		//same as FGM's own damage oggs). Preview plays instantly when the loaded
		//set matches the directory and only chains a full resource reload when the
		//files changed, so newly dropped/renamed files are picked up without restart
		this.hurtSoundStatus = HurtSoundManager.getStatusText();
		this.addRenderableWidget(new WildfireButton.Builder()
				.message(() -> Component.translatable("fgmplus.studio.sound_folder"))
				.position(bx, y + 28).size(77, 20)
				.onPress(button -> {
					HurtSoundManager.openFolder();
					this.hurtSoundStatus = HurtSoundManager.getStatusText();
				})
				.build());
		this.addRenderableWidget(new WildfireButton.Builder()
				.message(() -> Component.translatable("fgmplus.studio.preview"))
				.position(bx + 81, y + 28).size(77, 20)
				.tooltip(Tooltip.create(Component.translatable("fgmplus.studio.preview_tip")))
				.onPress(button -> {
					HurtSoundManager.reloadThenPlay();
					this.hurtSoundStatus = HurtSoundManager.getStatusText();
				})
				.build());

		this.addRenderableWidget(new WildfireButton.Builder()
				.message(() -> Component.translatable("fgmplus.studio.reset"))
				.position(bx, y + 48).size(77, 20)
				.onPress(button -> {
					((ShapeHolder) resolvePlayer()).fgmplus$setShape(new ShapeData());
					persist("reset", 0.0F);
					//Re-init so the sliders re-read from the fresh default shape
					this.rebuildWidgets();
				})
				.build());
		//Diagnosis: shows the computed torso-back plane; if it hugs the back from
		//every angle the transform chain behind the flatten clamp is proven
		this.addRenderableWidget(new WildfireButton.Builder()
				.message(() -> Component.translatable(ShapeRenderState.debugPlane ? "fgmplus.studio.debug_on" : "fgmplus.studio.debug_off"))
				.position(bx + 81, y + 48).size(77, 20)
				.tooltip(Tooltip.create(Component.translatable("fgmplus.studio.debug_tip")))
				.onPress(button -> {
					ShapeRenderState.debugPlane = !ShapeRenderState.debugPlane;
					button.updateMessage();
				})
				.build());

		super.init();
	}

	private static WildfireSlider slider(int x, int y, int width, float min, float max, float current,
			it.unimi.dsi.fastutil.floats.FloatConsumer update,
			it.unimi.dsi.fastutil.floats.Float2ObjectFunction<Component> message,
			it.unimi.dsi.fastutil.floats.FloatConsumer save) {
		return new WildfireSlider.Builder()
				.position(x, y).size(width, 20)
				.range(min, max).current(current)
				.update(update).message(message).save(save)
				.build();
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
		if(plr == null) {
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

	@Override
	public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.renderBackground(graphics, mouseX, mouseY, delta);
		//Same translucent panel style as FGM's breast customization screen: a fill
		//on the right, the world and the actual player stay visible on the left
		int x = this.width / 2;
		int y = this.height / 2;
		graphics.fill(x + 28, y - 85, x + 190, y + 98, 0x55000000);
		graphics.fill(x + 29, y - 84, x + 189, y - 60, 0x55000000);
	}

	@Override
	public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		renderBackground(graphics, mouseX, mouseY, delta);
		super.render(graphics, mouseX, mouseY, delta);

		int x = this.width / 2;
		int y = this.height / 2;
		graphics.drawString(this.font, this.title, x + 32, y - 81, 0xFFFFFF, false);

		//Large live preview of the actual player; the model renders through the same
		//GenderLayer pipeline, so live slider edits show up here
		if(this.minecraft != null && this.minecraft.level != null) {
			Player ent = this.minecraft.level.getPlayerByUUID(this.playerUUID);
			if(ent != null) {
				//Scissor + FGM's own entity renderer keeps the preview inside its frame
				int px = x - 102;
				graphics.enableScissor(px, y - 79, px + 76, y + 9);
				GuiUtils.drawEntityOnScreen(graphics, px, y - 79, px + 76, y + 69, 70, mouseX, mouseY, ent);
				graphics.disableScissor();
			}
		}

		int cx = x + 109; //center of the translucent panel (x+28..x+190)
		//Status text can exceed the 162 px panel (long file names); wrap it to the
		//panel width, at most two lines with an ellipsis tail
		int ly = y + 74;
		for(String line : wrapStatus(this.hurtSoundStatus.getString(), this.font, 150)) {
			graphics.drawCenteredString(this.font, line, cx, ly, 0xE0E0E0);
			ly += 10;
		}
		//The old "Auto-fit" recovery indicator is gone with the retired recovery
		//translate; back overflow is flattened per-vertex by the clamp since 1.4.0
	}

	/** Greedy character-wrap to at most {@code maxLines} lines of {@code maxWidth} px. */
	private static java.util.List<String> wrapStatus(String text, Font font, int maxWidth) {
		java.util.List<String> lines = new java.util.ArrayList<>();
		StringBuilder current = new StringBuilder();
		for(int i = 0; i < text.length(); ) {
			int codePoint = text.codePointAt(i);
			String piece = new String(Character.toChars(codePoint));
			if(current.length() > 0 && font.width(current + piece) > maxWidth) {
				lines.add(current.toString());
				current.setLength(0);
			}
			current.append(piece);
			i += Character.charCount(codePoint);
		}
		if(current.length() > 0) {
			lines.add(current.toString());
		}
		if(lines.size() > 2) {
			String second = lines.get(1);
			while(!second.isEmpty() && font.width(second + "…") > maxWidth) {
				second = second.substring(0, second.length() - 1);
			}
			return java.util.List.of(lines.get(0), second + "…");
		}
		return lines;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		//Ensure all sliders are saved even when the mouse comes up outside the widget
		if(scaleXSlider != null) scaleXSlider.save();
		if(scaleYSlider != null) scaleYSlider.save();
		if(scaleZSlider != null) scaleZSlider.save();
		if(offsetXSlider != null) offsetXSlider.save();
		if(offsetYSlider != null) offsetYSlider.save();
		if(offsetZSlider != null) offsetZSlider.save();
		if(perkinessSlider != null) perkinessSlider.save();
		return super.mouseReleased(event);
	}
}
