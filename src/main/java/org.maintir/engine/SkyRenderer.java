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
 * Солнце и луна: билборды на небесной сфере, вращаются вокруг игрока
 * по оси восток-запад. Текстуры с прозрачностью рисуются с блендингом
 * до основного мира (мир их перекрывает — «заходят за горы»).
 */
public class SkyRenderer {
    private static boolean initialized = false;
    private static Shader shader;
    private static int sunTex, moonTex, vao, vbo;

    public static void init() {
        if (initialized) return;

        String vert = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aUV;
            out vec2 uv;
            uniform mat4 model;
            uniform mat4 view;
            uniform mat4 projection;
            void main() {
                uv = aUV;
                gl_Position = projection * view * model * vec4(aPos, 1.0);
            }
        """;
        String frag = """
            #version 330 core
            in vec2 uv;
            out vec4 FragColor;
            uniform sampler2D tex;
            uniform float uAlpha;
            void main() {
                vec4 c = texture(tex, uv);
                if (c.a < 0.05) discard; // прозрачные части текстур
                FragColor = vec4(c.rgb, c.a * uAlpha);
            }
        """;
        shader = new Shader(vert, frag, true);

        sunTex = MenuRenderer.loadTexture("/suner.png");
        moonTex = MenuRenderer.loadTexture("/lyna.png");

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 6 * 5 * Float.BYTES, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);

        initialized = true;
    }

    /** Рисует солнце и луну. Вызывать сразу после очистки кадра, до мира. */
    public static void render(Matrix4f projection, Matrix4f view, Vector3f camPos, float timeOfDay) {
        if (!initialized) return;

        double th = timeOfDay * Math.PI * 2.0;
        Vector3f sunDir = new Vector3f((float) Math.cos(th), (float) Math.sin(th), 0.22f).normalize();

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        shader.use();
        shader.setUniform("projection", projection);
        shader.setUniform("view", view);
        shader.setUniform("tex", 0);

        if (sunTex != 0) drawBillboard(camPos, sunDir, 420f, 64f, sunTex, 1.0f);
        if (moonTex != 0) drawBillboard(camPos, new Vector3f(sunDir).negate(), 420f, 44f, moonTex, 1.0f);

        // Мягкий ореол (аддитивный блюм) вокруг светил
        glBlendFunc(GL_ONE, GL_ONE);
        if (sunTex != 0) drawBillboard(camPos, sunDir, 420f, 170f, sunTex, 0.35f);
        if (moonTex != 0) drawBillboard(camPos, new Vector3f(sunDir).negate(), 420f, 110f, moonTex, 0.30f);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    /** Квад, повёрнутый к камере, в направлении dir на расстоянии dist. */
    private static void drawBillboard(Vector3f camPos, Vector3f dir, float dist, float size, int texId, float alpha) {
        Vector3f center = new Vector3f(dir).mul(dist).add(camPos);

        Vector3f fwd = new Vector3f(dir).negate(); // к камере
        Vector3f worldUp = new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(fwd).cross(worldUp);
        if (right.lengthSquared() < 1e-6f) right.set(1, 0, 0);
        right.normalize();
        Vector3f up = new Vector3f(right).cross(fwd).normalize();

        float s = size * 0.5f;
        Vector3f c00 = new Vector3f(center).sub(new Vector3f(right).mul(s)).sub(new Vector3f(up).mul(s));
        Vector3f c10 = new Vector3f(center).add(new Vector3f(right).mul(s)).sub(new Vector3f(up).mul(s));
        Vector3f c11 = new Vector3f(center).add(new Vector3f(right).mul(s)).add(new Vector3f(up).mul(s));
        Vector3f c01 = new Vector3f(center).sub(new Vector3f(right).mul(s)).add(new Vector3f(up).mul(s));

        float[] v = {
                c00.x, c00.y, c00.z, 0, 1,
                c10.x, c10.y, c10.z, 1, 1,
                c11.x, c11.y, c11.z, 1, 0,
                c00.x, c00.y, c00.z, 0, 1,
                c11.x, c11.y, c11.z, 1, 0,
                c01.x, c01.y, c01.z, 0, 0,
        };
        FloatBuffer buf = BufferUtils.createFloatBuffer(v.length);
        buf.put(v).flip();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texId);
        Matrix4f model = new Matrix4f();
        shader.setUniform("model", model);
        shader.setUniform("uAlpha", alpha);

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buf);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
    }

    public static void cleanup() {
        if (!initialized) return;
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        if (sunTex != 0) glDeleteTextures(sunTex);
        if (moonTex != 0) glDeleteTextures(moonTex);
        initialized = false;
    }
}
