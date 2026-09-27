package io.github.e33epus.fgmplus.mixin;

import com.wildfire.main.entitydata.PlayerConfig;
import com.wildfire.main.networking.WildfireSync;
import io.github.e33epus.fgmplus.net.ShapeSyncPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Piggybacks the shape payload onto FGM's C2S sync: whenever FGM actually
 * consumed needsSync and sent its own packet, the shape goes along. Gated on
 * ClientPlayNetworking#canSend inside sendToServer, so FGM-only servers simply
 * never receive it.
 */
@Mixin(value = WildfireSync.class, remap = false)
public abstract class WildfireSyncClientMixin {

	@Unique private static boolean fp$wasNeedsSync;

	@Inject(method = "sendToServer", at = @At("HEAD"), require = 0, remap = false)
	private static void fp$capture(PlayerConfig plr, CallbackInfo ci) {
		fp$wasNeedsSync = plr.needsSync;
	}

	@Inject(method = "sendToServer", at = @At("TAIL"), require = 0, remap = false)
	private static void fp$sendShape(PlayerConfig plr, CallbackInfo ci) {
		//FGM resets needsSync after a real send; both true means the early return
		//path fired (nothing went out), in which case we stay quiet as well
		if(fp$wasNeedsSync && !plr.needsSync) {
			ShapeSyncPayload.sendToServer(plr);
		}
	}
}
