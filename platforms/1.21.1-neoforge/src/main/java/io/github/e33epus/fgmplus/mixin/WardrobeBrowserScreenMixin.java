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
 * Adds the Shape Studio entry button to the FGM wardrobe browser.
 *
 * <p>The mixin extends Screen so the protected addRenderableWidget of the
 * target's hierarchy can be called from the injection callback. NeoForge 1.21.1
 * runs mojmap names in production too, so the vanilla-method overrides init /
 * renderBackground match by their literal names (no SRG twin needed, unlike the
 * 1.20.1 Forge port).</p>
 */
@Mixin(value = WardrobeBrowserScreen.class, remap = false)
public abstract class WardrobeBrowserScreenMixin extends Screen {

    protected WardrobeBrowserScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 1, remap = false)
    private void fp$addShapeStudioButton(CallbackInfo ci) {
        //'this' is the merged WardrobeBrowserScreen instance at runtime, so the
        //protected addRenderableWidget is reachable through the Screen superclass.
        //Stack the button in the hollow interior below the wardrobe's own
        //right-column flow (158x20 buttons; PLAN P0-2 unified the rows with the
        //fabric port: the old y+8/y-12 sat on the texture's separator band)
        UUID uuid = ((BaseWildfireScreenAccessor) this).fgmplus$getPlayerUUID();
        PlayerConfig plr = WildfireGender.getPlayerById(uuid);
        int y = this.height / 2;
        //20px pitch continuation of FGM's own right-column flow (the button stack
        //is identical across FGM 3.1/3.2.x per bytecode): female column ends at
        //y-12, male at y-32, so the entry follows at y+8 / y-12
        int yPos = plr != null && plr.getGender().canHaveBreasts() ? y + 8 : y - 12;
        this.addRenderableWidget(new WildfireButton(this.width / 2 - 42, yPos, 158, 20,
                Component.translatable("fgmplus.studio.entry"),
                button -> Minecraft.getInstance().setScreen(new ShapeStudioScreen(
                        Component.translatable("fgmplus.studio.title"), this, uuid))));
    }

    //1.21.1 signature (GuiGraphics, int, int, float) — verified against the 3.2.2
    //jar's own renderBackground override
    @Inject(method = "renderBackground", at = @At("TAIL"), require = 1, remap = false)
    private void fp$drawPanelExtension(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        //The wardrobe textures end the right-hand panel's interior right after FGM's
        //own buttons (pixel-verified: interior through texture row 74 female / 54
        //male, below it opaque black), so the entry button would hang off the
        //panel's face. Draw the forge-accepted panel extension (same measured
        //colors as the texture: interior 10,10,10; border 107,107,107) after the
        //background blit.
        UUID uuid = ((BaseWildfireScreenAccessor) this).fgmplus$getPlayerUUID();
        PlayerConfig plr = WildfireGender.getPlayerById(uuid);
        boolean female = plr != null && plr.getGender().canHaveBreasts();
        int y = this.height / 2;
        int left = (this.width - 248) / 2;
        int top = (this.height - 134) / 2;
        int buttonTop = female ? y + 8 : y - 12;
        int fillBottom = buttonTop + 23; //border band ends 5px below the button
        //the texture's own panel border ends at y75 (bg2, female) / y56 (bg3, male);
        //continue from there so the extension reads as one seamless panel. Pixel
        //decode of the shared texture (md5-identical across FGM 3.1/3.2.2 blit
        //region) verified these colors hit the texture's own border/interior values
        int fillTop = female ? 75 : 56;
        graphics.fill(left + 77, top + fillTop, left + 82, fillBottom + 2, 0xFF6B6B6B);
        graphics.fill(left + 83, top + fillTop, left + 240, fillBottom, 0xFF0A0A0A);
        graphics.fill(left + 240, top + fillTop, left + 247, fillBottom + 2, 0xFF6B6B6B);
        graphics.fill(left + 77, fillBottom, left + 247, fillBottom + 2, 0xFF6B6B6B);
    }
}
