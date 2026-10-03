package io.github.e33epus.fgmplus.Config;


import io.github.e33epus.fgmplus.FgmPlusMod;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

public class FgmPlusConfig
{
    public static ForgeConfigSpec SPEC;
    public static ForgeConfigSpec.DoubleValue bustSizeMax;

    public static ForgeConfigSpec.DoubleValue bustOffsetYMin;
    public static ForgeConfigSpec.DoubleValue bustOffsetZMin;
    public static ForgeConfigSpec.DoubleValue bustOffsetZMax;

    public static ForgeConfigSpec.DoubleValue bounceMultiplierMax;
    public static ForgeConfigSpec.DoubleValue floppyMultiplierMax;

    //bustScaleGain/bustWidthScaleGain/bustScaleMax retired in 1.1.0:
    //per-player shape data (ShapeData) replaces the global real-scale knobs
    static
    {
        ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
        BUILDER.push("FGM Plus Config");
        bustSizeMax = BUILDER
                //4.0 tops FGM's x125 percent display at 500% — the mod's headline bound
                .defineInRange("bustSizeMax",4.0,0.8,4.0);
        bustOffsetYMin = BUILDER
                .defineInRange("Breast Height Min",-1.5,-4.0,-1.0);
        bustOffsetZMin = BUILDER
                .defineInRange("Breast Depth Min",-1.5,-4.0,-1.0);
        bustOffsetZMax = BUILDER
                .defineInRange("Breast Depth Max",0.0,0.0,4.0);

        bounceMultiplierMax = BUILDER
                .defineInRange("Bounce Multiplier Max",1.0,0.5,3.0);
        floppyMultiplierMax = BUILDER
                .defineInRange("Floppy Multiplier Max",1.5,1.0,3.0);

        SPEC = BUILDER.build();
    }

    public static void setup()
    {
        //config lives at config/fgmplus/fgmplus.toml since 1.6.1 (was fgmplus-common.toml
        //in the config root); carry the old file over so slider-widening settings survive
        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            Path target = configDir.resolve(FgmPlusMod.MODID).resolve(FgmPlusMod.MODID + ".toml");
            Files.createDirectories(target.getParent());
            Path legacy = configDir.resolve("fgmplus-common.toml");
            if (Files.isRegularFile(legacy) && !Files.exists(target)) {
                Files.move(legacy, target);
            }
        } catch (Exception e) {
            FgmPlusMod.LOGGER.warn("FGM Plus: could not migrate the old config file", e);
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, FgmPlusMod.MODID + "/" + FgmPlusMod.MODID + ".toml");
    }

}