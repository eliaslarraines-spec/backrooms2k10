package net.backrooms2k10.persistence;

import com.google.common.collect.ImmutableList;
import net.backrooms2k10.biome.BiomeManager;
import net.backrooms2k10.core.ModDimensions;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * DimensionManager:
 * Arquitectura de Dimensiones basada en Pool Estático (nivel_0 a nivel_99).
 *
 * Principios:
 * 1. Las 100 dimensiones están pre-registradas en datapacks y cargadas en la inicialización del servidor.
 * 2. Immersive Portals reconoce y sincroniza todas las dimensiones durante el handshake inicial de conexión.
 * 3. '/backrooms create <cantidad>' NUNCA registra una dimensión dinámica ni modifica registros en caliente.
 * 4. '/backrooms create' solo activa el siguiente nivel secuencial libre en BackroomsSavedData y genera físicamente sus áreas.
 * 5. Se elimina por completo el error 'Missing Dimension' y cualquier expulsión del jugador.
 */
public class DimensionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Backrooms2K10");
    public static final String NAMESPACE = ModDimensions.NAMESPACE;

    public static ResourceKey<Level> getLevelKey(String levelNameOrId) {
        return ModDimensions.getLevelKey(levelNameOrId);
    }

    public static ResourceKey<Level> getLevelKey(int index) {
        return ModDimensions.getLevelKey(index);
    }

    public static String getCanonicalId(int index) {
        return ModDimensions.getCanonicalId(index);
    }

    /**
     * Encuentra el índice numérico del siguiente nivel secuencial libre (0..99).
     * Nunca salta niveles ni reutiliza niveles existentes.
     */
    public static int getNextAvailableLevelIndex(MinecraftServer server) {
        BackroomsSavedData data = BackroomsSavedData.get(server.overworld());
        List<String> registered = data.getRegisteredLevels();
        for (int i = 0; i < ModDimensions.POOL_SIZE; i++) {
            if (!registered.contains(getCanonicalId(i))) {
                return i;
            }
        }
        return -1;
    }

    public static String getNextAvailableLevelId(MinecraftServer server) {
        int index = getNextAvailableLevelIndex(server);
        if (index < 0) return null;
        return getCanonicalId(index);
    }

    public static boolean levelExists(MinecraftServer server, String levelNameOrId) {
        ResourceKey<Level> key = getLevelKey(levelNameOrId);
        BackroomsSavedData data = BackroomsSavedData.get(server.overworld());
        return data.isLevelReady(key.location().toString());
    }

    @Nullable
    public static ServerLevel getOrLoadDimension(MinecraftServer server, String levelNameOrId) {
        ResourceKey<Level> key = getLevelKey(levelNameOrId);
        ServerLevel existing = server.getLevel(key);
        if (existing != null) {
            return existing;
        }

        // Si por alguna razón la dimensión del pool no fue cargada automáticamente por el servidor
        LevelStem stem = buildBackroomsLevelStem(server);
        return ensureServerLevelInstantiated(server, key, stem);
    }

    /**
     * Ejecución de '/backrooms create <cantidad>':
     * 1. Buscar el siguiente nivel secuencial libre en el pool (nivel_0 a nivel_99).
     * 2. Obtener su ServerLevel ya existente de forma segura.
     * 3. Iniciar registro en SavedData (CREATING).
     * 4. Generar físicamente las áreas solicitadas en el mundo plano.
     * 5. Guardar posiciones, dimensiones, límites y tipos de biomas.
     * 6. Marcar el nivel como READY en SavedData.
     *
     * NO crea dimensiones dinámicas en caliente.
     * NO invoca APIs experimentales ni modifica registros.
     * NO desconecta ni expulsa jugadores.
     */
    public static ServerLevel createNextLevel(MinecraftServer server, int areaCount) {
        int nextIndex = getNextAvailableLevelIndex(server);
        if (nextIndex < 0) {
            LOGGER.error("[Backrooms] LEVEL CREATION FAILED: Pool de niveles agotado (máximo {} niveles).", ModDimensions.POOL_SIZE);
            return null;
        }

        String levelId = getCanonicalId(nextIndex);
        ResourceKey<Level> key = getLevelKey(nextIndex);

        BackroomsSavedData data = BackroomsSavedData.get(server.overworld());
        data.startLevelCreation(levelId, areaCount);

        LOGGER.info("[Backrooms] Creating level: {}", levelId);

        ServerLevel level = getOrLoadDimension(server, levelId);
        if (level == null) {
            data.failLevelCreation(levelId);
            LOGGER.error("[Backrooms] LEVEL CREATION FAILED: No se pudo obtener ServerLevel para {}", levelId);
            return null;
        }

        LOGGER.info("[Backrooms] ServerLevel ready for level: {}", levelId);
        LOGGER.info("[Backrooms] Preparing {} biome areas", areaCount);

        // Generar físicamente las áreas/biomas solicitados
        BiomeManager.prepareAreasForLevel(level, levelId, areaCount);

        int preparedCount = data.getAreasForLevel(levelId).size();
        if (preparedCount < areaCount) {
            data.failLevelCreation(levelId);
            LOGGER.error("[Backrooms] LEVEL CREATION FAILED: Error al preparar áreas solicitadas ({}/{}) para {}",
                    preparedCount, areaCount, levelId);
            return null;
        }

        // Finalizar activación del nivel
        data.finishLevelCreation(levelId);
        LOGGER.info("[Backrooms] Level READY: {}", levelId);

        return level;
    }

    /**
     * Asegura que todas las dimensiones del pool (0..99) estén cargadas en server.levels
     * durante la inicialización del servidor antes de que los jugadores se conecten.
     */
    public static void initializeDimensionPoolOnServerStart(MinecraftServer server) {
        LOGGER.info("[Backrooms] Inicializando pool de dimensiones Backrooms (0 a {})...", ModDimensions.POOL_SIZE - 1);
        LevelStem sharedStem = null;

        for (int i = 0; i < ModDimensions.POOL_SIZE; i++) {
            ResourceKey<Level> key = ModDimensions.getLevelKey(i);
            if (server.getLevel(key) == null) {
                if (sharedStem == null) {
                    sharedStem = buildBackroomsLevelStem(server);
                }
                ensureServerLevelInstantiated(server, key, sharedStem);
            }
        }
        LOGGER.info("[Backrooms] Pool de dimensiones verificado y listo en el servidor.");
    }

    /**
     * Construye la configuración canónica de LevelStem para dimensiones Backrooms:
     * Mundo plano: Y=0 Bedrock, Y=1 Smooth Sandstone (suelo amarillo), Bioma Backrooms.
     */
    public static LevelStem buildBackroomsLevelStem(MinecraftServer server) {
        RegistryAccess registryAccess = server.registryAccess();
        Holder<DimensionType> dimTypeHolder = null;

        var dimTypeReg = registryAccess.registry(Registries.DIMENSION_TYPE);
        if (dimTypeReg.isPresent()) {
            var opt = dimTypeReg.get().getHolder(ModDimensions.BACKROOMS_TYPE);
            if (opt.isPresent()) {
                dimTypeHolder = opt.get();
            } else {
                dimTypeHolder = dimTypeReg.get().getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD);
            }
        }

        Holder<Biome> backroomsBiome = null;
        var biomeReg = registryAccess.registry(Registries.BIOME);
        if (biomeReg.isPresent()) {
            var opt = biomeReg.get().getHolder(ResourceKey.create(Registries.BIOME, new ResourceLocation(NAMESPACE, "backrooms_biome")));
            if (opt.isPresent()) {
                backroomsBiome = opt.get();
            }
        }
        if (backroomsBiome == null) {
            backroomsBiome = registryAccess.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
        }

        FlatLevelGeneratorSettings flatSettings = new FlatLevelGeneratorSettings(
                Optional.empty(),
                backroomsBiome,
                List.of()
        );
        flatSettings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
        flatSettings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.SMOOTH_SANDSTONE));
        flatSettings.updateLayers();

        FlatLevelSource chunkGenerator = new FlatLevelSource(flatSettings);
        return new LevelStem(dimTypeHolder, chunkGenerator);
    }

    @SuppressWarnings("unchecked")
    private static ServerLevel ensureServerLevelInstantiated(MinecraftServer server, ResourceKey<Level> levelKey, LevelStem levelStem) {
        try {
            LevelStorageSource.LevelStorageAccess storageAccess = null;
            try {
                storageAccess = ObfuscationReflectionHelper.getPrivateValue(MinecraftServer.class, server, "f_129744_");
            } catch (Throwable t) {
                for (Field f : MinecraftServer.class.getDeclaredFields()) {
                    if (LevelStorageSource.LevelStorageAccess.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        storageAccess = (LevelStorageSource.LevelStorageAccess) f.get(server);
                        break;
                    }
                }
            }

            if (storageAccess == null) return null;

            ServerLevelData serverLevelData = server.getWorldData().overworldData();
            DerivedLevelData derivedLevelData = new DerivedLevelData(server.getWorldData(), serverLevelData);

            ChunkProgressListener dummyListener = new ChunkProgressListener() {
                @Override public void updateSpawnPos(ChunkPos center) {}
                @Override public void onStatusChange(ChunkPos chunkPosition, @Nullable ChunkStatus status) {}
                @Override public void start() {}
                @Override public void stop() {}
            };

            Executor executor = (Runnable r) -> server.execute(r);
            long biomeZoomSeed = 0L;
            boolean isDebug = server.getWorldData().isDebugWorld();

            net.minecraft.world.RandomSequences randomSequences = null;
            try {
                randomSequences = server.overworld().getRandomSequences();
            } catch (Throwable ignored) {}

            ServerLevel newLevel = new ServerLevel(
                    server,
                    executor,
                    storageAccess,
                    derivedLevelData,
                    levelKey,
                    levelStem,
                    dummyListener,
                    isDebug,
                    biomeZoomSeed,
                    ImmutableList.of(),
                    true,
                    randomSequences
            );

            Map<ResourceKey<Level>, ServerLevel> levelsMap = null;
            try {
                levelsMap = ObfuscationReflectionHelper.getPrivateValue(MinecraftServer.class, server, "f_129762_");
            } catch (Throwable t) {
                for (Field f : MinecraftServer.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object obj = f.get(server);
                        if (obj instanceof Map<?, ?> map && !map.isEmpty()) {
                            Object firstKey = map.keySet().iterator().next();
                            if (firstKey instanceof ResourceKey<?>) {
                                levelsMap = (Map<ResourceKey<Level>, ServerLevel>) obj;
                                break;
                            }
                        }
                    }
                }
            }

            if (levelsMap != null) {
                levelsMap.put(levelKey, newLevel);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.LevelEvent.Load(newLevel));
                return newLevel;
            }
        } catch (Throwable t) {
            LOGGER.error("[Backrooms] Error al instanciar ServerLevel para {}: ", levelKey.location(), t);
        }
        return null;
    }
}
