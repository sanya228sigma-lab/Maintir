package org.maintir.engine;

import static org.lwjgl.opengl.GL11.*;

public class Hotbar {

    private static int textureId = 0;
    private static boolean initialized = false;

    private static void init() {
        if (initialized) return;
        // Загружаем указанную тобой текстуру ячейки
        textureId = MenuRenderer.loadTexture("/invtntfr.png");
        initialized = true;
    }

    public static void render(int screenWidth, int screenHeight, int selectedSlot) {
        init();

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        int slotSize = 48; // Размер 1 ячейки на экране в пикселях
        int totalSlots = 9;
        int totalWidth = totalSlots * slotSize;
        float startX = (screenWidth - totalWidth) / 2.0f;
        float y = screenHeight - slotSize - 15; // 15px отступа снизу

        for (int i = 0; i < totalSlots; i++) {
            float x = startX + i * slotSize;

            if (textureId != 0) {
                // Отрисовка ячейки из invtntfr.png
                MenuRenderer.drawTextureButton(screenWidth, screenHeight, -1, -1, x, y, slotSize, slotSize, textureId, false);
            } else {
                // Запасной вариант, если картинку еще не положили
                MenuRenderer.drawColorButton(screenWidth, screenHeight, -1, -1, x, y, slotSize - 2, slotSize - 2, 0.2f, 0.2f, 0.2f, "", false);
            }

            // Выделение выбранного слота ярким рамкой/текстом
            if (i == selectedSlot) {
                FontRenderer.drawString(">", x + 4, y + 4, 2.0f, screenWidth, screenHeight);
            }
        }

        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
    }
}