package org.maintir.engine;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class LoadingScreen {
    private static int vao, vbo;
    private static Shader uiShader;
    private static boolean initialized = false;

    private static final String UI_VERTEX_SHADER = """
        #version 330 core
        layout (location = 0) in vec2 aPos;
        layout (location = 1) in vec3 aColor;

        out vec3 FragColor;
        uniform mat4 projection;

        void main() {
            FragColor = aColor;
            gl_Position = projection * vec4(aPos, 0.0, 1.0);
        }
        """;

    private static final String UI_FRAGMENT_SHADER = """
        #version 330 core
        in vec3 FragColor;
        out vec4 FragColorOut;

        void main() {
            FragColorOut = vec4(FragColor, 1.0);
        }
        """;

    private static void initUI() {
        // Передаем true как третий аргумент, чтобы Shader использовал исходный код строк
        uiShader = new Shader(UI_VERTEX_SHADER, UI_FRAGMENT_SHADER, true);

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        initialized = true;
    }

    public static void render(int windowWidth, int windowHeight, String statusText, float progress) {
        if (!initialized) {
            initUI();
        }

        glClearColor(0.08f, 0.09f, 0.12f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        uiShader.use();

        Matrix4f proj = new Matrix4f().ortho(0, windowWidth, windowHeight, 0, -1, 1);
        uiShader.setUniform("projection", proj);

        float barWidth = 400.0f;
        float barHeight = 20.0f;
        float x = (windowWidth - barWidth) / 2.0f;
        float y = (windowHeight - barHeight) / 2.0f + 50.0f;

        float[] vertices = new float[] {
                // 1. Рамка
                x - 4, y - 4, 0.2f, 0.22f, 0.28f,
                x + barWidth + 4, y - 4, 0.2f, 0.22f, 0.28f,
                x + barWidth + 4, y + barHeight + 4, 0.2f, 0.22f, 0.28f,
                x - 4, y - 4, 0.2f, 0.22f, 0.28f,
                x + barWidth + 4, y + barHeight + 4, 0.2f, 0.22f, 0.28f,
                x - 4, y + barHeight + 4, 0.2f, 0.22f, 0.28f,

                // 2. Фон
                x, y, 0.05f, 0.05f, 0.07f,
                x + barWidth, y, 0.05f, 0.05f, 0.07f,
                x + barWidth, y + barHeight, 0.05f, 0.05f, 0.07f,
                x, y, 0.05f, 0.05f, 0.07f,
                x + barWidth, y + barHeight, 0.05f, 0.05f, 0.07f,
                x, y + barHeight, 0.05f, 0.05f, 0.07f,

                // 3. Прогресс
                x, y, 0.2f, 0.85f, 0.4f,
                x + (barWidth * progress), y, 0.2f, 0.85f, 0.4f,
                x + (barWidth * progress), y + barHeight, 0.2f, 0.85f, 0.4f,
                x, y, 0.2f, 0.85f, 0.4f,
                x + (barWidth * progress), y + barHeight, 0.2f, 0.85f, 0.4f,
                x, y + barHeight, 0.2f, 0.85f, 0.4f
        };

        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STREAM_DRAW);

        int stride = 5 * Float.BYTES;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glDrawArrays(GL_TRIANGLES, 0, 18);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public static void cleanup() {
        if (initialized) {
            glDeleteBuffers(vbo);
            glDeleteVertexArrays(vao);
            initialized = false;
        }
    }
}