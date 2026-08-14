package org.maintir.world;

import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Chunk {
    public static final int SIZE_X = 30;
    public static final int SIZE_Y = 175;
    public static final int SIZE_Z = 30;

    private final ChunkPos position;
    private final byte[][][] blocks = new byte[SIZE_X][SIZE_Y][SIZE_Z];

    // Массив вершин, рассчитанный в фоновом потоке
    private float[] meshData;
    private int vertexCount = 0;

    private int vao, vbo;
    private boolean meshBuilt = false;
    private boolean uploadedToGPU = false;

    public Chunk(ChunkPos position, long seed, TerrainGenerator.BiomeType biome) {
        this.position = position;
        TerrainGenerator.generateChunkTerrain(position, blocks, SIZE_X, SIZE_Y, SIZE_Z, seed, biome);
    }

    // 1. ВЫЗЫВАЕТСЯ В ФОНОВОМ ПОТОКЕ (Никакого OpenGL!)
    public void generateMeshCPU() {
        float[] bufferData = new float[SIZE_X * SIZE_Y * SIZE_Z * 6 * 6 * 9];
        int pointer = 0;

        for (int x = 0; x < SIZE_X; x++) {
            for (int y = 0; y < SIZE_Y; y++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    byte blockType = blocks[x][y][z];
                    if (blockType == 0) continue;

                    float texIndex = switch (blockType) {
                        case 2 -> 1.0f;
                        case 3 -> 2.0f;
                        case 4 -> 3.0f;
                        default -> 0.0f;
                    };

                    int worldX = position.x() * SIZE_X + x;
                    int worldZ = position.z() * SIZE_Z + z;

                    if (isTransparent(x, y + 1, z)) pointer = addFace(bufferData, pointer, worldX, y, worldZ, 0, 1, 0, texIndex);
                    if (isTransparent(x, y - 1, z)) pointer = addFace(bufferData, pointer, worldX, y, worldZ, 0, -1, 0, texIndex);
                    if (isTransparent(x, y, z + 1)) pointer = addFace(bufferData, pointer, worldX, y, worldZ, 0, 0, 1, texIndex);
                    if (isTransparent(x, y, z - 1)) pointer = addFace(bufferData, pointer, worldX, y, worldZ, 0, 0, -1, texIndex);
                    if (isTransparent(x + 1, y, z)) pointer = addFace(bufferData, pointer, worldX, y, worldZ, 1, 0, 0, texIndex);
                    if (isTransparent(x - 1, y, z)) pointer = addFace(bufferData, pointer, worldX, y, worldZ, -1, 0, 0, texIndex);
                }
            }
        }

        vertexCount = pointer / 9;
        if (vertexCount > 0) {
            meshData = new float[pointer];
            System.arraycopy(bufferData, 0, meshData, 0, pointer);
        }
        meshBuilt = true;
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

        int stride = 9 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);                 // Pos
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);   // UV
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5 * Float.BYTES);   // Normal
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 8 * Float.BYTES);   // TexIndex
        glEnableVertexAttribArray(3);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        meshData = null; // Очищаем массив в ОЗУ, он больше не нужен
        uploadedToGPU = true;
    }

    private boolean isTransparent(int x, int y, int z) {
        if (y < 0 || y >= SIZE_Y) return true;
        if (x < 0 || x >= SIZE_X || z < 0 || z >= SIZE_Z) return true;
        return blocks[x][y][z] == 0;
    }

    private int addFace(float[] buf, int ptr, float x, float y, float z, float nx, float ny, float nz, float texIndex) {
        float[][] p = new float[4][3];
        if (ny == 1)      p = new float[][]{{x, y+1, z+1}, {x+1, y+1, z+1}, {x+1, y+1, z}, {x, y+1, z}};
        else if (ny == -1) p = new float[][]{{x, y, z}, {x+1, y, z}, {x+1, y, z+1}, {x, y, z+1}};
        else if (nz == 1)  p = new float[][]{{x, y, z+1}, {x+1, y, z+1}, {x+1, y+1, z+1}, {x, y+1, z+1}};
        else if (nz == -1) p = new float[][]{{x+1, y, z}, {x, y, z}, {x, y+1, z}, {x+1, y+1, z}};
        else if (nx == 1)  p = new float[][]{{x+1, y, z+1}, {x+1, y, z}, {x+1, y+1, z}, {x+1, y+1, z+1}};
        else if (nx == -1) p = new float[][]{{x, y, z}, {x, y, z+1}, {x, y+1, z+1}, {x, y+1, z}};

        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        int[] indices = {0, 1, 2, 0, 2, 3};

        for (int i : indices) {
            buf[ptr++] = p[i][0]; buf[ptr++] = p[i][1]; buf[ptr++] = p[i][2];
            buf[ptr++] = uv[i][0]; buf[ptr++] = uv[i][1];
            buf[ptr++] = nx; buf[ptr++] = ny; buf[ptr++] = nz;
            buf[ptr++] = texIndex;
        }
        return ptr;
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