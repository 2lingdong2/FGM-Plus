package io.github.e33epus.fgmplus.mixin;

import com.wildfire.main.entitydata.EntityConfig;
import com.wildfire.main.entitydata.PlayerConfig;
import io.github.e33epus.fgmplus.shape.ShapeData;
import io.github.e33epus.fgmplus.shape.ShapeHolder;
import io.github.e33epus.fgmplus.shape.ShapeStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Adds a per-player shape payload to FGM's PlayerConfig and applies the persisted
 * shape right after FGM loads the cached config from disk.
 */
@Mixin(value = PlayerConfig.class, remap = false)
public abstract class PlayerConfigMixin implements ShapeHolder {

	//volatile: written from FGM's async loader thread, read from the render/main thread
	@Unique private volatile ShapeData fp$shape;

	@Override
	public ShapeData fgmplus$getShape() {
		//mixin field initializers never run, so lazily create the default here
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
	 * markForSync is only true for the local player, so remote players never pull
	 * shape data from this machine's disk. FGM 3.2.1 loads through the static
	 * loadCachedPlayer (same entry point the 3.1 target used).
	 */
	//RETURN (not TAIL): loadCachedPlayer has multiple return points and the last one is
	//'return null', so a TAIL hook would only fire on the failure path
	@Inject(method = "loadCachedPlayer", at = @At("RETURN"), require = 1, remap = false)
	private static void fp$loadShape(UUID uuid, boolean markForSync, CallbackInfoReturnable<PlayerConfig> cir) {
		PlayerConfig plr = cir.getReturnValue();
		if(plr == null) {
			return;
		}
		//EntityConfig.uuid is a public final field; the cast reaches it without @Shadow
		var uuid2 = ((EntityConfig) plr).uuid;
		ShapeHolder holder = (ShapeHolder) plr;
		ShapeData loaded = ShapeStore.load(uuid2);
		if(markForSync) {
			//local player: disk is the source of truth
			holder.fgmplus$setShape(loaded);
		} else if(!loaded.isDefault() && holder.fgmplus$getShape().isDefault()) {
			//remote player: only fill in, never clobber a shape that came from a sync packet
			holder.fgmplus$setShape(loaded);
		}
	}
}
