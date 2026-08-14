package org.maintir.world;

import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class World {
    public static final int RENDER_DISTANCE = 8;

    private final Map<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Boolean> loadingChunks = new ConcurrentHashMap<>();

    private long currentSeed = 12345L;
    private TerrainGenerator.BiomeType currentBiome = TerrainGenerator.BiomeType.ALL;

    public void setWorldSettings(long seed, TerrainGenerator.BiomeType biome) {
        this.currentSeed = seed;
        this.currentBiome = biome;
        cleanup();
    }

    public void preloadArea(Vector3f playerPos, BiConsumer<String, Float> progressCallback) {
        int playerChunkX = (int) Math.floor(playerPos.x / Chunk.SIZE_X);
        int playerChunkZ = (int) Math.floor(playerPos.z / Chunk.SIZE_Z);

        int totalChunks = (RENDER_DISTANCE * 2 + 1) * (RENDER_DISTANCE * 2 + 1);
        int loadedChunks = 0;

        for (int x = -RENDER_DISTANCE; x <= RENDER_DISTANCE; x++) {
            for (int z = -RENDER_DISTANCE; z <= RENDER_DISTANCE; z++) {
                ChunkPos pos = new ChunkPos(playerChunkX + x, playerChunkZ + z);
                if (!chunks.containsKey(pos)) {
                    Chunk chunk = new Chunk(pos, currentSeed, currentBiome);
                    chunk.generateMeshCPU();
                    chunk.uploadToGPU();
                    chunks.put(pos, chunk);
                }
                loadedChunks++;
                if (progressCallback != null) {
                    progressCallback.accept("Генерация мира: " + loadedChunks + " / " + totalChunks, (float) loadedChunks / totalChunks);
                }
            }
        }
    }

    public void update(Vector3f playerPos) {
        int playerChunkX = (int) Math.floor(playerPos.x / Chunk.SIZE_X);
        int playerChunkZ = (int) Math.floor(playerPos.z / Chunk.SIZE_Z);

        // Фоновый расчет блоков и меша
        for (int x = -RENDER_DISTANCE; x <= RENDER_DISTANCE; x++) {
            for (int z = -RENDER_DISTANCE; z <= RENDER_DISTANCE; z++) {
                ChunkPos pos = new ChunkPos(playerChunkX + x, playerChunkZ + z);

                if (!chunks.containsKey(pos) && !loadingChunks.containsKey(pos)) {
                    loadingChunks.put(pos, true);

                    CompletableFuture.runAsync(() -> {
                        Chunk newChunk = new Chunk(pos, currentSeed, currentBiome);
                        newChunk.generateMeshCPU(); // Считаем математику в фоне!
                        chunks.put(pos, newChunk);
                        loadingChunks.remove(pos);
                    });
                }
            }
        }

        // Выгрузка чанков
        chunks.keySet().removeIf(pos -> {
            boolean far = Math.abs(pos.x() - playerChunkX) > RENDER_DISTANCE + 2 ||
                    Math.abs(pos.z() - playerChunkZ) > RENDER_DISTANCE + 2;
            if (far) {
                Chunk chunk = chunks.get(pos);
                if (chunk != null) chunk.cleanup();
            }
            return far;
        });
    }

    public void render() {
        for (Chunk chunk : chunks.values()) {
            chunk.render();
        }
    }

    public int getLoadedChunksCount() {
        return chunks.size();
    }

    public void cleanup() {
        for (Chunk chunk : chunks.values()) {
            chunk.cleanup();
        }
        chunks.clear();
        loadingChunks.clear();
    }
}