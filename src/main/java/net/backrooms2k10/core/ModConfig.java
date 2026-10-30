package net.backrooms2k10.core;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * ModConfig:
 * Configuración general y de compatibilidad para Backrooms 2K10.
 */
public class ModConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_IMMERSIVE_PORTALS_COMPAT;

    static {
        BUILDER.push("GeneralSettings");

        ENABLE_IMMERSIVE_PORTALS_COMPAT = BUILDER
                .comment("Habilita la verificación y futura compatibilidad con Immersive Portals")
                .define("enableImmersivePortalsCompat", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
