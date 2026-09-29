package io.github.e33epus.fgmplus.mixin;

import com.wildfire.main.WildfireGender;
import com.wildfire.main.entitydata.PlayerConfig;
import com.wildfire.main.WildfireEventHandler;
import io.github.e33epus.fgmplus.net.ShapeSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Piggybacks the shape payload onto FGM's C2S sync: FGM's onGUI tick handler is
 * the only ServerboundSyncPacket send site in 3.2.2 (verified in the jar
 * bytecode) — it gates on the negotiated channel and the needsSync flag, then
 * clears the flag right after PacketDistributor.sendToServer. Whenever that
 * transition (true -> false) actually happened, the shape goes along, sent
 * through our own channel which is only present when the server also runs
 * fgmplus (hasChannel-gated inside sendToServer's caller side too).
 */
@Mixin(value = WildfireEventHandler.class, remap = false)
public abstract class WildfireSyncClientMixin {

	@Unique private static boolean fp$wasNeedsSync;

	@Inject(method = "onGUI", at = @At("HEAD"), require = 0, remap = false)
	private void fp$capture(ClientTickEvent.Post event, CallbackInfo ci) {
		fp$wasNeedsSync = false;
		LocalPlayer player = Minecraft.getInstance().player;
		if(player != null) {
			PlayerConfig plr = WildfireGender.getPlayerById(player.getUUID());
			if(plr != null) {
				fp$wasNeedsSync = plr.needsSync;
			}
		}
	}

	@Inject(method = "onGUI", at = @At("TAIL"), require = 0, remap = false)
	private void fp$sendShape(ClientTickEvent.Post event, CallbackInfo ci) {
		if(!fp$wasNeedsSync) {
			return;
		}
		//FGM resets needsSync after a real send; still true means the early return
		//path fired (nothing went out), in which case we stay quiet as well
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if(player == null || mc.getConnection() == null) {
			return;
		}
		PlayerConfig plr = WildfireGender.getPlayerById(player.getUUID());
		if(plr == null || plr.needsSync) {
			return;
		}
		//Our own channel must be negotiated with the server, otherwise the shape
		//payload has nowhere to go (FGM-only server)
		if(!mc.getConnection().hasChannel(ShapeSyncPayload.TYPE)) {
			return;
		}
		ShapeSyncPayload.sendToServer(plr);
	}
}
