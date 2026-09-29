package io.github.e33epus.fgmplus.mixin;

import com.wildfire.main.config.FloatConfigKey;
import io.github.e33epus.fgmplus.config.FgmPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Widens FGM's slider bounds at the single source every consumer shares:
 * FloatConfigKey#getMinInclusive/getMaxInclusive (both present in 3.2.2, both
 * just reading the raw fields). Hooking the getters covers the UI sliders AND
 * the persisted-value load clamp — FloatConfigKey#read clamps through
 * Mth.clamp(getMinInclusive(), value, getMaxInclusive()) (verified in the 3.2.2
 * jar bytecode), so widened values survive relogs.
 *
 * <p>Key names are matched so unrelated FloatConfigKeys pass through untouched;
 * the bounds themselves come from FgmPlusConfig (loaded from disk before any FGM
 * class initializes, see FgmPlusConfig's static block).</p>
 */
@Mixin(value = FloatConfigKey.class, remap = false)
public abstract class FloatConfigKeyMixin {

	@Inject(method = "getMinInclusive", at = @At("RETURN"), cancellable = true, require = 1, remap = false)
	private void fgmplus$widenMin(CallbackInfoReturnable<Float> cir) {
		float widened = FgmPlusConfig.widenedMin(((ConfigKeyAccessor) (Object) this).fgmplus$getKey());
		if(!Float.isNaN(widened)) {
			cir.setReturnValue(widened);
		}
	}

	@Inject(method = "getMaxInclusive", at = @At("RETURN"), cancellable = true, require = 1, remap = false)
	private void fgmplus$widenMax(CallbackInfoReturnable<Float> cir) {
		float widened = FgmPlusConfig.widenedMax(((ConfigKeyAccessor) (Object) this).fgmplus$getKey());
		if(!Float.isNaN(widened)) {
			cir.setReturnValue(widened);
		}
	}
}
