package io.github.e33epus.fgmplus.mixin;

import com.wildfire.gui.WildfireButton;
import com.wildfire.gui.screen.WardrobeBrowserScreen;
import com.wildfire.main.GenderPlayer;
import com.wildfire.main.WildfireGender;
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
 * Adds the Shape Studio entry button to the FGM wardrobe browser.
 *
 * The mixin extends Screen so the protected addRenderableWidget of the
 * target's hierarchy can be called from the injection callback.
 */
@Mixin(value = WardrobeBrowserScreen.class, remap = false)
public abstract class WardrobeBrowserScreenMixin extends Screen {

    protected WardrobeBrowserScreenMixin(Component title) {
        super(title);
    }

    //init is overridden from vanilla Screen: named "init" under dev mappings
    //and "m_7856_" in the released FGM jar (verified via javap), so try both
    @Inject(method = {"init", "m_7856_"}, at = @At("TAIL"), require = 1, remap = false)
    private void fp$addShapeStudioButton(CallbackInfo ci) {
        //'this' is the merged WardrobeBrowserScreen instance at runtime, so the
        //protected addRenderableWidget is reachable through the Screen superclass.
        //Stack the button directly below the wardrobe's own right-column flow
        //(158px buttons, 20px pitch): female shows two buttons (last at y-12), male
        //shows one (last at y-32), so the entry follows at y+8 / y-12 respectively
        UUID uuid = ((BaseWildfireScreenAccessor) this).fgmplus$getPlayerUUID();
        GenderPlayer plr = WildfireGender.getPlayerById(uuid);
        int y = this.height / 2;
        int yPos = plr != null && plr.getGender().canHaveBreasts() ? y + 8 : y - 12;
        this.addRenderableWidget(new WildfireButton(this.width / 2 - 42, yPos, 158, 20,
                Component.translatable("fgmplus.studio.entry"),
                button -> Minecraft.getInstance().setScreen(new ShapeStudioScreen(
                        Component.translatable("fgmplus.studio.title"), this, uuid))));
    }

    //renderBackground is overridden from vanilla Screen: "renderBackground" under dev
    //mappings, "m_280273_" in the released FGM jar (verified against the 1.20.1 SRG
    //mapping file and the prod jar's bytecode)
    @Inject(method = {"renderBackground", "m_280273_"}, at = @At("TAIL"), require = 1, remap = false)
    private void fp$drawPanelExtension(GuiGraphics graphics, CallbackInfo ci) {
        //The 3.1 wardrobe textures end the right-hand panel right after FGM's own
        //buttons (measured: bg2 border bottom at texture y82, bg3 at y60), so the
        //entry button would hang in transparent space. Draw a matching panel
        //extension (same measured colors: interior 10,10,10; border 107,107,107)
        //after the background blit so it is not dimmed by the vignette gradient;
        //the texture's transparent pixels let it show through.
        UUID uuid = ((BaseWildfireScreenAccessor) this).fgmplus$getPlayerUUID();
        GenderPlayer plr = WildfireGender.getPlayerById(uuid);
        boolean female = plr != null && plr.getGender().canHaveBreasts();
        int y = this.height / 2;
        int left = (this.width - 248) / 2;
        int top = (this.height - 134) / 2;
        int buttonTop = female ? y + 8 : y - 12;
        int fillBottom = buttonTop + 23; //border band ends 5px below the button
        //the texture's own panel border ends at y75 (bg2, female) / y56 (bg3, male);
        //continue from there so the extension reads as one seamless panel
        int fillTop = female ? 75 : 56;
        //side borders and interior, same column layout as the texture
        graphics.fill(left + 77, top + fillTop, left + 82, fillBottom + 2, 0xFF6B6B6B);
        graphics.fill(left + 83, top + fillTop, left + 240, fillBottom, 0xFF0A0A0A);
        graphics.fill(left + 240, top + fillTop, left + 247, fillBottom + 2, 0xFF6B6B6B);
        graphics.fill(left + 77, fillBottom, left + 247, fillBottom + 2, 0xFF6B6B6B);
    }
}
