package org.maintir.engine;

import org.maintir.world.Blocks;
import org.maintir.world.Inventory;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;

/**
 * Экран инвентаря (клавиша E).
 * Творческий режим: вкладки «Строительное»/«Природная», клик по палитре кладёт блок
 * в выбранный слот хотбара. Выживание: сетка 8x8 с запасом (стек до 25).
 * Перетаскивание: клик по слоту захватывает стопку, клик в другом слот кладёт её
 * (пустой слот, досыпка в неполный стек того же типа или обмен).
 */
public final class InventoryGui {

    public static int tab = 0; // 0 — строительное, 1 — природная

    private static final byte[] CONSTRUCTION = {
            Blocks.DOOR_BOTTOM, Blocks.STAIRS_PLANKS_N, Blocks.STAIRS_STONE_N,
            Blocks.PLANKS, Blocks.PLANKS_SLAB, Blocks.LAMPA};
    private static final byte[] NATURE = {
            Blocks.WOOD, Blocks.STONE, Blocks.LEAVES, Blocks.GRASS, Blocks.DIRT,
            Blocks.SAND, Blocks.DIRT_SLAB, Blocks.STONE_SLAB};

    // Состояние перетаскивания: слот инвентаря, из которого взята стопка (или -1)
    private static int dragSlot = -1;
    private static byte dragId = 0;
    private static int dragCount = 0;

    /** Сброс перетаскивания при закрытии экрана. */
    public static void resetDrag() {
        dragSlot = -1;
        dragId = 0;
        dragCount = 0;
    }

    private InventoryGui() {}

    private static final int GRID_COLS = 8;
    private static final int GRID_ROWS = Inventory.SIZE / GRID_COLS;
    private static final int CELL = 46;
    private static final int STEP = 50;

    /** @return новый выбранный слот хотбара (может поменяться кликом по мини-хотбару). */
    public static int render(int w, int h, float mx, float my, boolean clicked,
                             boolean creative, Inventory inv, byte[] hotbar, int selectedSlot) {
        MenuRenderer.init();

        // Затемнение фона
        Hotbar.drawScreenFade(w, h, 0f, 0f, 0f, 0.6f);
        // drawScreenFade включает GL_DEPTH_TEST; для GUI его нужно снова выключить,
        // иначе после первого примитива всё остальное отсекается по глубине 0.5
        glDisable(GL_DEPTH_TEST);

        GridPos g = computeLayout(w, h, creative);
        byte nameOf = 0;

        // Панель с рамкой
        MenuRenderer.drawFlatRect(w, h, g.panelX, g.panelY, g.panelW, g.panelH, 0.10f, 0.11f, 0.15f);
        MenuRenderer.drawFlatRect(w, h, g.panelX, g.panelY, g.panelW, 8, 0.55f, 0.60f, 0.65f);
        FontRenderer.drawString("ИНВЕНТАРЬ", g.gridX + 2, g.panelY + 14, 3.0f, w, h);

        int hbY = g.gridY + GRID_ROWS * STEP + 16;

        if (creative) {
            // Вкладки
            int tabW = 150, tabH = 30;
            int taby = g.panelY + 52;
            if (drawTab(w, h, mx, my, clicked, g.gridX, taby, tabW, tabH, "СТРОИТЕЛЬНОЕ", tab == 0)) {
                tab = 0;
            }
            if (drawTab(w, h, mx, my, clicked, g.gridX + tabW + 6, taby, tabW, tabH, "ПРИРОДНАЯ", tab == 1)) {
                tab = 1;
            }

            // Палитра
            byte[] palette = (tab == 0) ? CONSTRUCTION : NATURE;
            int px0 = g.gridX;
            int py0 = taby + tabH + 12;
            FontRenderer.drawString("ПАЛИТРА — КЛИК ВЫБИРАЕТ БЛОК", px0 + 2, py0 - 16, 1.5f, w, h);
            for (int i = 0; i < palette.length; i++) {
                int x = px0 + i * (STEP + 8), y = py0;
                MenuRenderer.drawFlatRect(w, h, x, y, CELL, CELL, 0.13f, 0.14f, 0.17f);
                Hotbar.drawSlotIcon(w, h, x + 3, y + 3, CELL - 6, (byte) palette[i], 1.0f);
                boolean hov = MenuRenderer.isHovered(mx, my, x, y, CELL, CELL);
                if (hov) nameOf = (byte) palette[i];
                if (hov && clicked && dragSlot < 0) {
                    hotbar[selectedSlot % hotbar.length] = (byte) palette[i];
                }
            }

            // Запас (перетаскивание по той же сетке)
            FontRenderer.drawString("ЗАПАС — ПЕРЕТАСКИВАНИЕ", g.gridX + 2, g.gridY - 16, 1.5f, w, h);
            drawStorageGrid(w, h, mx, my, clicked, g.gridX, g.gridY, inv);
            selectedSlot = drawHotbar(w, h, mx, my, clicked, g.gridX, hbY, inv, hotbar, selectedSlot, false);
        } else {
            FontRenderer.drawString("КЛИК ПО СЛОТУ — ВЗЯТЬ, КЛИК В ДРУГОМ — ПОЛОЖИТЬ", g.gridX + 2, g.panelY + 40, 1.5f, w, h);
            FontRenderer.drawString("СТЕК ДО 25. ЗАКРЫТЬ: E ИЛИ ESC", g.gridX + 2, g.panelY + 60, 1.5f, w, h);
            drawStorageGrid(w, h, mx, my, clicked, g.gridX, g.gridY, inv);
            selectedSlot = drawHotbar(w, h, mx, my, clicked, g.gridX, hbY, inv, hotbar, selectedSlot, true);
        }

        // Перетаскиваемая стопка следует за курсором
        if (dragSlot >= 0 && dragId > 0) {
            float cx = mx - CELL / 2f, cy = my - CELL / 2f;
            MenuRenderer.drawFlatRect(w, h, cx - 3, cy - 3, CELL + 6, CELL + 6, 1f, 1f, 1f);
            Hotbar.drawSlotIcon(w, h, cx, cy, CELL, dragId, 1.0f);
            String n = String.valueOf(dragCount);
            FontRenderer.drawString(n, cx + CELL - n.length() * 6 - 3, cy + 2, 1.5f, w, h);
        }

        // Подпись наведённого предмета палитры
        if (nameOf > 0) {
            FontRenderer.drawString(Blocks.name(nameOf), g.panelX + 10, g.panelY + g.panelH - 26, 2.0f, w, h);
        }
        return selectedSlot;
    }

