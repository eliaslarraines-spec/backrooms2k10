package net.backrooms2k10.portal;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BackroomsPortalService:
 * Capa central y unificada responsable de integrar Backrooms con la entidad Portal
 * nativa de Immersive Portals (qouteall.imm_ptl.core.portal.Portal).
 *
 * Características:
 * - Creación de portales reales de Immersive Portals (sin bloques falsos, sin anchors, sin marcas ficticias).
 * - Portales 3x3 HORIZONTALES con normal apuntando verticalmente hacia arriba (UP).
 * - Portal de ENTRADA: mirando hacia abajo se visualiza el cielo de la dimensión Backrooms.
 * - Portal de SALIDA: mirando hacia abajo se visualiza la dimensión de origen (Overworld).
 * - Acceso exclusivo individual por jugador (specificPlayerId).
 * - Eliminación atómica una vez cruzado el portal.
 */
public class BackroomsPortalService {
    private static final Logger LOGGER = LoggerFactory.getLogger("Backrooms2K10");

    public static final String TAG_PORTAL = "backrooms_portal";
    public static final String TAG_ENTRANCE = "backrooms_entrance_portal";
    public static final String TAG_EXIT = "backrooms_exit_portal";

    private static Boolean ipPresent = null;
    private static final Map<UUID, List<UUID>> MANAGED_PORTALS = new ConcurrentHashMap<>();

    public static boolean isImmersivePortalsPresent() {
        if (ipPresent == null) {
            ipPresent = ModList.get().isLoaded("immersive_portals") || ModList.get().isLoaded("imm_ptl_core");
            LOGGER.info("[Backrooms] Immersive Portals: {}", ipPresent ? "DETECTADO" : "NO DETECTADO");
        }
        return ipPresent;
    }

    /**
     * Crea un Portal REAL 3x3 HORIZONTAL de Immersive Portals para ENTRADA hacia Backrooms.
     * Ubicación: directamente bajo los pies del jugador (normal UP).
     * Rotación: 180° sobre el eje horizontal para que mirar hacia abajo revele el cielo de Backrooms.
     * Acceso: específico para el jugador asignado.
     */
    public static UUID createEntrancePortal(
            ServerLevel originLevel,
            Vec3 originPos,
            ResourceKey<Level> destDim,
            Vec3 destPos,
            ServerPlayer player
    ) {
        if (!isImmersivePortalsPresent()) {
            LOGGER.warn("[Backrooms] Immersive Portals no está presente; no se puede instanciar la entidad Portal.");
            return UUID.randomUUID();
        }

        try {
            EntityType<?> entityType = getPortalEntityType();
            if (entityType == null) {
                LOGGER.error("[Backrooms] Error crítico: No se encontró EntityType de Portal de Immersive Portals.");
                return null;
            }

            Entity portalEntity = entityType.create(originLevel);
            if (portalEntity == null) {
                LOGGER.error("[Backrooms] Error crítico: No se pudo instanciar Entity Portal.");
                return null;
            }

            Class<?> portalClass = Class.forName("qouteall.imm_ptl.core.portal.Portal");

            // 1. Posición de origen (ligeramente elevado sobre el suelo para visibilidad inmediata y contacto)
            Method setOriginPos = portalClass.getMethod("setOriginPos", Vec3.class);
            setOriginPos.invoke(portalEntity, originPos);

            // 2. Dimensión y posición de destino
            Method setDestDim = portalClass.getMethod("setDestinationDimension", ResourceKey.class);
            setDestDim.invoke(portalEntity, destDim);

            Method setDestPos = portalClass.getMethod("setDestination", Vec3.class);
            setDestPos.invoke(portalEntity, destPos);

            // 3. Orientación horizontal 3x3 con normal UP:
            // axisW = Este (1, 0, 0), axisH = Norte (0, 0, -1) -> Normal = (1,0,0) x (0,0,-1) = (0, 1, 0) [UP]
            Method setOrientationAndSize = portalClass.getMethod("setOrientationAndSize", Vec3.class, Vec3.class, double.class, double.class);
            setOrientationAndSize.invoke(portalEntity, new Vec3(1, 0, 0), new Vec3(0, 0, -1), 3.0, 3.0);

            // 4. Rotación de 180° alrededor de X (axisW):
            // Al mirar hacia abajo (-Y) desde arriba, el rayo de visión apunta hacia arriba (+Y) en el destino,
            // mostrando directamente el cielo estrellado de Backrooms.
            setPortal180Rotation(portalEntity, portalClass);

            // 5. Restricción individual por jugador (accessor de IP)
            if (player != null) {
                try {
                    Field specificPlayerField = portalClass.getField("specificPlayerId");
                    specificPlayerField.set(portalEntity, player.getUUID());
                } catch (Throwable t) {
                    LOGGER.warn("[Backrooms] specificPlayerId no accesible: {}", t.getMessage());
                }
            }

            // 6. Propiedades de teletransporte
            try {
                Field teleportableField = portalClass.getField("teleportable");
                teleportableField.set(portalEntity, true);
            } catch (Throwable ignored) {}

            try {
                Method setChangesScale = portalClass.getMethod("setTeleportChangesScale", boolean.class);
                setChangesScale.invoke(portalEntity, false);
            } catch (Throwable ignored) {}

            try {
                Method setChangesGravity = portalClass.getMethod("setTeleportChangesGravity", boolean.class);
                setChangesGravity.invoke(portalEntity, false);
            } catch (Throwable ignored) {}

            // 7. Tags e inserción al mundo
            UUID portalUuid = portalEntity.getUUID();
            portalEntity.addTag(TAG_PORTAL);
            portalEntity.addTag(TAG_ENTRANCE);
            portalEntity.addTag(TAG_PORTAL + "_" + portalUuid);
            if (player != null) {
                portalEntity.addTag("player_" + player.getUUID());
            }

            originLevel.addFreshEntity(portalEntity);

            List<UUID> list = new ArrayList<>();
            list.add(portalUuid);
            MANAGED_PORTALS.put(portalUuid, list);

            LOGGER.info("[Backrooms] Portal REAL de entrada 3x3 creado exitosamente en {} hacia {} en {}",
                    originPos, destDim.location(), destPos);
            return portalUuid;
        } catch (Throwable t) {
            LOGGER.error("[Backrooms] Error al crear portal de entrada de Immersive Portals: ", t);
            return null;
        }
    }

