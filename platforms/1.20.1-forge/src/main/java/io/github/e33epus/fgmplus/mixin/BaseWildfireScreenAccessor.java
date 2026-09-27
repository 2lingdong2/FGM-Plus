package io.github.e33epus.fgmplus.mixin;

import com.wildfire.gui.screen.BaseWildfireScreen;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes BaseWildfireScreen's protected fields to the GUI package so the
 * Shape Studio entry button can pass the displayed player's UUID through.
 * The field belongs to the FGM mod itself and is never obfuscated.
 */
@Mixin(value = BaseWildfireScreen.class, remap = false)
public interface BaseWildfireScreenAccessor {

    @Accessor(value = "playerUUID", remap = false)
    UUID fgmplus$getPlayerUUID();
}