    /**
     * Сетка 8x8: клик по занятому слоту захватывает стопку, клик в другом слот кладёт
     * (пустой слот, досыпка в неполный стек того же типа или обмен).
     */
    private static void drawStorageGrid(int w, int h, float mx, float my, boolean clicked,
                                        int gridX, int gridY, Inventory inv) {
        for (int i = 0; i < Inventory.SIZE; i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int x = gridX + col * STEP;
            int y = gridY + row * STEP;
            byte id = inv.slotId(i);
            MenuRenderer.drawFlatRect(w, h, x, y, CELL, CELL, i < Inventory.HOTBAR_SLOTS ? 0.13f : 0.11f, 0.12f, 0.15f);
            if (id > 0) {
                Hotbar.drawSlotIcon(w, h, x + 3, y + 3, CELL - 6, id, 1.0f);
                if (inv.slotCount(i) > 1) {
                    String n = String.valueOf(inv.slotCount(i));
                    FontRenderer.drawString(n, x + CELL - n.length() * 6 - 3, y + 2, 1.4f, w, h);
                }
            } else if (i >= Inventory.HOTBAR_SLOTS) {
                MenuRenderer.drawFlatRect(w, h, x + 10, y + 10, CELL - 20, CELL - 20, 0.16f, 0.16f, 0.18f);
            }
            boolean hov = MenuRenderer.isHovered(mx, my, x, y, CELL, CELL);
            if (hov) {
                if (dragSlot >= 0) drawRing(w, h, x, y);
                if (clicked) clickInventorySlot(i, inv);
            }
        }
    }

