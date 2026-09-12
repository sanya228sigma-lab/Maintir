package org.maintir.engine;

import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;
import org.maintir.world.Blocks;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Хотбар: слоты с текстурами блоков (из массива текстур игры),
 * подсветка выбранного слота и номера клавиш.
 * Блоки и количества передаются извне: в креативе — выбранные через вкладки,
 * в выживании — первые 9 слотов инвентаря.
 */
public class Hotbar {

    private static Shader slotShader;     // иконки блоков из TextureArray
    private static int quadVao, quadVbo;
    private static int frameVao, frameVbo;
    private static boolean initialized = false;
    private static int slotTextureId = 0;

    private static void init() {
        if (initialized) return;

        String vert = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec2 aUV;
            out vec2 uv;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                uv = aUV;
            }
        """;
        String frag = """
            #version 330 core
            in vec2 uv;
            out vec4 FragColor;
            uniform sampler2DArray uArray;
            uniform float uLayer;
            uniform float uBright;
            void main() {
                FragColor = texture(uArray, vec3(uv, uLayer)) * uBright;
            }
        """;
        slotShader = new Shader(vert, frag, true);

        quadVao = glGenVertexArrays();
        quadVbo = glGenBuffers();
        glBindVertexArray(quadVao);
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        glBufferData(GL_ARRAY_BUFFER, 6 * 4 * Float.BYTES, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        frameVao = glGenVertexArrays();
        frameVbo = glGenBuffers();
        glBindVertexArray(frameVao);
        glBindBuffer(GL_ARRAY_BUFFER, frameVbo);
        glBufferData(GL_ARRAY_BUFFER, 6 * 5 * Float.BYTES, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 5 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        slotTextureId = MenuRenderer.loadTexture("/invtntfr.png");
        initialized = true;
    }

    /**
     * @param slotIds     id блоков в слотах (9 штук)
     * @param slotCounts  количества по слотам (null — креатив, бесконечно)
     */
    public static void render(int screenW, int screenH, int selectedSlot, byte[] slotIds, int[] slotCounts) {
        init();

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        int slotSize = 52;
        int pad = 4;
        int count = slotIds.length;
        int totalWidth = count * (slotSize + pad);
        float startX = (screenW - totalWidth) / 2.0f;
        float y = screenH - slotSize - 18;

        for (int i = 0; i < count; i++) {
            float x = startX + i * (slotSize + pad);
            boolean selected = i == selectedSlot;

            // Фон слота
            if (slotTextureId != 0) {
                MenuRenderer.drawTextureButton(screenW, screenH, -1, -1, x, y, slotSize, slotSize, slotTextureId, false);
            } else {
                drawFlatRect(screenW, screenH, x, y, slotSize, slotSize, 0.12f, 0.12f, 0.14f, 0.8f);
            }

            // Иконка блока — реальная текстура из массива
            byte id = slotIds[i];
            boolean empty = id <= 0 || (slotCounts != null && slotCounts[i] <= 0);
            float m = selected ? 7 : 9;
            drawArrayIcon(screenW, screenH, x + m, y + m, slotSize - m * 2, Blocks.texLayer(id),
                    empty ? 0.35f : 1.0f);

            if (selected) {
                float b = 2.0f;
                drawFlatRect(screenW, screenH, x - b, y - b, slotSize + b * 2, b, 1f, 1f, 1f, 0.95f);
                drawFlatRect(screenW, screenH, x - b, y + slotSize, slotSize + b * 2, b, 1f, 1f, 1f, 0.95f);
                drawFlatRect(screenW, screenH, x - b, y, b, slotSize, 1f, 1f, 1f, 0.95f);
                drawFlatRect(screenW, screenH, x + slotSize, y, b, slotSize, 1f, 1f, 1f, 0.95f);
            }

            FontRenderer.drawString(String.valueOf(i + 1), x + 4, y + slotSize + 6, 2.0f, screenW, screenH);

            // Количество в режиме выживания
            if (slotCounts != null && slotCounts[i] > 0) {
                String n = String.valueOf(slotCounts[i]);
                FontRenderer.drawString(n, x + slotSize - n.length() * 6 - 3, y + 2, 1.5f, screenW, screenH);
            }
        }

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    /** Полупрозрачная заливка всего экрана (вспышка урона и т.п.). */
    public static void drawScreenFade(int screenW, int screenH, float r, float g, float b, float alpha) {
        init();
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        drawFlatRect(screenW, screenH, 0, 0, screenW, screenH, r, g, b, alpha);
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    /** Иконка блока из массива текстур игры (публичная — для инвентаря). */
    public static void drawSlotIcon(int screenW, int screenH, float x, float y, float size, byte blockId, float bright) {
        init();
        if (blockId <= 0) return;
        drawArrayIcon(screenW, screenH, x, y, size, Blocks.texLayer(blockId), bright);
    }

    /** Иконка блока из массива текстур игры. */
    private static void drawArrayIcon(int screenW, int screenH, float x, float y, float size, float layer) {
        drawArrayIcon(screenW, screenH, x, y, size, layer, 1.0f);
    }

    private static void drawArrayIcon(int screenW, int screenH, float x, float y, float size, float layer, float bright) {
        float x1 = (x / screenW) * 2.0f - 1.0f;
        float y1 = 1.0f - (y / screenH) * 2.0f;
        float x2 = ((x + size) / screenW) * 2.0f - 1.0f;
        float y2 = 1.0f - ((y + size) / screenH) * 2.0f;

        float[] v = {
                x1, y1, 0, 1,  x2, y1, 1, 1,  x2, y2, 1, 0,
                x1, y1, 0, 1,  x2, y2, 1, 0,  x1, y2, 0, 0
        };
        FloatBuffer buf = BufferUtils.createFloatBuffer(v.length);
        buf.put(v).flip();

        slotShader.use();
        slotShader.setUniform("uArray", 0); // массив текстур уже привязан к юниту 0 в игровом рендере
        slotShader.setUniform("uLayer", layer);
        slotShader.setUniform("uBright", bright);

        glBindVertexArray(quadVao);
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buf);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
    }

    /** Цветной прямоугольник с прозрачностью. */
    private static void drawFlatRect(int screenW, int screenH, float x, float y, float w, float h,
                                     float r, float g, float b, float a) {
        float x1 = (x / screenW) * 2.0f - 1.0f;
        float y1 = 1.0f - (y / screenH) * 2.0f;
        float x2 = ((x + w) / screenW) * 2.0f - 1.0f;
        float y2 = 1.0f - ((y + h) / screenH) * 2.0f;

        float[] v = {
                x1, y1, r, g, b,  x2, y1, r, g, b,  x2, y2, r, g, b,
                x1, y1, r, g, b,  x2, y2, r, g, b,  x1, y2, r, g, b
        };
        FloatBuffer buf = BufferUtils.createFloatBuffer(v.length);
        buf.put(v).flip();

        ensureFrameShader();
        frameShader.use();
        frameShader.setUniform("uAlpha", a);
        glBindVertexArray(frameVao);
        glBindBuffer(GL_ARRAY_BUFFER, frameVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buf);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
    }

    private static Shader frameShader;
    private static void ensureFrameShader() {
        if (frameShader != null) return;
        String vert = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec3 aColor;
            out vec3 c;
            void main() { gl_Position = vec4(aPos, 0.0, 1.0); c = aColor; }
        """;
        String frag = """
            #version 330 core
            in vec3 c;
            out vec4 FragColor;
            uniform float uAlpha;
            void main() { FragColor = vec4(c, uAlpha); }
        """;
        frameShader = new Shader(vert, frag, true);
    }

    private static int crosshairId = -1;

    /** Прицел по центру экрана (текстура с прозрачностью). */
    public static void drawCrosshair(int screenW, int screenH) {
        init();
        if (crosshairId == -1) crosshairId = MenuRenderer.loadTexture("/Prisel.png");
        if (crosshairId == 0) return;

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        int size = 20;
        float x = (screenW - size) / 2.0f, y = (screenH - size) / 2.0f;
        MenuRenderer.drawTextureButton(screenW, screenH, -1, -1, x, y, size, size, crosshairId, false);

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }
}
