package net.backrooms2k10.persistence;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.UUID;

/**
 * ReturnManager:
 * - Guarda temporalmente: dimensión original, X, Y, Z, yaw, pitch al ingresar a Backrooms.
 * - Al usar un portal de salida o comando de retorno, devuelve al jugador exactamente a esas coordenadas y orientación.
 * - Consume y borra de forma atómica el registro para evitar reutilizaciones.
 * - Si el jugador entra a otro nivel posteriormente, se genera un registro nuevo.
 */
public class ReturnManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Backrooms2K10");

    public static void savePlayerReturnPoint(ServerPlayer player) {
        ServerLevel originLevel = player.serverLevel();
        BackroomsSavedData data = BackroomsSavedData.get(originLevel);

        BackroomsSavedData.ReturnData returnData = new BackroomsSavedData.ReturnData(
                originLevel.dimension(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );

        data.setReturnPoint(player.getUUID(), returnData);
        LOGGER.info("[Backrooms] Punto de retorno guardado para {}: dim={}, pos=[{}, {}, {}]",
                player.getName().getString(),
                originLevel.dimension().location(),
                (int) player.getX(), (int) player.getY(), (int) player.getZ());
    }

    public static BackroomsSavedData.ReturnData consumeReturnPoint(UUID playerUuid, MinecraftServer server) {
        if (server == null || server.overworld() == null) return null;
        BackroomsSavedData data = BackroomsSavedData.get(server.overworld());
        return data.consumeReturnPoint(playerUuid);
    }

    public static BackroomsSavedData.ReturnData consumeReturnPoint(ServerPlayer player) {
        return consumeReturnPoint(player.getUUID(), player.getServer());
    }

    public static boolean returnPlayer(ServerPlayer player) {
        BackroomsSavedData.ReturnData returnData = consumeReturnPoint(player);
        if (returnData == null) {
            LOGGER.warn("[Backrooms] No se encontró punto de retorno para {}", player.getName().getString());
            return false;
        }

        ServerLevel destLevel = player.getServer().getLevel(returnData.dimension);
        if (destLevel == null) {
            destLevel = player.getServer().overworld();
        }

        if (player.isPassenger()) {
            player.stopRiding();
        }

        player.teleportTo(
                destLevel,
                returnData.x,
                returnData.y,
                returnData.z,
                Collections.emptySet(),
                returnData.yaw,
                returnData.pitch
        );

        player.sendSystemMessage(Component.literal("§a[Backrooms] Has regresado a tu posición original."));
        LOGGER.info("[Backrooms] Jugador {} retornado exitosamente y registro consumido.", player.getName().getString());
        return true;
    }

    public static BackroomsSavedData.ReturnData getReturnData(ServerPlayer player) {
        return BackroomsSavedData.get(player.serverLevel()).getReturnPoint(player.getUUID());
    }
}
