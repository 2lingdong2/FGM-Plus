package io.github.e33epus.fgmplus.net;

import com.wildfire.main.entitydata.PlayerConfig;
import com.wildfire.main.WildfireGender;
import io.github.e33epus.fgmplus.FgmPlusMod;
import io.github.e33epus.fgmplus.shape.ShapeData;
import io.github.e33epus.fgmplus.shape.ShapeHolder;
import io.netty.buffer.ByteBuf;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Standalone shape-sync channel ({@code fgmplus:shape_sync}), used in both directions.
 *
 * <p>FGM 5 moved to StreamCodec-based payloads, so the 1.20.1 trick of appending a byte
 * tail to FGM's own packets no longer applies. Instead this payload is piggybacked next
 * to FGM's sync points via mixins: the client sends it whenever FGM's C2S sync fires,
 * and the server broadcasts it next to FGM's S2C syncs. Peers without this mod never
 * receive it (each receiver is gated on ServerPlayNetworking#canSend), so mixed-version
 * setups degrade to default shapes instead of desyncing.</p>
 */
public record ShapeSyncPayload(UUID uuid, ShapeData shape) implements CustomPacketPayload {

	public static final Type<ShapeSyncPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(FgmPlusMod.MODID, "shape_sync"));

	public static final StreamCodec<ByteBuf, ShapeData> SHAPE_CODEC = StreamCodec.of(
			(buf, shape) -> shape.write(buf),
			ShapeData::read
	);

	public static final StreamCodec<ByteBuf, ShapeSyncPayload> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, ShapeSyncPayload::uuid,
			SHAPE_CODEC, ShapeSyncPayload::shape,
			ShapeSyncPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}

	/** Applies a synced shape to the cached config; shared by the server and client receivers. */
	public static void apply(UUID uuid, ShapeData shape) {
		PlayerConfig plr = WildfireGender.getOrAddPlayerById(uuid);
		if(plr != null) {
			((ShapeHolder) plr).fgmplus$setShape(shape == null ? new ShapeData() : shape);
		}
	}

	/** Sends the local player's shape to the server; mirrors FGM's C2S sync cadence. */
	@Environment(EnvType.CLIENT)
	public static void sendToServer(PlayerConfig plr) {
		if(!ClientPlayNetworking.canSend(ID)) return;
		ClientPlayNetworking.send(new ShapeSyncPayload(plr.uuid, ((ShapeHolder) plr).fgmplus$getShape().copy()));
	}

	/** Server: sends one player's shape to a single client, when that client accepts the payload. */
	public static void sendTo(ServerPlayer sendTo, PlayerConfig toSync) {
		if(!ServerPlayNetworking.canSend(sendTo, ID)) return;
		ServerPlayNetworking.send(sendTo, new ShapeSyncPayload(toSync.uuid, ((ShapeHolder) toSync).fgmplus$getShape().copy()));
	}

	/** Server: sends one player's shape to everyone currently tracking them. */
	public static void broadcast(ServerPlayer toSync, PlayerConfig playerConfig) {
		PlayerLookup.tracking(toSync).stream()
				.filter(player -> !player.equals(toSync))
				.forEach(player -> sendTo(player, playerConfig));
	}
}
