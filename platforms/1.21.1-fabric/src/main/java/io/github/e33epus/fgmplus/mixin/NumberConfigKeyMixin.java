package io.github.e33epus.fgmplus.mixin;

import io.github.e33epus.fgmplus.config.FgmPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code NumberConfigKey#validate} compares the incoming value against the RAW
 * minInclusive/maxInclusive fields, not the getters (same shape as the 3.2.2
 * target) — so the widened bounds that FloatConfigKeyMixin feeds to the slider
 * UI (via getMinInclusive/getMaxInclusive) never reach the live update path:
 * dragging the breast-size slider to the widened maximum shows 125% but
 * updateBustSize silently fails validation and the value snaps back to the last
 * accepted one. This accepts any value inside our widened window up front;
 * values outside it fall through to the stock bounds check unchanged.
 */
@Mixin(value = com.wildfire.main.config.NumberConfigKey.class, remap = false)
public abstract class NumberConfigKeyMixin {

	//Erasure-qualified: the synthetic bridge validate(Object) must not match.
	//require = 1: defaultRequire is 0 globally, and a silent skip here would bring
	//back the "slider won't save" defect with nothing in the log
	@Inject(method = "validate(Ljava/lang/Number;)Z", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
	private void fgmplus$acceptWidenedValues(Number value, CallbackInfoReturnable<Boolean> cir) {
		if(value == null) {
			return; //the stock null check reports this
		}
		String key = ((ConfigKeyAccessor) (Object) this).fgmplus$getKey();
		float min = FgmPlusConfig.widenedMin(key);
		float max = FgmPlusConfig.widenedMax(key);
		if(Float.isNaN(min) && Float.isNaN(max)) {
			return; //not one of our widened keys
		}
		float f = value.floatValue();
		if((Float.isNaN(min) || f >= min) && (Float.isNaN(max) || f <= max)) {
			cir.setReturnValue(true);
		}
		//outside the widened window: fall through so the stock bounds still reject it
	}
}
