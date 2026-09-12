package org.maintir.engine;

import org.lwjgl.BufferUtils;
import org.maintir.world.Blocks;
import org.maintir.world.Door;
import org.maintir.world.World;

import java.nio.FloatBuffer;
import java.util.Collection;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Динамический рендер дверей: панели поворачиваются вокруг петли (плавное открытие),
 * стороны и торцы — текстура досок, окошко на верхней половине прозрачно через discard в шейдере.
 * Использует уже активный игровой блочный шейдер (model = identity, мировой координаты)
 * и TextureArray на юните 0 — Main вызывает после world.render().
 */
public final class DoorRenderer {

    private static int vao, vbo;
    private static boolean initialized = false;

    private static final float PLANK_LAYER = 6.0f; // доски — для боков и торцов

    private DoorRenderer() {}

    private static void init() {
        if (initialized) return;
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 1 << 16, GL_DYNAMIC_DRAW);
        int stride = 11 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);                  // Pos
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);    // UV
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5 * Float.BYTES);    // Normal
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 8 * Float.BYTES);    // TexIndex
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 9 * Float.BYTES);    // SkyLight
        glEnableVertexAttribArray(4);
        glVertexAttribPointer(5, 1, GL_FLOAT, false, stride, 10 * Float.BYTES);   // BlockLight
        glEnableVertexAttribArray(5);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        initialized = true;
    }

    /** Рисует все двери мира. Вызывать с активным блочным шейдером и TextureArray. */
    public static void render(Collection<Door> doors, World world) {
        if (doors == null || doors.isEmpty()) return;
        init();

        FloatBuffer buf = BufferUtils.createFloatBuffer(doors.size() * 2 * 24 * 11);
        int verts = 0;
        for (Door d : doors) {
            verts += buildCell(buf, d, d.wy, Blocks.DOOR_BOTTOM, 10.0f, world);
            if (d.wy + 1 < ChunkSIZE_Y) {
                verts += buildCell(buf, d, d.wy + 1, Blocks.DOOR_TOP, 11.0f, world);
            }
        }
        if (verts == 0) return;

        buf.flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buf, GL_DYNAMIC_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, verts);
        glBindVertexArray(0);
    }

    private static final int ChunkSIZE_Y = 175;

    /** Панель одной ячейки двери (низ или верх), повёрнутая вокруг петли. */
    private static int buildCell(FloatBuffer buf, Door d, int wy, byte doorId, float doorLayer, World world) {
        float bx0 = 0.35f, bx1 = 0.65f, by0 = 0f, by1 = 1f, bz0 = 0.02f, bz1 = 0.98f;

        double a = d.angleRad();
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        float pivotZ = d.pivotZ();

        int verts = 0;
        float[] lightBottom = doorLight(world, d.wx, wy, d.wz);
        float[] lightTop = wy + 1 < ChunkSIZE_Y ? doorLight(world, d.wx, wy + 1, d.wz) : lightBottom;

        // Широкие грани (фасад с текстурой двери) — видны всегда
        verts += emitDoorFace(buf, d.wx, wy, d.wz, lightBottom[0], lightBottom[1], doorLayer, ca, sa, pivotZ,
                new float[][]{{bx1, by0, bz0}, {bx1, by0, bz1}, {bx1, by1, bz1}, {bx1, by1, bz0}},
                1, 0, 0, 2, 1);
        verts += emitDoorFace(buf, d.wx, wy, d.wz, lightBottom[0], lightBottom[1], doorLayer, ca, sa, pivotZ,
                new float[][]{{bx0, by0, bz1}, {bx0, by0, bz0}, {bx0, by1, bz0}, {bx0, by1, bz1}},
                -1, 0, 0, 2, 1);

        // Тонкие боковины — досками
        verts += emitDoorFace(buf, d.wx, wy, d.wz, lightBottom[0], lightBottom[1], PLANK_LAYER, ca, sa, pivotZ,
                new float[][]{{bx0, by0, bz1}, {bx1, by0, bz1}, {bx1, by1, bz1}, {bx0, by1, bz1}},
                0, 0, 1, 0, 1);
        verts += emitDoorFace(buf, d.wx, wy, d.wz, lightBottom[0], lightBottom[1], PLANK_LAYER, ca, sa, pivotZ,
                new float[][]{{bx1, by0, bz0}, {bx0, by0, bz0}, {bx0, by1, bz0}, {bx1, by1, bz0}},
                0, 0, -1, 0, 1);

        // Верх и низ (стык половинок) — досками, только если рядом нет другой половинки двери
        byte up = world.getBlockAt(d.wx, wy + 1, d.wz);
        if (!Blocks.isDoor(up) && !Blocks.isFullCube(up)) {
            verts += emitDoorFace(buf, d.wx, wy, d.wz, lightTop[0], lightTop[1], PLANK_LAYER, ca, sa, pivotZ,
                    new float[][]{{bx0, by1, bz1}, {bx1, by1, bz1}, {bx1, by1, bz0}, {bx0, by1, bz0}},
                    0, 1, 0, 0, 2);
        }
        byte down = world.getBlockAt(d.wx, wy - 1, d.wz);
        if (!Blocks.isDoor(down) && !Blocks.isFullCube(down)) {
            verts += emitDoorFace(buf, d.wx, wy, d.wz, lightBottom[0], lightBottom[1], PLANK_LAYER, ca, sa, pivotZ,
                    new float[][]{{bx0, by0, bz0}, {bx1, by0, bz0}, {bx1, by0, bz1}, {bx0, by0, bz1}},
                    0, -1, 0, 0, 2);
        }
        return verts;
    }

    /** Свет двери: максимум по своей ячейке и 4 горизонтальным соседям (дверь тонкая, свою ячейку и соседний воздух). */
    private static float[] doorLight(World world, int wx, int wy, int wz) {
        float sky = -1f, blk = -1f;
        int[][] dxz = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dxz) {
            sky = Math.max(sky, world.skyLightAt(wx + d[0], wy, wz + d[1]) / 15.0f);
            blk = Math.max(blk, world.blockLightAt(wx + d[0], wy, wz + d[1]) / 15.0f);
        }
        return new float[]{sky, blk};
    }

    /**
     * Пишет грань панели: поворот вокруг петли в мировых координатах двери wx,wy,wz,
     * свет двери уже посчитан. axisA/axisB — касательные оси для UV (как в Chunk.emitFace).
     */
    private static int emitDoorFace(FloatBuffer buf, int wx, int wy, int wz,
                                    float sky, float blk, float texLayer, float ca, float sa, float pivotZ,
                                    float[][] c, int nx, int ny, int nz,
                                    int axisA, int axisB) {
        float nrx = nx * ca - nz * sa;
        float nrz = nx * sa + nz * ca;

        int[] idx = {0, 1, 2, 0, 2, 3};
        for (int i : idx) {
            float lx0 = c[i][0], ly0 = c[i][1], lz0 = c[i][2];
            float rx = 0.5f + (lx0 - 0.5f) * ca - (lz0 - pivotZ) * sa;
            float rz = pivotZ + (lx0 - 0.5f) * sa + (lz0 - pivotZ) * ca;

            float u = axisA == 0 ? c[i][0] : axisA == 1 ? c[i][1] : c[i][2];
            float v = axisB == 0 ? c[i][0] : axisB == 1 ? c[i][1] : c[i][2];

            buf.put(wx + rx).put(wy + ly0).put(wz + rz);
            buf.put(u).put(v);
            buf.put(nrx).put(ny).put(nrz);
            buf.put(texLayer).put(sky).put(blk);
        }
        return 6;
    }
}