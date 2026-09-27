package io.github.e33epus.fgmplus;


import io.github.e33epus.fgmplus.Config.FgmPlusConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;


@Mod(FgmPlusMod.MODID)
public class FgmPlusMod
{
    public static final String MODID = "fgmplus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FgmPlusMod() {

        MinecraftForge.EVENT_BUS.register(this);
        FgmPlusConfig.setup();
    }


}
