package org.maintir.world;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

public class World {

    /** Доступ к карте чанков для фонового расчёта света. */
    public interface ChunkMapView {
        Chunk get(int cx, int cz);
    }

    // Дальность прорисовки настраивается в меню (3..16)
    private int renderDistance = 8;

    // Цикл дня и ночи: полный оборот за 10 минут
    public static final float DAY_LENGTH_SEC = 600f;
    private float timeOfDay = 0.06f; // 0 = рассвет, 0.25 = полдень, 0.75 = полночь

    // Сколько чанков одновременно считается в фоне.
    // Больше -> мир грузится быстрее, но выше просадка FPS. 3-4 держит FPS >= 30-60.
    private static final int GEN_THREADS = 2;
    private static final int MAX_IN_FLIGHT = 4;
    // Сколько чанков за один кадр заливается в видеопамять (иначе все разом -> фриз)
    private static final int MAX_GPU_UPLOADS_PER_FRAME = 2;

    private final Map<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Boolean> loadingChunks = new ConcurrentHashMap<>();
    private final ExecutorService generatorPool = Executors.newFixedThreadPool(GEN_THREADS, r -> {
        Thread t = new Thread(r, "chunk-gen");
        t.setDaemon(true);
        return t;
    });
    // Номер поколения мира: смена сида/биома отменяет устаревшие задачи
    private final AtomicLong epoch = new AtomicLong();

    // Свиньи
    private final List<Pig> pigs = new ArrayList<>();
    private int desiredPigCount = 8;

    // Изменения игрока поверх сгенерированного рельефа (для сохранений)
    private final Map<ChunkPos, Map<Integer, Byte>> overrides = new ConcurrentHashMap<>();

    // Состояние дверей (ключ — нижняя ячейка; двери анимируются и рендерятся динамически)
    private final Map<Long, Door> doors = new ConcurrentHashMap<>();

    private long currentSeed = 12345L;
    private TerrainGenerator.BiomeType currentBiome = TerrainGenerator.BiomeType.ALL;

    /** Создаёт чанк с применением сохранённых изменений (меш считать после). */
    private Chunk createChunk(ChunkPos pos) {
        Chunk chunk = new Chunk(pos, currentSeed, currentBiome);
        chunk.applyOverrides(overrides.get(pos));
        return chunk;
    }

    public ChunkMapView chunkMapView() {
        return (cx, cz) -> chunks.get(new ChunkPos(cx, cz));
    }

    // ---------- Цикл дня и ночи ----------

    public void advanceTime(float dt) {
        timeOfDay = (timeOfDay + dt / DAY_LENGTH_SEC) % 1f;
    }

    public float getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeOfDay(float t) {
        timeOfDay = ((t % 1f) + 1f) % 1f;
    }

    /** Направление на солнце: t=0 рассвет (восток), 0.25 полдень, 0.75 полночь. */
    public Vector3f getSunDir() {
        double th = timeOfDay * Math.PI * 2.0;
        Vector3f d = new Vector3f((float) Math.cos(th), (float) Math.sin(th), 0.22f);
        return d.normalize();
    }

    /** Уровень солнечного света 0..1 (ночью лунные ~0.12). */
    public float getSunLevel() {
        float elev = (float) Math.sin(timeOfDay * Math.PI * 2.0);
        float f = Math.max(0f, Math.min(1f, (elev + 0.10f) / 0.30f));
        f = f * f * (3 - 2 * f); // smoothstep
        return 0.12f + 0.88f * f;
    }

    /** Цвет неба/тумана с закатным оттенком. */
    public Vector3f getSkyColor() {
        double th = timeOfDay * Math.PI * 2.0;
        float elev = (float) Math.sin(th);
        float dayF = Math.max(0f, Math.min(1f, (elev + 0.10f) / 0.30f));
        dayF = dayF * dayF * (3 - 2 * dayF);

        float r = 0.02f + (0.50f - 0.02f) * dayF;
        float g = 0.03f + (0.80f - 0.03f) * dayF;
        float b = 0.09f + (1.00f - 0.09f) * dayF;

        // Закат/рассвет: тёплый оттенок у горизонта
        float dusk = Math.max(0f, 1f - Math.abs(elev) / 0.16f);
        r += (0.85f - r) * dusk * 0.55f;
        g += (0.38f - g) * dusk * 0.55f;
        b += (0.20f - b) * dusk * 0.35f;
        return new Vector3f(r, g, b);
    }

