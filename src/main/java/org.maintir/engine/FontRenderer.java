package org.maintir.engine;

import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class FontRenderer {

    private static int vao, vbo;
    private static Shader textShader;
    private static boolean initialized = false;

    // 5x5 Пиксельная сетка для каждого символа
    private static final Map<Character, int[]> GLYPHS = new HashMap<>();

    static {
        GLYPHS.put('A', new int[]{0,1,1,1,0, 1,0,0,0,1, 1,1,1,1,1, 1,0,0,0,1, 1,0,0,0,1});
        GLYPHS.put('B', new int[]{1,1,1,1,0, 1,0,0,0,1, 1,1,1,1,0, 1,0,0,0,1, 1,1,1,1,0});
        GLYPHS.put('C', new int[]{0,1,1,1,1, 1,0,0,0,0, 1,0,0,0,0, 1,0,0,0,0, 0,1,1,1,1});
        GLYPHS.put('D', new int[]{1,1,1,1,0, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 1,1,1,1,0});
        GLYPHS.put('E', new int[]{1,1,1,1,1, 1,0,0,0,0, 1,1,1,1,0, 1,0,0,0,0, 1,1,1,1,1});
        GLYPHS.put('F', new int[]{1,1,1,1,1, 1,0,0,0,0, 1,1,1,1,0, 1,0,0,0,0, 1,0,0,0,0});
        GLYPHS.put('G', new int[]{0,1,1,1,1, 1,0,0,0,0, 1,0,1,1,1, 1,0,0,0,1, 0,1,1,1,0});
        GLYPHS.put('H', new int[]{1,0,0,0,1, 1,0,0,0,1, 1,1,1,1,1, 1,0,0,0,1, 1,0,0,0,1});
        GLYPHS.put('I', new int[]{1,1,1,1,1, 0,0,1,0,0, 0,0,1,0,0, 0,0,1,0,0, 1,1,1,1,1});
        GLYPHS.put('K', new int[]{1,0,0,0,1, 1,0,0,1,0, 1,1,1,0,0, 1,0,0,1,0, 1,0,0,0,1});
        GLYPHS.put('L', new int[]{1,0,0,0,0, 1,0,0,0,0, 1,0,0,0,0, 1,0,0,0,0, 1,1,1,1,1});
        GLYPHS.put('M', new int[]{1,0,0,0,1, 1,1,0,1,1, 1,0,1,0,1, 1,0,0,0,1, 1,0,0,0,1});
        GLYPHS.put('N', new int[]{1,0,0,0,1, 1,1,0,0,1, 1,0,1,0,1, 1,0,0,1,1, 1,0,0,0,1});
        GLYPHS.put('O', new int[]{0,1,1,1,0, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0});
        GLYPHS.put('P', new int[]{1,1,1,1,0, 1,0,0,0,1, 1,1,1,1,0, 1,0,0,0,0, 1,0,0,0,0});
        GLYPHS.put('R', new int[]{1,1,1,1,0, 1,0,0,0,1, 1,1,1,1,0, 1,0,0,1,0, 1,0,0,0,1});
        GLYPHS.put('S', new int[]{0,1,1,1,1, 1,0,0,0,0, 0,1,1,1,0, 0,0,0,0,1, 1,1,1,1,0});
        GLYPHS.put('T', new int[]{1,1,1,1,1, 0,0,1,0,0, 0,0,1,0,0, 0,0,1,0,0, 0,0,1,0,0});
        GLYPHS.put('U', new int[]{1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0});
        GLYPHS.put('V', new int[]{1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 0,1,0,1,0, 0,0,1,0,0});
        GLYPHS.put('W', new int[]{1,0,0,0,1, 1,0,0,0,1, 1,0,1,0,1, 1,1,0,1,1, 1,0,0,0,1});
        GLYPHS.put('X', new int[]{1,0,0,0,1, 0,1,0,1,0, 0,0,1,0,0, 0,1,0,1,0, 1,0,0,0,1});
        GLYPHS.put('Y', new int[]{1,0,0,0,1, 0,1,0,1,0, 0,0,1,0,0, 0,0,1,0,0, 0,0,1,0,0});
        GLYPHS.put('Z', new int[]{1,1,1,1,1, 0,0,0,1,0, 0,0,1,0,0, 0,1,0,0,0, 1,1,1,1,1});

        GLYPHS.put('0', new int[]{1,1,1,1,1, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 1,1,1,1,1});
        GLYPHS.put('1', new int[]{0,0,1,0,0, 0,1,1,0,0, 0,0,1,0,0, 0,0,1,0,0, 0,1,1,1,0});
        GLYPHS.put('2', new int[]{1,1,1,1,0, 0,0,0,0,1, 0,1,1,1,0, 1,0,0,0,0, 1,1,1,1,1});
        GLYPHS.put('3', new int[]{1,1,1,1,0, 0,0,0,0,1, 0,1,1,1,0, 0,0,0,0,1, 1,1,1,1,0});
        GLYPHS.put('4', new int[]{1,0,0,0,1, 1,0,0,0,1, 1,1,1,1,1, 0,0,0,0,1, 0,0,0,0,1});
        GLYPHS.put('5', new int[]{1,1,1,1,1, 1,0,0,0,0, 1,1,1,1,0, 0,0,0,0,1, 1,1,1,1,0});
        GLYPHS.put('6', new int[]{1,1,1,1,1, 1,0,0,0,0, 1,1,1,1,1, 1,0,0,0,1, 1,1,1,1,1});
        GLYPHS.put('7', new int[]{1,1,1,1,1, 0,0,0,0,1, 0,0,0,1,0, 0,0,1,0,0, 0,0,1,0,0});
        GLYPHS.put('8', new int[]{1,1,1,1,1, 1,0,0,0,1, 1,1,1,1,1, 1,0,0,0,1, 1,1,1,1,1});
        GLYPHS.put('9', new int[]{1,1,1,1,1, 1,0,0,0,1, 1,1,1,1,1, 0,0,0,0,1, 1,1,1,1,1});
        GLYPHS.put(':', new int[]{0,0,0,0,0, 0,0,1,0,0, 0,0,0,0,0, 0,0,1,0,0, 0,0,0,0,0});
        GLYPHS.put('-', new int[]{0,0,0,0,0, 0,0,0,0,0, 0,1,1,1,0, 0,0,0,0,0, 0,0,0,0,0});
        GLYPHS.put(' ', new int[]{0,0,0,0,0, 0,0,0,0,0, 0,0,0,0,0, 0,0,0,0,0, 0,0,0,0,0});

        // Кириллица (названия биомов и служебные слова)
        GLYPHS.put('А', new int[]{0,1,1,1,0, 1,0,0,0,1, 1,1,1,1,1, 1,0,0,0,1, 1,0,0,0,1});
        GLYPHS.put('Б', new int[]{1,1,1,1,1, 1,0,0,0,0, 1,1,1,1,0, 1,0,0,0,1, 1,1,1,1,0});
        GLYPHS.put('В', new int[]{1,1,1,1,0, 1,0,0,0,1, 1,1,1,1,0, 1,0,0,0,1, 1,1,1,1,0});
        GLYPHS.put('Г', new int[]{1,1,1,1,1, 1,0,0,0,0, 1,0,0,0,0, 1,0,0,0,0, 1,0,0,0,0});
        GLYPHS.put('Д', new int[]{0,0,1,1,0, 0,1,0,0,1, 0,1,0,0,1, 0,1,0,0,1, 1,1,1,1,1});
        GLYPHS.put('Е', new int[]{1,1,1,1,1, 1,0,0,0,0, 1,1,1,1,0, 1,0,0,0,0, 1,1,1,1,1});
        GLYPHS.put('И', new int[]{1,0,0,0,1, 1,0,0,1,1, 1,0,1,0,1, 1,1,0,0,1, 1,0,0,0,1});
        GLYPHS.put('Л', new int[]{0,1,1,1,0, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1});
        GLYPHS.put('М', new int[]{1,0,0,0,1, 1,1,0,1,1, 1,0,1,0,1, 1,0,0,0,1, 1,0,0,0,1});
        GLYPHS.put('Н', new int[]{1,0,0,0,1, 1,0,0,0,1, 1,1,1,1,1, 1,0,0,0,1, 1,0,0,0,1});
        GLYPHS.put('О', new int[]{0,1,1,1,0, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 0,1,1,1,0});
        GLYPHS.put('П', new int[]{1,1,1,1,1, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1});
        GLYPHS.put('Р', new int[]{1,1,1,1,0, 1,0,0,0,1, 1,1,1,1,0, 1,0,0,0,0, 1,0,0,0,0});
        GLYPHS.put('С', new int[]{0,1,1,1,1, 1,0,0,0,0, 1,0,0,0,0, 1,0,0,0,0, 0,1,1,1,1});
        GLYPHS.put('Т', new int[]{1,1,1,1,1, 0,0,1,0,0, 0,0,1,0,0, 0,0,1,0,0, 0,0,1,0,0});
        GLYPHS.put('У', new int[]{1,0,0,0,1, 1,0,0,0,1, 0,1,1,1,1, 0,0,0,0,1, 0,1,1,1,0});
        GLYPHS.put('Х', new int[]{1,0,0,0,1, 0,1,0,1,0, 0,0,1,0,0, 0,1,0,1,0, 1,0,0,0,1});
        GLYPHS.put('Ы', new int[]{1,0,0,0,1, 1,1,1,1,1, 1,0,0,0,1, 1,0,0,0,1, 1,0,0,0,1});
        GLYPHS.put('Ь', new int[]{1,1,1,1,0, 1,0,0,0,1, 1,1,1,1,0, 1,0,0,0,0, 1,0,0,0,0});
        GLYPHS.put('Я', new int[]{0,1,1,1,1, 1,0,0,0,1, 0,1,1,1,1, 1,0,0,0,1, 1,0,0,0,1});
    }

    private static void init() {
        if (initialized) return;

        String vert = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """;

        String frag = """
            #version 330 core
            out vec4 FragColor;
            void main() {
                FragColor = vec4(1.0, 1.0, 1.0, 1.0); // Белый цвет текста
            }
        """;

        textShader = new Shader(vert, frag, true);

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 10000 * Float.BYTES, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        initialized = true;
    }

    public static void drawString(String text, float startX, float startY, float pixelSize, int screenW, int screenH) {
        init();

        text = text.toUpperCase();
        float currentX = startX;
        float currentY = startY;

        FloatBuffer buffer = BufferUtils.createFloatBuffer(text.length() * 25 * 6 * 2);
        int vertexCount = 0;

        for (char c : text.toCharArray()) {
            int[] glyph = GLYPHS.getOrDefault(c, GLYPHS.get(' '));

            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 5; col++) {
                    if (glyph[row * 5 + col] == 1) {
                        float px = currentX + col * pixelSize;
                        float py = currentY + row * pixelSize;

                        float x1 = (px / screenW) * 2.0f - 1.0f;
                        float y1 = 1.0f - (py / screenH) * 2.0f;
                        float x2 = ((px + pixelSize) / screenW) * 2.0f - 1.0f;
                        float y2 = 1.0f - ((py + pixelSize) / screenH) * 2.0f;

                        buffer.put(new float[]{
                                x1, y1,  x2, y1,  x2, y2,
                                x1, y1,  x2, y2,  x1, y2
                        });
                        vertexCount += 6;
                    }
                }
            }
            currentX += 6 * pixelSize; // Смещение для следующей буквы
        }

        buffer.flip();

        textShader.use();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);

        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }
}