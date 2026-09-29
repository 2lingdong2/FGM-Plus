package io.github.e33epus.fgmplus;

import com.mojang.logging.LogUtils;
import io.github.e33epus.fgmplus.config.FgmPlusConfig;
import io.github.e33epus.fgmplus.net.ShapeSyncPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/**
 * Client-only entrypoint. The shape-sync payload is registered here on the mod
 * event bus, mirroring how FGM 3.2.2 registers its own packets
 * (WildfireHelper#registerMessages: RegisterPayloadHandlersEvent -> optional()
 * registrar). One playBidirectional registration covers both directions: on
 * NeoForge 21.1 a second register() call for the same Type throws
 * UnsupportedOperationException ("already registered" — hit in the first dev
 * smoke), so playToClient + playToServer on the same Type is not an option.
 */
@Mod(value = FgmPlusMod.MODID, dist = Dist.CLIENT)
public class FgmPlusMod {

    public static final String MODID = "fgmplus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FgmPlusMod(IEventBus modEventBus) {
        modEventBus.addListener(this::onRegisterPayloadHandlers);
        FgmPlusConfig.setup();
    }

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MODID).optional();
        registrar.playBidirectional(ShapeSyncPayload.TYPE, ShapeSyncPayload.STREAM_CODEC, ShapeSyncPayload::handle);
    }
}
