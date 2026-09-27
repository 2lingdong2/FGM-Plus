package io.github.e33epus.fgmplus.mixin;

import com.wildfire.main.WildfireEventHandler;
import com.wildfire.main.config.enums.Gender;
import io.github.e33epus.fgmplus.sound.HurtSoundManager;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the per-gender hurt sound lookup inside FGM's
 * WildfireEventHandler#onEntityHurt (FGM's own client hurt-sound event) so that
 * when the user provides custom oggs in config/fgmplus/sounds/ (and the
 * sounds.json entry map is live), FGM's female hurt sound is swapped for
 * fgmplus:custom_hurt. Everything else in FGM's handler (the hasHurtSounds
 * gate, the gender check, volume/pitch, the pitch variation) is left untouched.
 */
@Mixin(value = WildfireEventHandler.class, remap = false)
public abstract class WildfireEventHandlerMixin {

	@Redirect(method = "onEntityHurt", require = 1, remap = false,
			at = @At(value = "INVOKE",
					target = "Lcom/wildfire/main/config/enums/Gender;getHurtSound()Lnet/minecraft/sounds/SoundEvent;",
					remap = false))
	private static SoundEvent fp$redirectHurtSound(Gender gender) {
		SoundEvent original = gender.getHurtSound();
		// null (non-FEMALE/OTHER gender) must stay null so FGM's own null-check keeps working
		return HurtSoundManager.resolveHurtSound(original);
	}
}