    /**
     * Crea un Portal REAL 3x3 HORIZONTAL de Immersive Portals para SALIDA desde Backrooms.
     * Ubicación: dentro del bioma asignado sobre el suelo amarillo Y=1 (pos Y=1.05, normal UP).
     * Destino: exactamente el punto original de retorno guardado para ese jugador.
     * Acceso: exclusivo del jugador asignado.
     */
    public static UUID createExitPortal(
            ServerLevel backroomsLevel,
            Vec3 originPos,
            ResourceKey<Level> returnDim,
            Vec3 returnPos,
            float returnYaw,
            float returnPitch,
            ServerPlayer player
    ) {
        if (!isImmersivePortalsPresent()) {
            LOGGER.warn("[Backrooms] Immersive Portals no está presente; no se puede instanciar portal de salida.");
            return UUID.randomUUID();
        }

        try {
            EntityType<?> entityType = getPortalEntityType();
            if (entityType == null) {
                LOGGER.error("[Backrooms] Error: No se pudo obtener EntityType de Portal para salida.");
                return null;
            }

            Entity portalEntity = entityType.create(backroomsLevel);
            if (portalEntity == null) {
                LOGGER.error("[Backrooms] Error: No se pudo instanciar Entity Portal de salida.");
                return null;
            }

            Class<?> portalClass = Class.forName("qouteall.imm_ptl.core.portal.Portal");

            // 1. Origen a ras del suelo del área en Backrooms
            Method setOriginPos = portalClass.getMethod("setOriginPos", Vec3.class);
            setOriginPos.invoke(portalEntity, originPos);

            // 2. Destino exacto de retorno
            Method setDestDim = portalClass.getMethod("setDestinationDimension", ResourceKey.class);
            setDestDim.invoke(portalEntity, returnDim);

            Method setDestPos = portalClass.getMethod("setDestination", Vec3.class);
            setDestPos.invoke(portalEntity, returnPos);

            // 3. Orientación horizontal 3x3 con normal UP
            Method setOrientationAndSize = portalClass.getMethod("setOrientationAndSize", Vec3.class, Vec3.class, double.class, double.class);
            setOrientationAndSize.invoke(portalEntity, new Vec3(1, 0, 0), new Vec3(0, 0, -1), 3.0, 3.0);

            // 4. Rotación para que la vista coincida
            setPortal180Rotation(portalEntity, portalClass);

            // 5. Restricción individual por jugador
            if (player != null) {
                try {
                    Field specificPlayerField = portalClass.getField("specificPlayerId");
                    specificPlayerField.set(portalEntity, player.getUUID());
                } catch (Throwable ignored) {}
            }

            // 6. Configuración de teletransporte
            try {
                Field teleportableField = portalClass.getField("teleportable");
                teleportableField.set(portalEntity, true);
            } catch (Throwable ignored) {}

            try {
                Method setChangesScale = portalClass.getMethod("setTeleportChangesScale", boolean.class);
                setChangesScale.invoke(portalEntity, false);
            } catch (Throwable ignored) {}

            try {
                Method setChangesGravity = portalClass.getMethod("setTeleportChangesGravity", boolean.class);
                setChangesGravity.invoke(portalEntity, false);
            } catch (Throwable ignored) {}

            // 7. Tags e inserción
            UUID portalUuid = portalEntity.getUUID();
            portalEntity.addTag(TAG_PORTAL);
            portalEntity.addTag(TAG_EXIT);
            portalEntity.addTag(TAG_PORTAL + "_" + portalUuid);
            if (player != null) {
                portalEntity.addTag("player_" + player.getUUID());
            }

            backroomsLevel.addFreshEntity(portalEntity);

            List<UUID> list = new ArrayList<>();
            list.add(portalUuid);
            MANAGED_PORTALS.put(portalUuid, list);

            LOGGER.info("[Backrooms] Portal REAL de salida 3x3 creado exitosamente en {} hacia {} en {}",
                    originPos, returnDim.location(), returnPos);
            return portalUuid;
        } catch (Throwable t) {
            LOGGER.error("[Backrooms] Error al crear portal de salida de Immersive Portals: ", t);
            return null;
        }
    }

