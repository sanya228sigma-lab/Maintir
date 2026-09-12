package org.maintir.world;

import java.util.Random;

public class TerrainGenerator {

    public enum BiomeType {
        ALL,        // Все биомы с плавными переходами
        PLAINS,     // Поляны: ровные луга, деревья почти отсутствуют
        FOREST,     // Лес: густые леса на пологом рельефе
        DESERT,     // Только пустыня
        HILLS,      // Только холмы: трава и немного деревьев
        MOUNTAINS,  // Только горы
        MAZE;       // Наш баго-биомовый лабиринт!

        /** Русское название биома для меню. */
        public String displayName() {
            return switch (this) {
                case ALL -> "ВСЕ";
                case PLAINS -> "ПОЛЯНЫ";
                case FOREST -> "ЛЕС";
                case DESERT -> "ПУСТЫНЯ";
                case HILLS -> "ХОЛМЫ";
                case MOUNTAINS -> "ГОРЫ";
                case MAZE -> "ЛАБИРИНТ";
            };
        }
    }

    // ID блоков = индексы в TextureArray Main.java:
    // 0 - воздух, 1 - Trava, 2 - kamen, 3 - pesok, 4 - Trava_s_naskom, 5 - dyb(ствол),
    // 6 - listva(листва), 7 - doski(доски), 8 - zheleznaya_ruda
    private static final byte GRASS = 1;
    private static final byte STONE = 2;
    private static final byte SAND = 3;
    private static final byte MAZE_MIX = 4;
    private static final byte WOOD = 5;
    private static final byte LEAVES = 6;
    private static final byte PLANKS = 7;
    private static final byte IRON_ORE = 8;
    private static final byte DIRT = 10;   // земля под слоем травы

    // Полублоки (ID такие же, как в Blocks.java)
    private static final byte PLANKS_SLAB = 21;
    private static final byte STONE_SLAB = 22;
    private static final byte DIRT_SLAB = 23;
    private static final byte GRASS_SLAB = 24;

    // Границы климата для режима ALL (климат ~[-0.9..0.9], пик вокруг нуля)
    private static final double CLIMATE_DESERT_START = -0.30;  // ниже - чистая пустыня
    private static final double CLIMATE_PLAINS_START = -0.18;  // переход пустыня->поляны закончился
    private static final double CLIMATE_HILLS_START  =  0.12;  // начало перехода поляны->холмы
    private static final double CLIMATE_HILLS_FULL   =  0.22;
    private static final double CLIMATE_MOUNT_START  =  0.40;  // начало перехода холмы->горы (широкие предгорья)
    private static final double CLIMATE_MOUNT_FULL   =  0.56;

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    /**
     * Горы: многооктавный ридж-шум с искажением координат.
     * Гребень в первой степени (округлые вершины), мягкая степень и
     * умеренная амплитуда дают плавные реалистичные склоны, а маска
     * массивов делает разные горные регионы разной высоты.
     */
    private static double mountainsHeight(double wx, double wz) {
        // Доменное искажение — кривые хребты вместо прямых стен
        double wpx = wx + 35 * SimplexNoise.noise(wx * 0.008 + 31.4, wz * 0.008 - 17.7);
        double wpz = wz + 35 * SimplexNoise.noise(wx * 0.008 - 45.3, wz * 0.008 + 88.1);

        // 3 октавы: крупный рельеф + лёгкие детали (без резких зазубрин)
        double r1 = 1.0 - Math.abs(SimplexNoise.noise(wpx * 0.007, wpz * 0.007));
        double r2 = 1.0 - Math.abs(SimplexNoise.noise(wpx * 0.016 + 50.0, wpz * 0.016 - 33.0));
        double r3 = 1.0 - Math.abs(SimplexNoise.noise(wpx * 0.038 - 77.0, wpz * 0.038 + 21.0));

        // Гребень в первой степени -> округлые вершины; мелкие октавы лишь слегка рельефят склоны
        double ridged = r1 * (0.65 + 0.25 * r2 + 0.10 * r3) + 0.10 * r2 * r2;

        // Разные горные массивы имеют разную высоту (не одна монотонная стена)
        double massif = 0.55 + 0.45 * SimplexNoise.noise(wx * 0.0016 + 400.0, wz * 0.0016 - 250.0);

        // Мягкая степень и умеренная амплитуда: пологие длинные склоны без "стен"
        return 56 + Math.pow(ridged, 1.3) * 74 * massif;
    }

