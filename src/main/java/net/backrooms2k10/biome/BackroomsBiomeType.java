package net.backrooms2k10.biome;

import java.util.Random;

public enum BackroomsBiomeType {
    // 1. GRAND HALL
    GRAND_HALL("grand_hall", Category.MAIN, 20, 320, 320, 14),
    // 2. COLUMN ZONE
    COLUMN_ZONE("column_zone", Category.MAIN, 20, 300, 300, 6),
    // 3. CORRIDOR ZONE
    CORRIDOR_ZONE("corridor_zone", Category.MAIN, 20, 310, 310, 5),
    // 4. DARK ZONE
    DARK_ZONE("dark_zone", Category.MAIN, 12, 300, 300, 5),
    // 5. OFFICE ZONE
    OFFICE_ZONE("office_zone", Category.MAIN, 15, 300, 300, 5),
    // 6. MAZE ZONE
    MAZE_ZONE("maze_zone", Category.MAIN, 15, 300, 300, 5),
    // 7. LOW CEILING ZONE
    LOW_CEILING_ZONE("low_ceiling_zone", Category.MAIN, 12, 300, 300, 3),
    // 8. ENDLESS ROOM ZONE
    ENDLESS_ROOM_ZONE("endless_room_zone", Category.MAIN, 15, 315, 315, 5),

    // 9. TRANSITION ZONE
    TRANSITION_ZONE("transition_zone", Category.TRANSITION, 10, 300, 300, 7),
    // 10. DAMAGED ZONE
    DAMAGED_ZONE("damaged_zone", Category.SPECIAL_ZONE, 10, 300, 300, 5),
    // 11. MAINTENANCE ZONE
    MAINTENANCE_ZONE("maintenance_zone", Category.SPECIAL_ZONE, 10, 300, 300, 5),
    // 12. OPEN CEILING ZONE
    OPEN_CEILING_ZONE("open_ceiling_zone", Category.SPECIAL_ZONE, 10, 300, 300, 14),

    // 13. ATRIUM ZONE
    ATRIUM_ZONE("atrium_zone", Category.SPECIAL_ZONE, 4, 340, 340, 20),
    // 14. STAIRWELL ZONE
    STAIRWELL_ZONE("stairwell_zone", Category.SPECIAL_ZONE, 5, 300, 300, 16),
    // 15. FLOODED ZONE
    FLOODED_ZONE("flooded_zone", Category.SPECIAL_ZONE, 4, 300, 300, 5),
    // 16. SPECIAL ROOMS
    SPECIAL_ROOMS("special_rooms", Category.SPECIAL_ROOM, 6, 300, 300, 6);

    public enum Category {
        MAIN,
        TRANSITION,
        SPECIAL_ZONE,
        SPECIAL_ROOM
    }

    private final String id;
    private final Category category;
    private final int defaultWeight;
    private final int defaultWidth;
    private final int defaultDepth;
    private final int defaultCeilingHeight;

    BackroomsBiomeType(String id, Category category, int defaultWeight, int defaultWidth, int defaultDepth, int defaultCeilingHeight) {
        this.id = id;
        this.category = category;
        this.defaultWeight = defaultWeight;
        this.defaultWidth = defaultWidth;
        this.defaultDepth = defaultDepth;
        this.defaultCeilingHeight = defaultCeilingHeight;
    }

    public String getId() { return id; }
    public Category getCategory() { return category; }
    public int getDefaultWeight() { return defaultWeight; }
    public int getDefaultWidth() { return defaultWidth; }
    public int getDefaultDepth() { return defaultDepth; }
    public int getDefaultCeilingHeight() { return defaultCeilingHeight; }

    public static BackroomsBiomeType fromId(String id) {
        for (BackroomsBiomeType t : values()) {
            if (t.id.equalsIgnoreCase(id) || t.name().equalsIgnoreCase(id)) {
                return t;
            }
        }
        return GRAND_HALL;
    }

    public static BackroomsBiomeType getRandomBiome(Random random) {
        int totalWeight = 0;
        for (BackroomsBiomeType t : values()) {
            totalWeight += t.defaultWeight;
        }

        int roll = random.nextInt(totalWeight);
        int current = 0;
        for (BackroomsBiomeType t : values()) {
            current += t.defaultWeight;
            if (roll < current) {
                return t;
            }
        }
        return GRAND_HALL;
    }
}
