package net.backrooms2k10.portal;

import net.backrooms2k10.biome.BiomeManager;
import net.backrooms2k10.core.ModDimensions;
import net.backrooms2k10.generator.ProceduralGenerator;
import net.backrooms2k10.persistence.BackroomsSavedData;
import net.backrooms2k10.persistence.DimensionManager;
import net.backrooms2k10.persistence.ReturnManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PortalManager:
 * Gestiona el ciclo de vida de los portales de entrada y salida para Backrooms.
 *
 * Arquitectura del ciclo de vida:
 * 1. '/backrooms portal <nivel> <targets>':
 *    - Guarda el punto de retorno individual de cada jugador (dimensión, X, Y, Z, yaw, pitch).
 *    - Asigna a cada jugador un área generada del nivel indicado.
 *    - Separa portales de entrada al menos 6 bloques si los jugadores están juntos.
 *    - Crea físicamente el portal REAL 3x3 horizontal bajo los pies del jugador.
 *    - NO crea el portal de salida aún.
 *
 * 2. Cruce del portal de entrada:
 *    - Jugador llega 3 bloques frente a la puerta norte del bioma asignado, 2 bloques sobre suelo Y=1.
 *    - Cae por gravedad al suelo amarillo.
 *    - El portal de entrada se elimina atómicamente.
 *    - Se crea físicamente el portal REAL de SALIDA 3x3 horizontal dentro del bioma asignado.
 *
 * 3. Cruce del portal de salida:
 *    - Jugador vuelve exactamente a su punto de origen (dimensión, X, Y, Z, yaw, pitch).
 *    - El portal de salida se elimina.
 *    - Los datos de retorno se consumen y eliminan.
 */
