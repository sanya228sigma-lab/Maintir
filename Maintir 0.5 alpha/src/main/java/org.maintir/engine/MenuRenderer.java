package org.maintir.engine;

import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBImage.*;

public class MenuRenderer {

    private static int colorVao, colorVbo;
    private static int texVao, texVbo;
    private static Shader uiShader, texShader;
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;

        // Шейдер для простых цветных плашек
        String vertShader = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec3 aColor;
            out vec3 fragColor;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                fragColor = aColor;
            }
        """;
        String fragShader = """
            #version 330 core
            in vec3 fragColor;
            out vec4 FragColor;
            void main() { FragColor = vec4(fragColor, 1.0); }
        """;
        uiShader = new Shader(vertShader, fragShader, true);

        colorVao = glGenVertexArrays();
        colorVbo = glGenBuffers();
        glBindVertexArray(colorVao);
        glBindBuffer(GL_ARRAY_BUFFER, colorVbo);
        glBufferData(GL_ARRAY_BUFFER, 6 * 5 * Float.BYTES, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 5 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        // Шейдер для спрайтов/кнопок с текстурой (с отсечением белого фона)
        String texVert = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec2 aTex;
            out vec2 TexCoord;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                TexCoord = aTex;
            }
        """;
        String texFrag = """
            #version 330 core
            in vec2 TexCoord;
            out vec4 FragColor;
            uniform sampler2D tex;
            void main() {
                vec4 c = texture(tex, TexCoord);
                if (c.r > 0.85 && c.g > 0.85 && c.b > 0.85) discard; // Белый фон -> прозрачный
                if (c.a < 0.1) discard;
                FragColor = c;
            }
        """;
        texShader = new Shader(texVert, texFrag, true);

        texVao = glGenVertexArrays();
        texVbo = glGenBuffers();
        glBindVertexArray(texVao);
        glBindBuffer(GL_ARRAY_BUFFER, texVbo);
        glBufferData(GL_ARRAY_BUFFER, 6 * 4 * Float.BYTES, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        initialized = true;
    }

    public static int loadTexture(String path) {
        IntBuffer w = BufferUtils.createIntBuffer(1);
        IntBuffer h = BufferUtils.createIntBuffer(1);
        IntBuffer comp = BufferUtils.createIntBuffer(1);
        stbi_set_flip_vertically_on_load(true);

        try (var rawData = TextureArray.class.getResourceAsStream(path)) {
            if (rawData == null) return 0;
            byte[] bytes = rawData.readAllBytes();
            var buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();

            var data = stbi_load_from_memory(buffer, w, h, comp, 4);
            if (data == null) return 0;

            int id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, id);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            stbi_image_free(data);
            return id;
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean drawTextureButton(int screenW, int screenH, float mx, float my, float x, float y, float w, float h, int texId, boolean clicked) {
        if (texId == 0) return false;
        boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;

        float x1 = (x / screenW) * 2.0f - 1.0f;
        float y1 = 1.0f - (y / screenH) * 2.0f;
        float x2 = ((x + w) / screenW) * 2.0f - 1.0f;
        float y2 = 1.0f - ((y + h) / screenH) * 2.0f;

        float[] vertices = {
                x1, y1, 0.0f, 1.0f,  x2, y1, 1.0f, 1.0f,  x2, y2, 1.0f, 0.0f,
                x1, y1, 0.0f, 1.0f,  x2, y2, 1.0f, 0.0f,  x1, y2, 0.0f, 0.0f
        };

        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();

        texShader.use();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texId);

        glBindVertexArray(texVao);
        glBindBuffer(GL_ARRAY_BUFFER, texVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);

        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        return hovered && clicked;
    }

    public static boolean drawColorButton(int screenW, int screenH, float mx, float my, float x, float y, float w, float h, float r, float g, float b, String label, boolean clicked) {
        boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (hovered) { r += 0.15f; g += 0.15f; b += 0.15f; }

        float x1 = (x / screenW) * 2.0f - 1.0f;
        float y1 = 1.0f - (y / screenH) * 2.0f;
        float x2 = ((x + w) / screenW) * 2.0f - 1.0f;
        float y2 = 1.0f - ((y + h) / screenH) * 2.0f;

        float[] vertices = {
                x1, y1, r, g, b,  x2, y1, r, g, b,  x2, y2, r, g, b,
                x1, y1, r, g, b,  x2, y2, r, g, b,  x1, y2, r, g, b
        };

        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();

        uiShader.use();
        glBindVertexArray(colorVao);
        glBindBuffer(GL_ARRAY_BUFFER, colorVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);

        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        if (label != null && !label.isEmpty()) {
            float scale = 3.0f;
            float textW = label.length() * 6 * scale;
            float textX = x + (w - textW) / 2.0f;
            float textY = y + (h - (5 * scale)) / 2.0f;
            FontRenderer.drawString(label, textX, textY, scale, screenW, screenH);
        }

        return hovered && clicked;
    }
}