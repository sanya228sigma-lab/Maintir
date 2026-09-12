package org.maintir.engine;

import org.maintir.world.TerrainGenerator.BiomeType;
import org.maintir.world.WorldSave;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import java.util.HashMap;
import java.util.Map;

public class MainMenu {

    public enum MenuAction {
        NONE, OPEN_WORLDS, OPEN_WORLD_SETUP, START_GAME, LOAD_WORLD,
        SETTINGS, EXIT, TOGGLE_FULLSCREEN, RES_1280, RES_1920, BACK
    }

    private static boolean mousePressedLastFrame = false;
    private static boolean rightPressedLastFrame = false;

    public static long worldSeed = 12345L;
    public static BiomeType selectedBiome = BiomeType.ALL;

    // --- Тонкая настройка мира ---
    public static int renderDistance = 8;        // чанки (3..16)
    // Плотность деревьев и поголовье свиней генерируются сами — настроек больше нет

    /** Выбранный в списке мир (индекс в savesCache). */
    public static int selectedWorld = -1;
    /** Кэш списка сохранений — обновляется при входе на экран выбора мира. */
    public static final java.util.List<WorldSave.Meta> savesCache = new java.util.ArrayList<>();

    public static void refreshSaves() {
        savesCache.clear();
        savesCache.addAll(WorldSave.listSaves());
        selectedWorld = -1;
    }

    // Режим ввода сида с клавиатуры
    private static boolean editingSeed = false;
    private static String seedBuffer = "";
    private static final Map<Integer, Boolean> keyPrev = new HashMap<>();

    private static void setup() {
        MenuRenderer.init();
    }