    /** Мини-хотбар внизу панели. В выживании это слоты 0..8 инвентаря (перетаскивание). */
    private static int drawHotbar(int w, int h, float mx, float my, boolean clicked,
                                  int gridX, int hbY, Inventory inv, byte[] hotbar,
                                  int selectedSlot, boolean inventoryHotbar) {
        FontRenderer.drawString("ХОТБАР", gridX + 2, hbY - 16, 1.5f, w, h);
        for (int i = 0; i < Inventory.HOTBAR_SLOTS; i++) {
            int x = gridX + i * STEP;
            byte id = inventoryHotbar ? inv.slotId(i) : hotbar[i];
            boolean sel = i == selectedSlot;
            MenuRenderer.drawFlatRect(w, h, x, hbY, CELL, CELL,
                    sel ? 0.30f : 0.13f, sel ? 0.30f : 0.14f, 0.22f);
            if (id > 0) {
                Hotbar.drawSlotIcon(w, h, x + 4, hbY + 4, CELL - 8, id, 1.0f);
                if (inventoryHotbar && inv.slotCount(i) > 0) {
                    String n = String.valueOf(inv.slotCount(i));
                    FontRenderer.drawString(n, x + CELL - n.length() * 6 - 3, hbY + 2, 1.4f, w, h);
                }
            } else if (inventoryHotbar) {
                MenuRenderer.drawFlatRect(w, h, x + 10, hbY + 10, CELL - 20, CELL - 20, 0.18f, 0.18f, 0.20f);
            }
            if (sel) drawRing(w, h, x, hbY);
            boolean hov = MenuRenderer.isHovered(mx, my, x, hbY, CELL, CELL);
            if (hov) {
                if (dragSlot >= 0 && inventoryHotbar) drawRing(w, h, x, hbY);
                if (clicked) {
                    selectedSlot = i;
                    if (inventoryHotbar) clickInventorySlot(i, inv);
                }
            }
        }
        return selectedSlot;
    }

    /** Захват/укладка стопки: клик по слоту. */
    private static void clickInventorySlot(int slot, Inventory inv) {
        if (dragSlot >= 0) {
            if (slot == dragSlot) {
                inv.setSlot(dragSlot, dragId, dragCount);            // вернуть на место
            } else if (inv.slotId(slot) == 0) {
                inv.setSlot(slot, dragId, dragCount);                // положить в пустой слот
            } else if (inv.slotId(slot) == dragId && inv.slotCount(slot) + dragCount <= Inventory.MAX_STACK) {
                inv.setSlot(slot, dragId, inv.slotCount(slot) + dragCount); // досыпка в неполный стек
            } else {
                inv.swap(slot, dragSlot);                            // обмен стопками
            }
            resetDrag();
        } else {
            int n = inv.slotCount(slot);
            if (n > 0) {
                dragSlot = slot;
                dragId = inv.slotId(slot);
                dragCount = n;
                inv.clearSlot(slot);
            }
        }
    }

    /** Белая рамка вокруг слота (выбранный или цель перетаскивания). */
    private static void drawRing(int w, int h, float x, float y) {
        MenuRenderer.drawFlatRect(w, h, x - 2, y - 2, CELL + 4, 3, 1f, 1f, 1f);
        MenuRenderer.drawFlatRect(w, h, x - 2, y + CELL - 1, CELL + 4, 3, 1f, 1f, 1f);
        MenuRenderer.drawFlatRect(w, h, x - 2, y - 2, 3, CELL + 4, 1f, 1f, 1f);
        MenuRenderer.drawFlatRect(w, h, x + CELL - 1, y - 2, 3, CELL + 4, 1f, 1f, 1f);
    }

    private static boolean drawTab(int w, int h, float mx, float my, boolean clicked,
                                   int x, int y, int tw, int th, String label, boolean active) {
        MenuRenderer.drawFlatRect(w, h, x, y, tw, th, active ? 0.35f : 0.18f, active ? 0.40f : 0.20f, active ? 0.28f : 0.22f);
        FontRenderer.drawString(label, x + 8, y + 10, 1.9f, w, h);
        return clicked && MenuRenderer.isHovered(mx, my, x, y, tw, th);
    }

    private static final class GridPos {
        int panelX, panelY, panelW, panelH;
        int gridX, gridY;
    }

    private static GridPos computeLayout(int w, int h, boolean creative) {
        GridPos g = new GridPos();
        int panelW = GRID_COLS * STEP + 24;
        int top = creative ? 158 : 90;
        int panelH = top + GRID_ROWS * STEP + 96;
        g.panelW = panelW;
        g.panelH = panelH;
        g.panelX = (int) (w * 0.5f) - panelW / 2;
        g.panelY = (int) (h * 0.5f) - panelH / 2;
        g.gridX = g.panelX + 12;
        g.gridY = g.panelY + top;
        return g;
    }
}