    public void clearOverrides() {
        overrides.clear();
    }

    /** Подменяет все изменения блоков данными из сохранения. */
    public void importOverrides(Map<ChunkPos, Map<Integer, Byte>> data) {
        overrides.clear();
        if (data != null) overrides.putAll(data);
    }

    public Map<ChunkPos, Map<Integer, Byte>> exportOverrides() {
        return overrides;
    }

    /** Запоминает изменение блока игроком. */
    private void recordOverride(int wx, int wy, int wz, byte id) {
        int cx = Math.floorDiv(wx, Chunk.SIZE_X);
        int cz = Math.floorDiv(wz, Chunk.SIZE_Z);
        int lx = Math.floorMod(wx, Chunk.SIZE_X);
        int lz = Math.floorMod(wz, Chunk.SIZE_Z);
        overrides.computeIfAbsent(new ChunkPos(cx, cz), k -> new java.util.HashMap<>())
                .put(Chunk.packIndex(lx, wy, lz), id);
    }

    public int getRenderDistance() {
        return renderDistance;
    }

    public long getSeed() {
        return currentSeed;
    }

    public TerrainGenerator.BiomeType getBiome() {
        return currentBiome;
    }

    public int getDesiredPigs() {
        return desiredPigCount;
    }

    public void setRenderDistance(int rd) {
        renderDistance = Math.max(3, Math.min(16, rd));
    }

    public void setDesiredPigs(int count) {
        desiredPigCount = Math.max(0, Math.min(40, count));
    }

    /** Первичное заселение свиньями по всей загруженной области. */
    public void spawnInitialPigs(float cx, float cz) {
        pigs.clear();
        float maxR = renderDistance * 30.0f;
        for (int i = 0; i < desiredPigCount; i++) {
            trySpawnPigNear(cx, cz, 8, maxR);
        }
    }

