package io.github.e33epus.fgmplus.mixin;

import net.minecraft.client.model.AgeableListModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes AgeableListModel's private baby-transform finals so the GenderLayerMixin
 * capture can reconstruct the body-space view for baby entities exactly as FGM
 * does (FGM reads the same fields through its own access widener; the fields are
 * private final floats on AgeableListModel in 1.21.1 — verified via javap).
 */
@Mixin(AgeableListModel.class)
public interface AgeableListModelAccessor {

	@Accessor("babyBodyScale")
	float fgmplus$babyBodyScale();

	@Accessor("bodyYOffset")
	float fgmplus$bodyYOffset();
}
