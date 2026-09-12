package org.maintir.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Обводка блока под прицелом: каркас из линий по рёбрам ячейки.
 * Рисуется в мировых координатах, собственный мини-шейдер.
 */
public final class BlockOutline {

    private static Shader shader;
    private static int vao, vbo;
    private static boolean initialized = false;

    /** Вынос рёбер наружу ячейки, чтобы линии не тонули в гранях. */
    private static final float E = 0.008f;

    private BlockOutline() {}

    private static void init() {
        if (initialized) return;
        String vert = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            uniform mat4 view;
            uniform mat4 projection;
            void main() { gl_Position = projection * view * vec4(aPos, 1.0); }
        """;
        String frag = """
            #version 330 core
            out vec4 FragColor;
            uniform vec3 uColor;
            uniform float uAlpha;
            void main() { FragColor = vec4(uColor, uAlpha); }
        """;
        shader = new Shader(vert, frag, true);

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 24 * 3 * Float.BYTES, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        initialized = true;
    }

    /** Рисует обводку ячейки блока (wx, wy, wz) в мировых координатах. */
    public static void draw(int wx, int wy, int wz, Matrix4f view, Matrix4f projection) {
        init();

        float x0 = wx - E, x1 = wx + 1f + E, y0 = wy - E, y1 = wy + 1f + E, z0 = wz - E, z1 = wz + 1f + E;
        float[] e = {
                x0, y0, z0,  x1, y0, z0,   x1, y0, z0,  x1, y0, z1,   x1, y0, z1,  x0, y0, z1,   x0, y0, z1,  x0, y0, z0,
                x0, y1, z0,  x1, y1, z0,   x1, y1, z0,  x1, y1, z1,   x1, y1, z1,  x0, y1, z1,   x0, y1, z1,  x0, y1, z0,
                x0, y0, z0,  x0, y1, z0,   x1, y0, z0,  x1, y1, z0,   x1, y0, z1,  x1, y1, z1,   x0, y0, z1,  x0, y1, z1
        };
        FloatBuffer buf = BufferUtils.createFloatBuffer(e.length);
        buf.put(e).flip();

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        shader.use();
        shader.setUniform("view", view);
        shader.setUniform("projection", projection);
        shader.setUniform("uColor", new Vector3f(1.0f, 0.98f, 0.85f));
        shader.setUniform("uAlpha", 0.9f);

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buf, GL_DYNAMIC_DRAW);
        glLineWidth(2.0f);
        glDrawArrays(GL_LINES, 0, 24);
        glBindVertexArray(0);

        glDisable(GL_BLEND);
        glDepthFunc(GL_LESS);
    }
}