    public static void generateChunkTerrain(ChunkPos position, byte[][][] blocks, int sizeX, int sizeY, int sizeZ, long seed, BiomeType selectedBiome) {
        int startX = position.x() * sizeX;
        int startZ = position.z() * sizeZ;

        // Смещение по сиду
        double seedOffsetX = (seed % 10000) * 100.0;
        double seedOffsetZ = ((seed / 10000) % 10000) * 100.0;

        double[][] heightMap = new double[sizeX][sizeZ];
        double[][] climateMap = new double[sizeX][sizeZ];

        // --- Проход 1: высоты и климат ---
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                double worldX = startX + x + seedOffsetX;
                double worldZ = startZ + z + seedOffsetZ;

                double climate = SimplexNoise.noise(worldX * 0.001, worldZ * 0.001);
                climateMap[x][z] = climate;

                double desertH = 45 + SimplexNoise.noise(worldX * 0.015, worldZ * 0.015) * 6;
                // Поляны: почти плоские луга
                double plainsH = 44 + SimplexNoise.noise(worldX * 0.02, worldZ * 0.02) * 5;
                // Холмы: плавные округлые возвышенности
                double hillsH = 55 + SimplexNoise.noise(worldX * 0.01, worldZ * 0.01) * 13
                        + SimplexNoise.noise(worldX * 0.035, worldZ * 0.035) * 4;
                // Лес: пологий рельеф, слегка волнистый
                double forestH = 52 + SimplexNoise.noise(worldX * 0.012, worldZ * 0.012) * 10
                        + SimplexNoise.noise(worldX * 0.03, worldZ * 0.03) * 3;

                double mountainsH = mountainsHeight(worldX, worldZ);

                double h;
                switch (selectedBiome) {
                    case DESERT -> h = desertH;
                    case PLAINS -> h = plainsH;
                    case FOREST -> h = forestH;
                    case HILLS -> h = hillsH;
                    case MOUNTAINS -> h = mountainsH;
                    case MAZE -> h = 55 + SimplexNoise.noise(worldX * 0.02, worldZ * 0.02) * 8;
                    default -> { // ALL: широкие полосы биомов с плавными переходами
                        if (climate < CLIMATE_DESERT_START) h = desertH;
                        else if (climate < CLIMATE_PLAINS_START)
                            h = lerp(desertH, plainsH, (climate - CLIMATE_DESERT_START) / (CLIMATE_PLAINS_START - CLIMATE_DESERT_START));
                        else if (climate < CLIMATE_HILLS_START) h = plainsH;
                        else if (climate < CLIMATE_HILLS_FULL)
                            h = lerp(plainsH, hillsH, (climate - CLIMATE_HILLS_START) / (CLIMATE_HILLS_FULL - CLIMATE_HILLS_START));
                        else if (climate < CLIMATE_MOUNT_START) h = hillsH;
                        else if (climate < CLIMATE_MOUNT_FULL)
                            h = lerp(hillsH, mountainsH, (climate - CLIMATE_MOUNT_START) / (CLIMATE_MOUNT_FULL - CLIMATE_MOUNT_START));
                        else h = mountainsH;
                    }
                }
                heightMap[x][z] = h;
            }
        }

        Random rnd = new Random(seed * 31L + position.x() * 73856093L + position.z() * 19349663L);

        // --- Проход 2: заполнение блоков + деревья ---
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                double worldX = startX + x + seedOffsetX;
                double worldZ = startZ + z + seedOffsetZ;

                double climate = climateMap[x][z];
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
                    topBlock = SAND;
                } else if (selectedBiome == BiomeType.MAZE) {
                    topBlock = (mazeNoise > 0.2) ? GRASS : (mazeNoise > -0.2) ? MAZE_MIX : SAND;
                } else if (selectedBiome == BiomeType.PLAINS || selectedBiome == BiomeType.FOREST) {
                    topBlock = GRASS;
                } else if (selectedBiome == BiomeType.HILLS) {
                    topBlock = (slope > 2.2 || height > 75) ? STONE : GRASS;
                } else if (selectedBiome == BiomeType.MOUNTAINS) {
                    topBlock = (slope > 1.6 || height > 100) ? STONE : GRASS;
                } else {
                    // ALL BIOMES
                    if (slope > 1.8 || height > 120) topBlock = STONE;
                    else if (climate < -0.24) topBlock = SAND;
                    else if (climate < -0.14) topBlock = (mazeNoise > 0.2) ? GRASS : (mazeNoise > -0.2) ? MAZE_MIX : SAND;
                    else topBlock = GRASS;
                }

                for (int y = 0; y < height; y++) {
                    if (y == height - 1) blocks[x][y][z] = topBlock;
                    else if (y > height - 4) {
                        // Под травой — слой земли, под песком/наростом — песок
                        if (topBlock == GRASS) blocks[x][y][z] = (y > height - 3) ? DIRT : STONE;
                        else blocks[x][y][z] = (topBlock == SAND || topBlock == MAZE_MIX) ? SAND : STONE;
                    }
                    else {
                        // Железная руда жилами в глубоком камне
                        byte b = STONE;
                        if (y > 2 && y < height - 6 && ironVein(worldX, y, worldZ)) b = IRON_ORE;
                        blocks[x][y][z] = b;
                    }
                }

                // --- Пещеры: вырезаем туннели после заполнения ---
                for (int y = 3; y < height; y++) {
                    if (isCave(startX + x, y, startZ + z, height, seed)) {
                        blocks[x][y][z] = 0;
                    }
                }
                boolean carvedSurface = blocks[x][height - 1][z] == 0;

                // --- Полублоки на склонах: «ступеньки» рельефа в переходах земли и в горах ---
                boolean madeSlab = false;
                if (!carvedSurface && slope >= 1.3 && height >= 3) {
                    double slabNoise = SimplexNoise.noise(worldX * 0.055 + 40.0, worldZ * 0.055 - 20.0);
                    if (slabNoise > -0.1) {
                        byte slabId = 0;
                        if (topBlock == STONE) {
                            slabId = STONE_SLAB;                       // горы — каменные полублоки
                        } else if (topBlock == GRASS) {
                            slabId = (slope >= 2.0) ? DIRT_SLAB : GRASS_SLAB; // переходы — земля/трава
                        }
                        if (slabId != 0) {
                            blocks[x][height - 1][z] = slabId;
                            if (blocks[x][height - 2][z] == STONE) blocks[x][height - 2][z] = DIRT;
                            madeSlab = true;
                        }
                    }
                }

                // --- Деревья ---
                if (topBlock != GRASS || carvedSurface || madeSlab) continue;
                // Лесные массивы: пятна густого леса внутри полян и холмов (режим ALL)
                double forestNoise = SimplexNoise.noise(worldX * 0.004 + 900.0, worldZ * 0.004 - 700.0);
                double density = treeDensity(selectedBiome, climate, forestNoise, height);
                if (density <= 0) continue;
                if (slope > 1.6) continue;
                // Крона радиусом 2 должна целиком помещаться в чанк
                if (x < 2 || x >= sizeX - 2 || z < 2 || z >= sizeZ - 2) continue;

                if (rnd.nextDouble() < density) {
                    placeTree(blocks, x, z, height, sizeY, rnd);
                }
            }
        }
    }

    /** Порог лесного массива: где шум выше — густой лес даже среди полян. */
    private static final double FOREST_PATCH_THRESHOLD = 0.30;

    /** Общий множитель плотности деревьев (задаётся в меню мира, 0..2). */
    public static double treeDensityScale = 1.0;

    /**
     * Плотность деревьев на блок для текущей колонки.
     */
    private static double treeDensity(BiomeType biome, double climate, double forestNoise, int height) {
        double d = switch (biome) {
            case PLAINS -> 0.004;                               // поляны: единичные деревья
            case FOREST -> 0.16;                                // лес: очень густо
            case HILLS -> 0.015;                                // холмы: немного деревьев
            case ALL -> {
                if (climate < CLIMATE_PLAINS_START) yield 0.0;  // пустыня и переход — без деревьев
                if (forestNoise > FOREST_PATCH_THRESHOLD) yield 0.13; // лесные массивы
                if (climate < CLIMATE_HILLS_START) yield 0.004; // поляны
                if (climate < CLIMATE_MOUNT_START) yield 0.015; // холмы
                yield height < 95 ? 0.004 : 0.0;                // нижние склоны гор
            }
            default -> 0.0;                                     // пустыня, горы, лабиринт
        };
        return d * treeDensityScale;
    }

    /**
     * Ставит дерево: ствол 4-6 блоков + крона из листвы.
     * groundY — уровень первой воздушной ячейки над поверхностью.
     */
    private static void placeTree(byte[][][] blocks, int baseX, int baseZ, int groundY, int sizeY, Random rnd) {
        int trunkH = 4 + rnd.nextInt(3); // 4..6
        int canopyY = groundY + trunkH - 1;   // верхний блок ствола
        if (canopyY + 2 >= sizeY) return;

        // Крона: два широких слоя, затем 3x3 и крест на макушке
        for (int dy = -2; dy <= 1; dy++) {
            int y = canopyY + dy;
            int r = (dy <= -1) ? 2 : 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dy == 1 && Math.abs(dx) + Math.abs(dz) > 1) continue; // макушка-крест
                    boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                    if (corner && (dy == 1 || rnd.nextBoolean())) continue;   // срезаем углы
                    setIfAir(blocks, baseX + dx, y, baseZ + dz, LEAVES);
                }
            }
        }

        // Ствол поверх листвы, чтобы не было дыр
        for (int i = 0; i < trunkH; i++) {
            blocks[baseX][groundY + i][baseZ] = WOOD;
        }
    }

    private static void setIfAir(byte[][][] blocks, int x, int y, int z, byte block) {
        if (x < 0 || x >= blocks.length || z < 0 || z >= blocks[0][0].length || y < 0 || y >= blocks[0].length) return;
        if (blocks[x][y][z] == 0) blocks[x][y][z] = block;
    }

    // ================= Пещеры =================

    /**
     * Спагетти-туннели: пересечение двух 3D-шумов около нуля.
     * Чем глубже под поверхностью, тем шире туннель (у поверхности — редкие входы).
     */
    private static boolean isCave(int wx, int wy, int wz, int surfaceY, long seed) {
        double depth = surfaceY - wy;
        // Порог: у поверхности узко (0.03), глубже 12 блоков — широко (0.12)
        double t = depth >= 12 ? 0.12 : 0.03 + 0.09 * (depth / 12.0);

        // Частоты подобраны так, чтобы туннели были в каждом втором-третьем чанке
        double n1 = valueNoise3(wx * 0.028, wy * 0.05, wz * 0.028, seed);
        if (Math.abs(n1) > t) return false;
        double n2 = valueNoise3(wx * 0.028 + 91.7, wy * 0.05 + 33.3, wz * 0.028 - 77.7, seed ^ 0x9E3779B97F4A7C15L);
        return Math.abs(n2) < t;
    }

    /** Железные жилы: пересечение двух наклонных 2D-шумов даёт связные кластеры. */
    private static boolean ironVein(double wx, double wy, double wz) {
        double n1 = SimplexNoise.noise(wx * 0.11 + wy * 0.13, wz * 0.11 - wy * 0.07);
        if (n1 < 0.52) return false;
        double n2 = SimplexNoise.noise(wz * 0.12 - wy * 0.09 + 512.0, wx * 0.06 + 256.0);
        return n2 > 0.52;
    }

    /** Быстрый 3D value-noise на целочисленном хеше, диапазон [-1..1]. */
    private static double valueNoise3(double x, double y, double z, long seed) {
        long xi = (long) Math.floor(x);
        long yi = (long) Math.floor(y);
        long zi = (long) Math.floor(z);

        double fx = fade(x - xi), fy = fade(y - yi), fz = fade(z - zi);

        double c000 = h01(hash3(xi, yi, zi, seed));
        double c100 = h01(hash3(xi + 1, yi, zi, seed));
        double c010 = h01(hash3(xi, yi + 1, zi, seed));
        double c110 = h01(hash3(xi + 1, yi + 1, zi, seed));
        double c001 = h01(hash3(xi, yi, zi + 1, seed));
        double c101 = h01(hash3(xi + 1, yi, zi + 1, seed));
        double c011 = h01(hash3(xi, yi + 1, zi + 1, seed));
        double c111 = h01(hash3(xi + 1, yi + 1, zi + 1, seed));

        double x00 = lerp(c000, c100, fx), x10 = lerp(c010, c110, fx);
        double x01 = lerp(c001, c101, fx), x11 = lerp(c011, c111, fx);
        double y0 = lerp(x00, x10, fy), y1 = lerp(x01, x11, fy);
        return lerp(y0, y1, fz) * 2.0 - 1.0;
    }

    private static long hash3(long x, long y, long z, long seed) {
        long h = seed * 6364136223846793005L;
        h ^= x * 374761393L;
        h = Long.rotateLeft(h, 13);
        h ^= y * 668265263L;
        h = Long.rotateLeft(h, 17);
        h ^= z * 1274126177L;
        return h * 6364136223846793005L;
    }

    private static double h01(long h) {
        return ((h >>> 11) * (1.0 / 9007199254740992.0)); // [0..1)
    }

    private static double fade(double t) {
        return t * t * (3.0 - 2.0 * t);
    }
}
