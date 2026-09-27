package io.github.e33epus.fgmplus.mixin;


import com.wildfire.main.GenderPlayer;
import io.github.e33epus.fgmplus.sound.HurtSoundManager;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the per-gender hurt sound lookup inside FGM's
 * WildfireEventHandler#onPlaySound (PlayLevelSoundEvent.AtEntity, LOWEST) so
 * that when the user provides custom oggs in config/fgmplus/sounds/ (and the
 * sounds.json entry map is live), FGM's female hurt sound is swapped for
 * fgmplus:custom_hurt. Everything else in FGM's handler (the
 * hasHurtSounds gate, the FEMALE-only gender check, volume/pitch passthrough,
 * the hurtTime windowing and the local-sound replay) is left untouched.
 *
 * Call site verified in the FGM 3.1 dependency jar: WildfireEventHandler.
 * onPlaySound bytecode offset 137, "invokevirtual
 * GenderPlayer$Gender.getHurtSound:()Lnet/minecraft/sounds/SoundEvent;".
 */
@Mixin(com.wildfire.main.WildfireEventHandler.class)
public abstract class WildfireEventHandlerMixin {

    @Redirect(method = "onPlaySound", require = 1, remap = false,
        at = @At(value = "INVOKE",
            target = "Lcom/wildfire/main/GenderPlayer$Gender;getHurtSound()Lnet/minecraft/sounds/SoundEvent;",
            remap = false))
    private SoundEvent fp$redirectHurtSound(GenderPlayer.Gender gender) {
        SoundEvent original = gender.getHurtSound();
        // null (non-FEMALE gender) must stay null so FGM's own null-check keeps working
        return HurtSoundManager.resolveHurtSound(original);
    }
}
