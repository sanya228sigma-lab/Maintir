package org.maintir.world;

import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Чанк 30×175×30: блоки, воксельный свет и меш.
 * Вертекс: pos3 + uv2 + normal3 + texIndex1 + skyLight1 + blockLight1 = 11 float.
 * Свет на вершину — среднее по 4 ячейкам у угла грани (мягкие градиенты и круги от ламп).
 */
public class Chunk {
    public static final int SIZE_X = 30;
    public static final int SIZE_Y = 175;
    public static final int SIZE_Z = 30;

    private final ChunkPos position;
    private final byte[][][] blocks = new byte[SIZE_X][SIZE_Y][SIZE_Z];

    // Упакованный свет внутренней области: (sky<<4)|block
    private volatile byte[] light = new byte[SIZE_X * SIZE_Y * SIZE_Z];
    private volatile boolean fullyLit = false;      // посчитан со всеми загруженными соседями

    // Массив вершин, рассчитанный в фоновом потоке
    private volatile float[] meshData;
    private volatile int vertexCount = 0;
    private volatile boolean meshBuilt = false;

    private int vao, vbo;
    private volatile boolean uploadedToGPU = false;
    private volatile boolean gpuStale = false;      // данные пересчитаны, нужно перезалить

    public Chunk(ChunkPos position, long seed, TerrainGenerator.BiomeType biome) {
        this.position = position;
        TerrainGenerator.generateChunkTerrain(position, blocks, SIZE_X, SIZE_Y, SIZE_Z, seed, biome);
    }

    /** Первичная установка света и меша при генерации (фоновый поток). */
    public void installGenerated(Lighting.Pad pad) {
        absorbLight(pad);
        fullyLit = pad.completeNeighbors;
        generateMeshCPU(pad);
    }

    /** Пересчёт света/меша существующего чанка (фоновый поток; GL трогать нельзя). */
    public void relightData(Lighting.Pad pad) {
        absorbLight(pad);
        fullyLit = pad.completeNeighbors;
        generateMeshCPU(pad);
        gpuStale = true; // главный поток заметит и перезальёт буфер
    }

