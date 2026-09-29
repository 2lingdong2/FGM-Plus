package io.github.e33epus.fgmplus.mixin;

import com.wildfire.main.entitydata.PlayerConfig;
import com.wildfire.main.networking.WildfireSync;
import io.github.e33epus.fgmplus.net.ShapeSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Piggybacks the shape payload onto FGM's S2C syncs so remote players' shapes
 * reach every client that FGM itself syncs to. Each send is individually gated
 * on ServerPlayNetworking#canSend, so peers without this mod are skipped.
 */
@Mixin(value = WildfireSync.class, remap = false)
public abstract class WildfireSyncServerMixin {

	@Inject(method = "sendToAllClients", at = @At("TAIL"), require = 0, remap = false)
	private static void fp$broadcast(ServerPlayer toSync, PlayerConfig playerConfig, CallbackInfo ci) {
		ShapeSyncPayload.broadcast(toSync, playerConfig);
	}

	@Inject(method = "sendToClient", at = @At("TAIL"), require = 0, remap = false)
	private static void fp$sendOne(ServerPlayer sendTo, PlayerConfig toSync, CallbackInfo ci) {
		ShapeSyncPayload.sendTo(sendTo, toSync);
	}
}
