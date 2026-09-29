package io.github.e33epus.fgmplus.mixin;

import com.wildfire.main.config.ConfigKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes ConfigKey's protected key name so the widening mixins can match FGM's
 * persisted keys without class-init-order coupling to Configuration.
 * The field belongs to the FGM mod itself and is never obfuscated.
 */
@Mixin(value = ConfigKey.class, remap = false)
public interface ConfigKeyAccessor {

	@Accessor(value = "key", remap = false)
	String fgmplus$getKey();
}
