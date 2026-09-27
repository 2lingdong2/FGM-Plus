package io.github.e33epus.fgmplus.mixin;

import com.wildfire.main.config.types.FloatConfigKey;
import io.github.e33epus.fgmplus.config.FgmPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Widens FGM's slider bounds at the single source every consumer shares:
 * FloatConfigKey#getMinInclusive/getMaxInclusive. This is the load-bearing half of
 * the old ClientConfigurationMixin trick — redefining FGM's static final keys is
 * NOT enough on FGM 5, because Configuration.KEYS captures the original key
 * objects inside its own <clinit> and the load path (PlayerConfig#loadFromConfig
 * -> FloatConfigKey#read) clamps every persisted value through THOSE objects'
 * original bounds, silently resetting widened values on every relog. Intercepting
 * the getters instead covers the load clamp, the validate gate, and every
 * .range(key) slider in one place.
 *
 * <p>Key names are matched so unrelated FloatConfigKeys pass through untouched;
 * the bounds themselves come from FgmPlusConfig (loaded from disk before any FGM
 * class initializes, see FgmPlusConfig's static block).</p>
 */
@Mixin(value = FloatConfigKey.class, remap = false)
public abstract class FloatConfigKeyMixin {

	@Inject(method = "getMinInclusive", at = @At("RETURN"), cancellable = true, require = 1, remap = false)
	private void fgmplus$widenMin(CallbackInfoReturnable<Float> cir) {
		float widened = FgmPlusConfig.widenedMin(((FloatConfigKey) (Object) this).getKey());
		if(!Float.isNaN(widened)) {
			cir.setReturnValue(widened);
		}
	}

	@Inject(method = "getMaxInclusive", at = @At("RETURN"), cancellable = true, require = 1, remap = false)
	private void fgmplus$widenMax(CallbackInfoReturnable<Float> cir) {
		float widened = FgmPlusConfig.widenedMax(((FloatConfigKey) (Object) this).getKey());
		if(!Float.isNaN(widened)) {
			cir.setReturnValue(widened);
		}
	}
}
