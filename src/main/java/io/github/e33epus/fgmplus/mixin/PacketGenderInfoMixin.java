package io.github.e33epus.fgmplus.mixin;

import com.wildfire.main.GenderPlayer;
import com.wildfire.main.networking.PacketGenderInfo;
import io.github.e33epus.fgmplus.shape.ShapeData;
import io.github.e33epus.fgmplus.shape.ShapeHolder;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends the per-player shape (7 floats, 28 bytes since 1.2.0) after FGM's own packet body.
 * FGM has no dedicated decode method: both concrete packets (PacketSendGenderInfo,
 * PacketSync) decode through the protected PacketGenderInfo(FriendlyByteBuf)
 * constructor and their own constructors only call super(buffer), so a single TAIL
 * hook on the base constructor covers C2S and S2C. Receivers without this mod never
 * read the trailing bytes; receivers with it skip the tail when fewer than 16 bytes
 * remain, so mixed-version traffic keeps default shapes instead of desyncing.
 */
@Mixin(value = PacketGenderInfo.class, remap = false)
public abstract class PacketGenderInfoMixin {

    //Minimum v1 tail size; v2 tails are 28 bytes. Shorter than this means the sender
    //carries no shape payload at all (FGM-only peer) - degrade to defaults
    @Unique private static final int fp$MIN_TAIL_BYTES = 16;

    @Unique private volatile ShapeData fp$shape;

    @Inject(method = "<init>(Lcom/wildfire/main/GenderPlayer;)V", at = @At("TAIL"), require = 1, remap = false)
    private void fp$captureShape(GenderPlayer plr, CallbackInfo ci) {
        fp$shape = ((ShapeHolder) plr).fgmplus$getShape().copy();
    }

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("TAIL"), require = 1, remap = false)
    private void fp$readShape(FriendlyByteBuf buffer, CallbackInfo ci) {
        fp$shape = new ShapeData();
        if (buffer.readableBytes() < fp$MIN_TAIL_BYTES) {
            return;
        }
        buffer.markReaderIndex();
        try {
            fp$shape = ShapeData.read(buffer);
        } catch (Exception e) {
            //short or malformed tail: rewind so FGM's own fields stay intact and degrade to defaults
            buffer.resetReaderIndex();
            fp$shape = new ShapeData();
        }
    }

    @Inject(method = "encode", at = @At("TAIL"), require = 1, remap = false)
    private void fp$writeShape(FriendlyByteBuf buffer, CallbackInfo ci) {
        ShapeData shape = fp$shape == null ? new ShapeData() : fp$shape;
        shape.write(buffer);
    }

    @Inject(method = "updatePlayerFromPacket", at = @At("TAIL"), require = 1, remap = false)
    private void fp$applyShape(GenderPlayer plr, CallbackInfo ci) {
        if (fp$shape != null) {
            ((ShapeHolder) plr).fgmplus$setShape(fp$shape);
        }
    }
}
