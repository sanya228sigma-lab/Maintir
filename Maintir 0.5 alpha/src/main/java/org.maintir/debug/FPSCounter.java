package org.maintir.debug;

import org.lwjgl.glfw.GLFW;

public class FPSCounter {
    private double lastTime;
    private int frames;
    private int currentFPS;

    public FPSCounter() {
        this.lastTime = GLFW.glfwGetTime();
    }

    public void update(long windowHandle, String baseTitle) {
        double currentTime = GLFW.glfwGetTime();
        frames++;
        if (currentTime - lastTime >= 1.0) {
            currentFPS = frames;
            GLFW.glfwSetWindowTitle(windowHandle, baseTitle + " | FPS: " + currentFPS);
            frames = 0;
            lastTime = currentTime;
        }
    }

    public int getFPS() { return currentFPS; }
}