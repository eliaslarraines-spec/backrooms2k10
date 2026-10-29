package net.backrooms2k10.persistence;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * SavedData central y transaccional del mod Backrooms.
 * Almacena el estado persistente del mundo actual en DimensionDataStorage.
 * Admite estados transaccionales (CREATING, READY, FAILED) para evitar que
 * dimensiones incompletas queden guardadas como válidas.
 */
public class BackroomsSavedData extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger("Backrooms2K10");
    private static final String DATA_NAME = "backrooms_universe_data";

    public enum LevelStatus {
        CREATING,
        READY,
        FAILED
    }

    public static class LevelMetadata {
        public String id;
        public LevelStatus status = LevelStatus.CREATING;
        public int requestedAreaCount = 0;

        public LevelMetadata(String id, LevelStatus status, int requestedAreaCount) {
            this.id = id;
            this.status = status;
            this.requestedAreaCount = requestedAreaCount;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id != null ? id : "");
            tag.putString("Status", status != null ? status.name() : LevelStatus.READY.name());
            tag.putInt("RequestedAreaCount", requestedAreaCount);
            return tag;
        }

        public static LevelMetadata load(CompoundTag tag) {
            String id = tag.getString("Id");
            LevelStatus status = LevelStatus.READY;
            try {
                if (tag.contains("Status")) {
                    status = LevelStatus.valueOf(tag.getString("Status"));
                }
            } catch (Exception ignored) {}
            int areaCount = tag.getInt("RequestedAreaCount");
            return new LevelMetadata(id, status, areaCount);
        }
    }

    // Mapa de metadatos de niveles
    private final Map<String, LevelMetadata> levelsMap = new LinkedHashMap<>();

    // Metadata de áreas por nivel: LevelId -> (AreaId -> AreaData)
    private final Map<String, Map<String, AreaData>> areasByLevel = new HashMap<>();

    // Puntos de retorno temporales de jugadores: UUID -> ReturnData
    private final Map<UUID, ReturnData> returnPoints = new HashMap<>();

    public static BackroomsSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                BackroomsSavedData::load,
                BackroomsSavedData::new,
                DATA_NAME
        );
    }

    public BackroomsSavedData() {
        // Constructor vacío para mundo nuevo
    }

    /**
     * Retorna la lista de IDs de niveles que han completado exitosamente su creación (READY).
     */
    public synchronized List<String> getRegisteredLevels() {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, LevelMetadata> entry : levelsMap.entrySet()) {
            if (entry.getValue().status == LevelStatus.READY) {
                list.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(list);
    }

    public synchronized boolean hasLevel(String levelId) {
        LevelMetadata meta = levelsMap.get(levelId);
        return meta != null && meta.status == LevelStatus.READY;
    }

    public synchronized boolean isLevelReady(String levelId) {
        LevelMetadata meta = levelsMap.get(levelId);
        return meta != null && meta.status == LevelStatus.READY;
    }

    public synchronized LevelMetadata getLevelMetadata(String levelId) {
        return levelsMap.get(levelId);
    }

    /**
     * Paso 1 de la transacción: Inicia la creación y la marca como CREATING.
     */
    public synchronized void startLevelCreation(String levelId, int areaCount) {
        levelsMap.put(levelId, new LevelMetadata(levelId, LevelStatus.CREATING, areaCount));
        areasByLevel.computeIfAbsent(levelId, k -> new LinkedHashMap<>());
        setDirty();
    }

    /**
     * Paso final de la transacción: Marca el nivel como READY una vez que todo el registro y áreas están completos.
     */
    public synchronized void finishLevelCreation(String levelId) {
        LevelMetadata meta = levelsMap.get(levelId);
        if (meta != null) {
            meta.status = LevelStatus.READY;
        } else {
            levelsMap.put(levelId, new LevelMetadata(levelId, LevelStatus.READY, getAreasForLevel(levelId).size()));
        }
        setDirty();
        LOGGER.info("[Backrooms] Nivel marcado como READY en SavedData: {}", levelId);
    }

    /**
     * Aborta la creación: si falla en cualquier etapa previa a READY, remueve o marca como FAILED.
     */
    public synchronized void failLevelCreation(String levelId) {
        LevelMetadata meta = levelsMap.get(levelId);
        if (meta != null) {
            meta.status = LevelStatus.FAILED;
        }
        areasByLevel.remove(levelId);
        levelsMap.remove(levelId);
        setDirty();
        LOGGER.warn("[Backrooms] Creación de nivel abortada y limpiada de SavedData: {}", levelId);
    }

    public synchronized Map<String, AreaData> getAreasForLevel(String levelId) {
        return areasByLevel.getOrDefault(levelId, Collections.emptyMap());
    }

    public synchronized AreaData getArea(String levelId, String areaId) {
        Map<String, AreaData> map = areasByLevel.get(levelId);
        return map != null ? map.get(areaId) : null;
    }

    public synchronized void putArea(String levelId, AreaData area) {
        areasByLevel.computeIfAbsent(levelId, k -> new LinkedHashMap<>()).put(area.id, area);
        setDirty();
    }

    // Retorno
    public synchronized void setReturnPoint(UUID playerUuid, ReturnData data) {
        returnPoints.put(playerUuid, data);
        setDirty();
    }

    public synchronized ReturnData consumeReturnPoint(UUID playerUuid) {
        ReturnData data = returnPoints.remove(playerUuid);
        if (data != null) {
            setDirty();
        }
        return data;
    }

    public synchronized ReturnData getReturnPoint(UUID playerUuid) {
        return returnPoints.get(playerUuid);
    }

    // Estructuras de datos
    public static class AreaData {
        public String id;
        public String levelId;
        public String biomeType;
        public BlockPos origin;
        public int minX, maxX;
        public int minZ, maxZ;
        public int width, depth;
        public int groundY = 0;
        public BlockPos northDoor;
        public BlockPos spawnPoint;
        public BlockPos exitPoint;
        public boolean generated;
        public long seed;
        public String transitionTargetBiome = "";

        public AreaData() {}

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id != null ? id : "");
            tag.putString("LevelId", levelId != null ? levelId : "");
            tag.putString("BiomeType", biomeType != null ? biomeType : "");
            tag.putLong("Origin", origin != null ? origin.asLong() : 0L);
            tag.putInt("GroundY", groundY);
            tag.putInt("MinX", minX);
            tag.putInt("MaxX", maxX);
            tag.putInt("MinZ", minZ);
            tag.putInt("MaxZ", maxZ);
            tag.putInt("Width", width);
            tag.putInt("Depth", depth);
            tag.putLong("NorthDoor", northDoor != null ? northDoor.asLong() : 0L);
            tag.putLong("SpawnPoint", spawnPoint != null ? spawnPoint.asLong() : 0L);
            tag.putLong("ExitPoint", exitPoint != null ? exitPoint.asLong() : 0L);
            tag.putBoolean("Generated", generated);
            tag.putLong("Seed", seed);
            tag.putString("TransitionTargetBiome", transitionTargetBiome != null ? transitionTargetBiome : "");
            return tag;
        }

        public static AreaData load(CompoundTag tag) {
            AreaData data = new AreaData();
            data.id = tag.getString("Id");
            data.levelId = tag.getString("LevelId");
            data.biomeType = tag.getString("BiomeType");
            data.origin = BlockPos.of(tag.getLong("Origin"));
            data.groundY = tag.contains("GroundY") ? tag.getInt("GroundY") : 0;
            data.minX = tag.getInt("MinX");
            data.maxX = tag.getInt("MaxX");
            data.minZ = tag.getInt("MinZ");
            data.maxZ = tag.getInt("MaxZ");
            data.width = tag.getInt("Width");
            data.depth = tag.getInt("Depth");
            data.northDoor = BlockPos.of(tag.getLong("NorthDoor"));
            data.spawnPoint = BlockPos.of(tag.getLong("SpawnPoint"));
            data.exitPoint = BlockPos.of(tag.getLong("ExitPoint"));
            data.generated = tag.getBoolean("Generated");
            data.seed = tag.getLong("Seed");
            data.transitionTargetBiome = tag.getString("TransitionTargetBiome");
            return data;
        }
    }

    public static class ReturnData {
        public ResourceKey<Level> dimension;
        public double x, y, z;
        public float yaw, pitch;

        public ReturnData(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dim", dimension.location().toString());
            tag.putDouble("X", x);
            tag.putDouble("Y", y);
            tag.putDouble("Z", z);
            tag.putFloat("Yaw", yaw);
            tag.putFloat("Pitch", pitch);
            return tag;
        }

        public static ReturnData load(CompoundTag tag) {
            ResourceLocation dimLoc = new ResourceLocation(tag.getString("Dim"));
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            return new ReturnData(
                    dimKey,
                    tag.getDouble("X"),
                    tag.getDouble("Y"),
                    tag.getDouble("Z"),
                    tag.getFloat("Yaw"),
                    tag.getFloat("Pitch")
            );
        }
    }

    public static BackroomsSavedData load(CompoundTag nbt) {
        BackroomsSavedData data = new BackroomsSavedData();

        if (nbt.contains("LevelMetadata", Tag.TAG_LIST)) {
            ListTag metaList = nbt.getList("LevelMetadata", Tag.TAG_COMPOUND);
            for (int i = 0; i < metaList.size(); i++) {
                LevelMetadata meta = LevelMetadata.load(metaList.getCompound(i));
                data.levelsMap.put(meta.id, meta);
            }
        } else if (nbt.contains("Levels", Tag.TAG_LIST)) {
            ListTag levelsList = nbt.getList("Levels", Tag.TAG_STRING);
            for (int i = 0; i < levelsList.size(); i++) {
                String id = levelsList.getString(i);
                data.levelsMap.put(id, new LevelMetadata(id, LevelStatus.READY, 0));
            }
        }

        CompoundTag areasTag = nbt.getCompound("Areas");
        for (String levelId : areasTag.getAllKeys()) {
            ListTag list = areasTag.getList(levelId, Tag.TAG_COMPOUND);
            Map<String, AreaData> map = new LinkedHashMap<>();
            for (int i = 0; i < list.size(); i++) {
                AreaData area = AreaData.load(list.getCompound(i));
                map.put(area.id, area);
            }
            data.areasByLevel.put(levelId, map);
        }

        CompoundTag returnsTag = nbt.getCompound("Returns");
        for (String uuidStr : returnsTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                data.returnPoints.put(uuid, ReturnData.load(returnsTag.getCompound(uuidStr)));
            } catch (Exception e) {
                LOGGER.warn("[Backrooms] Error al cargar ReturnData para {}", uuidStr, e);
            }
        }

        LOGGER.info("[Backrooms] Datos persistentes cargados: {} niveles, {} retornos.",
                data.getRegisteredLevels().size(), data.returnPoints.size());
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag) {
        ListTag metaList = new ListTag();
        for (LevelMetadata meta : levelsMap.values()) {
            metaList.add(meta.save());
        }
        compoundTag.put("LevelMetadata", metaList);

        // Lista legacy de strings para compatibilidad
        ListTag levelsList = new ListTag();
        for (String levelId : getRegisteredLevels()) {
            levelsList.add(net.minecraft.nbt.StringTag.valueOf(levelId));
        }
        compoundTag.put("Levels", levelsList);

        CompoundTag areasTag = new CompoundTag();
        for (Map.Entry<String, Map<String, AreaData>> entry : areasByLevel.entrySet()) {
            ListTag list = new ListTag();
            for (AreaData area : entry.getValue().values()) {
                list.add(area.save());
            }
            areasTag.put(entry.getKey(), list);
        }
        compoundTag.put("Areas", areasTag);

        CompoundTag returnsTag = new CompoundTag();
        for (Map.Entry<UUID, ReturnData> entry : returnPoints.entrySet()) {
            returnsTag.put(entry.getKey().toString(), entry.getValue().save());
        }
        compoundTag.put("Returns", returnsTag);

        LOGGER.info("[Backrooms] Datos persistentes guardados: {} niveles READY.", getRegisteredLevels().size());
        return compoundTag;
    }
}
