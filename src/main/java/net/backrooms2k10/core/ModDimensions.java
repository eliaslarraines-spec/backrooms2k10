package net.backrooms2k10.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * ModDimensions:
 * Define de forma limpia la clave de la dimensión única: backrooms:nivel_0.
 * La dimensión está registrada estáticamente mediante data pack / Forge para
 * garantizar sincronización cliente-servidor sin IDs dinámicos.
 */
public class ModDimensions {
    public static final String NAMESPACE = "backrooms";
    public static final String LEVEL_0_NAME = "nivel_0";

    // Tipo de dimensión Backrooms
    public static final ResourceKey<DimensionType> BACKROOMS_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            new ResourceLocation(NAMESPACE, "backrooms_type")
    );

    // Clave de la dimensión única: backrooms:nivel_0
    public static final ResourceKey<Level> NIVEL_0 = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(NAMESPACE, LEVEL_0_NAME)
    );

    /**
     * Verifica si una dimensión corresponde a la dimensión Level 0 de Backrooms.
     */
    public static boolean isBackroomsDimension(ResourceKey<Level> key) {
        return key != null && NIVEL_0.equals(key);
    }
}
