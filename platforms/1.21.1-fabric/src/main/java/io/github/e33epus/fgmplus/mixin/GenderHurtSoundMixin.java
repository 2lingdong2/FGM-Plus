package io.github.e33epus.fgmplus.mixin;

import com.wildfire.main.Gender;
import io.github.e33epus.fgmplus.sound.HurtSoundManager;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps FGM's female hurt sound for fgmplus:custom_hurt when the user provides
 * custom oggs in config/fgmplus/sounds/ (and the sounds.json entry map is live).
 *
 * <p>3.2.1 deviation from the other targets: WildfireEventHandler has no
 * onEntityHurt/onPlaySound any more — the only {@code Gender#getHurtSound} call
 * site is FGM's own LivingEntityMixin hurt handler (redirecting INSIDE another
 * mod's mixin handler would pin its handler name, so the seam moved one level
 * down to the enum method's return value). Everything upstream of the sound
 * choice (the hasHurtSounds gate, the FEMALE gender check, volume/pitch, the
 * pitch variation) is untouched: null (non-FEMALE) passes through unchanged so
 * FGM's own null-check keeps working, and with no custom sounds present this
 * returns the original event, making the swap a no-op.</p>
 */
@Mixin(value = Gender.class, remap = false)
public abstract class GenderHurtSoundMixin {

	@Inject(method = "getHurtSound", at = @At("RETURN"), cancellable = true, require = 1, remap = false)
	private void fgmplus$swapHurtSound(CallbackInfoReturnable<SoundEvent> cir) {
		SoundEvent swapped = HurtSoundManager.resolveHurtSound(cir.getReturnValue());
		if(swapped != cir.getReturnValue()) {
			cir.setReturnValue(swapped);
		}
	}
}
