package io.github.e33epus.fgmplus.mixin;


import io.github.e33epus.fgmplus.Config.FgmPlusConfig;
import com.wildfire.main.config.ClientConfiguration;
import com.wildfire.main.config.FloatConfigKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientConfiguration.class)
public abstract class ClientConfigurationMixin {

    @Final @Shadow public static final FloatConfigKey BUST_SIZE = new FloatConfigKey("bust_size", 0.6F, 0.0F, FgmPlusConfig.bustSizeMax.get().floatValue());

    @Final @Shadow public static final FloatConfigKey BREASTS_OFFSET_Y = new FloatConfigKey("breasts_yOffset", 0.0F, FgmPlusConfig.bustOffsetYMin.get().floatValue(), 1.0F);
    @Final @Shadow public static final FloatConfigKey BREASTS_OFFSET_Z = new FloatConfigKey("breasts_zOffset", 0.0F, FgmPlusConfig.bustOffsetZMin.get().floatValue(), FgmPlusConfig.bustOffsetZMax.get().floatValue());

    @Final @Shadow public static final FloatConfigKey BOUNCE_MULTIPLIER = new FloatConfigKey("bounce_multiplier", 0.333F, 0.0F, FgmPlusConfig.bounceMultiplierMax.get().floatValue());
    @Final @Shadow public static final FloatConfigKey FLOPPY_MULTIPLIER = new FloatConfigKey("floppy_multiplier", 0.75F, 0.25F, FgmPlusConfig.floppyMultiplierMax.get().floatValue());
}