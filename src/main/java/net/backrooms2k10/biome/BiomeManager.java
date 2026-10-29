package net.backrooms2k10.biome;

import net.backrooms2k10.persistence.BackroomsSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * BiomeManager:
 * - Registro de áreas en cuadrícula (GRID)
 * - Separación mínima garantizada de >= 300 bloques entre bordes exteriores
 * - Cálculo de zona norte, puerta norte, spawn y exitPoint
 * - Asignación coordinada para grupos de jugadores
 */
public class BiomeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Backrooms2K10");
    public static final int MIN_AREA_SEPARATION = 300;
    public static final int BACKROOMS_GROUND_Y = 1; // Suelo amarillo en Y=1

    /**
     * Prepara 'count' áreas para un nivel recién creado o expandido y construye inmediatamente sus estructuras.
     */
    public static void prepareAreasForLevel(ServerLevel level, String levelId, int count) {
        BackroomsSavedData data = BackroomsSavedData.get(level);
        Map<String, BackroomsSavedData.AreaData> existingAreas = data.getAreasForLevel(levelId);
        int startIndex = existingAreas.size();

        Random random = new Random(level.getSeed() ^ levelId.hashCode());

        for (int i = 0; i < count; i++) {
            int areaIndex = startIndex + i;
            String areaId = "area_" + areaIndex;

            BackroomsBiomeType biomeType = BackroomsBiomeType.getRandomBiome(random);
            int width = Math.max(300, biomeType.getDefaultWidth() + random.nextInt(41));
            int depth = Math.max(300, biomeType.getDefaultDepth() + random.nextInt(41));

            // Calcular posición en GRID con separación garantizada >= 300 bloques
            AreaBounds bounds = calculateNextGridPosition(existingAreas.values(), width, depth);

            BackroomsSavedData.AreaData area = new BackroomsSavedData.AreaData();
            area.id = areaId;
            area.levelId = levelId;
            area.biomeType = biomeType.getId();
            area.groundY = BACKROOMS_GROUND_Y;
            area.origin = new BlockPos(bounds.getMinX(), BACKROOMS_GROUND_Y, bounds.getMinZ());
            area.minX = bounds.getMinX();
            area.maxX = bounds.getMaxX();
            area.minZ = bounds.getMinZ();
            area.maxZ = bounds.getMaxZ();
            area.width = bounds.getWidth();
            area.depth = bounds.getDepth();
            area.generated = false;
            area.seed = random.nextLong();

            // Puerta Norte en Z mínimo sobre el piso amarillo Y=1 (puerta a Y=2)
            int northDoorX = bounds.getCenterX();
            int northDoorZ = bounds.getMinZ();
            area.northDoor = new BlockPos(northDoorX, 2, northDoorZ);

            // Spawn: Frente a la puerta norte dentro de la estructura, 3 bloques frente a la puerta y 2 bloques sobre el suelo (Y=3) para caída física
            area.spawnPoint = new BlockPos(northDoorX, 3, northDoorZ + 3);

            // Portal de salida: ubicación aleatoria válida y accesible dentro del bioma
            area.exitPoint = calculateRandomExitPoint(bounds, area.northDoor, random);

            if (biomeType == BackroomsBiomeType.TRANSITION_ZONE) {
                BackroomsBiomeType target = BackroomsBiomeType.getRandomBiome(random);
                while (target == BackroomsBiomeType.TRANSITION_ZONE) {
                    target = BackroomsBiomeType.getRandomBiome(random);
                }
                area.transitionTargetBiome = target.getId();
            }

            data.putArea(levelId, area);
            existingAreas.put(areaId, area);

            // Generación procedural física inmediata en el mundo al crear el nivel
            net.backrooms2k10.generator.ProceduralGenerator.generateAreaIfNeeded(level, area);
        }

        LOGGER.info("[Backrooms] Preparadas y generadas físicamente {} áreas para nivel '{}'.", count, levelId);
    }

    /**
     * Calcula una posición de salida aleatoria válida dentro de la estructura del bioma,
     * respetando márgenes y distancia a la puerta norte.
     */
    public static BlockPos calculateRandomExitPoint(AreaBounds bounds, BlockPos northDoor, Random random) {
        int margin = 25;
        int minX = bounds.getMinX() + margin;
        int maxX = bounds.getMaxX() - margin;
        int minZ = bounds.getMinZ() + margin;
        int maxZ = bounds.getMaxZ() - margin;

        int exitX = minX + random.nextInt(Math.max(1, maxX - minX + 1));
        int exitZ = minZ + random.nextInt(Math.max(1, maxZ - minZ + 1));

        int attempts = 0;
        while (attempts < 30 && Math.abs(exitX - northDoor.getX()) < 35 && Math.abs(exitZ - northDoor.getZ()) < 35) {
            exitX = minX + random.nextInt(Math.max(1, maxX - minX + 1));
            exitZ = minZ + random.nextInt(Math.max(1, maxZ - minZ + 1));
            attempts++;
        }

        return new BlockPos(exitX, BACKROOMS_GROUND_Y, exitZ);
    }

    /**
     * Calcula la posición en cuadrícula en espiral asegurando que bounds.intersectsWithMargin(other, 300) sea falso.
     */
    public static AreaBounds calculateNextGridPosition(Collection<BackroomsSavedData.AreaData> existing, int width, int depth) {
        if (existing.isEmpty()) {
            return new AreaBounds(0, 0, width - 1, depth - 1);
        }

        int cellStep = 750; // Paso base holgado para la cuadrícula (estructuras de 300-380 + 300 de separación)
        int ring = 1;

        while (true) {
            for (int gx = -ring; gx <= ring; gx++) {
                for (int gz = -ring; gz <= ring; gz++) {
                    if (Math.abs(gx) != ring && Math.abs(gz) != ring) continue;

                    int originX = gx * cellStep;
                    int originZ = gz * cellStep;
                    AreaBounds candidate = new AreaBounds(originX, originZ, originX + width - 1, originZ + depth - 1);

                    boolean valid = true;
                    for (BackroomsSavedData.AreaData area : existing) {
                        AreaBounds existingBounds = new AreaBounds(area.minX, area.minZ, area.maxX, area.maxZ);
                        if (candidate.intersectsWithMargin(existingBounds, MIN_AREA_SEPARATION)) {
                            valid = false;
                            break;
                        }
                    }

                    if (valid) {
                        return candidate;
                    }
                }
            }
            ring++;
        }
    }

    /**
     * Asignación coordinada de áreas para un conjunto de jugadores.
     * Si hay suficientes áreas, asigna un área distinta a cada uno.
     * Si hay más jugadores que áreas, reutiliza áreas asegurando separación y posiciones de spawn seguras.
     */
    public static Map<ServerPlayer, BackroomsSavedData.AreaData> assignAreasToPlayers(
            ServerLevel targetLevel,
            String levelId,
            List<ServerPlayer> players
    ) {
        BackroomsSavedData data = BackroomsSavedData.get(targetLevel);
        Map<String, BackroomsSavedData.AreaData> areas = new LinkedHashMap<>(data.getAreasForLevel(levelId));

        if (areas.isEmpty()) {
            prepareAreasForLevel(targetLevel, levelId, Math.max(1, players.size()));
            areas = data.getAreasForLevel(levelId);
        }

        List<BackroomsSavedData.AreaData> areaList = new ArrayList<>(areas.values());
        Map<ServerPlayer, BackroomsSavedData.AreaData> assignment = new LinkedHashMap<>();

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            BackroomsSavedData.AreaData assignedArea = areaList.get(i % areaList.size());
            assignment.put(player, assignedArea);
        }

        return assignment;
    }
}
