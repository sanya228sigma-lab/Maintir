package org.maintir.world;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.maintir.engine.MenuRenderer;
import org.maintir.engine.Shader;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Первая мобa — свинья.
 * Модель собрана из коробок с текстурой кожи koza_svini.png.
 * Глаза процедурные: белок и зрачок отдельными квадами.
 * Анимация глаз: случайное моргание + зрачки следят за игроком
 * (вблизи) или смотрят по направлению движения (вдали).
 */
public class Pig {

    // --- Габариты модели (локальные координаты, морда смотрит в +Z) ---
    private static final float BODY_HX = 0.35f, BODY_HY = 0.28f, BODY_HZ = 0.55f, BODY_CY = 0.62f;
    private static final float HEAD_S = 0.26f, HEAD_CY = 0.72f, HEAD_CZ = 0.72f;
    private static final float SNOUT_HX = 0.12f, SNOUT_HY = 0.08f, SNOUT_HZ = 0.05f;
    private static final float LEG_H = 0.34f, LEG_HX = 0.09f, LEG_HZ = 0.09f;
    private static final float LEG_FZ = 0.38f, LEG_X = 0.22f;
    private static final float EYE_Y = 0.80f, EYE_X = 0.13f, EYE_S = 0.12f;

    private static final int FLOATS_PER_PIG = 12000;

    private static boolean assetsReady = false;
    private static int skinTexture;
    private static int vao, vbo;       // тело: pos3 + uv2 + shade1
    private static int eyeVao, eyeVbo; // глаза: pos3 + color3
    private static Shader bodyShader;
    private static Shader colorShader;
    private static final FloatBuffer staging = BufferUtils.createFloatBuffer(FLOATS_PER_PIG);

    /** Общая яркость (день/ночь) — задаётся миром перед отрисовкой. */
    private static volatile float dayBrightness = 1.0f;

    public static void setDayBrightness(float b) {
        dayBrightness = b;
    }

    public float x, y, z;
    private float yaw;
    private float targetYaw;
    private float stateTimer;
    private boolean walking;
    private float legPhase;
    private float fallSpeed;

    // Анимация глаз
    private float blinkTimer;   // через сколько секунд моргнуть
    private float blinkClose;   // 0 — глаза открыты, 1 — закрыты
    private float pupilX, pupilY;      // текущий сдвиг зрачка
    private float pupilTargetX, pupilTargetY;
    private final long seed = (long) (Math.random() * 10000);

    public Pig(float x, float surfaceY, float z) {
        this.x = x;
        this.y = surfaceY;
        this.z = z;
        this.yaw = (float) (Math.random() * Math.PI * 2);
        this.targetYaw = yaw;
        this.stateTimer = 1.0f;
        this.blinkTimer = 1.0f + (float) Math.random() * 3.0f;
    }

    // ---------- Ресурсы ----------

