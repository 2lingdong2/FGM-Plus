package io.github.e33epus.fgmplus;

import com.mojang.logging.LogUtils;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.entitydata.PlayerConfig;
import io.github.e33epus.fgmplus.config.FgmPlusConfig;
import io.github.e33epus.fgmplus.net.ShapeSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;

/**
 * Common entrypoint: registers the shape-sync payload on both sides and the dedicated-server
 * receiver. The shape is applied to FGM's cached PlayerConfig and immediately rebroadcast to
 * everyone tracking the sender, mirroring how FGM itself rebroadcasts its C2S sync.
 *
 * <p>Target: FGM 3.2.1+1.21 — PlayerConfig/WildfireGender have the same shapes here as on the
 * FGM 5 (1.21.11) target, so the server side ports unchanged.</p>
 */
public class FgmPlusMod implements ModInitializer {

	public static final String MODID = "fgmplus";
	public static final Logger LOGGER = LogUtils.getLogger();

	@Override
	public void onInitialize() {
		FgmPlusConfig.setup();

		// each payload type must be registered on both directions regardless of side
		PayloadTypeRegistry.playC2S().register(ShapeSyncPayload.ID, ShapeSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ShapeSyncPayload.ID, ShapeSyncPayload.CODEC);

		ServerPlayConnectionEvents.INIT.register((handler, server) ->
				ServerPlayNetworking.registerReceiver(handler, ShapeSyncPayload.ID, (payload, context) -> {
					//Trust the connection, not the payload: FGM's own C2S handler ignores the
					//embedded uuid the same way, so a modified client cannot spoof other players
					ShapeSyncPayload.apply(context.player().getUUID(), payload.shape());
					PlayerConfig plr = WildfireGender.getOrAddPlayerById(context.player().getUUID());
					if(plr != null) {
						ShapeSyncPayload.broadcast(context.player(), plr);
					}
				}));
	}
}
