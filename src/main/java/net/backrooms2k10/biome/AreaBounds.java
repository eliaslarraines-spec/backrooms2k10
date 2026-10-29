package net.backrooms2k10.biome;

public class AreaBounds {
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;

    public AreaBounds(int minX, int minZ, int maxX, int maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public int getMinX() { return minX; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxZ() { return maxZ; }

    public int getWidth() { return maxX - minX + 1; }
    public int getDepth() { return maxZ - minZ + 1; }

    public int getCenterX() { return minX + (getWidth() / 2); }
    public int getCenterZ() { return minZ + (getDepth() / 2); }

    /**
     * Verifica si se superpone con otra área o si viola la separación mínima en bloques.
     * @param other Otra área
     * @param minSeparation Separación mínima obligatoria entre bordes (ej: 300 bloques)
     */
    public boolean intersectsWithMargin(AreaBounds other, int minSeparation) {
        // Expandir los límites por minSeparation y verificar solapamiento AABB
        int expMinX = this.minX - minSeparation;
        int expMaxX = this.maxX + minSeparation;
        int expMinZ = this.minZ - minSeparation;
        int expMaxZ = this.maxZ + minSeparation;

        return !(other.maxX < expMinX || other.minX > expMaxX ||
                 other.maxZ < expMinZ || other.minZ > expMaxZ);
    }

    /**
     * Calcula la distancia mínima entre los bordes de dos áreas.
     */
    public int distanceTo(AreaBounds other) {
        int dx = 0;
        if (this.maxX < other.minX) {
            dx = other.minX - this.maxX;
        } else if (other.maxX < this.minX) {
            dx = this.minX - other.maxX;
        }

        int dz = 0;
        if (this.maxZ < other.minZ) {
            dz = other.minZ - this.maxZ;
        } else if (other.maxZ < this.minZ) {
            dz = this.minZ - other.maxZ;
        }

        return Math.max(dx, dz);
    }
}
