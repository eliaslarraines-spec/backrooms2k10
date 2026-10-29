package net.backrooms2k10.generator;

import net.backrooms2k10.biome.BackroomsBiomeType;
import net.backrooms2k10.persistence.BackroomsSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * ProceduralGenerator:
 * Generador procedural paramétrico bajo demanda para los 16 tipos de biomas/zonas de Backrooms.
 * Soporta variaciones infinitas con seeds deterministas, techos variables, paredes, alfombra amarilla,
 * iluminación fluorescente (Sea Lanterns / Ochre Froglights / Glowstone), puertas norte y zonas especiales.
 */
public class ProceduralGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Backrooms2K10");

    // Paleta visual clásica de Level 0
    private static final BlockState FLOOR_CARPET = Blocks.YELLOW_CARPET.defaultBlockState();
    private static final BlockState FLOOR_BASE = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
    private static final BlockState WALL_MAIN = Blocks.YELLOW_CONCRETE_POWDER.defaultBlockState();
    private static final BlockState WALL_SECONDARY = Blocks.SANDSTONE.defaultBlockState();
    private static final BlockState WALL_OFFICE = Blocks.WHITE_TERRACOTTA.defaultBlockState();
    private static final BlockState CEILING_TILE = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
    private static final BlockState LIGHT_BLOCK = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState COLUMN_BLOCK = Blocks.CUT_SANDSTONE.defaultBlockState();

    public static synchronized void generateAreaIfNeeded(ServerLevel level, BackroomsSavedData.AreaData area) {
        if (area.generated) return;

        LOGGER.info("[Backrooms] Generando proceduralmente área '{}' [{}] en [{}, {}]",
                area.id, area.biomeType, area.minX, area.minZ);

        Random random = new Random(area.seed);
        BackroomsBiomeType type = BackroomsBiomeType.fromId(area.biomeType);

        int minX = area.minX;
        int maxX = area.maxX;
        int minZ = area.minZ;
        int maxZ = area.maxZ;
        int groundY = area.groundY; // Suelo en Y=1 (Smooth Sandstone generado por Flat generator)
        int structBaseY = 2;        // Estructuras, paredes y puertas a Y>=2

        int ceilingHeight = type.getDefaultCeilingHeight();

        // 1. Perímetro exterior (paredes exteriores) desde Y=2 hasta 2 + ceilingHeight
        for (int y = structBaseY; y <= structBaseY + ceilingHeight; y++) {
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), WALL_MAIN, 18);
                level.setBlock(new BlockPos(x, y, maxZ), WALL_MAIN, 18);
            }
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(minX, y, z), WALL_MAIN, 18);
                level.setBlock(new BlockPos(maxX, y, z), WALL_MAIN, 18);
            }
        }

        // 2. Techo e iluminación
        if (type != BackroomsBiomeType.OPEN_CEILING_ZONE) {
            int ceilY = structBaseY + ceilingHeight + 1;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean isLight = false;
                    if (type != BackroomsBiomeType.DARK_ZONE) {
                        // Fluorescentes regulares cada 8 bloques
                        if ((x - minX) % 8 == 4 && (z - minZ) % 8 == 4) {
                            isLight = true;
                        }
                    } else {
                        // Zona oscura: solo 1% de luces parpadeantes
                        if (random.nextFloat() < 0.01f) {
                            isLight = true;
                        }
                    }

                    BlockPos cPos = new BlockPos(x, ceilY, z);
                    level.setBlock(cPos, isLight ? LIGHT_BLOCK : CEILING_TILE, 18);
                }
            }
        }

        // 3. Caso especial Flooded Zone (agua poco profunda sobre el suelo Y=1 a nivel Y=2)
        if (type == BackroomsBiomeType.FLOODED_ZONE) {
            for (int x = minX + 2; x <= maxX - 2; x += 3) {
                for (int z = minZ + 2; z <= maxZ - 2; z += 3) {
                    if (random.nextFloat() < 0.7f) {
                        level.setBlock(new BlockPos(x, structBaseY, z), Blocks.WATER.defaultBlockState(), 18);
                    }
                }
            }
        }

        // 4. Arquitectura interna según BiomeType (usando structBaseY=2 como base)
        switch (type) {
            case GRAND_HALL -> generateGrandHall(level, area, random, structBaseY, ceilingHeight);
            case COLUMN_ZONE -> generateColumnZone(level, area, random, structBaseY, ceilingHeight);
            case CORRIDOR_ZONE -> generateCorridorZone(level, area, random, structBaseY, ceilingHeight);
            case DARK_ZONE -> generateDarkZone(level, area, random, structBaseY, ceilingHeight);
            case OFFICE_ZONE -> generateOfficeZone(level, area, random, structBaseY, ceilingHeight);
            case MAZE_ZONE -> generateMazeZone(level, area, random, structBaseY, ceilingHeight);
            case LOW_CEILING_ZONE -> generateLowCeilingZone(level, area, random, structBaseY, ceilingHeight);
            case ENDLESS_ROOM_ZONE -> generateEndlessRoomZone(level, area, random, structBaseY, ceilingHeight);
            case TRANSITION_ZONE -> generateTransitionZone(level, area, random, structBaseY, ceilingHeight);
            case DAMAGED_ZONE -> generateDamagedZone(level, area, random, structBaseY, ceilingHeight);
            case MAINTENANCE_ZONE -> generateMaintenanceZone(level, area, random, structBaseY, ceilingHeight);
            case OPEN_CEILING_ZONE -> generateOpenCeilingZone(level, area, random, structBaseY, ceilingHeight);
            case ATRIUM_ZONE -> generateAtriumZone(level, area, random, structBaseY, ceilingHeight);
            case STAIRWELL_ZONE -> generateStairwellZone(level, area, random, structBaseY, ceilingHeight);
            case FLOODED_ZONE -> generateFloodedZone(level, area, random, structBaseY, ceilingHeight);
            case SPECIAL_ROOMS -> generateSpecialRooms(level, area, random, structBaseY, ceilingHeight);
        }

        // 5. Instalar puerta norte sobre el piso Y=1 (a Y=2 y Y=3)
        installNorthDoor(level, area);

        // 6. Despejar zona de spawn del jugador frente a la puerta norte
        clearSpawnPoint(level, area);

        // 7. Despejar zona 3x3 del portal de salida aleatorio dentro del bioma
        clearExitPoint(level, area);

        area.generated = true;
        BackroomsSavedData.get(level).setDirty();
        LOGGER.info("[Backrooms] Área '{}' generada exitosamente sobre suelo Y={}.", area.id, groundY);
    }

    private static void installNorthDoor(ServerLevel level, BackroomsSavedData.AreaData area) {
        int doorX = area.northDoor.getX();
        int doorZ = area.northDoor.getZ();
        int doorY = 2; // Sobre suelo amarillo en Y=1

        BlockPos lowerPos = new BlockPos(doorX, doorY, doorZ);
        BlockPos upperPos = lowerPos.above();

        BlockState lowerDoor = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);

        BlockState upperDoor = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);

        level.setBlock(lowerPos, lowerDoor, 3);
        level.setBlock(upperPos, upperDoor, 3);

        // Despejar pasillo frente y detrás de la puerta (aire a Y=2, 3, 4)
        for (int dz = -2; dz <= 3; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dz == 0 && dx == 0) continue;
                for (int y = 2; y <= 4; y++) {
                    level.setBlock(new BlockPos(doorX + dx, y, doorZ + dz), Blocks.AIR.defaultBlockState(), 18);
                }
            }
        }
    }

    private static void clearSpawnPoint(ServerLevel level, BackroomsSavedData.AreaData area) {
        if (area.spawnPoint == null) return;
        int spX = area.spawnPoint.getX();
        int spZ = area.spawnPoint.getZ();

        // 5x5 área totalmente despejada a Y=2, 3, 4 con piso de arenisca amarilla en Y=1
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(new BlockPos(spX + dx, 1, spZ + dz), FLOOR_BASE, 18);
                for (int y = 2; y <= 4; y++) {
                    level.setBlock(new BlockPos(spX + dx, y, spZ + dz), Blocks.AIR.defaultBlockState(), 18);
                }
            }
        }
    }

    private static void clearExitPoint(ServerLevel level, BackroomsSavedData.AreaData area) {
        if (area.exitPoint == null) return;
        int exitX = area.exitPoint.getX();
        int exitZ = area.exitPoint.getZ();

        // 3x3 área completamente despejada a Y=2, 3, 4 y piso firme a Y=1
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos floorPos = new BlockPos(exitX + dx, 1, exitZ + dz);
                level.setBlock(floorPos, FLOOR_BASE, 18);

                for (int y = 2; y <= 4; y++) {
                    level.setBlock(new BlockPos(exitX + dx, y, exitZ + dz), Blocks.AIR.defaultBlockState(), 18);
                }
            }
        }
    }

    // --- Biome Generators ---

    private static void generateGrandHall(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Enormes espacios abiertos, muy pocas columnas masivas
        int minX = area.minX + 8;
        int maxX = area.maxX - 8;
        int minZ = area.minZ + 8;
        int maxZ = area.maxZ - 8;

        for (int x = minX; x <= maxX; x += 18) {
            for (int z = minZ; z <= maxZ; z += 18) {
                if (random.nextFloat() < 0.6f) {
                    buildPillar(level, new BlockPos(x, floorY, z), 2, 2, h, COLUMN_BLOCK);
                }
            }
        }
    }

    private static void generateColumnZone(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Gran cantidad de columnas densas e irregulares que bloquean la visión
        for (int x = area.minX + 5; x <= area.maxX - 5; x += 6) {
            for (int z = area.minZ + 5; z <= area.maxZ - 5; z += 6) {
                if (random.nextFloat() < 0.75f) {
                    int colSize = random.nextBoolean() ? 1 : 2;
                    buildPillar(level, new BlockPos(x, floorY, z), colSize, colSize, h, COLUMN_BLOCK);
                }
            }
        }
    }

    private static void generateCorridorZone(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Pasillos largos, giros en 90 grados y callejones sin salida
        for (int x = area.minX + 6; x <= area.maxX - 6; x += 8) {
            for (int z = area.minZ + 6; z <= area.maxZ - 6; z++) {
                if (random.nextFloat() < 0.7f) {
                    for (int y = floorY; y <= floorY + h; y++) {
                        level.setBlock(new BlockPos(x, y, z), WALL_MAIN, 2);
                    }
                }
            }
        }
    }

    private static void generateDarkZone(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Igual que Level 0 pero sin luces y algunas paredes derruidas
        generateCorridorZone(level, area, random, floorY, h);
    }

    private static void generateOfficeZone(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Cubículos y pequeñas salas de reunión
        for (int x = area.minX + 8; x <= area.maxX - 8; x += 10) {
            for (int z = area.minZ + 8; z <= area.maxZ - 8; z += 10) {
                // Paredes de cubículo a media altura
                for (int cx = 0; cx < 8; cx++) {
                    level.setBlock(new BlockPos(x + cx, floorY, z), WALL_OFFICE, 2);
                    level.setBlock(new BlockPos(x + cx, floorY + 1, z), WALL_OFFICE, 2);
                }
                for (int cz = 0; cz < 8; cz++) {
                    level.setBlock(new BlockPos(x, floorY, z + cz), WALL_OFFICE, 2);
                    level.setBlock(new BlockPos(x, floorY + 1, z + cz), WALL_OFFICE, 2);
                }
            }
        }
    }

    private static void generateMazeZone(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Laberinto denso
        for (int x = area.minX + 4; x <= area.maxX - 4; x += 4) {
            for (int z = area.minZ + 4; z <= area.maxZ - 4; z += 4) {
                boolean wallX = random.nextBoolean();
                for (int step = 0; step < 4; step++) {
                    for (int y = floorY; y <= floorY + h; y++) {
                        BlockPos p = wallX ? new BlockPos(x + step, y, z) : new BlockPos(x, y, z + step);
                        level.setBlock(p, WALL_SECONDARY, 2);
                    }
                }
            }
        }
    }

    private static void generateLowCeilingZone(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Techos muy bajos (3 bloques), sensación de claustrofobia
        for (int x = area.minX + 4; x <= area.maxX - 4; x += 6) {
            for (int z = area.minZ + 4; z <= area.maxZ - 4; z += 6) {
                buildPillar(level, new BlockPos(x, floorY, z), 1, 1, h, WALL_MAIN);
            }
        }
    }

    private static void generateEndlessRoomZone(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Salas repetitivas de 7x7 con puertas
        for (int x = area.minX + 7; x <= area.maxX - 7; x += 7) {
            for (int z = area.minZ + 7; z <= area.maxZ - 7; z += 7) {
                for (int y = floorY; y <= floorY + h; y++) {
                    level.setBlock(new BlockPos(x, y, z), COLUMN_BLOCK, 2);
                }
            }
        }
    }

    private static void generateTransitionZone(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Interpolación gradual de altura y columnas desde el norte hacia el sur
        int minZ = area.minZ;
        int maxZ = area.maxZ;
        for (int z = minZ; z <= maxZ; z += 8) {
            float progress = (float) (z - minZ) / (maxZ - minZ);
            int currentH = (int) (12 - (progress * 7)); // 12 -> 5
            for (int x = area.minX + 5; x <= area.maxX - 5; x += 8) {
                if (random.nextFloat() < progress) {
                    buildPillar(level, new BlockPos(x, floorY, z), 1, 1, currentH, COLUMN_BLOCK);
                }
            }
        }
    }

    private static void generateDamagedZone(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Grietas, alfombra rota y agua/lodo
        for (int x = area.minX + 2; x <= area.maxX - 2; x++) {
            for (int z = area.minZ + 2; z <= area.maxZ - 2; z++) {
                if (random.nextFloat() < 0.15f) {
                    level.setBlock(new BlockPos(x, floorY, z), Blocks.MUD.defaultBlockState(), 2);
                }
            }
        }
        generateCorridorZone(level, area, random, floorY, h);
    }

    private static void generateMaintenanceZone(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Tuberías de hierro en el techo y paredes técnicas
        int ceilY = floorY + h;
        for (int x = area.minX + 4; x <= area.maxX - 4; x += 8) {
            for (int z = area.minZ + 4; z <= area.maxZ - 4; z++) {
                level.setBlock(new BlockPos(x, ceilY - 1, z), Blocks.IRON_BARS.defaultBlockState(), 2);
            }
        }
    }

    private static void generateOpenCeilingZone(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Sin techo continuo, vigas expuestas de hierro/cadenas
        for (int x = area.minX + 6; x <= area.maxX - 6; x += 6) {
            for (int z = area.minZ + 6; z <= area.maxZ - 6; z += 6) {
                buildPillar(level, new BlockPos(x, floorY, z), 2, 2, h + 8, Blocks.CHAIN.defaultBlockState());
            }
        }
    }

    private static void generateAtriumZone(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Enorme espacio central con balcones perimetrales
        int midX = area.minX + area.width / 2;
        int midZ = area.minZ + area.depth / 2;
        int radius = 15;

        for (int y = floorY + 4; y <= floorY + 12; y += 4) {
            // Pasarelas en balcones
            for (int x = midX - radius; x <= midX + radius; x++) {
                level.setBlock(new BlockPos(x, y, midZ - radius), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, y, midZ + radius), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 2);
            }
            for (int z = midZ - radius; z <= midZ + radius; z++) {
                level.setBlock(new BlockPos(midX - radius, y, z), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 2);
                level.setBlock(new BlockPos(midX + radius, y, z), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 2);
            }
        }
    }

    private static void generateStairwellZone(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Escaleras que suben y bajan de forma anómala
        int sx = area.minX + 10;
        int sz = area.minZ + 10;
        for (int step = 0; step < 12; step++) {
            level.setBlock(new BlockPos(sx + step, floorY + step, sz), Blocks.SANDSTONE_STAIRS.defaultBlockState(), 2);
        }
    }

    private static void generateFloodedZone(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Agua en el piso y luces apagadas
        generateCorridorZone(level, area, random, floorY, h);
    }

    private static void generateSpecialRooms(ServerLevel level, BackroomsSavedData.AreaData area, Random random, int floorY, int h) {
        // Habitaciones anómalas con geometría extraña
        int cx = area.minX + area.width / 2;
        int cz = area.minZ + area.depth / 2;
        buildPillar(level, new BlockPos(cx - 2, floorY, cz - 2), 4, 4, h, Blocks.OBSIDIAN.defaultBlockState());
    }

    private static void buildPillar(ServerLevel level, BlockPos base, int sizeX, int sizeZ, int height, BlockState state) {
        for (int y = 0; y < height; y++) {
            for (int dx = 0; dx < sizeX; dx++) {
                for (int dz = 0; dz < sizeZ; dz++) {
                    level.setBlock(base.offset(dx, y, dz), state, 2);
                }
            }
        }
    }
}
