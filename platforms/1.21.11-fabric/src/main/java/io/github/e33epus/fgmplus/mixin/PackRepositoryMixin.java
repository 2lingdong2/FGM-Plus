package io.github.e33epus.fgmplus.mixin;

import io.github.e33epus.fgmplus.FgmPlusMod;
import io.github.e33epus.fgmplus.sound.HurtSoundManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/**
 * Injects the always-on dynamic hurt-sound pack into the client pack repository
 * after every reload and force-selects it, replacing the Forge 1.20.1
 * AddPackFinders + required=true mechanism (Fabric has no pack-finder event).
 * require=0: if upstream reshapes PackRepository the feature degrades to FGM's
 * default hurt sounds instead of crashing.
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {

	@Shadow @Final private Map<String, Pack> available;
	@Shadow @Final private List<Pack> selected;

	@Inject(method = "reload", at = @At("TAIL"), require = 0)
	private void fgmplus$injectHurtSoundPack(CallbackInfo ci) {
		if(FabricLoader.getInstance().getEnvironmentType() != net.fabricmc.api.EnvType.CLIENT) {
			return;
		}
		try {
			Pack pack = this.available.get(HurtSoundManager.PACK_ID);
			if(pack == null) {
				pack = HurtSoundManager.createHurtSoundPack();
				this.available.put(HurtSoundManager.PACK_ID, pack);
			}
			//'selected' holds Pack instances in 1.21.11; re-adding the same id is idempotent
			if(!this.selected.contains(pack)) {
				this.selected.add(pack);
			}
		} catch(Exception e) {
			FgmPlusMod.LOGGER.warn("FGM Plus: failed to inject the custom hurt sound pack", e);
		}
	}
}