    /** Копирует внутреннюю область пада в компактный массив света чанка. */
    private void absorbLight(Lighting.Pad pad) {
        byte[] dst = new byte[SIZE_X * SIZE_Y * SIZE_Z];
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int y = 0; y < SIZE_Y; y++) {
                    dst[packIndex(x, y, z)] = pad.light[pad.idx(x + Lighting.MARGIN, y, z + Lighting.MARGIN)];
                }
            }
        }
        light = dst;
    }

    public boolean isFullyLit() {
        return fullyLit;
    }

    public int getSkyLight(int lx, int y, int lz) {
        return (light[packIndex(lx, y, lz)] >> 4) & 0xF;
    }

    public int getBlockLight(int lx, int y, int lz) {
        return light[packIndex(lx, y, lz)] & 0xF;
    }

    public static int packIndex(int lx, int y, int lz) {
        return lx * SIZE_Y * SIZE_Z + y * SIZE_Z + lz;
    }

    /** Применяет сохранённые изменения блоков (вызвать ДО расчёта света и меша). */
    public void applyOverrides(java.util.Map<Integer, Byte> overrides) {
        if (overrides == null) return;
        for (java.util.Map.Entry<Integer, Byte> e : overrides.entrySet()) {
            int idx = e.getKey();
            int lx = idx / (SIZE_Y * SIZE_Z);
            int rem = idx % (SIZE_Y * SIZE_Z);
            int y = rem / SIZE_Z;
            int lz = rem % SIZE_Z;
            blocks[lx][y][lz] = e.getValue();
        }
    }

    /**
     * Сборка меша. Вершины пишем в МИРОВЫХ координатах (шейдер использует model = identity),
     * свет берём из пада (индекс пада = локальные координаты + MARGIN).
     * Вызывается в фоновом потоке (никакого OpenGL!).
     */
    public void generateMeshCPU(Lighting.Pad pad) {
        int originX = position.x() * SIZE_X;
        int originZ = position.z() * SIZE_Z;

        // Верхний непустой блок каждой колонки — чтобы не сканировать пустое небо
        int[] colTop = new int[SIZE_X * SIZE_Z];
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int y = SIZE_Y - 1; y >= 0; y--) {
                    if (blocks[x][y][z] != 0) { colTop[z * SIZE_X + x] = y; break; }
                }
            }
        }

        float[] buf = new float[8192 * 11];
        int pointer = 0;

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                int topY = colTop[z * SIZE_X + x];
                for (int y = 0; y <= topY; y++) {
                    byte blockType = blocks[x][y][z];
                    if (blockType == 0) continue;

                    float texIndex = Blocks.texLayer(blockType);

                    // Достаточно места под максимум граней одного блока (24)
                    if (buf.length - pointer < 24 * 11) {
                        buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                    }

                    if (Blocks.isStairs(blockType)) {
                        pointer = addStairs(buf, pointer, x, y, z, originX, originZ, blockType, texIndex, pad);
                    } else if (Blocks.isSlab(blockType)) {
                        pointer = addSlab(buf, pointer, x, y, z, originX, originZ, blockType, texIndex, pad);
                    } else if (Blocks.isDoor(blockType)) {
                        // Двери рисует DoorRenderer динамически (анимация открывания) — в статике пропускаем
                    } else {
                        pointer = addCube(buf, pointer, x, y, z, originX, originZ, blockType, texIndex, pad);
                    }
                }
            }
        }

        vertexCount = pointer / 11;
        meshData = vertexCount > 0 ? java.util.Arrays.copyOf(buf, pointer) : null;
        meshBuilt = true;
    }

    private int addCube(float[] buf, int ptr, int lx, int y, int lz, int ox, int oz, byte id, float tex,
                        Lighting.Pad pad) {
        ptr = cubeFace(buf, ptr, lx, y, lz, ox, oz, id, tex, 0, 1, 0, pad);
        ptr = cubeFace(buf, ptr, lx, y, lz, ox, oz, id, tex, 0, -1, 0, pad);
        ptr = cubeFace(buf, ptr, lx, y, lz, ox, oz, id, tex, 0, 0, 1, pad);
        ptr = cubeFace(buf, ptr, lx, y, lz, ox, oz, id, tex, 0, 0, -1, pad);
        ptr = cubeFace(buf, ptr, lx, y, lz, ox, oz, id, tex, 1, 0, 0, pad);
        return cubeFace(buf, ptr, lx, y, lz, ox, oz, id, tex, -1, 0, 0, pad);
    }

    /**
     * Грань полного куба: закрыта соседом-кубом или спиной ступеньки;
     * напротив плиты ступеньки рисуем только верхнюю половину.
     */
    private int cubeFace(float[] buf, int ptr, int lx, int y, int lz, int ox, int oz, byte id, float tex,
                         int nx, int ny, int nz, Lighting.Pad pad) {
        float emitYFrom = partialCoverY(lx + nx, y + ny, lz + nz, nx, ny, nz);
        if (emitYFrom < 0f) return ptr; // полностью закрыто
        return emitFace(buf, ptr, lx, y, lz, ox, oz, 0, 0, 0, 1, 1, 1, nx, ny, nz, tex, emitYFrom, pad);
    }

    /** Возвращает y-отрез (0..1) от которого видим грань, либо -1 (закрыта целиком). */
    private float partialCoverY(int nx2, int ny2, int nz2, int nx, int ny, int nz) {
        byte nb = neighborAt(nx2, ny2, nz2);
        if (nb == 0) return 0f;
        if (Blocks.isFullCube(nb)) return -1f;
        if (Blocks.isDoor(nb)) return 0f; // дверь в середине ячейки, грань видна целиком
        if (Blocks.isSlab(nb) && (nx != 0 || nz != 0)) return 0.5f; // полублок закрывает нижнюю половину
        if (!Blocks.isStairs(nb)) return 0f;
        if (nx == 0 && nz == 0) return 0f; // горизонтальные грани не режутся
        int bnX = Blocks.stairBackNx(nb), bnZ = Blocks.stairBackNz(nb);
        if ((nx != 0 && bnX == nx) || (nz != 0 && bnZ == nz)) return -1f; // спина закрывает всё
        if ((nx != 0 && bnX == -nx) || (nz != 0 && bnZ == -nz)) return 0.5f; // напротив плиты
        return 0f;
    }

    /** Ступенька: высокая часть («спина») + низкая плита («перед»). */
    private int addStairs(float[] buf, int ptr, int lx, int y, int lz, int ox, int oz,
                          byte id, float tex, Lighting.Pad pad) {
        int bnX = Blocks.stairBackNx(id), bnZ = Blocks.stairBackNz(id);
        float bx0 = 0f, bx1 = 1f, bz0 = 0f, bz1 = 1f;
        int cutX = 0, cutZ = 0; // внутренняя грань между спиной и плитой
        if (bnX != 0) {
            if (bnX < 0) { bx0 = 0f; bx1 = 0.5f; }
            else         { bx0 = 0.5f; bx1 = 1f; }
            cutX = -bnX; // направление скоса (к переду)
        } else {
            if (bnZ < 0) { bz0 = 0f; bz1 = 0.5f; }
            else         { bz0 = 0.5f; bz1 = 1f; }
            cutZ = -bnZ;
        }

        // Спина — полный полубокс (внутренняя грань к плите пропускается)
        ptr = stairBox(buf, ptr, lx, y, lz, ox, oz, bx0, 0, bz0, bx1, 1, bz1, tex, pad,
                cutX, 0, cutZ);

        // Плита — передняя нижняя половина (внутренняя грань к спине пропускается)
        float fx0 = bx0, fx1 = bx1, fz0 = bz0, fz1 = bz1;
        if (bnX != 0) {
            if (bnX < 0) { fx1 = 1f; } else { fx0 = 0f; }
        } else {
            if (bnZ < 0) { fz1 = 1f; } else { fz0 = 0f; }
        }
        ptr = stairBox(buf, ptr, lx, y, lz, ox, oz, fx0, 0, fz0, fx1, 0.5f, fz1, tex, pad,
                -cutX, 0, -cutZ);

        return ptr;
    }

    /** Грани полубокса ступеньки; внутренняя грань к плите пропускается. */
    private int stairBox(float[] buf, int ptr, int lx, int y, int lz, int ox, int oz,
                         float bx0, float by0, float bz0, float bx1, float by1, float bz1,
                         float tex, Lighting.Pad pad, int skipX, int skipY, int skipZ) {
        return boxFaces(buf, ptr, lx, y, lz, ox, oz,
                bx0, by0, bz0, bx1, by1, bz1, tex, pad, skipX, skipY, skipZ);
    }

    private int boxFaces(float[] buf, int ptr, int lx, int y, int lz, int ox, int oz,
                         float bx0, float by0, float bz0, float bx1, float by1, float bz1,
                         float tex, Lighting.Pad pad, int skipX, int skipY, int skipZ) {
        if (skipY != 1) ptr = partialFace(buf, ptr, lx, y, lz, ox, oz, bx0, by0, bz0, bx1, by1, bz1, 0, 1, 0, tex, pad);
        if (skipY != -1) ptr = partialFace(buf, ptr, lx, y, lz, ox, oz, bx0, by0, bz0, bx1, by1, bz1, 0, -1, 0, tex, pad);
        if (skipZ != 1) ptr = partialFace(buf, ptr, lx, y, lz, ox, oz, bx0, by0, bz0, bx1, by1, bz1, 0, 0, 1, tex, pad);
        if (skipZ != -1) ptr = partialFace(buf, ptr, lx, y, lz, ox, oz, bx0, by0, bz0, bx1, by1, bz1, 0, 0, -1, tex, pad);
        if (skipX != 1) ptr = partialFace(buf, ptr, lx, y, lz, ox, oz, bx0, by0, bz0, bx1, by1, bz1, 1, 0, 0, tex, pad);
        if (skipX != -1) ptr = partialFace(buf, ptr, lx, y, lz, ox, oz, bx0, by0, bz0, bx1, by1, bz1, -1, 0, 0, tex, pad);
        return ptr;
    }

    /**
     * Грань полубокса с учётом соседа: закрыта кубом, спиной ступеньки
     * или полной половиной, иначе показывается (при необходимости только верхняя часть).
     */
    private int partialFace(float[] buf, int ptr, int lx, int y, int lz, int ox, int oz,
                            float bx0, float by0, float bz0, float bx1, float by1, float bz1,
                            int nx, int ny, int nz, float tex, Lighting.Pad pad) {
        float emitYFrom = partialCoverY(lx + nx, y + ny, lz + nz, nx, ny, nz);
        if (emitYFrom < 0f) return ptr;
        return emitFace(buf, ptr, lx, y, lz, ox, oz, bx0, by0, bz0, bx1, by1, bz1, nx, ny, nz, tex, emitYFrom, pad);
    }

    /** Полублок: нижняя половина ячейки [0..0.5] по Y. */
    private int addSlab(float[] buf, int ptr, int lx, int y, int lz, int ox, int oz,
                        byte id, float tex, Lighting.Pad pad) {
        float bx0 = 0f, by0 = 0f, bz0 = 0f, bx1 = 1f, by1 = 0.5f, bz1 = 1f;

        // Верхняя грань: скрыта полным кубом (полублок над нами — воздух между, грань видна)
        byte up = neighborAt(lx, y + 1, lz);
        if (!Blocks.isFullCube(up)) {
            ptr = partialFace(buf, ptr, lx, y, lz, ox, oz, bx0, by0, bz0, bx1, by1, bz1, 0, 1, 0, tex, pad);
        }

        // Нижняя грань: скрыта полным кубом или полублоком (совпадает плоскость)
        byte down = neighborAt(lx, y - 1, lz);
        if (!Blocks.isFullCube(down) && !Blocks.isSlab(down)) {
            ptr = partialFace(buf, ptr, lx, y, lz, ox, oz, bx0, by0, bz0, bx1, by1, bz1, 0, -1, 0, tex, pad);
        }

        // Боковые грани: закрыты полным кубом или соседним полублоком того же уровня (совпадение плоскостей)
        ptr = slabSide(buf, ptr, lx, y, lz, ox, oz, bx0, by0, bz0, bx1, by1, bz1, 1, 0, 0, tex, pad);
        ptr = slabSide(buf, ptr, lx, y, lz, ox, oz, bx0, by0, bz0, bx1, by1, bz1, -1, 0, 0, tex, pad);
        ptr = slabSide(buf, ptr, lx, y, lz, ox, oz, bx0, by0, bz0, bx1, by1, bz1, 0, 0, 1, tex, pad);
        return slabSide(buf, ptr, lx, y, lz, ox, oz, bx0, by0, bz0, bx1, by1, bz1, 0, 0, -1, tex, pad);
    }

    private int slabSide(float[] buf, int ptr, int lx, int y, int lz, int ox, int oz,
                         float bx0, float by0, float bz0, float bx1, float by1, float bz1,
                         int nx, int ny, int nz, float tex, Lighting.Pad pad) {
        byte nb = neighborAt(lx + nx, y + ny, lz + nz);
        if (Blocks.isFullCube(nb)) return ptr;
        if (Blocks.isSlab(nb)) return ptr; // копланарная грань соседнего полублока — убрать z-fight
        return partialFace(buf, ptr, lx, y, lz, ox, oz, bx0, by0, bz0, bx1, by1, bz1, nx, ny, nz, tex, pad);
    }

    private static float[] ensureCapacity(float[] buf, int pointer) {
        return buf.length - pointer < 6 * 11 ? java.util.Arrays.copyOf(buf, buf.length * 2) : buf;
    }

    public boolean isUploadedToGPU() {
        return uploadedToGPU;
    }

    public byte getBlock(int x, int y, int z) {
        if (x < 0 || x >= SIZE_X || z < 0 || z >= SIZE_Z || y < 0 || y >= SIZE_Y) return 0;
        return blocks[x][y][z];
    }

    /** Меняет блок без пересчёта меша (меш пересчитает вызывающий через пад). */
    public void setBlock(int x, int y, int z, byte blockId) {
        if (x < 0 || x >= SIZE_X || z < 0 || z >= SIZE_Z || y < 0 || y >= SIZE_Y) return;
        blocks[x][y][z] = blockId;
    }

    /**
     * Главный поток: если данные меша пересчитаны в фоне — удалить старые
     * GL-буферы, чтобы render()/uploadToGPU() залили свежие.
     */
    public void refreshGpu() {
        if (gpuStale && uploadedToGPU) {
            glDeleteBuffers(vbo);
            glDeleteVertexArrays(vao);
            vao = 0;
            vbo = 0;
            uploadedToGPU = false;
            gpuStale = false;
        } else if (gpuStale) {
            gpuStale = false;
        }
    }

    // 2. ВЫЗЫВАЕТСЯ В ГЛАВНОМ ПОТОКЕ (Загрузка в GPU)
    public void uploadToGPU() {
        if (!meshBuilt || uploadedToGPU || vertexCount == 0 || meshData == null) return;

        FloatBuffer buffer = BufferUtils.createFloatBuffer(meshData.length);
        buffer.put(meshData).flip();

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

        int stride = 11 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);                  // Pos
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);    // UV
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5 * Float.BYTES);    // Normal
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 8 * Float.BYTES);    // TexIndex
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 9 * Float.BYTES);    // SkyLight 0..1
        glEnableVertexAttribArray(4);
        glVertexAttribPointer(5, 1, GL_FLOAT, false, stride, 10 * Float.BYTES);   // BlockLight 0..1
        glEnableVertexAttribArray(5);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        meshData = null; // Очищаем массив в ОЗУ, он больше не нужен
        uploadedToGPU = true;
    }

    /** Блок соседа во внутреннем массиве (за границей чанка — воздух). */
    private byte neighborAt(int x, int y, int z) {
        if (y < 0 || y >= SIZE_Y) return 0;
        if (x < 0 || x >= SIZE_X || z < 0 || z >= SIZE_Z) return 0;
        return blocks[x][y][z];
    }

    /**
     * Добавляет одну грань бокса в МИРОВЫХ координатах (зейдер работает с model = identity),
     * с мягким освещением: для каждого угла усредняется свет 4 ячеек пада вокруг угла.
     * emitYFrom > 0 режет вертикальную грань снизу (для перекрытых плитой частей).
     * Индекс пада = мировые координаты - origin + MARGIN (по Y без сдвига).
     */
    private int emitFace(float[] buf, int ptr,
                         int lx, int y, int lz, int ox, int oz,
                         float bx0, float by0, float bz0, float bx1, float by1, float bz1,
                         int nx, int ny, int nz, float texIndex, float emitYFrom,
                         Lighting.Pad pad) {
        float X0 = ox + lx + bx0, X1 = ox + lx + bx1;
        float Y0 = y + Math.max(by0, emitYFrom), Y1 = y + by1;
        float Z0 = oz + lz + bz0, Z1 = oz + lz + bz1;
        if (Y1 <= Y0 + 1e-5f) return ptr; // загрань целиком ниже среза

        float[][] p;
        if (ny == 1)       p = new float[][]{{X0, Y1, Z1}, {X1, Y1, Z1}, {X1, Y1, Z0}, {X0, Y1, Z0}};
        else if (ny == -1) p = new float[][]{{X0, Y0, Z0}, {X1, Y0, Z0}, {X1, Y0, Z1}, {X0, Y0, Z1}};
        else if (nz == 1)  p = new float[][]{{X0, Y0, Z1}, {X1, Y0, Z1}, {X1, Y1, Z1}, {X0, Y1, Z1}};
        else if (nz == -1) p = new float[][]{{X1, Y0, Z0}, {X0, Y0, Z0}, {X0, Y1, Z0}, {X1, Y1, Z0}};
        else if (nx == 1)  p = new float[][]{{X1, Y0, Z1}, {X1, Y0, Z0}, {X1, Y1, Z0}, {X1, Y1, Z1}};
        else               p = new float[][]{{X0, Y0, Z0}, {X0, Y0, Z1}, {X0, Y1, Z1}, {X0, Y1, Z0}};

        // Тангенциальные оси грани
        int axisA, axisB;
        if (ny != 0)      { axisA = 0; axisB = 2; }
        else if (nx != 0) { axisA = 2; axisB = 1; }
        else              { axisA = 0; axisB = 1; }

        // Ячейка света перед гранью (в координатах пада): сели занята — шагаем по нормали
        float cxW = (X0 + X1) * 0.5f, cyW = (Y0 + Y1) * 0.5f, czW = (Z0 + Z1) * 0.5f;
        final int M = Lighting.MARGIN;
        int bx = ((int) Math.floor(cxW - ox + 0.5f * nx + 1e-3f)) + M;
        int by = (int) Math.floor(cyW + 0.5f * ny + 1e-3f);
        int bz = ((int) Math.floor(czW - oz + 0.5f * nz + 1e-3f)) + M;
        for (int s = 0; s < 3; s++) {
            if (bx < 0 || bx >= pad.px || by < 0 || by >= pad.py || bz < 0 || bz >= pad.pz) break;
            if (pad.blocks[pad.idx(bx, by, bz)] == 0) break;
            bx += nx; by += ny; bz += nz;
        }
        int[] f = { bx, by, bz };

        // UV тайлятся по ячейке блока (не по мировой координате!):
        // полубоксы (ступеньки/полублоки) показывают часть текстуры, а не растягивают её
        int oA = axisA == 1 ? y : (axisA == 0 ? ox + lx : oz + lz);
        int oB = axisB == 1 ? y : (axisB == 0 ? ox + lx : oz + lz);
        float[][] uv = new float[4][2];
        for (int i = 0; i < 4; i++) {
            uv[i][0] = p[i][axisA] - oA;
            uv[i][1] = p[i][axisB] - oB;
        }
        int[] indices = {0, 1, 2, 0, 2, 3};

        float[][] cornerLight = new float[4][2];
        for (int ci = 0; ci < 4; ci++) {
            int sA = signAlong(p[ci][0], p[ci][1], p[ci][2], axisA, cxW, cyW, czW);
            int sB = signAlong(p[ci][0], p[ci][1], p[ci][2], axisB, cxW, cyW, czW);
            cornerLight[ci] = cornerLight(pad, f, axisA, sA, axisB, sB);
            if (texIndex == 8.0f) {
                cornerLight[ci][1] = 1.0f; // лампа светится сама
            }
        }

        for (int i : indices) {
            buf[ptr++] = p[i][0]; buf[ptr++] = p[i][1]; buf[ptr++] = p[i][2];
            buf[ptr++] = uv[i][0]; buf[ptr++] = uv[i][1];
            buf[ptr++] = nx; buf[ptr++] = ny; buf[ptr++] = nz;
            buf[ptr++] = texIndex;
            buf[ptr++] = cornerLight[i][0];
            buf[ptr++] = cornerLight[i][1];
        }
        return ptr;
    }

    private static int signAlong(float px, float py, float pz, int axis, float cx, float cy, float cz) {
        float v = switch (axis) { case 0 -> px; case 1 -> py; default -> pz; };
        float c = switch (axis) { case 0 -> cx; case 1 -> cy; default -> cz; };
        return v > c ? 1 : -1;
    }

    /** Средний свет 4 ячеек вокруг угла; сплошные дают 0 (лёгкое затемнение углов = мягкость). */
    private static float[] cornerLight(Lighting.Pad pad, int[] f, int axisA, int sA, int axisB, int sB) {
        int skySum = 0, blkSum = 0;
        for (int m = 0; m < 4; m++) {
            int a = f[0], b = f[1], c = f[2];
            if ((m & 1) != 0) {
                if (axisA == 0) a += sA; else if (axisA == 1) b += sA; else c += sA;
            }
            if ((m & 2) != 0) {
                if (axisB == 0) a += sB; else if (axisB == 1) b += sB; else c += sB;
            }
            if (a < 0 || a >= pad.px || b < 0 || b >= pad.py || c < 0 || c >= pad.pz) continue;
            int idx = pad.idx(a, b, c);
            if (pad.blocks[idx] != 0) continue; // сплошная ячейка не добавляет света
            skySum += pad.skyAt(idx);
            blkSum += pad.blockLightAt(idx);
        }
        return new float[]{ skySum / (4.0f * 15.0f), blkSum / (4.0f * 15.0f) };
    }

    public void render() {
        if (!uploadedToGPU) {
            uploadToGPU(); // Если еще не загрузили в GPU, загружаем прямо перед отрисовкой
        }
        if (vertexCount == 0 || !uploadedToGPU) return;

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void cleanup() {
        if (uploadedToGPU) {
            glDeleteBuffers(vbo);
            glDeleteVertexArrays(vao);
            uploadedToGPU = false;
        }
    }
}
