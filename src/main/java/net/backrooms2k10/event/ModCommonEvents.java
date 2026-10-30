package net.backrooms2k10.event;

import net.backrooms2k10.Backrooms2K10;
import net.backrooms2k10.core.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ModCommonEvents:
 * Maneja las reglas básicas y estables del mundo para backrooms:nivel_0.
 */
@Mod.EventBusSubscriber(modid = Backrooms2K10.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCommonEvents {

    /**
     * Regla: Solo jugadores pueden viajar a Backrooms Nivel 0.
     * Evita que entidades pasivas u hostiles crucen automáticamente la dimensión.
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
     * Regla: Cero generación natural de mobs vanilla en Backrooms Nivel 0.
     * Cancela exclusivamente spawns de mobs vanilla en la dimensión nivel_0.
     * No modifica el comportamiento de Overworld, Nether o End.
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
