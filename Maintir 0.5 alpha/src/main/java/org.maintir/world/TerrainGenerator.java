package org.maintir.world;

public class TerrainGenerator {

    public enum BiomeType {
        ALL,        // Все биомы с плавными переходами
        DESERT,     // Только пустыня
        HILLS,      // Только холмы
        MOUNTAINS,  // Только горы
        MAZE        // Наш баго-биомовый лабиринт!
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    public static void generateChunkTerrain(ChunkPos position, byte[][][] blocks, int sizeX, int sizeY, int sizeZ, long seed, BiomeType selectedBiome) {
        int startX = position.x() * sizeX;
        int startZ = position.z() * sizeZ;

        // Смещение по сиду
        double seedOffsetX = (seed % 10000) * 100.0;
        double seedOffsetZ = ((seed / 10000) % 10000) * 100.0;

        double[][] heightMap = new double[sizeX][sizeZ];

        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                double worldX = startX + x + seedOffsetX;
                double worldZ = startZ + z + seedOffsetZ;

                double climate = SimplexNoise.noise(worldX * 0.001, worldZ * 0.001);

                double desert = 45 + SimplexNoise.noise(worldX * 0.015, worldZ * 0.015) * 6;
                double hills = 60 + SimplexNoise.noise(worldX * 0.01, worldZ * 0.01) * 15;

                double ridge1 = 1.0 - Math.abs(SimplexNoise.noise(worldX * 0.006, worldZ * 0.006));
                double ridge2 = 1.0 - Math.abs(SimplexNoise.noise(worldX * 0.015, worldZ * 0.015));
                double mountains = 75 + (ridge1 * ridge1) * 65 + (ridge2 * 15);

                double h;
                switch (selectedBiome) {
                    case DESERT -> h = desert;
                    case HILLS -> h = hills;
                    case MOUNTAINS -> h = mountains;
                    case MAZE -> h = 55 + SimplexNoise.noise(worldX * 0.02, worldZ * 0.02) * 8;
                    default -> { // ALL
                        if (climate < -0.15) h = desert;
                        else if (climate < 0.05) h = lerp(desert, hills, (climate - (-0.15)) / 0.20);
                        else if (climate < 0.25) h = hills;
                        else if (climate < 0.45) h = lerp(hills, mountains, (climate - 0.25) / 0.20);
                        else h = mountains;
                    }
                }
                heightMap[x][z] = h;
            }
        }

        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                double worldX = startX + x + seedOffsetX;
                double worldZ = startZ + z + seedOffsetZ;

                double climate = SimplexNoise.noise(worldX * 0.001, worldZ * 0.001);
                double mazeNoise = SimplexNoise.noise(worldX * 0.15, worldZ * 0.15);

                int height = (int) Math.max(1, Math.min(heightMap[x][z], sizeY - 1));

                double slope = 0;
                if (x > 0 && z > 0) {
                    double dx = Math.abs(heightMap[x][z] - heightMap[x - 1][z]);
                    double dz = Math.abs(heightMap[x][z] - heightMap[x][z - 1]);
                    slope = Math.max(dx, dz);
                }

                byte topBlock;

                if (selectedBiome == BiomeType.DESERT) {
                    topBlock = 3; // Чистый песок
                } else if (selectedBiome == BiomeType.MAZE) {
                    topBlock = (mazeNoise > 0.2) ? (byte)1 : (mazeNoise > -0.2) ? (byte)4 : (byte)3;
                } else if (selectedBiome == BiomeType.HILLS) {
                    topBlock = 1; // Трава
                } else if (selectedBiome == BiomeType.MOUNTAINS) {
                    topBlock = (slope > 1.2 || height > 85) ? (byte)2 : (byte)1;
                } else {
                    // ALL BIOMES
                    if (slope > 1.4 || height > 88) topBlock = 2;
                    else if (climate < -0.25) topBlock = 3;
                    else if (climate < -0.05) topBlock = (mazeNoise > 0.2) ? (byte)1 : (mazeNoise > -0.2) ? (byte)4 : (byte)3;
                    else topBlock = 1;
                }

                for (int y = 0; y < height; y++) {
                    if (y == height - 1) blocks[x][y][z] = topBlock;
                    else if (y > height - 4) blocks[x][y][z] = (topBlock == 3 || topBlock == 4) ? (byte)3 : (byte)2;
                    else blocks[x][y][z] = 2;
                }
            }
        }
    }
}