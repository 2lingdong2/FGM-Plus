package io.github.e33epus.fgmplus.mixin;

import com.wildfire.gui.WildfireButton;
import com.wildfire.gui.screen.WardrobeBrowserScreen;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.entitydata.PlayerConfig;
import io.github.e33epus.fgmplus.gui.ShapeStudioScreen;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the Shape Studio entry button to the FGM 3.2.1 wardrobe browser.
 *
 * <p>3.2.1 wardrobe layout (verified in the jar bytecode): the button column sits
 * at x = width/2 - 42 (158x20 buttons) — gender cycle at y-52, appearance settings
 * at y-32 (female only), character settings at y-(12|32), so the entry follows
 * 28px below the column's last button. The 3.2.1 wardrobe textures (248x156 blit,
 * measured: right-column interior ends at texture row 83 female / 63 male) leave
 * the region below the panel a HOLLOW area with no right or bottom border, so the
 * extension draws the interior fill only — any self-drawn border is a ghost frame
 * the texture never had (the "vanilla-gray overflow" screenshot root cause).</p>
 *
 * <p>The mixin extends Screen so the protected addRenderableWidget of the
 * target's hierarchy can be called from the injection callback. "init" and
 * "renderBackground" are vanilla overrides: both the mojmap name and the
 * intermediary name (verified in the 3.2.1 prod jar: method_25426 /
 * method_25420) are listed, since a remap=false mixin carries no refmap.</p>
 */
@Mixin(value = WardrobeBrowserScreen.class, remap = false)
public abstract class WardrobeBrowserScreenMixin extends Screen {

	protected WardrobeBrowserScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = {"init", "method_25426"}, at = @At("TAIL"), require = 1, remap = false)
	private void fp$addShapeStudioButton(CallbackInfo ci) {
		UUID uuid = ((BaseWildfireScreenAccessor) this).fgmplus$getPlayerUUID();
		PlayerConfig plr = WildfireGender.getPlayerById(uuid);
		boolean female = plr != null && plr.getGender().canHaveBreasts();
		int y = this.height / 2;
		int yPos = female ? y + 16 : y - 4;
		this.addRenderableWidget(new WildfireButton(this.width / 2 - 42, yPos, 158, 20,
				Component.translatable("fgmplus.studio.entry"),
				button -> Minecraft.getInstance().setScreen(new ShapeStudioScreen(
						Component.translatable("fgmplus.studio.title"), this, uuid))));
	}

	@Inject(method = {"renderBackground", "method_25420"}, at = @At("TAIL"), require = 1, remap = false)
	private void fp$drawPanelExtension(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		//The 3.2.1 wardrobe textures end the right-hand panel's interior right after
		//FGM's own buttons (measured: interior through texture row 83 female / 63 male)
		//and the region below is HOLLOW — no right or bottom border exists there, so
		//the extension draws the interior fill only. Any self-drawn border/band fill
		//is a ghost frame the texture never had (PLAN P0-2: the "vanilla-gray
		//overflow" screenshot root cause). Drawn after the background blit so it is
		//not dimmed by the vignette gradient; the texture's transparent pixels let
		//it show through.
		UUID uuid = ((BaseWildfireScreenAccessor) this).fgmplus$getPlayerUUID();
		PlayerConfig plr = WildfireGender.getPlayerById(uuid);
		boolean female = plr != null && plr.getGender().canHaveBreasts();
		int y = this.height / 2;
		int left = (this.width - 248) / 2;
		int top = (this.height - 134) / 2;
		int buttonTop = female ? y + 16 : y - 4;
		int bandTop = buttonTop + 23; //3px below the button's bottom edge
		//first texture row below the measured interior end (83 female / 63 male)
		int fillTop = female ? 84 : 64;
		graphics.fill(left + 83, top + fillTop, left + 240, bandTop, 0xFF0A0A0A);
	}
}