    public static void initAssets() {
        if (assetsReady) return;

        skinTexture = MenuRenderer.loadTexture("/koza_svini.png");

        String bodyVert = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aUV;
            layout (location = 2) in float aShade;
            out vec2 uv;
            out float shade;
            uniform mat4 view;
            uniform mat4 projection;
            void main() {
                uv = aUV;
                shade = aShade;
                gl_Position = projection * view * vec4(aPos, 1.0);
            }
        """;
        String bodyFrag = """
            #version 330 core
            in vec2 uv;
            in float shade;
            out vec4 FragColor;
            uniform sampler2D uSkin;
            uniform float uBright;
            void main() {
                vec4 c = texture(uSkin, uv);
                FragColor = vec4(c.rgb * shade * uBright, c.a);
            }
        """;
        bodyShader = new Shader(bodyVert, bodyFrag, true);

        String colVert = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec3 aColor;
            out vec3 color;
            uniform mat4 view;
            uniform mat4 projection;
            void main() {
                color = aColor;
                gl_Position = projection * view * vec4(aPos, 1.0);
            }
        """;
        String colFrag = """
            #version 330 core
            in vec3 color;
            out vec4 FragColor;
            uniform float uAlpha;
            uniform float uBright;
            void main() { FragColor = vec4(color * uBright, uAlpha); }
        """;
        colorShader = new Shader(colVert, colFrag, true);

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) FLOATS_PER_PIG * Float.BYTES, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, 6 * Float.BYTES, 5 * Float.BYTES);
        glEnableVertexAttribArray(2);
        glBindVertexArray(0);

        // Отдельный VAO для глаз: pos3 + color3
        eyeVao = glGenVertexArrays();
        eyeVbo = glGenBuffers();
        glBindVertexArray(eyeVao);
        glBindBuffer(GL_ARRAY_BUFFER, eyeVbo);
        glBufferData(GL_ARRAY_BUFFER, 256 * Float.BYTES, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);

        assetsReady = true;
    }

    public static void cleanupAssets() {
        if (!assetsReady) return;
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(eyeVbo);
        glDeleteVertexArrays(eyeVao);
        assetsReady = false;
    }

    // ---------- Логика ----------

    public void update(float dt, World world, Vector3f playerPos) {
        // Не обновлять в невычисленных чанках (иначе телепорт на "потолок мира")
        if (world.topBlockId((int) Math.floor(x), (int) Math.floor(z)) == 0) return;

        // --- ИИ брожения ---
        stateTimer -= dt;
        if (stateTimer <= 0) {
            walking = Math.random() < 0.75;
            if (walking) {
                targetYaw = (float) (Math.random() * Math.PI * 2);
                stateTimer = 2.0f + (float) Math.random() * 3.0f;
            } else {
                stateTimer = 1.0f + (float) Math.random() * 2.5f;
            }
        }

        // Плавный поворот к целевому направлению
        double diff = Math.atan2(Math.sin(targetYaw - yaw), Math.cos(targetYaw - yaw));
        yaw += diff * Math.min(1.0f, dt * 4.0f);

        if (walking) {
            float speed = 1.4f;
            float nx = x + (float) Math.sin(yaw) * speed * dt;
            float nz = z + (float) Math.cos(yaw) * speed * dt;

            // Стена впереди (блок на уровне тела) — разворот
            int ax = (int) Math.floor(x + Math.sin(yaw) * 0.7);
            int az = (int) Math.floor(z + Math.cos(yaw) * 0.7);
            if (world.isSolidForPhysics(ax, (int) Math.floor(y) + 1, az)) {
                targetYaw = yaw + (float) Math.PI + (float) (Math.random() - 0.5);
                stateTimer = Math.max(stateTimer, 1.2f);
            } else {
                x = nx;
                z = nz;
                legPhase += dt * 10.0f;
            }
        }

        // --- Рельеф: мягкое следование поверхности ---
        int surface = world.surfaceHeight((int) Math.floor(x), (int) Math.floor(z));
        if (y < surface - 0.01f) {
            fallSpeed += 22.0f * dt;
            y += fallSpeed * dt;
            if (y >= surface) { y = surface; fallSpeed = 0; }
        } else {
            y += (surface - y) * Math.min(1.0f, dt * 10.0f);
            fallSpeed = 0;
        }

        // --- Глаза: моргание ---
        blinkTimer -= dt;
        if (blinkTimer <= 0) {
            blinkClose = 1.0f;
            blinkTimer = 2.0f + (float) Math.random() * 3.5f;
        }
        blinkClose = Math.max(0.0f, blinkClose - dt * 9.0f);

        // --- Глаза: направление взгляда ---
        float dxp = playerPos.x - x;
        float dzp = playerPos.z - z;
        float distSq = dxp * dxp + dzp * dzp;
        if (distSq < 49.0f) {
            // Смотрим на игрока: переводим направление в локальные оси свиньи
            float dist = (float) Math.sqrt(distSq) + 1e-4f;
            float fwd = (dxp * (float) Math.sin(yaw) + dzp * (float) Math.cos(yaw)) / dist;
            float side = (dxp * (float) Math.cos(yaw) - dzp * (float) Math.sin(yaw)) / dist;
            pupilTargetX = clamp(side, -1, 1);
            pupilTargetY = clamp((playerPos.y - (y + EYE_Y)) / dist * 1.5f, -0.7f, 0.9f);
        } else {
            // Смотрим по движению + слегка "озирается"
            float wander = (float) Math.sin((legPhase + seed) * 0.7) * 0.5f;
            pupilTargetX = walking ? 0 : wander;
            pupilTargetY = 0;
        }
        pupilX += (pupilTargetX - pupilX) * Math.min(1.0f, dt * 6.0f);
        pupilY += (pupilTargetY - pupilY) * Math.min(1.0f, dt * 6.0f);
    }

    // ---------- Рендер ----------

    public void render(Matrix4f view, Matrix4f projection, Vector3f playerPos) {
        staging.clear();
        float cos = (float) Math.cos(yaw);
        float sin = (float) Math.sin(yaw);
        float bob = walking ? (float) Math.abs(Math.sin(legPhase)) * 0.03f : 0.0f;

        // UV-области кожи (текстура однотонная, области дают лёгкое разнообразие)
        float[][] uvBody   = {{0.05f, 0.05f, 0.55f, 0.55f}};
        float[][] uvHead   = {{0.55f, 0.05f, 0.95f, 0.45f}};
        float[][] uvSnout  = {{0.30f, 0.62f, 0.70f, 0.86f}};
        float[][] uvLeg    = {{0.05f, 0.62f, 0.30f, 0.88f}};

        // Тело
        addBox(staging, 0, BODY_CY + bob, 0, BODY_HX, BODY_HY, BODY_HZ, uvBody[0], cos, sin, 1.0f);
        // Голова
        addBox(staging, 0, HEAD_CY + bob, HEAD_CZ, HEAD_S, HEAD_S, HEAD_S, uvHead[0], cos, sin, 1.0f);
        // Пятачок
        addBox(staging, 0, HEAD_CY - 0.04f + bob, HEAD_CZ + HEAD_S + SNOUT_HZ - 0.02f,
                SNOUT_HX, SNOUT_HY, SNOUT_HZ, uvSnout[0], cos, sin, 0.95f);

        // Ноги: диагональные пары качаются в противофазе
        float swing = walking ? (float) Math.sin(legPhase) * 0.13f : 0.0f;
        addLeg(staging, -LEG_X, LEG_FZ, swing, uvLeg[0], cos, sin);
        addLeg(staging, LEG_X, -LEG_FZ, swing, uvLeg[0], cos, sin);
        addLeg(staging, LEG_X, LEG_FZ, -swing, uvLeg[0], cos, sin);
        addLeg(staging, -LEG_X, -LEG_FZ, -swing, uvLeg[0], cos, sin);

        staging.flip();

        bodyShader.use();
        bodyShader.setUniform("view", view);
        bodyShader.setUniform("projection", projection);
        bodyShader.setUniform("uSkin", 0);
        bodyShader.setUniform("uBright", dayBrightness);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, skinTexture);

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, staging);
        glDrawArrays(GL_TRIANGLES, 0, staging.remaining() / 6);

        renderEyes(view, projection, cos, sin, bob);
    }

    private void renderEyes(Matrix4f view, Matrix4f projection, float cos, float sin, float bob) {
        // Моргающие веки: высота глаза сжимается
        float eyeH = EYE_S * (1.0f - 0.85f * blinkClose);
        if (eyeH < 0.005f) return;

        float zf = HEAD_CZ + HEAD_S + 0.012f; // чуть перед мордой
        float px = pupilX * 0.035f;
        float py = pupilY * 0.03f;

        staging.clear();
        // Белки
        eyeQuad(staging, -EYE_X, EYE_Y + bob, zf, EYE_S, eyeH, 1f, 1f, 1f, cos, sin);
        eyeQuad(staging, EYE_X, EYE_Y + bob, zf, EYE_S, eyeH, 1f, 1f, 1f, cos, sin);
        // Зрачки (сдвиг внутри глаза)
        eyeQuad(staging, -EYE_X + px, EYE_Y + bob + py, zf + 0.006f, 0.055f, 0.055f * (1 - 0.85f * blinkClose), 0.05f, 0.05f, 0.07f, cos, sin);
        eyeQuad(staging, EYE_X + px, EYE_Y + bob + py, zf + 0.006f, 0.055f, 0.055f * (1 - 0.85f * blinkClose), 0.05f, 0.05f, 0.07f, cos, sin);
        staging.flip();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        colorShader.use();
        colorShader.setUniform("view", view);
        colorShader.setUniform("projection", projection);
        colorShader.setUniform("uAlpha", 1.0f);
        colorShader.setUniform("uBright", Math.max(0.35f, dayBrightness)); // глаза чуть светятся в темноте

        glBindVertexArray(eyeVao);
        glBindBuffer(GL_ARRAY_BUFFER, eyeVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, staging);
        glDrawArrays(GL_TRIANGLES, 0, staging.remaining() / 6);
        glBindVertexArray(0);

        glDisable(GL_BLEND);
    }

    /** Квад глаза, стоящий вертикально на передней грани головы. */
    private void eyeQuad(FloatBuffer buf, float lx, float ly, float lz,
                         float hw, float hh, float r, float g, float b, float cos, float sin) {
        // 4 угла в локальных координатах (плоскость XY, нормаль +Z)
        float[][] c = {
                {lx - hw, ly - hh, lz},
                {lx + hw, ly - hh, lz},
                {lx + hw, ly + hh, lz},
                {lx - hw, ly + hh, lz},
        };
        int[][] idx = {{0, 1, 2}, {0, 2, 3}};
        for (int[] tri : idx) {
            for (int i : tri) {
                buf.put(transformX(c[i][0], c[i][2], cos, sin) + x);
                buf.put(c[i][1] + y);
                buf.put(transformZ(c[i][0], c[i][2], cos, sin) + z);
                buf.put(r); buf.put(g); buf.put(b);
            }
        }
    }

    private void addLeg(FloatBuffer buf, float lx, float lz, float swing,
                        float[] uv, float cos, float sin) {
        float lzz = lz + swing;
        addBox(buf, lx, LEG_H, lzz, LEG_HX, LEG_H, LEG_HZ, uv, cos, sin, 0.85f);
    }

    /**
     * Коробка: 6 граней с затенением и UV. Центр (cx,cy,cz), полуразмеры (hx,hy,hz).
     * Локальные координаты поворачиваются вокруг Y и переносятся в мир.
     */
    private void addBox(FloatBuffer buf, float cx, float cy, float cz,
                        float hx, float hy, float hz, float[] uv,
                        float cos, float sin, float shadeMul) {
        float x0 = cx - hx, x1 = cx + hx;
        float y0 = cy - hy, y1 = cy + hy;
        float z0 = cz - hz, z1 = cz + hz;
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        // shade: верх 1.0, низ 0.55, перед/зад 0.88, бока 0.74
        // Верх (+Y)
        face(buf, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, u0,v0,u1,v1, 1.00f*shadeMul, cos, sin);
        // Низ (-Y)
        face(buf, x0,y0,z0, x0,y0,z1, x1,y0,z1, x1,y0,z0, u0,v0,u1,v1, 0.55f*shadeMul, cos, sin);
        // Перед (+Z)
        face(buf, x0,y0,z1, x0,y1,z1, x1,y1,z1, x1,y0,z1, u0,v0,u1,v1, 0.88f*shadeMul, cos, sin);
        // Зад (-Z)
        face(buf, x1,y0,z0, x1,y1,z0, x0,y1,z0, x0,y0,z0, u0,v0,u1,v1, 0.88f*shadeMul, cos, sin);
        // Право (+X)
        face(buf, x1,y0,z1, x1,y1,z1, x1,y1,z0, x1,y0,z0, u0,v0,u1,v1, 0.74f*shadeMul, cos, sin);
        // Лево (-X)
        face(buf, x0,y0,z0, x0,y1,z0, x0,y1,z1, x0,y0,z1, u0,v0,u1,v1, 0.74f*shadeMul, cos, sin);
    }

    /** Одна грань: 4 угла (локальные) + UV, развёрнута в 6 вершин (уже в мировых координатах). */
    private void face(FloatBuffer buf,
                      float ax, float ay, float az,
                      float bx, float by, float bz,
                      float cx2, float cy2, float cz2,
                      float dx, float dy, float dz,
                      float u0, float v0, float u1, float v1,
                      float shade, float cos, float sin) {
        float[][] p = {
                {ax, ay, az, u0, v0},
                {bx, by, bz, u1, v0},
                {cx2, cy2, cz2, u1, v1},
                {dx, dy, dz, u0, v1},
        };
        int[][] idx = {{0, 1, 2}, {0, 2, 3}};
        for (int[] tri : idx) {
            for (int i : tri) {
                buf.put(transformX(p[i][0], p[i][2], cos, sin) + x);
                buf.put(p[i][1] + y);
                buf.put(transformZ(p[i][0], p[i][2], cos, sin) + z);
                buf.put(p[i][3]);
                buf.put(p[i][4]);
                buf.put(shade);
            }
        }
    }

    private static float transformX(float lx, float lz, float cos, float sin) {
        return lx * cos + lz * sin;
    }

    private static float transformZ(float lx, float lz, float cos, float sin) {
        return -lx * sin + lz * cos;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
