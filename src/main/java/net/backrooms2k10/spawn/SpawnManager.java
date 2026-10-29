package net.backrooms2k10.spawn;

import net.backrooms2k10.generator.ProceduralGenerator;
import net.backrooms2k10.persistence.BackroomsSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * SpawnManager:
 * - Posición de aparición en el lado norte frente a la puerta norte.
 * - Caída física de 2 bloques por encima del suelo (sin frenado artificial ni bloques invisibles).
 * - Convención de Norte: Z negativo (yaw = 180° mirando hacia el sur/interior del área).
 * - Garantía de generación de área bajo demanda antes del teletransporte.
 */
public class SpawnManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Backrooms2K10");
    public static final float NORTH_FACING_YAW = 180.0f; // Mirando hacia el interior (+Z) desde la puerta norte
    public static final float ZERO_PITCH = 0.0f;

    public static void teleportPlayerToAreaSpawn(ServerPlayer player, ServerLevel targetLevel, BackroomsSavedData.AreaData area) {
        // Asegurar que la estructura esté físicamente generada antes de teletransportar
        ProceduralGenerator.generateAreaIfNeeded(targetLevel, area);

        BlockPos spawnPos = area.spawnPoint;
        if (spawnPos == null) {
            spawnPos = new BlockPos(area.minX + area.width / 2, area.groundY + 3, area.minZ + 2);
        }

        // Posición: 2 bloques por encima del suelo para que caiga físicamente
        double targetX = spawnPos.getX() + 0.5;
        double targetY = spawnPos.getY(); // spawnPoint ya está a floorY + 2
        double targetZ = spawnPos.getZ() + 0.5;

        // Desmontar si viaja en vehículo
        if (player.isPassenger()) {
            player.stopRiding();
        }

        player.teleportTo(
                targetLevel,
                targetX,
                targetY,
                targetZ,
                Collections.emptySet(),
                NORTH_FACING_YAW,
                ZERO_PITCH
        );

        LOGGER.info("[Backrooms] Jugador {} apareció en área '{}' en [{}, {}, {}] con caída libre de 2 bloques.",
                player.getName().getString(), area.id, (int) targetX, (int) targetY, (int) targetZ);
    }
}
