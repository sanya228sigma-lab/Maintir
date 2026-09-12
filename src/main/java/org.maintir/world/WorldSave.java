package org.maintir.world;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сохранения мира: папка saves/<имя>/world.dat.
 * Файл хранит метаданные (сид, биом, настройки, позиция игрока)
 * и все изменённые игроком блоки (diff поверх сгенерированного рельефа).
 */
public class WorldSave {
    public static final Path SAVES_DIR = Paths.get("saves");
    private static final int MAGIC = 0x4D57_5343;      // "MWSC" — v3: + режим игры
    private static final int MAGIC_V2 = 0x4D57_5342;   // "MWSB" — старый, с временем суток
    private static final int MAGIC_V1 = 0x4D57_5341;   // "MWSA" — самый старый

    /** Метаданные сохранения + позиция игрока. */
    public static class Meta {
        public String name = "Мир";
        public long seed = 12345L;
        public String biome = "ALL";
        public int treeDensityPercent = 100;
        public int renderDistance = 8;
        public int pigCount = 8;
        public float px, py, pz;
        public float yaw, pitch;
        public int selectedSlot = 0;
        public float timeOfDay = 0.10f;
        public String gameMode = "CREATIVE";
        /** Папка этого сохранения (заполняется при чтении списка/сохранении). */
        public Path folder;
    }

    /** Результат загрузки: мета + изменения блоков. */
    public static class Loaded {
        public final Meta meta = new Meta();
        public final Map<ChunkPos, Map<Integer, Byte>> overrides = new HashMap<>();
    }

    // ---------- Список сохранений ----------

    public static List<Meta> listSaves() {
        List<Meta> list = new ArrayList<>();
        if (!Files.isDirectory(SAVES_DIR)) return list;
        try (var stream = Files.list(SAVES_DIR)) {
            var dirs = stream.filter(Files::isDirectory).sorted().toList();
            for (Path dir : dirs) {
                Path file = dir.resolve("world.dat");
                if (!Files.isRegularFile(file)) continue;
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                    Meta m = readHeader(in);
                    if (m != null) {
                        m.folder = dir;
                        list.add(m);
                    }
                } catch (Exception ignored) {
                    // битый файл — пропускаем
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    // ---------- Сохранение ----------

    /**
     * Пишет мир в папку. Если folder null — создаёт новую уникальную по имени.
     * @return фактическая папка сохранения (запомнить для перезаписи при следующих сохранениях)
     */
    public static Path saveTo(Path folder, Meta meta, Map<ChunkPos, Map<Integer, Byte>> overrides) throws IOException {
        if (folder == null) folder = uniqueFolder(sanitize(meta.name));
        Files.createDirectories(folder);

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(folder.resolve("world.dat"))))) {
            out.writeInt(MAGIC);
            out.writeUTF(meta.name);
            out.writeLong(meta.seed);
            out.writeUTF(meta.biome);
            out.writeInt(meta.treeDensityPercent);
            out.writeInt(meta.renderDistance);
            out.writeInt(meta.pigCount);
            out.writeFloat(meta.px);
            out.writeFloat(meta.py);
            out.writeFloat(meta.pz);
            out.writeFloat(meta.yaw);
            out.writeFloat(meta.pitch);
            out.writeInt(meta.selectedSlot);
            out.writeFloat(meta.timeOfDay);
            out.writeUTF(meta.gameMode);

            int chunks = overrides != null ? overrides.size() : 0;
            out.writeInt(chunks);
            if (overrides != null) {
                for (Map.Entry<ChunkPos, Map<Integer, Byte>> e : overrides.entrySet()) {
                    out.writeInt(e.getKey().x());
                    out.writeInt(e.getKey().z());
                    Map<Integer, Byte> blocks = e.getValue();
                    out.writeInt(blocks.size());
                    for (Map.Entry<Integer, Byte> b : blocks.entrySet()) {
                        out.writeInt(b.getKey());
                        out.writeByte(b.getValue());
                    }
                }
            }
        }
        return folder;
    }

    // ---------- Загрузка ----------

    public static Loaded load(Path folder) throws IOException {
        Path file = folder.resolve("world.dat");
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            Loaded loaded = new Loaded();
            Meta m = readHeader(in);
            if (m == null) throw new IOException("Неверный формат файла мира");

            loaded.meta.name = m.name;
            loaded.meta.seed = m.seed;
            loaded.meta.biome = m.biome;
            loaded.meta.treeDensityPercent = m.treeDensityPercent;
            loaded.meta.renderDistance = m.renderDistance;
            loaded.meta.pigCount = m.pigCount;
            loaded.meta.px = m.px;
            loaded.meta.py = m.py;
            loaded.meta.pz = m.pz;
            loaded.meta.yaw = m.yaw;
            loaded.meta.pitch = m.pitch;
            loaded.meta.selectedSlot = m.selectedSlot;
            loaded.meta.timeOfDay = m.timeOfDay;
            loaded.meta.gameMode = m.gameMode;

            int chunkCount = in.readInt();
            for (int i = 0; i < chunkCount; i++) {
                int cx = in.readInt();
                int cz = in.readInt();
                int n = in.readInt();
                Map<Integer, Byte> blocks = new HashMap<>(n * 2);
                for (int j = 0; j < n; j++) {
                    int idx = in.readInt();
                    byte id = in.readByte();
                    blocks.put(idx, id);
                }
                loaded.overrides.put(new ChunkPos(cx, cz), blocks);
            }
            return loaded;
        }
    }

    // ---------- Вспомогательные ----------

    /** Читает шапку файла (метаданные без изменений блоков). null — неверный формат. */
    private static Meta readHeader(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC && magic != MAGIC_V2 && magic != MAGIC_V1) return null;
        Meta m = new Meta();
        m.name = in.readUTF();
        m.seed = in.readLong();
        m.biome = in.readUTF();
        m.treeDensityPercent = in.readInt();
        m.renderDistance = in.readInt();
        m.pigCount = in.readInt();
        m.px = in.readFloat();
        m.py = in.readFloat();
        m.pz = in.readFloat();
        m.yaw = in.readFloat();
        m.pitch = in.readFloat();
        m.selectedSlot = in.readInt();
        if (magic == MAGIC_V2) {
            m.timeOfDay = in.readFloat();
        } else if (magic == MAGIC) {
            m.timeOfDay = in.readFloat();
            m.gameMode = in.readUTF();
        }
        return m;
    }

    /** Убирает из имени всё опасное для файловой системы. */
    public static String sanitize(String name) {
        String s = name == null ? "" : name.trim();
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_') sb.append(c);
            else sb.append('_');
            if (sb.length() >= 32) break;
        }
        s = sb.toString().trim();
        return s.isEmpty() ? "Мир" : s;
    }

    /** Подбирает свободную папку: <имя>, <имя> 2, <имя> 3... */
    private static Path uniqueFolder(String base) {
        Path p = SAVES_DIR.resolve(base);
        int i = 2;
        while (Files.exists(p)) {
            p = SAVES_DIR.resolve(base + " " + i);
            i++;
        }
        return p;
    }
}
