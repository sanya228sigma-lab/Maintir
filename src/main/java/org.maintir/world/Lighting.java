package org.maintir.world;

/**
 * Воксельное освещение в стиле Minecraft: небесный канал + блоковый (лампы).
 * Свет считается на области "чанк + поле 15 блоков": BFS распространяется
 * по реальным блокам соседей, поэтому туннели корректно темнеют, а свет ламп
 * мягко перетекает через границы чанков.
 */
public final class Lighting {
    public static final int MARGIN = 15;      // радиус влияния света
    public static final int MAX_SKY = 15;
    public static final int LAMP_EMISSION = 14;
    public static final byte LAMP_ID = 9;

    private Lighting() {}

    /** Непрозрачный ли блок для света (полные кубы); ступеньки/двери свет пропускают. */
    public static boolean opaque(byte id) {
        return Blocks.isFullCube(id);
    }

    /** Область расчёта: блоки + свет вокруг чанка (индексы ax∈[0..px), y, az). */
    public static final class Pad {
        public final int px, py, pz;
        public final byte[] blocks;
        public final byte[] light;               // (sky<<4)|block
        public boolean completeNeighbors;        // все 4 горизонтальных соседа были загружены

        Pad(int px, int py, int pz) {
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.blocks = new byte[px * py * pz];
            this.light = new byte[px * py * pz];
        }

        public int idx(int ax, int y, int az) {
            return (y * pz + az) * px + ax;
        }

        public int skyAt(int i) {
            return (light[i] >> 4) & 0xF;
        }

        public int blockLightAt(int i) {
            return light[i] & 0xF;
        }
    }

    /** Простая очередь индексов без боксинга. */
    private static final class IndexQueue {
        int[] a = new int[1 << 16];
        int head, tail;

        void push(int v) {
            if (tail == a.length) {
                // уплотнение: переносим хвост в начало
                int n = tail - head;
                System.arraycopy(a, head, a, 0, n);
                head = 0;
                tail = n;
                if (tail == a.length) a = java.util.Arrays.copyOf(a, a.length * 2);
            }
            a[tail++] = v;
        }

        boolean isEmpty() { return head >= tail; }

        int pop() { return a[head++]; }

        void reset() { head = tail = 0; }
    }

    /**
     * Считает свет для чанка pos (данные центра — center, ещё не в карте мира).
     * Отсутствующие соседи дают непрозрачную тёмную заглушку и помечают
     * pad.completeNeighbors = false (такой чанк позже пересветится).
     */
    public static Pad compute(World world, ChunkPos pos, Chunk center) {
        final int SX = Chunk.SIZE_X, SY = Chunk.SIZE_Y, SZ = Chunk.SIZE_Z;
        final int PX = SX + 2 * MARGIN, PZ = SZ + 2 * MARGIN;

        Pad pad = new Pad(PX, SY, PZ);
        byte[] blocks = pad.blocks;
        byte[] light = pad.light;
        pad.completeNeighbors = true;

        World.ChunkMapView chunks = world.chunkMapView();

        // 1. Блоки: центр + соседи; нет соседа — камень-заглушка (свет не протекает)
        for (int az = 0; az < PZ; az++) {
            int wz = pos.z() * SZ + az - MARGIN;
            int cz = Math.floorDiv(wz, SZ);
            int lz = Math.floorMod(wz, SZ);
            for (int ax = 0; ax < PX; ax++) {
                int wx = pos.x() * SX + ax - MARGIN;
                int cx = Math.floorDiv(wx, SX);
                int lx = Math.floorMod(wx, SX);
                Chunk src = (cx == pos.x() && cz == pos.z()) ? center : chunks.get(cx, cz);
                if (src == null) {
                    pad.completeNeighbors = false;
                    for (int y = 0; y < SY; y++) blocks[(y * PZ + az) * PX + ax] = 2;
                } else {
                    for (int y = 0; y < SY; y++) {
                        blocks[(y * PZ + az) * PX + ax] = src.getBlock(lx, y, lz);
                    }
                }
            }
        }

        IndexQueue queue = new IndexQueue();

        // 2. Небесный свет: сверху вниз по воздуху до первой преграды
        for (int az = 0; az < PZ; az++) {
            for (int ax = 0; ax < PX; ax++) {
                for (int y = SY - 1; y >= 0; y--) {
                    int i = (y * PZ + az) * PX + ax;
                    if (opaque(blocks[i])) break;
                    light[i] = (byte) (MAX_SKY << 4);
                    queue.push(i);
                }
            }
        }
        spread(queue, blocks, light, PX, SY, PZ, true);

        // 3. Блоковый свет от ламп
        queue.reset();
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] == LAMP_ID) {
                light[i] |= LAMP_EMISSION;
                queue.push(i);
            }
        }
        spread(queue, blocks, light, PX, SY, PZ, false);

        return pad;
    }

    /** BFS с затуханием 1; небесный свет 15 вниз не гаснет. */
    private static void spread(IndexQueue queue, byte[] blocks, byte[] light,
                               int px, int py, int pz, boolean sky) {
        while (!queue.isEmpty()) {
            int i = queue.pop();
            int level = sky ? ((light[i] >> 4) & 0xF) : (light[i] & 0xF);
            if (level <= 1) continue;

            int y = i / (pz * px);
            int rem = i % (pz * px);
            int az = rem / px;
            int ax = rem % px;

            for (int d = 0; d < 6; d++) {
                int nax = ax, ny = y, naz = az;
                switch (d) {
                    case 0 -> nax++;
                    case 1 -> nax--;
                    case 2 -> ny++;
                    case 3 -> ny--;
                    case 4 -> naz++;
                    case 5 -> naz--;
                }
                if (nax < 0 || nax >= px || ny < 0 || ny >= py || naz < 0 || naz >= pz) continue;
                int ni = (ny * pz + naz) * px + nax;
                if (opaque(blocks[ni])) continue;

                int nl = (sky && d == 3 && level == 15) ? 15 : level - 1;
                if (nl <= 0) continue;

                int cur = sky ? ((light[ni] >> 4) & 0xF) : (light[ni] & 0xF);
                if (cur >= nl) continue;

                light[ni] = sky
                        ? (byte) ((light[ni] & 0x0F) | (nl << 4))
                        : (byte) ((light[ni] & 0xF0) | nl);
                queue.push(ni);
            }
        }
    }
}
