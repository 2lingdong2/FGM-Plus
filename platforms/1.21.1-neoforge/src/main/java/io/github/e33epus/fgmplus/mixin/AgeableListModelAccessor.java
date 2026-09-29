package io.github.e33epus.fgmplus.mixin;

import net.minecraft.client.model.AgeableListModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes AgeableListModel's private baby-transform constants so the capture
 * window can replay FGM's own baby branch (scale(1/babyBodyScale) +
 * translate(0, bodyYOffset/16, 0) — verified in the 3.2.2 bytecode, which reads
 * the same two fields via FGM's own accesstransformer.cfg). On 1.21.1 both are
 * private final and have no accessors, and rather than widening vanilla fields
 * from our own jar the same way, this duck-typed accessor keeps the access
 * mixin-local (same pattern as BaseWildfireScreenAccessor/ConfigKeyAccessor).
 * Runtime names are mojmap on both the dev runtime and production NeoForge, so
 * the literal names need no refmap.
 */
@Mixin(AgeableListModel.class)
public interface AgeableListModelAccessor {

    @Accessor(value = "babyBodyScale", remap = false)
    float fgmplus$babyBodyScale();

    @Accessor(value = "bodyYOffset", remap = false)
    float fgmplus$bodyYOffset();
}
