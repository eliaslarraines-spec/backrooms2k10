package net.backrooms2k10.event;

import net.backrooms2k10.Backrooms2K10;
import net.backrooms2k10.command.CommandManager;
import net.backrooms2k10.core.ModDimensions;
import net.backrooms2k10.persistence.BackroomsSavedData;
import net.backrooms2k10.persistence.DimensionManager;
import net.backrooms2k10.portal.PortalManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Backrooms2K10.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCommonEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandManager.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (event.getServer() != null && event.getServer().overworld() != null) {
            // Inicializar SavedData del mundo actual
            BackroomsSavedData.get(event.getServer().overworld());
            // Verificar e inicializar el pool de dimensiones (0..99) en el servidor
            DimensionManager.initializeDimensionPoolOnServerStart(event.getServer());
        }
        PortalManager.clearAllMemoryState();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PortalManager.clearAllMemoryState();
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PortalManager.onPlayerDimensionChange(player, event.getFrom(), event.getTo());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer serverPlayer) {
            PortalManager.checkPlayerTick(serverPlayer);
        }
    }

    /**
     * Regla: No copiar entidades del Overworld a Backrooms.
     * Solo los jugadores pueden atravesar hacia dimensiones de Backrooms.
     */
    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (ModDimensions.isBackroomsDimension(event.getDimension())) {
            if (!(event.getEntity() instanceof ServerPlayer)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Regla: Cero generación natural de mobs vanilla en Backrooms.
     * Cancela exclusivamente spawns naturales / de generación de chunks en dimensiones Backrooms.
     * Los mobs especiales de Backrooms se implementarán posteriormente.
     */
    @SubscribeEvent
    public static void onMobSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (ModDimensions.isBackroomsDimension(level.dimension())) {
                MobSpawnType type = event.getSpawnType();
                if (type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION
                        || type == MobSpawnType.SPAWNER || type == MobSpawnType.STRUCTURE
                        || type == MobSpawnType.PATROL || type == MobSpawnType.REINFORCEMENT
                        || type == MobSpawnType.EVENT) {
                    event.setSpawnCancelled(true);
                    event.setResult(Event.Result.DENY);
                }
            }
        }
    }
}
