package io.github.e33epus.fgmplus.gui;

import com.wildfire.gui.screen.BaseWildfireScreen;
import com.wildfire.gui.WildfireButton;
import com.wildfire.gui.WildfireSlider;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player breast shape editor (three-axis scale + perkiness + offsets), styled
 * after FGM 5's redesigned screens: darkened backdrop via renderTransparentBackground
 * (NOT Screen#renderBackground — 1.21.9+ blurs there and blur may only run once per
 * frame), the title centered at the same height as FGM's wardrobe, the live player
 * preview in the same left-hand frame (BaseWildfireScreen#renderPlayerInFrame), and
 * a translucent panel on the right in FGM's grid (166px content width, 82px halves).
 *
 * <p>Like FGM 5's own screens, all visible content goes through renderBackground +
 * super.render; render() only draws the dynamic sound-status text.</p>
 */
public class ShapeStudioScreen extends BaseWildfireScreen {

	//FGM 5's customization grid: 166px of content, split into two 82px halves
	private static final int FULL_WIDTH = 166;
	private static final int HALF_WIDTH = FULL_WIDTH / 2 - 2;
	private static final int PANEL_FILL = 0x55000000;
	//FGM 5's buttons are 15px tall (its sliders stay 20px); stretching the shared
	//skin to 20px is exactly what made our buttons look off
	private static final int FGM_BUTTON_HEIGHT = 15;

	//Cached at init and refreshed on button presses: getStatusText() stats the disk,
	//which must not run every render frame
	private Component hurtSoundStatus;

	public ShapeStudioScreen(Component title, net.minecraft.client.gui.screens.Screen parent, UUID uuid) {
		super(title, parent, uuid);
	}

	@Override
	public void init() {
		int y = this.height / 2 - 11; //FGM's baseline
		final int left = this.width / 2 - 36; //same column FGM's sliders live in
		final int right = left + HALF_WIDTH + 4;

		//Initial slider positions only; callbacks re-resolve the player so edits
		//always land on the live cache entry, never a captured reference
		ShapeData shape = ((ShapeHolder) resolvePlayer()).fgmplus$getShape();

		//Live preview: update applies to the live shape while dragging (no persist,
		//no sync); save on release writes to disk and flags for sync
		this.addRenderableWidget(slider(left, y - 24, HALF_WIDTH,
				ShapeData.MIN_SCALE, ShapeData.MAX_SCALE, shape.getScaleX(),
				value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setScaleX(value),
				value -> Component.translatable("fgmplus.studio.scale", "X", String.format(Locale.ROOT, "%.2f", value)),
				value -> persist("scaleX", value)));

		this.addRenderableWidget(slider(left, y - 4, HALF_WIDTH,
				ShapeData.MIN_SCALE, ShapeData.MAX_SCALE, shape.getScaleY(),
				value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setScaleY(value),
				value -> Component.translatable("fgmplus.studio.scale", "Y", String.format(Locale.ROOT, "%.2f", value)),
				value -> persist("scaleY", value)));

		this.addRenderableWidget(slider(left, y + 16, HALF_WIDTH,
				ShapeData.MIN_SCALE, ShapeData.MAX_SCALE, shape.getScaleZ(),
				value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setScaleZ(value),
				value -> Component.translatable("fgmplus.studio.scale", "Z", String.format(Locale.ROOT, "%.2f", value)),
				value -> persist("scaleZ", value)));

		//UI shows the inverted value so that dragging RIGHT protrudes (user's
		//expected direction): stored offsetZ is positive toward the torso back
		//(front = -z, user-measured), so the slider negates on both read and write
		WildfireSlider offsetZ = slider(right, y + 16, HALF_WIDTH,
				ShapeData.MIN_OFFSET, ShapeData.MAX_OFFSET, -shape.getOffsetZ(),
				value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setOffsetZ(-value),
				value -> Component.translatable("fgmplus.studio.pos", "Z", String.format(Locale.ROOT, "%+.2f", value)),
				value -> persist("offsetZ", -value));
		offsetZ.setTooltip(Tooltip.create(Component.translatable("fgmplus.studio.pos_z_tip")));
		this.addRenderableWidget(offsetZ);

		this.addRenderableWidget(slider(right, y - 24, HALF_WIDTH,
				ShapeData.MIN_OFFSET, ShapeData.MAX_OFFSET, shape.getOffsetX(),
				value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setOffsetX(value),
				value -> Component.translatable("fgmplus.studio.pos", "X", String.format(Locale.ROOT, "%+.2f", value)),
				value -> persist("offsetX", value)));

		this.addRenderableWidget(slider(right, y - 4, HALF_WIDTH,
				ShapeData.MIN_OFFSET, ShapeData.MAX_OFFSET, shape.getOffsetY(),
				value -> ((ShapeHolder) resolvePlayer()).fgmplus$getShape().setOffsetY(value),
				value -> Component.translatable("fgmplus.studio.pos", "Y", String.format(Locale.ROOT, "%+.2f", value)),
				value -> persist("offsetY", value)));

		this.addRenderableWidget(slider(left, y + 36, FULL_WIDTH,
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
					.position(left, y + 56).size(HALF_WIDTH, FGM_BUTTON_HEIGHT)
				.onPress(button -> {
					HurtSoundManager.openFolder();
					this.hurtSoundStatus = HurtSoundManager.getStatusText();
				})
				.build());
		this.addRenderableWidget(new WildfireButton.Builder()
					.message(() -> Component.translatable("fgmplus.studio.preview"))
					.position(right, y + 56).size(HALF_WIDTH, FGM_BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("fgmplus.studio.preview_tip")))
				.onPress(button -> {
					HurtSoundManager.reloadThenPlay();
					this.hurtSoundStatus = HurtSoundManager.getStatusText();
				})
				.build());

		this.addRenderableWidget(new WildfireButton.Builder()
					.message(() -> Component.translatable("fgmplus.studio.reset"))
					.position(left, y + 76).size(HALF_WIDTH, FGM_BUTTON_HEIGHT)
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
					.position(right, y + 76).size(HALF_WIDTH, FGM_BUTTON_HEIGHT)
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

	/**
	 * Styled after FGM 5's screens: darken the world with renderTransparentBackground
	 * (never Screen#renderBackground's blur — it throws after the first call per
	 * frame), panel + title on top. Vanilla Screen#render calls this exactly once.
	 */
	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		this.renderTransparentBackground(graphics);

		int x = this.width / 2;
		int y = this.height / 2;
		//Same translucent panel style as FGM's customization tabs, sized over the grid
		//(+1 on the bottom edge: fill's end is exclusive, and the wrapped status
		//text's second line reaches y+112)
		graphics.fill(x - 40, y - 32, x + 136, y + 113, PANEL_FILL);

		//Title centered at FGM's wardrobe title height
		graphics.drawString(this.font, this.title, x - this.font.width(this.title) / 2, y - 82, 0xFFFFFF, false);

		//Large live preview of the actual player in FGM's own frame; the model renders
		//through the same GenderLayer pipeline, so live slider edits show up here
		renderPlayerInFrame(graphics, x - 90, y + 44, mouseX, mouseY);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		//NO manual renderBackground here: vanilla Screen#render already calls it,
		//and the blur behind it may only run once per frame (crashed 1.5.0 first build)
		super.render(graphics, mouseX, mouseY, delta);

		int x = this.width / 2;
		int y = this.height / 2;
		//Status text (loaded sound files) below the 15px button rows; can exceed the
		//162 px panel (long file names), so wrap to at most two lines with a tail
		int ly = y + 94;
		for(String line : wrapStatus(this.hurtSoundStatus.getString(), this.font, FULL_WIDTH - 4)) {
			graphics.drawCenteredString(this.font, line, x + 48, ly, 0xE0E0E0);
			ly += 10;
		}
	}

	/** Greedy character-wrap to at most 2 lines of {@code maxWidth} px. */
	private static java.util.List<String> wrapStatus(String text, net.minecraft.client.gui.Font font, int maxWidth) {
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
		//(same children() sweep FGM 5's customization screen uses)
		for(var child : this.children()) {
			if(child instanceof WildfireSlider slider) {
				slider.save();
			}
		}
		return super.mouseReleased(event);
	}
}
