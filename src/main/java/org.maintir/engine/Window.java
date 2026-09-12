package org.maintir.engine;

import org.lwjgl.glfw.GLFWVidMode;

// Правильные импорты OpenGL и GLFW для LWJGL 3:
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.opengl.GL13.GL_MULTISAMPLE;

public class Window {
    private String title;
    private int width, height;
    private long handle;
    private boolean isFullscreen = false;

    public Window(String title, int width, int height) {
        this.title = title;
        this.width = width;
        this.height = height;
    }

    public void init() {
        if (!glfwInit()) throw new IllegalStateException("Ошибка GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4); // MSAA x4 — сглаживание рёбер

        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) throw new RuntimeException("Не удалось создать окно GLFW");

        glfwMakeContextCurrent(handle);
        glfwSwapInterval(1); // VSync
        glfwShowWindow(handle);

        // Вот эта строчка связывает OpenGL с контекстом GLFW:
        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_MULTISAMPLE); // MSAA

        glfwSetFramebufferSizeCallback(handle, (win, w, h) -> {
            this.width = w;
            this.height = h;
            glViewport(0, 0, w, h);
        });

        // Колесо мыши (для хотбара)
        glfwSetScrollCallback(handle, (win, xoff, yoff) -> scrollAccum += yoff);
    }

    private double scrollAccum = 0;

    /** Возвращает накопленное прокручивание колеса и сбрасывает его. */
    public double consumeScroll() {
        double s = scrollAccum;
        scrollAccum = 0;
        return s;
    }

    public void setResolution(int w, int h) {
        this.width = w;
        this.height = h;
        if (!isFullscreen) {
            glfwSetWindowSize(handle, w, h);
            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidMode != null) {
                glfwSetWindowPos(handle, (vidMode.width() - w) / 2, (vidMode.height() - h) / 2);
            }
        }
    }

    public void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode vidMode = glfwGetVideoMode(monitor);

        if (isFullscreen && vidMode != null) {
            glfwSetWindowMonitor(handle, monitor, 0, 0, vidMode.width(), vidMode.height(), vidMode.refreshRate());
        } else {
            glfwSetWindowMonitor(handle, NULL, 100, 100, width, height, 0);
        }
    }

    public boolean shouldClose() { return glfwWindowShouldClose(handle); }
    public void update() { glfwSwapBuffers(handle); glfwPollEvents(); }
    public void cleanup() { glfwDestroyWindow(handle); glfwTerminate(); }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getHandle() { return handle; }
    public boolean isFullscreen() { return isFullscreen; }
}