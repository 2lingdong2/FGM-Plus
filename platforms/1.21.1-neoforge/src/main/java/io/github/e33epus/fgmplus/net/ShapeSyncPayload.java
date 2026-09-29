package io.github.e33epus.fgmplus.net;

import com.wildfire.main.WildfireGender;
import com.wildfire.main.entitydata.PlayerConfig;
import io.github.e33epus.fgmplus.FgmPlusMod;
import io.github.e33epus.fgmplus.shape.ShapeData;
import io.github.e33epus.fgmplus.shape.ShapeHolder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Standalone shape-sync channel ({@code fgmplus:shape_sync}), used in both directions.
 *
 * <p>FGM 3.2.2 moved to StreamCodec-based CustomPacketPayloads, so the 1.20.1 trick of
 * appending a byte tail to FGM's own packets no longer applies. Instead this payload is
 * piggybacked next to FGM's sync points via mixins: the client sends it whenever FGM
 * actually consumed needsSync and sent its own ServerboundSyncPacket
 * (WildfireSyncClientMixin), and the server sends it next to FGM's StartTracking sync
 * (WildfireSyncServerMixin). Every send is gated on hasChannel, so peers without this
 * mod are skipped and mixed-version setups degrade to default shapes instead of
 * desyncing.</p>
 */
public record ShapeSyncPayload(UUID uuid, ShapeData shape) implements CustomPacketPayload {

	public static final Type<ShapeSyncPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(FgmPlusMod.MODID, "shape_sync"));

	public static final StreamCodec<ByteBuf, ShapeSyncPayload> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, ShapeSyncPayload::uuid,
			StreamCodec.of(
					(buf, shape) -> shape.write(buf),
					ShapeData::read),
			ShapeSyncPayload::shape,
			ShapeSyncPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** Applies a synced shape to the cached config; shared by the server and client receivers. */
	public static void apply(UUID uuid, ShapeData shape) {
		PlayerConfig plr = WildfireGender.getOrAddPlayerById(uuid);
		if(plr != null) {
			((ShapeHolder) plr).fgmplus$setShape(shape == null ? new ShapeData() : shape);
		}
	}

	/**
	 * Single receiver for both directions (one playBidirectional registration —
	 * NeoForge 21.1 rejects a second register() for the same Type). Server side
	 * trusts the connection, not the payload: only a payload claiming the sender's
	 * own uuid is accepted, and the accepted shape is rebroadcast to the tracking
	 * players immediately — mirroring FGM's own ServerboundSyncPacket#handle
	 * (validated sender uuid, then WildfireGender.getTrackers -> sendToPlayer), so
	 * nearby players see the update without waiting for a re-StartTracking.
	 */
	public static void handle(ShapeSyncPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if(context.player() instanceof ServerPlayer sender) {
				if(!sender.getUUID().equals(payload.uuid())) {
					FgmPlusMod.LOGGER.warn("FGM Plus dropped a shape payload claiming uuid {} from {}",
							payload.uuid(), sender.getGameProfile().getName());
					return;
				}
				apply(payload.uuid(), payload.shape());
				PlayerConfig updated = WildfireGender.getOrAddPlayerById(payload.uuid());
				if(updated != null) {
					for(ServerPlayer tracker : WildfireGender.getTrackers(sender)) {
						sendTo(tracker, updated);
					}
				}
			} else {
				//client side: the server vouched for the payload
				apply(payload.uuid(), payload.shape());
			}
		});
	}

	/** Sends the local player's shape to the server; mirrors FGM's C2S sync cadence. */
	public static void sendToServer(PlayerConfig plr) {
		PacketDistributor.sendToServer(new ShapeSyncPayload(plr.uuid, ((ShapeHolder) plr).fgmplus$getShape().copy()));
	}

	/** Server: sends one player's shape to a single client that has the channel. */
	public static void sendTo(ServerPlayer sendTo, PlayerConfig toSync) {
		if(!sendTo.connection.hasChannel(TYPE)) return;
		PacketDistributor.sendToPlayer(sendTo, new ShapeSyncPayload(toSync.uuid, ((ShapeHolder) toSync).fgmplus$getShape().copy()));
	}
}
