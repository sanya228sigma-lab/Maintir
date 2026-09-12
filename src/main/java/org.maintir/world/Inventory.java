package org.maintir.world;

/**
 * Инвентарь игрока: 64 слота с предметами, стек максимум 25.
 * Слоты 0..8 — хотбар. В творческом режиме инвентарь не используется
 * (блоки из вкладок кладутся прямо в слоты хотбара).
 */
public class Inventory {
    public static final int SIZE = 64;
    public static final int MAX_STACK = 25;
    public static final int HOTBAR_SLOTS = 9;

    private final byte[] items = new byte[SIZE];   // 0 — пусто
    private final int[] counts = new int[SIZE];

    /** Добавляет один предмет: в неполный стек того же типа, иначе в пустой слот. */
    public void add(byte blockId) {
        if (blockId <= 0) return;
        for (int i = 0; i < SIZE; i++) {
            if (items[i] == blockId && counts[i] < MAX_STACK) { counts[i]++; return; }
        }
        for (int i = 0; i < SIZE; i++) {
            if (items[i] == 0) { items[i] = blockId; counts[i] = 1; return; }
        }
    }

    /** Забирает один предмет из слота. */
    public boolean consumeSlot(int slot) {
        if (slot < 0 || slot >= SIZE || items[slot] == 0 || counts[slot] <= 0) return false;
        counts[slot]--;
        if (counts[slot] == 0) items[slot] = 0;
        return true;
    }

    /** Меняет содержимое двух слотов местами. */
    public void swap(int a, int b) {
        if (a < 0 || a >= SIZE || b < 0 || b >= SIZE || a == b) return;
        byte bii = items[a]; items[a] = items[b]; items[b] = bii;
        int bc = counts[a]; counts[a] = counts[b]; counts[b] = bc;
    }

    /** Записывает слот целиком (id и количество). */
    public void setSlot(int slot, byte id, int count) {
        if (slot < 0 || slot >= SIZE) return;
        items[slot] = id;
        counts[slot] = (id == 0) ? 0 : count;
    }

    /** Очищает слот. */
    public void clearSlot(int slot) {
        if (slot < 0 || slot >= SIZE) return;
        items[slot] = 0;
        counts[slot] = 0;
    }

    public byte slotId(int slot) {
        return (slot < 0 || slot >= SIZE) ? 0 : items[slot];
    }

    public int slotCount(int slot) {
        return (slot < 0 || slot >= SIZE) ? 0 : counts[slot];
    }

    public boolean slotHas(int slot, byte blockId) {
        return slotId(slot) == blockId && counts[slot] > 0;
    }

    /** Есть ли где-нибудь такой предмет. */
    public boolean has(byte blockId) {
        for (int i = 0; i < SIZE; i++) {
            if (items[i] == blockId && counts[i] > 0) return true;
        }
        return false;
    }

    public void clear() {
        java.util.Arrays.fill(items, (byte) 0);
        java.util.Arrays.fill(counts, 0);
    }

    /** Первые n слотов (хотбар) для отрисовки: id и количества. */
    public byte[] firstIds(int n) {
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) out[i] = (i < SIZE) ? items[i] : 0;
        return out;
    }

    public int[] firstCounts(int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = (i < SIZE) ? counts[i] : 0;
        return out;
    }
}