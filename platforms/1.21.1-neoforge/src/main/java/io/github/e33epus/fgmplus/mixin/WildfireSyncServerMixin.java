package io.github.e33epus.fgmplus.mixin;

import com.wildfire.main.WildfireGender;
import com.wildfire.main.entitydata.PlayerConfig;
import io.github.e33epus.fgmplus.net.ShapeSyncPayload;
import io.github.e33epus.fgmplus.shape.ShapeHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Piggybacks the shape payload onto FGM's S2C syncs so remote players' shapes
 * reach every client that FGM itself syncs to. StartTracking is the only
 * ClientboundSyncPacket send site in 3.2.2 (verified in the jar bytecode); our
 * hook mirrors its exact gating — target must be a player, the tracker must be
 * a ServerPlayer, and that client must have negotiated our channel — so peers
 * without this mod are skipped instead of erroring.
 */
@Mixin(value = WildfireGender.class, remap = false)
public abstract class WildfireSyncServerMixin {

	@Inject(method = "onStartTracking", at = @At("TAIL"), require = 0, remap = false)
	private void fp$sendShape(PlayerEvent.StartTracking event, CallbackInfo ci) {
		if(!(event.getEntity() instanceof ServerPlayer tracker)) {
			return;
		}
		if(!(event.getTarget() instanceof Player tracked)) {
			return;
		}
		if(!tracker.connection.hasChannel(ShapeSyncPayload.TYPE)) {
			return;
		}
		PlayerConfig config = WildfireGender.getPlayerById(tracked.getUUID());
		if(config == null) {
			return;
		}
		ShapeSyncPayload.sendTo(tracker, config);
	}
}
