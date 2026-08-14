package org.maintir;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.maintir.debug.FPSCounter;
import org.maintir.engine.*;
import org.maintir.world.World;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Main {

    public enum GameState {
        MENU, WORLD_SETUP, SETTINGS, GAME
    }

    private static GameState state = GameState.MENU;
    private static boolean escPressedLastFrame = false;

    public static void main(String[] args) {
        Window window = new Window("Maintir Voxel Engine", 1280, 720);
        window.init();

        Camera camera = new Camera();
        camera.position.set(0, 110, 0);

        World world = new World();

        Shader shader = new Shader("/shaders/block.vert", "/shaders/block.frag");
        TextureArray textures = new TextureArray(new String[] {
                "/Trava.png",
                "/kamen_cyka_64p.png",
                "/pesok_nasok.png",
                "/Trava_s_naskom.png"
        });

        FPSCounter fpsCounter = new FPSCounter();
        float lastFrame = (float) glfwGetTime();

        while (!window.shouldClose()) {
            float currentFrame = (float) glfwGetTime();
            float deltaTime = currentFrame - lastFrame;
            lastFrame = currentFrame;

            boolean escPressed = glfwGetKey(window.getHandle(), GLFW_KEY_ESCAPE) == GLFW_PRESS;

            // --- 1. ГЛАВНОЕ МЕНЮ ---
            if (state == GameState.MENU) {
                glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                MainMenu.MenuAction action = MainMenu.renderMain(window.getHandle(), window.getWidth(), window.getHeight());

                if (action == MainMenu.MenuAction.OPEN_WORLD_SETUP) {
                    state = GameState.WORLD_SETUP;
                } else if (action == MainMenu.MenuAction.SETTINGS) {
                    state = GameState.SETTINGS;
                } else if (action == MainMenu.MenuAction.EXIT) {
                    glfwSetWindowShouldClose(window.getHandle(), true);
                }
            }
            // --- 2. НАСТРОЙКА МИРА ---
            else if (state == GameState.WORLD_SETUP) {
                glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                MainMenu.MenuAction action = MainMenu.renderWorldSetup(window.getHandle(), window.getWidth(), window.getHeight());

                if (action == MainMenu.MenuAction.START_GAME) {
                    world.setWorldSettings(MainMenu.worldSeed, MainMenu.selectedBiome);
                    world.preloadArea(camera.position, (status, progress) -> {
                        LoadingScreen.render(window.getWidth(), window.getHeight(), status, progress);
                        window.update();
                    });
                    glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    state = GameState.GAME;
                } else if (action == MainMenu.MenuAction.BACK) {
                    state = GameState.MENU;
                }
            }
            // --- 3. НАСТРОЙКИ ---
            else if (state == GameState.SETTINGS) {
                glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                MainMenu.MenuAction action = MainMenu.renderSettings(window.getHandle(), window.getWidth(), window.getHeight());

                if (action == MainMenu.MenuAction.TOGGLE_FULLSCREEN) window.toggleFullscreen();
                else if (action == MainMenu.MenuAction.RES_1280) window.setResolution(1280, 720);
                else if (action == MainMenu.MenuAction.RES_1920) window.setResolution(1920, 1080);
                else if (action == MainMenu.MenuAction.BACK || (escPressed && !escPressedLastFrame)) state = GameState.MENU;
            }
            // --- 4. ИГРА ---
            else if (state == GameState.GAME) {
                if (escPressed && !escPressedLastFrame) {
                    state = GameState.MENU;
                    escPressedLastFrame = true;
                    continue;
                }

                camera.updateInput(window.getHandle(), deltaTime);
                camera.handleMouse(window.getHandle());

                world.update(camera.position);

                glClearColor(0.5f, 0.8f, 1.0f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                shader.use();
                textures.bind();

                Matrix4f projection = new Matrix4f().perspective(
                        (float) Math.toRadians(70.0f),
                        (float) window.getWidth() / window.getHeight(),
                        0.1f, 1000.0f
                );

                shader.setUniform("projection", projection);
                shader.setUniform("view", camera.getViewMatrix());
                shader.setUniform("model", new Matrix4f());
                shader.setUniform("lightDir", new Vector3f(0.5f, 1.0f, 0.3f));

                world.render();
                fpsCounter.update(window.getHandle(), "Maintir Voxel Engine");
            }

            escPressedLastFrame = escPressed;
            window.update();
        }

        LoadingScreen.cleanup();
        textures.cleanup();
        window.cleanup();
    }
}