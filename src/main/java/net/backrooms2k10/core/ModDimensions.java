package net.backrooms2k10.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ModDimensions:
 * Define el pool pre-registrado de dimensiones para Backrooms (nivel_0 hasta nivel_99).
 * Todas las dimensiones se registran desde la inicialización para evitar la creación dinámica
 * en caliente que provocaba errores de desincronización y 'Missing Dimension' en Immersive Portals.
 */
public class ModDimensions {
    public static final String NAMESPACE = "backrooms";
    public static final int POOL_SIZE = 100;

    // Dimensión tipo compartida por todos los niveles de Backrooms
    public static final ResourceKey<DimensionType> BACKROOMS_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            new ResourceLocation(NAMESPACE, "backrooms_type")
    );

    // Pool estático de ResourceKey<Level> para nivel_0 .. nivel_99
    public static final List<ResourceKey<Level>> LEVEL_KEYS;
    static {
        List<ResourceKey<Level>> list = new ArrayList<>(POOL_SIZE);
        for (int i = 0; i < POOL_SIZE; i++) {
            list.add(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(NAMESPACE, "nivel_" + i)));
        }
        LEVEL_KEYS = Collections.unmodifiableList(list);
    }

    public static final ResourceKey<Level> NIVEL_0 = LEVEL_KEYS.get(0);

    public static ResourceKey<Level> getLevelKey(int index) {
        if (index >= 0 && index < POOL_SIZE) {
            return LEVEL_KEYS.get(index);
        }
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(NAMESPACE, "nivel_" + index));
    }

    public static ResourceKey<Level> getLevelKey(String levelNameOrId) {
        String path = levelNameOrId.trim().toLowerCase();
        if (path.startsWith(NAMESPACE + ":")) {
            path = path.substring((NAMESPACE + ":").length());
        }
        if (!path.startsWith("nivel_")) {
            path = "nivel_" + path;
        }
        int index = getIndexFromPath(path);
        if (index >= 0 && index < POOL_SIZE) {
            return LEVEL_KEYS.get(index);
        }
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(NAMESPACE, path));
    }

    public static String getCanonicalId(int index) {
        return NAMESPACE + ":nivel_" + index;
    }

    public static int getIndexFromKey(ResourceKey<Level> key) {
        if (!key.location().getNamespace().equals(NAMESPACE)) return -1;
        return getIndexFromPath(key.location().getPath());
    }

    private static int getIndexFromPath(String path) {
        if (path.startsWith("nivel_")) {
            try {
                return Integer.parseInt(path.substring("nivel_".length()));
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    public static boolean isBackroomsDimension(ResourceKey<Level> key) {
        return key != null && NAMESPACE.equals(key.location().getNamespace());
    }
}