public class PortalManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Backrooms2K10");
    public static final double OVERWORLD_PORTAL_MIN_SEPARATION = 6.0;

    public static class PlayerPortalSession {
        public final UUID playerUuid;
        public final ResourceKey<Level> originDimension;
        public final ResourceKey<Level> backroomsDimension;
        public final String levelId;
        public final String areaId;
        public UUID entrancePortalUuid;
        public UUID exitPortalUuid;
        public boolean playerInBackrooms = false;

        public PlayerPortalSession(UUID playerUuid, ResourceKey<Level> originDim, ResourceKey<Level> backroomsDim,
                                   String levelId, String areaId, UUID entrancePortalUuid) {
            this.playerUuid = playerUuid;
            this.originDimension = originDim;
            this.backroomsDimension = backroomsDim;
            this.levelId = levelId;
            this.areaId = areaId;
            this.entrancePortalUuid = entrancePortalUuid;
        }
    }

    private static final Map<UUID, PlayerPortalSession> ACTIVE_SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> ENTRANCE_PORTAL_TO_PLAYER = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> EXIT_PORTAL_TO_PLAYER = new ConcurrentHashMap<>();

    /**
     * Crea portales temporales de entrada 3x3 bajo los jugadores.
     */
    public static int spawnPortalsForPlayers(ServerLevel originLevel, String levelId, List<ServerPlayer> players) {
        if (players.isEmpty()) return 0;

        ResourceKey<Level> destKey = DimensionManager.getLevelKey(levelId);
        MinecraftServer server = originLevel.getServer();

        // 1. Validar que el nivel exista y esté READY en SavedData
        if (!DimensionManager.levelExists(server, levelId)) {
            LOGGER.warn("[Backrooms] No se pudo crear portal: el nivel '{}' no existe o no está READY.", levelId);
            for (ServerPlayer p : players) {
                p.sendSystemMessage(Component.literal("§c[Backrooms] No se pudo crear el portal: el nivel " +
                        levelId + " no está disponible. Usa /backrooms create <cantidad>."));
            }
            return 0;
        }

        // 2. Obtener ServerLevel de destino
        ServerLevel destLevel = DimensionManager.getOrLoadDimension(server, levelId);
        if (destLevel == null) {
            LOGGER.error("[Backrooms] No se pudo cargar ServerLevel para '{}'.", levelId);
            for (ServerPlayer p : players) {
                p.sendSystemMessage(Component.literal("§c[Backrooms] Error: dimensión " + levelId + " no accesible en el servidor."));
            }
            return 0;
        }

        // 3. Asignar áreas independientes por jugador
        Map<ServerPlayer, BackroomsSavedData.AreaData> assignments =
                BiomeManager.assignAreasToPlayers(destLevel, levelId, players);

        // 4. Separar posiciones si los jugadores están muy cerca
        List<Vec3> portalPositions = calculateSeparatedPositions(players);

        int created = 0;
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            BackroomsSavedData.AreaData area = assignments.get(player);
            if (area == null) continue;

            // Asegurar que el área esté físicamente generada
            ProceduralGenerator.generateAreaIfNeeded(destLevel, area);

            // PASO 1: Guardar punto de retorno individual
            ReturnManager.savePlayerReturnPoint(player);

            // PASO 2: Posición de origen separada
            Vec3 pos = portalPositions.get(i);
            Vec3 portalOrigin = new Vec3(pos.x, player.getY() + 0.05, pos.z);

            // PASO 4 & 5: Destino del portal frente a la puerta norte
            // Y=3.0 (2 bloques por encima del suelo amarillo Y=1 para caída por gravedad)
            int spawnX = area.northDoor != null ? area.northDoor.getX() : (area.minX + area.width / 2);
            int spawnZ = area.northDoor != null ? area.northDoor.getZ() + 3 : (area.minZ + 3);
            Vec3 destVec = new Vec3(spawnX + 0.5, 3.0, spawnZ + 0.5);

            // PASO 3: Crear portal REAL de Immersive Portals (3x3 horizontal)
            LOGGER.info("[Backrooms] Creating Immersive Portal");
            LOGGER.info("[Backrooms] Source dimension: {}", originLevel.dimension().location());
            LOGGER.info("[Backrooms] Destination dimension: {}", destLevel.dimension().location());
            LOGGER.info("[Backrooms] Destination position: {} {} {}", (int) destVec.x, (int) destVec.y, (int) destVec.z);

            UUID entranceUuid = BackroomsPortalService.createEntrancePortal(
                    originLevel,
                    portalOrigin,
                    destLevel.dimension(),
                    destVec,
                    player
            );

            if (entranceUuid != null) {
                // Limpiar sesión previa si existía
                cleanupPlayerSession(player, originLevel.getServer());

                PlayerPortalSession session = new PlayerPortalSession(
                        player.getUUID(),
                        originLevel.dimension(),
                        destLevel.dimension(),
                        levelId,
                        area.id,
                        entranceUuid
                );

                ACTIVE_SESSIONS.put(player.getUUID(), session);
                ENTRANCE_PORTAL_TO_PLAYER.put(entranceUuid, player.getUUID());
                created++;

                player.sendSystemMessage(Component.literal("§a[Backrooms] Portal de entrada 3x3 abierto bajo tus pies hacia §e" +
                        destLevel.dimension().location()));
            }
        }

        return created;
    }

    private static List<Vec3> calculateSeparatedPositions(List<ServerPlayer> players) {
        List<Vec3> positions = new ArrayList<>();
        for (ServerPlayer player : players) {
            Vec3 desiredPos = player.position();
            Vec3 adjusted = desiredPos;

            boolean overlapping = true;
            int attempts = 0;
            while (overlapping && attempts < 20) {
                overlapping = false;
                for (Vec3 existing : positions) {
                    double distSq = (adjusted.x - existing.x) * (adjusted.x - existing.x) +
                                    (adjusted.z - existing.z) * (adjusted.z - existing.z);
                    if (distSq < OVERWORLD_PORTAL_MIN_SEPARATION * OVERWORLD_PORTAL_MIN_SEPARATION) {
                        overlapping = true;
                        adjusted = adjusted.add(OVERWORLD_PORTAL_MIN_SEPARATION, 0, 0);
                        break;
                    }
                }
                attempts++;
            }
            positions.add(adjusted);
        }
        return positions;
    }

    /**
     * Detección de cambio de dimensión del jugador.
     */
    public static void onPlayerDimensionChange(ServerPlayer player, ResourceKey<Level> fromDim, ResourceKey<Level> toDim) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // 1. Llegada a Backrooms:
        if (ModDimensions.isBackroomsDimension(toDim)) {
            handlePlayerArrivalInBackrooms(player, server);
        }

        // 2. Regreso desde Backrooms al Overworld:
        if (ModDimensions.isBackroomsDimension(fromDim) && !ModDimensions.isBackroomsDimension(toDim)) {
            handlePlayerReturnFromBackrooms(player, server);
        }
    }

    /**
     * Tick del jugador: safety check para sincronización de portales.
     */
    public static void checkPlayerTick(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ResourceKey<Level> currentDim = player.level().dimension();
        PlayerPortalSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) return;

        // Si el jugador está en Backrooms pero la sesión no registró la llegada
        if (ModDimensions.isBackroomsDimension(currentDim) && !session.playerInBackrooms) {
            handlePlayerArrivalInBackrooms(player, server);
        }

        // Si el jugador regresó a una dimensión normal y la sesión aún está marcada en Backrooms
        if (!ModDimensions.isBackroomsDimension(currentDim) && session.playerInBackrooms) {
            handlePlayerReturnFromBackrooms(player, server);
        }
    }

    /**
     * Jugador llegó correctamente a Backrooms:
     * 1. Elimina el portal de entrada en el Overworld.
     * 2. Crea el portal REAL de SALIDA dentro de su bioma en Backrooms.
     */
    private static synchronized void handlePlayerArrivalInBackrooms(ServerPlayer player, MinecraftServer server) {
        PlayerPortalSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null || session.playerInBackrooms) return;

        session.playerInBackrooms = true;

        // 1. Eliminar portal de entrada en Overworld
        if (session.entrancePortalUuid != null) {
            ServerLevel originLevel = server.getLevel(session.originDimension);
            if (originLevel != null) {
                BackroomsPortalService.removePortal(originLevel, session.entrancePortalUuid);
            }
            ENTRANCE_PORTAL_TO_PLAYER.remove(session.entrancePortalUuid);
            session.entrancePortalUuid = null;
            LOGGER.info("[Backrooms] Portal de entrada consumido y eliminado.");
        }

        // 2. Crear portal REAL de SALIDA en el bioma asignado
        ServerLevel backroomsLevel = server.getLevel(session.backroomsDimension);
        if (backroomsLevel == null) {
            LOGGER.error("[Backrooms] No se pudo encontrar ServerLevel de Backrooms para crear portal de salida.");
            return;
        }

        BackroomsSavedData data = BackroomsSavedData.get(server.overworld());
        BackroomsSavedData.ReturnData returnData = ReturnManager.getReturnData(player);
        if (returnData == null) {
            LOGGER.warn("[Backrooms] No hay ReturnData para {}; usando Overworld spawn como fallback.", player.getName().getString());
            returnData = new BackroomsSavedData.ReturnData(
                    Level.OVERWORLD,
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()
            );
        }

        BackroomsSavedData.AreaData area = data.getArea(session.levelId, session.areaId);
        if (area == null) {
            Map<String, BackroomsSavedData.AreaData> areas = data.getAreasForLevel(session.levelId);
            if (!areas.isEmpty()) {
                area = areas.values().iterator().next();
            }
        }

        // Calcular posición válida y aleatoria dentro del bioma sobre suelo amarillo Y=1
        Vec3 exitPortalPos = findValidExitPortalPos(backroomsLevel, area);

        LOGGER.info("[Backrooms] Exit portal created");
        LOGGER.info("[Backrooms] Exit destination: {} {} {} {}",
                returnData.dimension.location(), (int) returnData.x, (int) returnData.y, (int) returnData.z);

        UUID exitUuid = BackroomsPortalService.createExitPortal(
                backroomsLevel,
                exitPortalPos,
                returnData.dimension,
                new Vec3(returnData.x, returnData.y, returnData.z),
                returnData.yaw,
                returnData.pitch,
                player
        );

        if (exitUuid != null) {
            session.exitPortalUuid = exitUuid;
            EXIT_PORTAL_TO_PLAYER.put(exitUuid, player.getUUID());
            LOGGER.info("[Backrooms] Portal de salida activo en {} hacia {}", exitPortalPos, returnData.dimension.location());
        }
    }

    /**
     * Jugador cruzó el portal de salida y volvió al Overworld:
     * 1. Elimina el portal de salida en Backrooms.
     * 2. Consume y borra los datos de retorno del jugador.
     * 3. Limpia la sesión.
     */
    private static synchronized void handlePlayerReturnFromBackrooms(ServerPlayer player, MinecraftServer server) {
        PlayerPortalSession session = ACTIVE_SESSIONS.remove(player.getUUID());

        if (session != null && session.exitPortalUuid != null) {
            ServerLevel backroomsLevel = server.getLevel(session.backroomsDimension);
            if (backroomsLevel != null) {
                BackroomsPortalService.removePortal(backroomsLevel, session.exitPortalUuid);
            }
            EXIT_PORTAL_TO_PLAYER.remove(session.exitPortalUuid);
            session.exitPortalUuid = null;
            LOGGER.info("[Backrooms] Portal de salida consumido y eliminado.");
        }

        // Consumir datos de retorno
        BackroomsSavedData.ReturnData returnData = ReturnManager.consumeReturnPoint(player);
        if (returnData != null) {
            player.setYRot(returnData.yaw);
            player.setXRot(returnData.pitch);
            LOGGER.info("[Backrooms] Return data consumed");
        }
    }

    /**
     * Localiza una posición válida, despejada y accesible para un portal 3x3 horizontal
     * dentro de los límites del área del jugador sobre el suelo amarillo Y=1.
     */
    public static Vec3 findValidExitPortalPos(ServerLevel level, BackroomsSavedData.AreaData area) {
        Random random = new Random();
        int margin = 30;

        int minX = (area != null ? area.minX : 0) + margin;
        int maxX = (area != null ? area.maxX : 300) - margin;
        int minZ = (area != null ? area.minZ : 0) + margin;
        int maxZ = (area != null ? area.maxZ : 300) - margin;

        if (minX >= maxX) { minX = 0; maxX = 300; }
        if (minZ >= maxZ) { minZ = 0; maxZ = 300; }

        BlockPos door = (area != null && area.northDoor != null) ? area.northDoor : new BlockPos((minX + maxX) / 2, 2, minZ);

        for (int attempt = 0; attempt < 100; attempt++) {
            int candX = minX + random.nextInt(Math.max(1, maxX - minX + 1));
            int candZ = minZ + random.nextInt(Math.max(1, maxZ - minZ + 1));

            // Mantener al menos 35 bloques de distancia de la puerta norte
            if (Math.abs(candX - door.getX()) < 35 && Math.abs(candZ - door.getZ()) < 35) {
                continue;
            }

            // Validar footprint 3x3: suelo sólido a Y=1, aire libre a Y=2, 3, 4
            boolean valid = true;
            for (int dx = -1; dx <= 1 && valid; dx++) {
                for (int dz = -1; dz <= 1 && valid; dz++) {
                    BlockPos floorPos = new BlockPos(candX + dx, 1, candZ + dz);
                    BlockState floor = level.getBlockState(floorPos);
                    if (floor.isAir()) {
                        valid = false;
                        break;
                    }

                    for (int y = 2; y <= 4; y++) {
                        BlockPos space = new BlockPos(candX + dx, y, candZ + dz);
                        if (!level.getBlockState(space).isAir() && level.getBlockState(space).isSolid()) {
                            valid = false;
                            break;
                        }
                    }
                }
            }

            if (valid) {
                // Preparar y despejar el espacio 3x3
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        level.setBlock(new BlockPos(candX + dx, 1, candZ + dz), Blocks.SMOOTH_SANDSTONE.defaultBlockState(), 18);
                        for (int y = 2; y <= 4; y++) {
                            level.setBlock(new BlockPos(candX + dx, y, candZ + dz), Blocks.AIR.defaultBlockState(), 18);
                        }
                    }
                }
                return new Vec3(candX + 0.5, 1.05, candZ + 0.5);
            }
        }

        // Posición fallback centrada garantizada
        int fallbackX = (minX + maxX) / 2;
        int fallbackZ = (minZ + maxZ) / 2;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(new BlockPos(fallbackX + dx, 1, fallbackZ + dz), Blocks.SMOOTH_SANDSTONE.defaultBlockState(), 18);
                for (int y = 2; y <= 4; y++) {
                    level.setBlock(new BlockPos(fallbackX + dx, y, fallbackZ + dz), Blocks.AIR.defaultBlockState(), 18);
                }
            }
        }
        return new Vec3(fallbackX + 0.5, 1.05, fallbackZ + 0.5);
    }

    public static void cleanupPlayerSession(ServerPlayer player, MinecraftServer server) {
        PlayerPortalSession session = ACTIVE_SESSIONS.remove(player.getUUID());
        if (session != null) {
            if (session.entrancePortalUuid != null) {
                ServerLevel originLevel = server.getLevel(session.originDimension);
                if (originLevel != null) {
                    BackroomsPortalService.removePortal(originLevel, session.entrancePortalUuid);
                }
                ENTRANCE_PORTAL_TO_PLAYER.remove(session.entrancePortalUuid);
            }
            if (session.exitPortalUuid != null) {
                ServerLevel backroomsLevel = server.getLevel(session.backroomsDimension);
                if (backroomsLevel != null) {
                    BackroomsPortalService.removePortal(backroomsLevel, session.exitPortalUuid);
                }
                EXIT_PORTAL_TO_PLAYER.remove(session.exitPortalUuid);
            }
        }
    }

    public static boolean removeNearestModPortal(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int count = BackroomsPortalService.removeAllPortalsInLevel(level);
        if (count > 0) {
            player.sendSystemMessage(Component.literal("§a[Backrooms] Se han eliminado " + count + " portales en esta dimensión."));
            return true;
        }
        player.sendSystemMessage(Component.literal("§7[Backrooms] No se encontraron portales del mod en esta dimensión."));
        return false;
    }

    public static int removeAllModPortals(ServerLevel level) {
        clearAllMemoryState();
        return BackroomsPortalService.removeAllPortalsInLevel(level);
    }

    public static void clearAllMemoryState() {
        ACTIVE_SESSIONS.clear();
        ENTRANCE_PORTAL_TO_PLAYER.clear();
        EXIT_PORTAL_TO_PLAYER.clear();
        BackroomsPortalService.clearMemory();
    }

    public static Map<UUID, PlayerPortalSession> getActiveSessions() {
        return Collections.unmodifiableMap(ACTIVE_SESSIONS);
    }
}
