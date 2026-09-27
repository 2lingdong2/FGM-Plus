package io.github.e33epus.fgmplus.mixin;

import com.wildfire.main.entitydata.EntityConfig;
import com.wildfire.render.GenderRenderState;
import io.github.e33epus.fgmplus.shape.ShapeData;
import io.github.e33epus.fgmplus.shape.ShapeHolder;
import io.github.e33epus.fgmplus.shape.ShapeStateHolder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bridges the per-player shape from the config object onto FGM's per-frame
 * render state snapshot, so the render mixin can read it without touching any
 * entity references (the 1.21.9+ render state no longer carries the entity).
 */
@Mixin(value = GenderRenderState.class, remap = false)
public abstract class GenderRenderStateMixin implements ShapeStateHolder {

	@Unique private volatile ShapeData fp$shape;

	@Override
	public ShapeData fgmplus$getShape() {
		if(fp$shape == null) {
			fp$shape = new ShapeData();
		}
		return fp$shape;
	}

	@Override
	public void fgmplus$setShape(ShapeData shape) {
		fp$shape = shape == null ? new ShapeData() : shape;
	}

	/**
	 * Re-derives the config object after GenderRenderState.update finished; when
	 * the entity was unsupported the state lookup yields null and nothing happens.
	 */
	@Inject(method = "update", at = @At("TAIL"), require = 1, remap = false)
	private static void fp$bridge(LivingEntity entity, EntityRenderState state, CallbackInfo ci) {
		GenderRenderState genderState = GenderRenderState.get(state);
		if(genderState == null || !(EntityConfig.getEntity(entity) instanceof ShapeHolder holder)) {
			return;
		}
		((ShapeStateHolder) genderState).fgmplus$setShape(holder.fgmplus$getShape());
	}
}
