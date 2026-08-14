package org.maintir.engine;

import org.maintir.world.TerrainGenerator.BiomeType;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class MainMenu {

    public enum MenuAction {
        NONE, OPEN_WORLD_SETUP, START_GAME, SETTINGS, EXIT, TOGGLE_FULLSCREEN, RES_1280, RES_1920, BACK
    }

    private static boolean mousePressedLastFrame = false;

    public static long worldSeed = 12345L;
    public static BiomeType selectedBiome = BiomeType.ALL;

    // Флаг локализации (true = RU, false = EN)
    public static boolean isRussian = true;

    private static void setup() {
        MenuRenderer.init();
    }

    public static MenuAction renderMain(long win, int w, int h) {
        setup();
        prepareGL();

        float[] mouse = getMousePos(win);
        boolean clicked = isJustClicked(win);
        MenuAction action = MenuAction.NONE;

        // Красивые кнопки с текстом (Цвета: R, G, B)
        String playText = isRussian ? "IGRT'" : "PLAY"; // Пиксельный алфавит FontRenderer
        String settingsText = isRussian ? "NASTROYKI" : "SETTINGS";
        String exitText = isRussian ? "VYHOD" : "EXIT";

        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w/2f - 140, h/2f - 80, 280, 55, 0.2f, 0.6f, 0.3f, "PLAY", clicked)) {
            action = MenuAction.OPEN_WORLD_SETUP;
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
        MenuAction action = MenuAction.NONE;

        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w/2f - 180, h/2f - 110, 360, 50, 0.2f, 0.5f, 0.8f, "BIOME: " + selectedBiome.name(), clicked)) {
            selectedBiome = BiomeType.values()[(selectedBiome.ordinal() + 1) % BiomeType.values().length];
        }

        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w/2f - 180, h/2f - 40, 360, 50, 0.3f, 0.6f, 0.4f, "SEED: " + worldSeed, clicked)) {
            worldSeed += 11111L;
        }

        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w/2f - 140, h/2f + 40, 280, 55, 0.1f, 0.8f, 0.3f, "START GAME", clicked)) {
            action = MenuAction.START_GAME;
        }

        if (MenuRenderer.drawColorButton(w, h, mouse[0], mouse[1], w/2f - 100, h/2f + 120, 200, 45, 0.5f, 0.2f, 0.2f, "BACK", clicked)) {
            action = MenuAction.BACK;
        }

        restoreGL();
        return action;
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
}