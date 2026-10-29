package net.backrooms2k10.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.backrooms2k10.biome.BiomeManager;
import net.backrooms2k10.persistence.BackroomsSavedData;
import net.backrooms2k10.persistence.DimensionManager;
import net.backrooms2k10.persistence.ReturnManager;
import net.backrooms2k10.portal.PortalManager;
import net.backrooms2k10.spawn.SpawnManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * CommandManager: Centraliza todos los comandos administrativos del mod Backrooms.
 * Soporta selectores normales de Minecraft (@a, @p, @r, @e[type=player], etc.).
 */
public class CommandManager {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("backrooms")
                        .requires(s -> s.hasPermission(2))

                        // /backrooms create <cantidad>
                        .then(Commands.literal("create")
                                .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 1000))
                                        .executes(ctx -> executeCreate(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "cantidad")))))

                        // /backrooms portal <nivel> <targets>
                        .then(Commands.literal("portal")
                                .then(Commands.argument("nivel", StringArgumentType.word())
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> executePortal(ctx.getSource(), StringArgumentType.getString(ctx, "nivel"), EntityArgument.getPlayers(ctx, "targets"))))))

                        // /backrooms tp <nivel> [target]
                        .then(Commands.literal("tp")
                                .then(Commands.argument("nivel", StringArgumentType.word())
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(ctx -> executeTp(ctx.getSource(), StringArgumentType.getString(ctx, "nivel"), Collections.singletonList(EntityArgument.getPlayer(ctx, "target")))))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            return executeTp(ctx.getSource(), StringArgumentType.getString(ctx, "nivel"), Collections.singletonList(player));
                                        })))

                        // /backrooms force <nivel> <targets>
                        .then(Commands.literal("force")
                                .then(Commands.argument("nivel", StringArgumentType.word())
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> executeForce(ctx.getSource(), StringArgumentType.getString(ctx, "nivel"), EntityArgument.getPlayers(ctx, "targets"))))))

                        // /backrooms return [target]
                        .then(Commands.literal("return")
                                .then(Commands.argument("target", EntityArgument.player())
                                                .executes(ctx -> executeReturn(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))))
                                .executes(ctx -> executeReturn(ctx.getSource(), ctx.getSource().getPlayerOrException())))

                        // /backrooms spawn <nivel> [biome]
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("nivel", StringArgumentType.word())
                                        .then(Commands.argument("biome", StringArgumentType.word())
                                                .executes(ctx -> executeSpawn(ctx.getSource(), StringArgumentType.getString(ctx, "nivel"), StringArgumentType.getString(ctx, "biome"))))
                                        .executes(ctx -> executeSpawn(ctx.getSource(), StringArgumentType.getString(ctx, "nivel"), null))))

                        // /backrooms list
                        .then(Commands.literal("list").executes(ctx -> executeList(ctx.getSource())))

                        // /backrooms info <nivel>
                        .then(Commands.literal("info")
                                .then(Commands.argument("nivel", StringArgumentType.word())
                                        .executes(ctx -> executeInfo(ctx.getSource(), StringArgumentType.getString(ctx, "nivel")))))

                        // /backrooms test
                        .then(Commands.literal("test").executes(ctx -> executeTest(ctx.getSource())))

                        // /backrooms removeportal
                        .then(Commands.literal("removeportal").executes(ctx -> executeRemovePortal(ctx.getSource())))

                        // /backrooms removeportals
                        .then(Commands.literal("removeportals").executes(ctx -> executeRemovePortals(ctx.getSource())))

                        // /backrooms help
                        .then(Commands.literal("help").executes(ctx -> executeHelp(ctx.getSource())))
        );
    }

    private static int executeCreate(CommandSourceStack source, int areaCount) {
        ServerLevel newLevel = DimensionManager.createNextLevel(source.getServer(), areaCount);
        if (newLevel == null) {
            source.sendFailure(Component.literal("§c[Backrooms] Error al crear la dimensión en el servidor."));
            return 0;
        }

        String levelId = newLevel.dimension().location().toString();
        source.sendSuccess(() -> Component.literal("§a[Backrooms] Nivel creado exitosamente: §e" + levelId +
                " §a(" + areaCount + " áreas preparadas)."), true);
        return 1;
    }

    private static int executePortal(CommandSourceStack source, String levelInput, Collection<ServerPlayer> targets) {
        if (!DimensionManager.levelExists(source.getServer(), levelInput)) {
            source.sendFailure(Component.literal("§c[Backrooms] El nivel '" + levelInput +
                    "' no existe. Utiliza '/backrooms create <cantidad>' para crearlo."));
            return 0;
        }

        ResourceKey<Level> key = DimensionManager.getLevelKey(levelInput);
        List<ServerPlayer> players = new ArrayList<>(targets);
        int created = PortalManager.spawnPortalsForPlayers(source.getLevel(), key.location().toString(), players);

        if (created > 0) {
            source.sendSuccess(() -> Component.literal("§a[Backrooms] Portales temporales 3x3 creados para " +
                    created + " jugador(es) hacia §e" + key.location()), true);
        }
        return created;
    }

    private static int executeForce(CommandSourceStack source, String levelInput, Collection<ServerPlayer> targets) {
        if (!DimensionManager.levelExists(source.getServer(), levelInput)) {
            source.sendFailure(Component.literal("§c[Backrooms] El nivel '" + levelInput +
                    "' no existe. Utiliza '/backrooms create <cantidad>' para crearlo."));
            return 0;
        }

        ServerLevel destLevel = DimensionManager.getOrLoadDimension(source.getServer(), levelInput);
        if (destLevel == null) {
            source.sendFailure(Component.literal("§c[Backrooms] Error al cargar la dimensión de destino."));
            return 0;
        }

        List<ServerPlayer> players = new ArrayList<>(targets);
        Map<ServerPlayer, BackroomsSavedData.AreaData> assignments =
                BiomeManager.assignAreasToPlayers(destLevel, destLevel.dimension().location().toString(), players);

        for (ServerPlayer player : players) {
            ReturnManager.savePlayerReturnPoint(player);
            BackroomsSavedData.AreaData area = assignments.get(player);
            SpawnManager.teleportPlayerToAreaSpawn(player, destLevel, area);
        }

        source.sendSuccess(() -> Component.literal("§a[Backrooms] Entrada forzada aplicada a " + players.size() +
                " jugador(es) respetando áreas y separación de 300 bloques."), true);
        return players.size();
    }

    private static int executeTp(CommandSourceStack source, String levelInput, Collection<ServerPlayer> targets) {
        if (!DimensionManager.levelExists(source.getServer(), levelInput)) {
            source.sendFailure(Component.literal("§c[Backrooms] El nivel '" + levelInput +
                    "' no existe. Utiliza '/backrooms create <cantidad>' para crearlo."));
            return 0;
        }

        ServerLevel destLevel = DimensionManager.getOrLoadDimension(source.getServer(), levelInput);
        if (destLevel == null) {
            source.sendFailure(Component.literal("§c[Backrooms] Error al cargar la dimensión."));
            return 0;
        }

        List<ServerPlayer> players = new ArrayList<>(targets);
        for (ServerPlayer player : players) {
            ReturnManager.savePlayerReturnPoint(player);
            Map<ServerPlayer, BackroomsSavedData.AreaData> map =
                    BiomeManager.assignAreasToPlayers(destLevel, destLevel.dimension().location().toString(), Collections.singletonList(player));
            SpawnManager.teleportPlayerToAreaSpawn(player, destLevel, map.get(player));
        }

        source.sendSuccess(() -> Component.literal("§a[Backrooms] Teletransporte administrativo completado."), true);
        return players.size();
    }

    private static int executeReturn(CommandSourceStack source, ServerPlayer player) {
        boolean success = ReturnManager.returnPlayer(player);
        if (!success) {
            source.sendFailure(Component.literal("§c[Backrooms] No hay punto de retorno registrado para " + player.getName().getString()));
            return 0;
        }
        return 1;
    }

    private static int executeList(CommandSourceStack source) {
        BackroomsSavedData data = BackroomsSavedData.get(source.getServer().overworld());
        List<String> levels = data.getRegisteredLevels();

        source.sendSuccess(() -> Component.literal("§6=== Niveles de Backrooms (" + levels.size() + ") ==="), false);
        if (levels.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7(No hay niveles registrados. Usa /backrooms create <cantidad>)"), false);
            return 1;
        }

        for (String lvl : levels) {
            int areaCount = data.getAreasForLevel(lvl).size();
            source.sendSuccess(() -> Component.literal("§e- §f" + lvl + " §7(" + areaCount + " áreas preparadas)"), false);
        }
        return 1;
    }

    private static int executeInfo(CommandSourceStack source, String levelInput) {
        if (!DimensionManager.levelExists(source.getServer(), levelInput)) {
            source.sendFailure(Component.literal("§c[Backrooms] El nivel '" + levelInput + "' no existe."));
            return 0;
        }

        ResourceKey<Level> key = DimensionManager.getLevelKey(levelInput);
        BackroomsSavedData data = BackroomsSavedData.get(source.getServer().overworld());
        Map<String, BackroomsSavedData.AreaData> areas = data.getAreasForLevel(key.location().toString());

        source.sendSuccess(() -> Component.literal("§6=== Información de " + key.location() + " ==="), false);
        source.sendSuccess(() -> Component.literal("§eÁreas preparadas: §f" + areas.size()), false);

        int count = 0;
        for (BackroomsSavedData.AreaData area : areas.values()) {
            if (area.generated) count++;
        }
        final int finalCount = count;
        source.sendSuccess(() -> Component.literal("§eÁreas generadas físicamente: §f" + finalCount), false);
        return 1;
    }

    private static int executeSpawn(CommandSourceStack source, String levelInput, @org.jetbrains.annotations.Nullable String biomeInput) {
        if (!DimensionManager.levelExists(source.getServer(), levelInput)) {
            source.sendFailure(Component.literal("§c[Backrooms] El nivel '" + levelInput +
                    "' no existe. Utiliza '/backrooms create <cantidad>' para crearlo."));
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§cEste comando debe ser ejecutado por un jugador."));
            return 0;
        }

        ServerLevel destLevel = DimensionManager.getOrLoadDimension(source.getServer(), levelInput);
        if (destLevel == null) {
            source.sendFailure(Component.literal("§c[Backrooms] Error al cargar la dimensión de destino."));
            return 0;
        }

        ReturnManager.savePlayerReturnPoint(player);
        BackroomsSavedData data = BackroomsSavedData.get(destLevel);
        Map<String, BackroomsSavedData.AreaData> areas = data.getAreasForLevel(destLevel.dimension().location().toString());

        BackroomsSavedData.AreaData targetArea = null;
        if (biomeInput != null) {
            for (BackroomsSavedData.AreaData a : areas.values()) {
                if (a.id.equalsIgnoreCase(biomeInput) || a.biomeType.equalsIgnoreCase(biomeInput)) {
                    targetArea = a;
                    break;
                }
            }
        }

        if (targetArea == null && !areas.isEmpty()) {
            targetArea = areas.values().iterator().next();
        }

        if (targetArea == null) {
            source.sendFailure(Component.literal("§c[Backrooms] No se encontraron áreas preparadas en este nivel."));
            return 0;
        }

        final BackroomsSavedData.AreaData finalTargetArea = targetArea;
        SpawnManager.teleportPlayerToAreaSpawn(player, destLevel, finalTargetArea);
        source.sendSuccess(() -> Component.literal("§a[Backrooms] Teletransportado al spawn de §e" +
                destLevel.dimension().location() + " §7(área: " + finalTargetArea.id + " [" + finalTargetArea.biomeType + "])."), true);
        return 1;
    }

    private static int executeTest(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        ServerLevel currentLevel = source.getLevel();
        MinecraftServer server = source.getServer();
        BackroomsSavedData data = BackroomsSavedData.get(server.overworld());

        source.sendSuccess(() -> Component.literal("§6=== Backrooms Diagnóstico Completo ==="), false);
        source.sendSuccess(() -> Component.literal("§eDimensión actual: §f" + currentLevel.dimension().location()), false);

        if (player != null) {
            source.sendSuccess(() -> Component.literal("§ePosición: §f[" + (int) player.getX() + ", " + (int) player.getY() + ", " + (int) player.getZ() + "]"), false);

            // Bioma actual si está dentro de un área
            String currentDimStr = currentLevel.dimension().location().toString();
            Map<String, BackroomsSavedData.AreaData> currentAreas = data.getAreasForLevel(currentDimStr);
            String detectedBiome = "§7Espacio entre biomas (plano amarillo vacío)";
            for (BackroomsSavedData.AreaData area : currentAreas.values()) {
                if (player.getX() >= area.minX && player.getX() <= area.maxX &&
                    player.getZ() >= area.minZ && player.getZ() <= area.maxZ) {
                    detectedBiome = "§a" + area.id + " [" + area.biomeType + "] (" + area.width + "x" + area.depth + ")";
                    break;
                }
            }
            final String biomeInfo = detectedBiome;
            source.sendSuccess(() -> Component.literal("§eBioma actual: " + biomeInfo), false);

            // Datos de retorno
            BackroomsSavedData.ReturnData ret = ReturnManager.getReturnData(player);
            source.sendSuccess(() -> Component.literal("§eDatos de retorno: " + (ret != null ? "§a" + ret.dimension.location() + " [" + (int) ret.x + ", " + (int) ret.y + ", " + (int) ret.z + "]" : "§7Ninguno")), false);

            // Distancia al jugador más cercano
            double nearestDist = Double.MAX_VALUE;
            String nearestPlayerName = null;
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (!other.getUUID().equals(player.getUUID())) {
                    if (other.level().dimension().equals(player.level().dimension())) {
                        double d = player.distanceTo(other);
                        if (d < nearestDist) {
                            nearestDist = d;
                            nearestPlayerName = other.getName().getString();
                        }
                    }
                }
            }
            if (nearestPlayerName != null) {
                final String nName = nearestPlayerName;
                final int nDist = (int) nearestDist;
                source.sendSuccess(() -> Component.literal("§eJugador más cercano en esta dimensión: §f" + nName + " §7(" + nDist + " bloques)"), false);
            } else {
                source.sendSuccess(() -> Component.literal("§eJugador más cercano en esta dimensión: §7Ninguno en la misma dimensión"), false);
            }
        }

        // Niveles existentes
        List<String> registeredLevels = data.getRegisteredLevels();
        source.sendSuccess(() -> Component.literal("§eNiveles existentes (" + registeredLevels.size() + "): §f" + String.join(", ", registeredLevels)), false);

        // Áreas en la dimensión actual si es Backrooms
        String curDim = currentLevel.dimension().location().toString();
        if (curDim.startsWith(DimensionManager.NAMESPACE + ":")) {
            Map<String, BackroomsSavedData.AreaData> areas = data.getAreasForLevel(curDim);
            source.sendSuccess(() -> Component.literal("§eÁreas existentes en esta dimensión: §f" + areas.size()), false);
            source.sendSuccess(() -> Component.literal("§eEstado de la dimensión: " + (data.isLevelReady(curDim) ? "§aREADY" : "§cNOT READY")), false);
        }

        // Portales activos y destinos
        Map<UUID, PortalManager.PlayerPortalSession> activeSessions = PortalManager.getActiveSessions();
        source.sendSuccess(() -> Component.literal("§eSesiones de portales activas: §f" + activeSessions.size()), false);
        if (player != null) {
            PortalManager.PlayerPortalSession session = activeSessions.get(player.getUUID());
            if (session != null) {
                source.sendSuccess(() -> Component.literal("§7- Sesión activa: Nivel=" + session.levelId +
                        ", Área=" + session.areaId +
                        ", Entrada=" + (session.entrancePortalUuid != null ? "ACTIVO" : "CONSUMIDO") +
                        ", Salida=" + (session.exitPortalUuid != null ? "ACTIVO" : "PENDIENTE")), false);
            }
        }

        // Estado de Immersive Portals
        boolean ipPresent = net.backrooms2k10.portal.BackroomsPortalService.isImmersivePortalsPresent();
        source.sendSuccess(() -> Component.literal("§eImmersive Portals: " + (ipPresent ? "§aDETECTADO (API activa)" : "§cNO DETECTADO")), false);
        return 1;
    }

    private static int executeRemovePortal(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§cEste comando debe ser ejecutado por un jugador."));
            return 0;
        }
        boolean ok = PortalManager.removeNearestModPortal(player);
        return ok ? 1 : 0;
    }

    private static int executeRemovePortals(CommandSourceStack source) {
        int count = PortalManager.removeAllModPortals(source.getLevel());
        source.sendSuccess(() -> Component.literal("§a[Backrooms] Se han eliminado todos los portales temporales (" + count + ") del mod en esta dimensión."), true);
        return count;
    }

    private static int executeHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6=== Comandos de Backrooms ==="), false);
        source.sendSuccess(() -> Component.literal("§e/backrooms create <cantidad> §7- Crea el siguiente nivel secuencial con áreas."), false);
        source.sendSuccess(() -> Component.literal("§e/backrooms portal <nivel> <targets> §7- Abre portales 3x3 para jugadores."), false);
        source.sendSuccess(() -> Component.literal("§e/backrooms tp <nivel> [target] §7- Teletransporte administrativo seguro."), false);
        source.sendSuccess(() -> Component.literal("§e/backrooms force <nivel> <targets> §7- Forzar entrada con asignación de bioma."), false);
        source.sendSuccess(() -> Component.literal("§e/backrooms spawn <nivel> [biome] §7- Teletransporta al spawn del nivel o bioma."), false);
        source.sendSuccess(() -> Component.literal("§e/backrooms return [target] §7- Regresa al jugador a su origen exacto."), false);
        source.sendSuccess(() -> Component.literal("§e/backrooms list §7- Lista todos los niveles registrados."), false);
        source.sendSuccess(() -> Component.literal("§e/backrooms info <nivel> §7- Muestra detalles y estado del nivel."), false);
        source.sendSuccess(() -> Component.literal("§e/backrooms test §7- Diagnóstico completo del estado del sistema."), false);
        source.sendSuccess(() -> Component.literal("§e/backrooms removeportal §7- Elimina el portal temporal más cercano."), false);
        source.sendSuccess(() -> Component.literal("§e/backrooms removeportals §7- Elimina todos los portales temporales del mod."), false);
        return 1;
    }
}