    public static MenuAction renderMain(long win, int w, int h) {
        setup();
        prepareGL();

        float[] mouse = getMousePos(win);
        boolean clicked = isJustClicked(win);
        MenuAction action = MenuAction.NONE;

        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w/2f - 140, h/2f - 80, 280, 55, 0.2f, 0.6f, 0.3f, "PLAY", clicked)) {
            refreshSaves();
            action = MenuAction.OPEN_WORLDS;
        }

        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w/2f - 140, h/2f - 10, 280, 55, 0.25f, 0.35f, 0.55f, "SETTINGS", clicked)) {
            action = MenuAction.SETTINGS;
        }

        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w/2f - 140, h/2f + 60, 280, 55, 0.7f, 0.2f, 0.2f, "EXIT", clicked)) {
            action = MenuAction.EXIT;
        }

        restoreGL();
        return action;
    }

    public static MenuAction renderWorldSetup(long win, int w, int h) {
        setup();
        prepareGL();

        float[] mouse = getMousePos(win);
        boolean clicked = isJustClicked(win);
        boolean rightClicked = isJustRightClicked(win);
        MenuAction action = MenuAction.NONE;

        float x = w/2f - 180;
        float bw = 360;
        float bh = 50;

        // --- Биом ---
        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], x, h/2f - 170, bw, bh, 0.2f, 0.5f, 0.8f, "БИОМ: " + selectedBiome.displayName(), clicked)) {
            selectedBiome = BiomeType.values()[(selectedBiome.ordinal() + 1) % BiomeType.values().length];
        }

        // --- Сид: клик начинает/завершает ввод, цифры набираются с клавиатуры ---
        String seedLabel = editingSeed ? "SEED: " + seedBuffer : "SEED: " + worldSeed;
        boolean seedHover = MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], x, h/2f - 115, bw, bh,
                editingSeed ? 0.15f : 0.3f, editingSeed ? 0.45f : 0.6f, editingSeed ? 0.2f : 0.4f, seedLabel, clicked);
        if (seedHover && clicked) {
            if (!editingSeed) {
                editingSeed = true;
                seedBuffer = String.valueOf(worldSeed);
            } else {
                applySeedBuffer();
            }
        } else if (editingSeed && clicked && !seedHover) {
            applySeedBuffer(); // клик мимо поля — подтвердить
        }
        if (editingSeed) {
            handleSeedKeyboard(win, w, h, x, h/2f - 115, bw, bh, seedLabel);
        }

        // --- Дальность прорисовки: ЛКМ +1, ПКМ -1 ---
        float dx = w/2f - 180;
        boolean distHover = MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], dx, h/2f - 60, bw, bh, 0.45f, 0.35f, 0.6f, "ДАЛЬНОСТЬ: " + renderDistance, false);
        if (distHover && clicked) renderDistance = Math.min(16, renderDistance + 1);
        if (distHover && rightClicked) renderDistance = Math.max(3, renderDistance - 1);

        // --- Старт / назад ---
        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w/2f - 140, h/2f + 30, 280, 55, 0.1f, 0.8f, 0.3f, "START GAME", clicked)) {
            applySeedBuffer();
            action = MenuAction.START_GAME;
        }

        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w/2f - 100, h/2f + 105, 200, 45, 0.5f, 0.2f, 0.2f, "BACK", clicked)) {
            applySeedBuffer();
            action = MenuAction.BACK;
        }

        restoreGL();
        return action;
    }

    /** Экран выбора сохранённого мира (после PLAY). */
    public static MenuAction renderWorlds(long win, int w, int h) {
        setup();
        prepareGL();

        float[] mouse = getMousePos(win);
        boolean clicked = isJustClicked(win);
        MenuAction action = MenuAction.NONE;

        FontRenderer.drawString("ВЫБОР МИРА", w / 2f - 90, h / 2f - 260, 4.0f, w, h);

        float x = w / 2f - 220;
        float bw = 440;
        float bh = 46;
        float y = h / 2f - 200;

        if (savesCache.isEmpty()) {
            FontRenderer.drawString("ПОКА НЕТ СОХРАНЕНИЙ", w / 2f - 130, y + 20, 2.5f, w, h);
            y += 70;
        }

        int shown = Math.min(savesCache.size(), 6);
        for (int i = 0; i < shown; i++) {
            WorldSave.Meta m = savesCache.get(i);
            String label = m.name + "  [" + m.biome + "  SEED " + m.seed + "]";
            if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], x, y + i * (bh + 8), bw, bh,
                    0.25f, 0.45f, 0.3f, label, clicked)) {
                selectedWorld = i;
                action = MenuAction.LOAD_WORLD;
                break;
            }
        }
        if (savesCache.size() > 6) {
            FontRenderer.drawString("И ЕЩЕ " + (savesCache.size() - 6) + "...", x, y + 6 * (bh + 8), 2.0f, w, h);
        }

        // Кнопка нового мира — под списком
        float ny = h / 2f + 150;
        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w / 2f - 140, ny, 280, 50,
                0.15f, 0.5f, 0.65f, "НОВЫЙ МИР", clicked)) {
            action = MenuAction.OPEN_WORLD_SETUP;
        }

        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w / 2f - 100, ny + 62, 200, 42,
                0.5f, 0.2f, 0.2f, "BACK", clicked)) {
            action = MenuAction.BACK;
        }

        restoreGL();
        return action;
    }

    private static void handleSeedKeyboard(long win, int w, int h, float bx, float by, float bw, float bh, String label) {
        // Цифры (верхний ряд и нампад)
        for (int d = 0; d <= 9; d++) {
            if (justPressed(win, GLFW_KEY_0 + d) || justPressed(win, GLFW_KEY_KP_0 + d)) {
                if (seedBuffer.length() < 18) seedBuffer += (char) ('0' + d);
            }
        }
        if (justPressed(win, GLFW_KEY_BACKSPACE) && !seedBuffer.isEmpty()) {
            seedBuffer = seedBuffer.substring(0, seedBuffer.length() - 1);
        }
        if (justPressed(win, GLFW_KEY_MINUS) || justPressed(win, GLFW_KEY_KP_SUBTRACT)) {
            if (seedBuffer.startsWith("-")) seedBuffer = seedBuffer.substring(1);
            else seedBuffer = "-" + seedBuffer;
        }
        if (justPressed(win, GLFW_KEY_ENTER) || justPressed(win, GLFW_KEY_KP_ENTER)) {
            applySeedBuffer();
        }

        // Мигающий курсор после текста
        long t = (long) (glfwGetTime() * 2.0);
        if (t % 2 == 0) {
            float scale = 3.0f;
            float textW = label.length() * 6 * scale;
            float textX = bx + (bw - textW) / 2.0f;
            float textY = by + (bh - (5 * scale)) / 2.0f;
            MenuRenderer.drawFlatRect(w, h, textX + textW + 3, textY, 8, 15, 1f, 1f, 1f);
        }
    }

    private static void applySeedBuffer() {
        if (!editingSeed) return;
        editingSeed = false;
        try {
            worldSeed = Long.parseLong(seedBuffer.isEmpty() ? "0" : seedBuffer);
        } catch (NumberFormatException ignored) {
            // слишком длинное число — оставляем старый сид
        }
    }

    public static MenuAction renderSettings(long win, int w, int h) {
        setup();
        prepareGL();

        float[] mouse = getMousePos(win);
        boolean clicked = isJustClicked(win);
        MenuAction action = MenuAction.NONE;

        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w/2f - 150, h/2f - 100, 300, 50, 0.4f, 0.4f, 0.7f, "FULLSCREEN", clicked)) action = MenuAction.TOGGLE_FULLSCREEN;
        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w/2f - 150, h/2f - 30, 300, 50, 0.3f, 0.5f, 0.5f, "1280X720", clicked)) action = MenuAction.RES_1280;
        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w/2f - 150, h/2f + 40, 300, 50, 0.3f, 0.5f, 0.5f, "1920X1080", clicked)) action = MenuAction.RES_1920;
        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w/2f - 150, h/2f + 120, 300, 50, 0.5f, 0.2f, 0.2f, "BACK", clicked)) action = MenuAction.BACK;

        restoreGL();
        return action;
    }

    /** Однократное нажатие клавиши (грань PRESS). */
    private static boolean justPressed(long win, int key) {
        boolean now = glfwGetKey(win, key) == GLFW_PRESS;
        boolean prev = keyPrev.getOrDefault(key, false);
        keyPrev.put(key, now);
        return now && !prev;
    }

    private static void prepareGL() {
        glClearColor(0.08f, 0.09f, 0.12f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
    }

    private static void restoreGL() {
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
    }

    private static float[] getMousePos(long win) {
        double[] mx = new double[1], my = new double[1];
        glfwGetCursorPos(win, mx, my);
        return new float[]{(float) mx[0], (float) my[0]};
    }

    private static boolean isJustClicked(long win) {
        boolean click = glfwGetMouseButton(win, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        boolean just = click && !mousePressedLastFrame;
        mousePressedLastFrame = click;
        return just;
    }

    private static boolean isJustRightClicked(long win) {
        boolean click = glfwGetMouseButton(win, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;
        boolean just = click && !rightPressedLastFrame;
        rightPressedLastFrame = click;
        return just;
    }
}
