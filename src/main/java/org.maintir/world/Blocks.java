package org.maintir.world;

/**
 * Реестр блоков игры. ID = индекс в TextureArray + 1 (порядок текстур в Main).
 * 1..9 — старые блоки (обратная совместимость сохранений),
 * 10 — земля, 11..18 — ступеньки (4 стороны × доски/камень), 19..20 — дверь (низ/верх),
 * 21..24 — полублоки (доски/камень/земля/трава).
 */
public final class Blocks {
    public static final byte AIR = 0;
    public static final byte GRASS = 1;
    public static final byte STONE = 2;
    public static final byte SAND = 3;
    public static final byte MAZE_MIX = 4;   // нарост
    public static final byte WOOD = 5;       // ствол/брёвна
    public static final byte LEAVES = 6;
    public static final byte PLANKS = 7;     // доски
    public static final byte IRON_ORE = 8;
    public static final byte LAMPA = 9;
    public static final byte DIRT = 10;      // земля

    // Ступеньки: высокая часть («спина») прижата к стороне N/S/W/E
    public static final byte STAIRS_PLANKS_N = 11;
    public static final byte STAIRS_PLANKS_S = 12;
    public static final byte STAIRS_PLANKS_W = 13;
    public static final byte STAIRS_PLANKS_E = 14;
    public static final byte STAIRS_STONE_N = 15;
    public static final byte STAIRS_STONE_S = 16;
    public static final byte STAIRS_STONE_W = 17;
    public static final byte STAIRS_STONE_E = 18;

    // Дверь: два блока по высоте
    public static final byte DOOR_BOTTOM = 19;
    public static final byte DOOR_TOP = 20;

    // Полублоки (нижняя половина ячейки)
    public static final byte PLANKS_SLAB = 21;
    public static final byte STONE_SLAB = 22;
    public static final byte DIRT_SLAB = 23;
    public static final byte GRASS_SLAB = 24;

    private Blocks() {}

    public static boolean isSolid(byte id) {
        return id > 0;
    }

    /** Полный куб (обычная геометрия). Ступеньки/двери/полублоки — не полные. */
    public static boolean isFullCube(byte id) {
        return id >= GRASS && id <= DIRT;
    }

    public static boolean isStairs(byte id) {
        return id >= STAIRS_PLANKS_N && id <= STAIRS_STONE_E;
    }

    public static boolean isDoor(byte id) {
        return id == DOOR_BOTTOM || id == DOOR_TOP;
    }

    public static boolean isSlab(byte id) {
        return id >= PLANKS_SLAB && id <= GRASS_SLAB;
    }

    /** ID ступеньки по материалу и направлению «спины» (куда прижата высокая часть). */
    public static byte stairId(boolean stone, int backNx, int backNz) {
        int base = stone ? STAIRS_STONE_N : STAIRS_PLANKS_N;
        if (backNz < 0) return (byte) (base + 0);     // спина на -Z
        if (backNz > 0) return (byte) (base + 1);     // спина на +Z
        if (backNx < 0) return (byte) (base + 2);     // спина на -X
        if (backNx > 0) return (byte) (base + 3);     // спина на +X
        return (byte) (base + 1);                     // по умолчанию +Z
    }

    /** Направление «спины» (высокой части) ступеньки: (0,0,-1) N, (0,0,1) S, (-1,0,0) W, (1,0,0) E. */
    public static byte stairBackNx(byte id) {
        int v = id - STAIRS_PLANKS_N;
        int material = (v / 4) * 4; // индекс внутри группы: 0..3
        int o = v - material;
        if (o == 2) return -1;
        if (o == 3) return 1;
        return 0;
    }
    public static byte stairBackNz(byte id) {
        int v = id - STAIRS_PLANKS_N;
        int o = v % 4;
        if (o == 0) return -1;
        if (o == 1) return 1;
        return 0;
    }

    /** Слой в TextureArray игры (порядок текстур в Main). */
    public static float texLayer(byte id) {
        return switch (id) {
            case 2 -> 1.0f;
            case 3 -> 2.0f;
            case 4 -> 3.0f;
            case 5 -> 4.0f;    // ствол
            case 6 -> 5.0f;    // листва
            case 7 -> 6.0f;    // доски
            case 8 -> 7.0f;    // руда
            case 9 -> 8.0f;    // лампа
            case 10 -> 9.0f;   // земля
            case 11, 12, 13, 14 -> 6.0f; // ступеньки из досок
            case 15, 16, 17, 18 -> 1.0f; // ступеньки из камня
            case 19 -> 10.0f;  // дверь (низ)
            case 20 -> 11.0f;  // дверь (верх)
            case 21 -> 6.0f;   // полублок из досок
            case 22 -> 1.0f;   // каменный полублок
            case 23 -> 9.0f;   // земляной полублок
            case 24 -> 0.0f;   // травяной полублок
            default -> 0.0f;   // трава
        };
    }

    public static String name(byte id) {
        return switch (id) {
            case 1 -> "ТРАВА";
            case 2 -> "КАМЕНЬ";
            case 3 -> "ПЕСОК";
            case 4 -> "НАРОСТ";
            case 5 -> "БРЁВНА";
            case 6 -> "ЛИСТВА";
            case 7 -> "ДОСКИ";
            case 8 -> "РУДА";
            case 9 -> "ЛАМПА";
            case 10 -> "ЗЕМЛЯ";
            case 11, 12, 13, 14 -> "СТУПЕНЬКИ";
            case 15, 16, 17, 18 -> "КАМЕНЬ-СТУПЕНЬКИ";
            case 19, 20 -> "ДВЕРЬ";
            case 21 -> "ПОЛУБЛОК-ДОСКИ";
            case 22 -> "КАМЕНЬ-ПОЛУБЛОК";
            case 23 -> "ЗЕМЛЯ-ПОЛУБЛОК";
            case 24 -> "ТРАВА-ПОЛУБЛОК";
            default -> "?";
        };
    }
}