    private static void setPortal180Rotation(Entity portalEntity, Class<?> portalClass) {
        try {
            Class<?> dQuatClass = Class.forName("qouteall.q_misc_util.my_util.DQuaternion");
            Method rotByDeg = dQuatClass.getMethod("rotationByDegrees", Vec3.class, double.class);
            Object quat = rotByDeg.invoke(null, new Vec3(1, 0, 0), 180.0);
            Method setRot = portalClass.getMethod("setRotationTransformation", dQuatClass);
            setRot.invoke(portalEntity, quat);
        } catch (Throwable t) {
            LOGGER.warn("[Backrooms] No se pudo establecer rotación 180° de DQuaternion: {}", t.getMessage());
        }
    }

    private static EntityType<?> getPortalEntityType() {
        try {
            Class<?> ipRegistryClass = Class.forName("qouteall.imm_ptl.core.platform_specific.IPRegistry");
            Object regObj = ipRegistryClass.getField("PORTAL").get(null);
            if (regObj != null) {
                return (EntityType<?>) regObj.getClass().getMethod("get").invoke(regObj);
            }
        } catch (Throwable ignored) {}

        return ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("immersive_portals", "portal"));
    }

    /**
     * Elimina físicamente la entidad Portal del mundo de forma segura y atómica.
     */
    public static void removePortal(ServerLevel level, UUID portalUuid) {
        if (level == null || portalUuid == null) return;

        MANAGED_PORTALS.remove(portalUuid);

        Entity entity = level.getEntity(portalUuid);
        if (entity != null) {
            entity.discard();
            LOGGER.info("[Backrooms] Entidad Portal eliminada por UUID: {}", portalUuid);
            return;
        }

        // Búsqueda por tag en caso de recarga
        String tag = TAG_PORTAL + "_" + portalUuid;
        for (Entity e : level.getAllEntities()) {
            if (e.getTags().contains(tag)) {
                e.discard();
                LOGGER.info("[Backrooms] Entidad Portal eliminada por tag: {}", tag);
            }
        }
    }

    /**
     * Limpia todos los portales temporales generados por el mod en una dimensión.
     */
    public static int removeAllPortalsInLevel(ServerLevel level) {
        if (level == null) return 0;
        int count = 0;
        for (Entity e : level.getAllEntities()) {
            if (e.getTags().contains(TAG_PORTAL)) {
                e.discard();
                count++;
            }
        }
        LOGGER.info("[Backrooms] Eliminados {} portales en {}", count, level.dimension().location());
        return count;
    }

    /**
     * Limpia todos los portales temporales del mod en todas las dimensiones del servidor.
     */
    public static int removeAllPortalsServerWide(MinecraftServer server) {
        if (server == null) return 0;
        int total = 0;
        MANAGED_PORTALS.clear();
        for (ServerLevel level : server.getAllLevels()) {
            total += removeAllPortalsInLevel(level);
        }
        return total;
    }

    public static void clearMemory() {
        MANAGED_PORTALS.clear();
    }
}