    /** Попытка подсадить одну свинью на траву в кольце радиусов. */
    private void trySpawnPigNear(float cx, float cz, float minR, float maxR) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double ang = Math.random() * Math.PI * 2;
            float r = minR + (float) Math.random() * (maxR - minR);
            int wx = (int) (cx + Math.cos(ang) * r);
            int wz = (int) (cz + Math.sin(ang) * r);
            // Свиньи живут только на траве в уже готовых чанках
            if (topBlockId(wx, wz) == 1) {
                pigs.add(new Pig(wx + 0.5f, surfaceHeight(wx, wz), wz + 0.5f));
                return;
            }
        }
    }

    public void updatePigs(float dt, Vector3f playerPos) {
        // Далёких — убираем
        float despawnDist = renderDistance * 30.0f + 40.0f;
        pigs.removeIf(p -> {
            float dx = p.x - playerPos.x, dz = p.z - playerPos.z;
            return dx * dx + dz * dz > despawnDist * despawnDist;
        });
        // Подсаживаем недостающих по всей загруженной области
        while (pigs.size() < desiredPigCount) {
            int before = pigs.size();
            trySpawnPigNear(playerPos.x, playerPos.z, 20, despawnDist * 0.8f);
            if (pigs.size() == before) break; // травы рядом нет — не зацикливаемся
        }
        for (Pig p : pigs) p.update(dt, this, playerPos);
    }

    public void renderPigs(Matrix4f view, Matrix4f projection, Vector3f playerPos) {
        if (pigs.isEmpty()) return;
        Pig.initAssets();
        for (Pig p : pigs) p.render(view, projection, playerPos);
    }

    public void setWorldSettings(long seed, TerrainGenerator.BiomeType biome) {
        this.currentSeed = seed;
        this.currentBiome = biome;
        cleanup();
        clearOverrides(); // новый мир — без чужих правок (загрузка импортирует их следом)
    }

    public void preloadArea(Vector3f playerPos, BiConsumer<String, Float> progressCallback) {
        int playerChunkX = (int) Math.floor(playerPos.x / Chunk.SIZE_X);
        int playerChunkZ = (int) Math.floor(playerPos.z / Chunk.SIZE_Z);

        int totalChunks = (renderDistance * 2 + 1) * (renderDistance * 2 + 1);
        int loadedChunks = 0;

        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                ChunkPos pos = new ChunkPos(playerChunkX + x, playerChunkZ + z);
                if (!chunks.containsKey(pos)) {
                    Chunk chunk = createChunk(pos);
                    Lighting.Pad pad = Lighting.compute(this, pos, chunk);
                    chunk.installGenerated(pad);
                    chunks.put(pos, chunk);
                }
                loadedChunks++;
                if (progressCallback != null) {
                    float frac = 0.9f * loadedChunks / totalChunks;
                    progressCallback.accept("Генерация мира: " + loadedChunks + " / " + totalChunks, frac);
                }
            }
        }

        // Второй проход: досветить чанки, считавшиеся без части соседей
        List<ChunkPos> toRelight = new ArrayList<>();
        for (Map.Entry<ChunkPos, Chunk> e : chunks.entrySet()) {
            if (!e.getValue().isFullyLit()) toRelight.add(e.getKey());
        }
        int done = 0;
        for (ChunkPos pos : toRelight) {
            Chunk c = chunks.get(pos);
            if (c == null) continue;
            c.installGenerated(Lighting.compute(this, pos, c));
            done++;
            if (progressCallback != null) {
                progressCallback.accept("Освещение мира: " + done + " / " + toRelight.size(),
                        0.9f + 0.1f * done / Math.max(1, toRelight.size()));
            }
        }
    }

    public void update(Vector3f playerPos) {
        int playerChunkX = (int) Math.floor(playerPos.x / Chunk.SIZE_X);
        int playerChunkZ = (int) Math.floor(playerPos.z / Chunk.SIZE_Z);

        // Собираем недостающие чанки и сначала генерируем ближайшие
        List<ChunkPos> missing = new ArrayList<>();
        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                ChunkPos pos = new ChunkPos(playerChunkX + x, playerChunkZ + z);
                if (!chunks.containsKey(pos) && !loadingChunks.containsKey(pos)) {
                    missing.add(pos);
                }
            }
        }

        if (!missing.isEmpty() && loadingChunks.size() < MAX_IN_FLIGHT) {
            missing.sort(Comparator.comparingDouble(p -> {
                double dx = p.x() - playerChunkX;
                double dz = p.z() - playerChunkZ;
                return dx * dx + dz * dz;
            }));

            long e = epoch.get();
            for (ChunkPos pos : missing) {
                if (loadingChunks.size() >= MAX_IN_FLIGHT) break;
                loadingChunks.put(pos, true);

                generatorPool.submit(() -> {
                    if (epoch.get() != e) { // мир уже сменился — выбрасываем мусор
                        loadingChunks.remove(pos);
                        return;
                    }
                    Chunk newChunk = createChunk(pos);
                    newChunk.installGenerated(Lighting.compute(World.this, pos, newChunk));
                    if (epoch.get() != e) {
                        loadingChunks.remove(pos);
                        return;
                    }
                    chunks.put(pos, newChunk);
                    loadingChunks.remove(pos);

                    // Соседи могли считаться без наших блоков — досветить их
                    relightNeighborsOf(pos, e);
                });
            }
        }

        // Выгрузка чанков
        chunks.keySet().removeIf(pos -> {
            boolean far = Math.abs(pos.x() - playerChunkX) > renderDistance + 2 ||
                    Math.abs(pos.z() - playerChunkZ) > renderDistance + 2;
            if (far) {
                Chunk chunk = chunks.get(pos);
                if (chunk != null) chunk.cleanup();
            }
            return far;
        });
    }

    /** Если все 4 соседа чанка загружены — пересчитать его свет в фоне. */
    private void relightNeighborsOf(ChunkPos center, long e) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            ChunkPos p = new ChunkPos(center.x() + d[0], center.z() + d[1]);
            Chunk n = chunks.get(p);
            if (n != null && !n.isFullyLit()) submitRelight(p, e);
        }
        // И сам центральный, если он ждал соседей
        Chunk c = chunks.get(center);
        if (c != null && !c.isFullyLit()) submitRelight(center, e);
    }

    private void submitRelight(ChunkPos pos, long e) {
        if (!allNeighborsLoaded(pos)) return;
        generatorPool.submit(() -> {
            if (epoch.get() != e) return;
            Chunk c = chunks.get(pos);
            if (c == null) return;
            c.relightData(Lighting.compute(World.this, pos, c));
        });
    }

    private boolean allNeighborsLoaded(ChunkPos pos) {
        return chunks.containsKey(new ChunkPos(pos.x() + 1, pos.z()))
                && chunks.containsKey(new ChunkPos(pos.x() - 1, pos.z()))
                && chunks.containsKey(new ChunkPos(pos.x(), pos.z() + 1))
                && chunks.containsKey(new ChunkPos(pos.x(), pos.z() - 1));
    }

    public void render() {
        // Лимит загрузок в GPU на кадр: новые чанки появляются плавно, без фриза
        int uploads = MAX_GPU_UPLOADS_PER_FRAME;
        for (Chunk chunk : chunks.values()) {
            chunk.refreshGpu(); // перезаливка после фоновой пересветки
            if (!chunk.isUploadedToGPU()) {
                if (uploads <= 0) continue; // попадёт в следующий кадр
                chunk.uploadToGPU();
                uploads--;
            }
            chunk.render();
        }
    }

    public int getLoadedChunksCount() {
        return chunks.size();
    }

    // ---------- Работа с блоками по мировым координатам ----------

    private Chunk chunkAt(int wx, int wz) {
        int cx = Math.floorDiv(wx, Chunk.SIZE_X);
        int cz = Math.floorDiv(wz, Chunk.SIZE_Z);
        return chunks.get(new ChunkPos(cx, cz));
    }

    /** Твёрдый ли блок (для луча и ломания). Незагруженное — воздух. */
    public boolean isSolid(int wx, int wy, int wz) {
        if (wy < 0 || wy >= Chunk.SIZE_Y) return false;
        Chunk c = chunkAt(wx, wz);
        if (c == null) return false;
        return c.getBlock(Math.floorMod(wx, Chunk.SIZE_X), wy, Math.floorMod(wz, Chunk.SIZE_Z)) != 0;
    }

    /** Твёрдый ли блок (для физики игрока). Незагруженные чанки — стена. */
    public boolean isSolidForPhysics(int wx, int wy, int wz) {
        if (wy < 0) return true;
        if (wy >= Chunk.SIZE_Y) return false;
        Chunk c = chunkAt(wx, wz);
        if (c == null) return true;
        byte b = c.getBlock(Math.floorMod(wx, Chunk.SIZE_X), wy, Math.floorMod(wz, Chunk.SIZE_Z));
        if (b == 0) return false;
        if (Blocks.isDoor(b)) {
            // Открытая дверь — проходимая для игрока (стекло можно отрисовывать как есть)
            int bottomY = (b == Blocks.DOOR_BOTTOM) ? wy : wy - 1;
            Door d = doors.get(doorKey(wx, bottomY, wz));
            if (d != null && d.openFrac() > 0.85f) return false;
        }
        return true;
    }

    /** Высота поверхности: y первой воздушной ячейки над верхним блоком колонки. */
    public int surfaceHeight(int wx, int wz) {
        Chunk c = chunkAt(wx, wz);
        if (c == null) return Chunk.SIZE_Y - 1;
        int lx = Math.floorMod(wx, Chunk.SIZE_X);
        int lz = Math.floorMod(wz, Chunk.SIZE_Z);
        for (int y = Chunk.SIZE_Y - 1; y >= 0; y--) {
            if (c.getBlock(lx, y, lz) != 0) return y + 1;
        }
        return 1;
    }

    /** ID верхнего непустого блока колонки (0 — данных нет). */
    public int topBlockId(int wx, int wz) {
        Chunk c = chunkAt(wx, wz);
        if (c == null) return 0;
        int lx = Math.floorMod(wx, Chunk.SIZE_X);
        int lz = Math.floorMod(wz, Chunk.SIZE_Z);
        for (int y = Chunk.SIZE_Y - 1; y >= 0; y--) {
            byte b = c.getBlock(lx, y, lz);
            if (b != 0) return b;
        }
        return 0;
    }

    /** Небесный свет ячейки (0..15) для динамических объектов (двери). */
    public int skyLightAt(int wx, int wy, int wz) {
        Chunk c = chunkAt(wx, wz);
        if (c == null || wy < 0 || wy >= Chunk.SIZE_Y) return 15;
        return c.getSkyLight(Math.floorMod(wx, Chunk.SIZE_X), wy, Math.floorMod(wz, Chunk.SIZE_Z));
    }

    /** Свет ламп ячейки (0..15) для динамических объектов (двери). */
    public int blockLightAt(int wx, int wy, int wz) {
        Chunk c = chunkAt(wx, wz);
        if (c == null || wy < 0 || wy >= Chunk.SIZE_Y) return 0;
        return c.getBlockLight(Math.floorMod(wx, Chunk.SIZE_X), wy, Math.floorMod(wz, Chunk.SIZE_Z));
    }

    /**
     * Луч от глаз игрока по направлению взгляда (алгоритм DDA).
     * @return {x, y, z, nx, ny, nz} — блок и нормаль его грани, в которую попали; null — мимо.
     */
    public int[] raycastBlock(Vector3f origin, Vector3f dir, float maxDist) {
        float ox = origin.x, oy = origin.y, oz = origin.z;
        float dx = dir.x, dy = dir.y, dz = dir.z;

        int ix = (int) Math.floor(ox);
        int iy = (int) Math.floor(oy);
        int iz = (int) Math.floor(oz);

        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;

        double tDeltaX = dx != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
        double tDeltaY = dy != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
        double tDeltaZ = dz != 0 ? Math.abs(1.0 / dz) : Double.POSITIVE_INFINITY;

        double tMaxX = dx != 0 ? (dx > 0 ? (ix + 1 - ox) : (ox - ix)) * tDeltaX : Double.POSITIVE_INFINITY;
        double tMaxY = dy != 0 ? (dy > 0 ? (iy + 1 - oy) : (oy - iy)) * tDeltaY : Double.POSITIVE_INFINITY;
        double tMaxZ = dz != 0 ? (dz > 0 ? (iz + 1 - oz) : (oz - iz)) * tDeltaZ : Double.POSITIVE_INFINITY;

        int nx = 0, ny = 0, nz = 0;
        double t = 0;
        while (t <= maxDist) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                ix += stepX; t = tMaxX; tMaxX += tDeltaX;
                nx = -stepX; ny = 0; nz = 0;
            } else if (tMaxY < tMaxZ) {
                iy += stepY; t = tMaxY; tMaxY += tDeltaY;
                nx = 0; ny = -stepY; nz = 0;
            } else {
                iz += stepZ; t = tMaxZ; tMaxZ += tDeltaZ;
                nx = 0; ny = 0; nz = -stepZ;
            }
            if (t > maxDist) break;
            if (isSolid(ix, iy, iz)) return new int[]{ix, iy, iz, nx, ny, nz};
        }
        return null;
    }

    /** ID блока по мировым координатам (0 — воздух/нет данных). */
    public byte getBlockAt(int wx, int wy, int wz) {
        if (wy < 0 || wy >= Chunk.SIZE_Y) return 0;
        Chunk c = chunkAt(wx, wz);
        if (c == null) return 0;
        return c.getBlock(Math.floorMod(wx, Chunk.SIZE_X), wy, Math.floorMod(wz, Chunk.SIZE_Z));
    }

    /** Ломает блок (нижний слой мира — неразрушимый). Пересчитывает свет и меш чанка. */
    public boolean breakBlock(int wx, int wy, int wz) {
        if (wy <= 0 || wy >= Chunk.SIZE_Y) return false;
        Chunk c = chunkAt(wx, wz);
        if (c == null) return false;
        int lx = Math.floorMod(wx, Chunk.SIZE_X);
        int lz = Math.floorMod(wz, Chunk.SIZE_Z);
        if (c.getBlock(lx, wy, lz) == 0) return false;
        c.setBlock(lx, wy, lz, (byte) 0);
        editAndRelight(c, wx, wy, wz, (byte) 0);
        return true;
    }

    /** Ставит блок в пустую ячейку. Пересчитывает свет и меш чанка. */
    public boolean placeBlock(int wx, int wy, int wz, byte blockId) {
        if (wy <= 0 || wy >= Chunk.SIZE_Y) return false;
        Chunk c = chunkAt(wx, wz);
        if (c == null) return false;
        int lx = Math.floorMod(wx, Chunk.SIZE_X);
        int lz = Math.floorMod(wz, Chunk.SIZE_Z);
        if (c.getBlock(lx, wy, lz) != 0) return false;
        c.setBlock(lx, wy, lz, blockId);
        editAndRelight(c, wx, wy, wz, blockId);
        return true;
    }

    /** Ставит дверь (низ + верх) — обе ячейки должны быть пусты. */
    public boolean placeDoor(int wx, int wy, int wz) {
        if (getBlockAt(wx, wy, wz) != 0) return false;
        if (wy + 1 >= Chunk.SIZE_Y || getBlockAt(wx, wy + 1, wz) != 0) return false;
        if (!placeBlock(wx, wy, wz, Blocks.DOOR_BOTTOM)) return false;
        if (!placeBlock(wx, wy + 1, wz, Blocks.DOOR_TOP)) {
            breakBlock(wx, wy, wz);
            return false;
        }
        doors.computeIfAbsent(doorKey(wx, wy, wz), k -> new Door(wx, wy, wz));
        return true;
    }

    /** Ставит ступеньку с учётом положения игрока (спина — от игрока). */
    public boolean placeStairs(int wx, int wy, int wz, float playerX, float playerZ, byte baseId) {
        boolean stone = baseId >= Blocks.STAIRS_STONE_N;
        int bnX = 0, bnZ = 0;
        float dx = playerX - (wx + 0.5f), dz = playerZ - (wz + 0.5f);
        if (Math.abs(dx) >= Math.abs(dz)) bnX = dx > 0 ? -1 : 1;
        else bnZ = dz > 0 ? -1 : 1;
        return placeBlock(wx, wy, wz, Blocks.stairId(stone, bnX, bnZ));
    }

    /** Ломает блок; у двери ломаются обе половинки разом. */
    public boolean breakBlockDoor(int wx, int wy, int wz) {
        byte id = getBlockAt(wx, wy, wz);
        if (!Blocks.isDoor(id)) return breakBlock(wx, wy, wz);
        int bottomY = (id == Blocks.DOOR_BOTTOM) ? wy : wy - 1;
        doors.remove(doorKey(wx, bottomY, wz));
        boolean ok = breakBlock(wx, wy, wz);
        int otherY = (id == Blocks.DOOR_BOTTOM) ? wy + 1 : wy - 1;
        if (otherY > 0 && otherY < Chunk.SIZE_Y) breakBlock(wx, otherY, wz);
        return ok;
    }

    /** Открыть/закрыть дверь (клик ПКМ по любой её половинке). */
    public void toggleDoor(int wx, int wy, int wz) {
        byte id = getBlockAt(wx, wy, wz);
        if (!Blocks.isDoor(id)) return;
        int bottomY = (id == Blocks.DOOR_BOTTOM) ? wy : wy - 1;
        Door d = doors.computeIfAbsent(doorKey(wx, bottomY, wz), k -> new Door(wx, bottomY, wz));
        d.toggle();
    }

    /** Индекс нижней ячейки двери в реестре (мировые координаты). */
    private static long doorKey(int wx, int wy, int wz) {
        return ((long) wx & 0xFFFFF) | (((long) wy & 0xFF) << 20) | (((long) wz & 0xFFFFF) << 28);
    }

    /** Регистрирует двери, найденные в изменениях игрока (после загрузки мира). */
    public void scanDoorsFromOverrides() {
        doors.clear();
        for (Map.Entry<ChunkPos, Map<Integer, Byte>> e : overrides.entrySet()) {
            ChunkPos cp = e.getKey();
            for (Map.Entry<Integer, Byte> b : e.getValue().entrySet()) {
                byte id = b.getValue();
                if (id != Blocks.DOOR_BOTTOM && id != Blocks.DOOR_TOP) continue;
                int idx = b.getKey();
                int lx = idx / (Chunk.SIZE_Y * Chunk.SIZE_Z);
                int rem = idx % (Chunk.SIZE_Y * Chunk.SIZE_Z);
                int y = rem / Chunk.SIZE_Z;
                int lz = rem % Chunk.SIZE_Z;
                if (id == Blocks.DOOR_TOP) y--;
                final int wx = cp.x() * Chunk.SIZE_X + lx;
                final int bottomY = y;
                final int wz = cp.z() * Chunk.SIZE_Z + lz;
                doors.computeIfAbsent(doorKey(wx, bottomY, wz), k -> new Door(wx, bottomY, wz));
            }
        }
    }

    /** Активные двери для рендера (DoorRenderer). */
    public Collection<Door> activeDoors() {
        return doors.values();
    }

    /** Анимация открытия/закрытия дверей. */
    public void updateDoors(float dt) {
        for (Door d : doors.values()) d.update(dt);
    }

    /** После правки блока: свет этого чанка сразу, соседей — в фоне. */
    private void editAndRelight(Chunk c, int wx, int wy, int wz, byte id) {
        recordOverride(wx, wy, wz, id);
        ChunkPos pos = new ChunkPos(Math.floorDiv(wx, Chunk.SIZE_X), Math.floorDiv(wz, Chunk.SIZE_Z));
        c.relightData(Lighting.compute(this, pos, c));
        relightNeighborsOf(pos, epoch.get());
    }

    public void cleanup() {
        epoch.incrementAndGet();
        for (Chunk chunk : chunks.values()) {
            chunk.cleanup();
        }
        chunks.clear();
        loadingChunks.clear();
        pigs.clear();
    }
}
