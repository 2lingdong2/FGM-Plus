package io.github.e33epus.fgmplus;

import io.github.e33epus.fgmplus.net.ShapeSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;

@Environment(EnvType.CLIENT)
public class FgmPlusClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		//the import folder must exist for manual ogg drops, even if the studio's
		//open-folder button is never clicked
		io.github.e33epus.fgmplus.sound.HurtSoundManager.ensureSoundDir();

		//Seed the sound-file fingerprint before the game's initial resource load
		//scans the same directory (HurtSoundManager preview fast path)
		io.github.e33epus.fgmplus.sound.HurtSoundManager.seedFingerprint();

		ClientPlayConnectionEvents.INIT.register((handler, client) ->
				ClientPlayNetworking.registerReceiver(ShapeSyncPayload.ID, (payload, context) -> {
					// the server never sends our own shape back to us, but guard anyway
					LocalPlayer self = context.player();
					if(self != null && payload.uuid().equals(self.getUUID())) return;
					ShapeSyncPayload.apply(payload.uuid(), payload.shape());
				}));
	}
}
