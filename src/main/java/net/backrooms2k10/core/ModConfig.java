package net.backrooms2k10.core;

import net.minecraftforge.common.ForgeConfigSpec;

public class ModConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MIN_INTERIOR_SIZE;
    public static final ForgeConfigSpec.IntValue MAX_INTERIOR_SIZE;
    public static final ForgeConfigSpec.IntValue COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_IMMERSIVE_PORTALS_COMPAT;

    static {
        BUILDER.push("PortalSettings");

        MIN_INTERIOR_SIZE = BUILDER
                .comment("Tamaño interior mínimo de un portal (anchura/altura)")
                .defineInRange("minInteriorSize", 1, 1, 10);

        MAX_INTERIOR_SIZE = BUILDER
                .comment("Tamaño interior máximo de un portal (anchura/altura)")
                .defineInRange("maxInteriorSize", 20, 2, 50);

        COOLDOWN_TICKS = BUILDER
                .comment("Tiempo de cooldown en ticks entre teletransportes para evitar loops (20 ticks = 1s)")
                .defineInRange("cooldownTicks", 60, 10, 600);

        DEBUG_LOGGING = BUILDER
                .comment("Activar logging detallado en consola de eventos de portal y teletransporte")
                .define("debugLogging", true);

        ENABLE_IMMERSIVE_PORTALS_COMPAT = BUILDER
                .comment("Activar integración con Immersive Portals si se encuentra presente")
                .define("enableImmersivePortalsCompat", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
