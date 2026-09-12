package org.maintir.world;

import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Игрок: ходьба, бег (Shift), прыжок, гравитация и коллизии с блоками.
 * position — точка на уровне ступней по центру игрока.
 * Резкие вертикальные сдвиги (ступеньки, приземление) камера проходит плавно.
 */
public class Player {

    public static final float HALF_WIDTH = 0.3f;
    public static final float HEIGHT = 1.8f;
    public static final float EYE_HEIGHT = 1.62f;

    public static final float WALK_SPEED = 4.5f;
    public static final float SPRINT_SPEED = 8.5f;
    public static final float JUMP_VELOCITY = 8.2f;
    private static final float GRAVITY = 26.0f;
    private static final float MAX_FALL_SPEED = 50.0f;
    private static final float STEP_HEIGHT = 1.02f;   // автоподъём на одну ступеньку

    private final Vector3f position = new Vector3f();
    private float velocityY = 0.0f;
    private boolean onGround = false;
    private boolean sprinting = false;

    // --- Режим игры / креатив-полёт / здоровье ---
    private GameMode mode = GameMode.CREATIVE;
    public static final float MAX_HP = 10000f;
    private float hp = MAX_HP;
    private boolean flying = false;
    private double lastSpaceTap = -10.0;   // для двойного пробела
    private boolean prevSpace = false;
    private float fallDist = 0f;           // накопленная высота падения (блоки)
    private double lastDamageTime = -100.0;

    // Плавность камеры: смещение глаз, гаснущее со временем
    private float eyeSmoothOffset = 0.0f;

    // Покачивание при ходьбе
    private float bobPhase = 0.0f;
    private float bobAmount = 0.0f;

    // ---------- Режим / ХП ----------

    public void setMode(GameMode m) {
        this.mode = m;
        if (m == GameMode.SURVIVAL) flying = false;
    }

    public GameMode getMode() {
        return mode;
    }

    public boolean isFlying() {
        return flying;
    }

    public float getHp() {
        return hp;
    }

    public void resetHp() {
        hp = MAX_HP;
    }

    public void damage(float amount) {
        if (amount <= 0 || mode != GameMode.SURVIVAL) return;
        hp = Math.max(0f, hp - amount);
        lastDamageTime = nowSec();
    }

    private static double nowSec() {
        return System.nanoTime() / 1e9;
    }

    /** Секунды с момента последнего урона (для красной вспышки). */
    public double secondsSinceDamage() {
        return nowSec() - lastDamageTime;
    }

    /**
     * Урон от падения с высоты n блоков (менее 3 блоков — без урона):
     * 1, 2 -> 0, 3 -> 200, 4 -> 300, 5 -> 600, 6 -> 800, 7 -> 1000, 8 -> 1500,
     * 9 -> 3000, 10 -> 4000, дальше +1000 за блок.
     */
    public static float fallDamage(float fallDistanceBlocks) {
        int n = (int) Math.floor(fallDistanceBlocks + 1e-4);
        if (n <= 2) return 0;
        return switch (n) {
            case 3 -> 200;
            case 4 -> 300;
            case 5 -> 600;
            case 6 -> 800;
            case 7 -> 1000;
            case 8 -> 1500;
            case 9 -> 3000;
            default -> 4000 + (n - 10) * 1000;
        };
    }

    /** Телепорт в точку (загрузка сохранения). */
    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
        velocityY = 0.0f;
        onGround = true;
        eyeSmoothOffset = 0.0f;
        fallDist = 0f;
        flying = false;
    }

    public Vector3f getPosition() {
        return position;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    /** Y глаз с учётом плавного подъёма/спуска камеры. */
    public float getEyeY() {
        return position.y + EYE_HEIGHT + eyeSmoothOffset;
    }

    /** Ставит игрока на поверхность, подальше от деревьев (ствол/листва — id 5 и 6). */
    public void spawn(World world, int blockX, int blockZ) {
        int sx = blockX, sz = blockZ;
        search:
        for (int r = 0; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int top = world.topBlockId(blockX + dx, blockZ + dz);
                    if (top >= 1 && top <= 4) {
                        sx = blockX + dx;
                        sz = blockZ + dz;
                        break search;
                    }
                }
            }
        }
        int surface = world.surfaceHeight(sx, sz);
        position.set(sx + 0.5f, surface + 0.01f, sz + 0.5f);
        velocityY = 0.0f;
        onGround = true;
        eyeSmoothOffset = 0.0f;
        fallDist = 0f;
    }

    public void update(long window, float dt, World world, Vector3f flatFront, Vector3f right) {
        float fwd = 0, strafe = 0;
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) fwd += 1;
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) fwd -= 1;
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) strafe += 1;
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) strafe -= 1;

        boolean sprint = glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS;
        boolean jump = glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS;

        // Двойной пробел: включить/выключить полёт (только креатив)
        double now = glfwGetTime();
        if (jump && !prevSpace) {
            if (mode == GameMode.CREATIVE && now - lastSpaceTap < 0.35) {
                flying = !flying;
                velocityY = 0;
            }
            lastSpaceTap = now;
        }
        prevSpace = jump;

        move(world, dt, flatFront, right, fwd, strafe, jump, sprint);
    }

    /** Физика движения за кадр. */
    private void move(World world, float dt, Vector3f flatFront, Vector3f right,
                      float fwd, float strafe, boolean jump, boolean sprint) {
        sprinting = !flying && sprint && fwd > 0;

        float startX = position.x, startZ = position.z;

        float lenSq = fwd * fwd + strafe * strafe;
        float mx = 0, mz = 0;
        if (lenSq > 0) {
            float speedMul = flying ? 2.2f : 1.0f;
            float speed = (sprinting ? SPRINT_SPEED : WALK_SPEED) * dt * speedMul;
            mx = (flatFront.x * fwd + right.x * strafe);
            mz = (flatFront.z * fwd + right.z * strafe);
            float inv = speed / (float) Math.sqrt(mx * mx + mz * mz);
            mx *= inv;
            mz *= inv;
        }

        // Горизонталь по осям (с подшагами против туннелирования)
        moveHorizontal(world, mx, 0);
        moveHorizontal(world, 0, mz);

        if (flying && mode == GameMode.CREATIVE) {
            // Полёт: пробел — вверх, шифт — вниз, без гравитации
            float target = 0;
            if (jump) target += WALK_SPEED * 2.2f;
            if (sprint) target -= WALK_SPEED * 2.2f;
            velocityY += (target - velocityY) * Math.min(1.0f, dt * 12.0f);
            fallDist = 0f;
            moveVertical(world, velocityY * dt);
        } else {
            // Прыжок
            if (jump && onGround) {
                velocityY = JUMP_VELOCITY;
                onGround = false;
            }

            // Гравитация
            velocityY -= GRAVITY * dt;
            if (velocityY < -MAX_FALL_SPEED) velocityY = -MAX_FALL_SPEED;

            float yBefore = position.y;
            moveVertical(world, velocityY * dt);

            // Накопление высоты падения и урон при приземлении
            if (!onGround && velocityY < 0) {
                fallDist += Math.max(0f, yBefore - position.y);
            } else if (onGround && fallDist > 0f) {
                damage(fallDamage(fallDist));
                fallDist = 0f;
            } else if (onGround) {
                fallDist = 0f;
            }
        }

        // Страховка: если каким-то образом провалились — вернуть на поверхность
        if (position.y < -20) {
            spawn(world, (int) Math.floor(position.x), (int) Math.floor(position.z));
            fallDist = 0f;
        }

        updateCameraFeel(dt, startX, startZ);
    }

    /** Гасит смещение камеры и ведёт фазу покачивания по пройденному пути. */
    private void updateCameraFeel(float dt, float startX, float startZ) {
        // Плавное схлопывание смещения глаз после ступеньки/приземления
        float decay = Math.min(1.0f, dt * 10.0f);
        eyeSmoothOffset += (0.0f - eyeSmoothOffset) * decay;
        if (Math.abs(eyeSmoothOffset) < 0.001f) eyeSmoothOffset = 0.0f;

        // Покачивание: только на земле и при реальном движении
        double moved = Math.hypot(position.x - startX, position.z - startZ);
        boolean walking = onGround && moved > 1e-5;
        float target = walking ? Math.min(1.0f, (float) (moved / dt) / WALK_SPEED) : 0.0f;
        bobAmount += (target - bobAmount) * Math.min(1.0f, dt * 8.0f);
        if (walking) bobPhase += (float) (moved * 2.6);
    }

    /**
     * Смещение глаз для покачивания: вертикаль + лёгкий изгиб в стороны.
     * Добавить к позиции камеры после установки высоты глаз.
     */
    public void applyViewBob(Vector3f eyePos, Vector3f rightVec) {
        if (bobAmount <= 0.001f) return;
        float vertical = (float) Math.sin(bobPhase * 2.0) * 0.045f * bobAmount;
        float lateral = (float) Math.cos(bobPhase) * 0.05f * bobAmount;
        eyePos.y += vertical;
        eyePos.x += rightVec.x * lateral;
        eyePos.z += rightVec.z * lateral;
    }

    private void moveHorizontal(World world, float dx, float dz) {
        if (dx == 0 && dz == 0) return;

        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) / 0.4f));
        float sdx = dx / steps, sdz = dz / steps;

        for (int i = 0; i < steps; i++) {
            float nx = position.x + sdx;
            float nz = position.z + sdz;
            if (!collides(world, nx, position.y, nz)) {
                position.x = nx;
                position.z = nz;
                continue;
            }
            // Автоподъём на ступеньку, если стоим на земле
            if (onGround
                    && !collides(world, position.x, position.y + STEP_HEIGHT, position.z)
                    && !collides(world, nx, position.y + STEP_HEIGHT, nz)) {
                position.set(nx, position.y + STEP_HEIGHT, nz);
                eyeSmoothOffset -= STEP_HEIGHT; // камера мягко доедет вверх
            } else {
                break; // упёрлись — дальше не двигаем по этой оси
            }
        }
    }

    private void moveVertical(World world, float dy) {
        if (dy == 0) return;

        int steps = Math.max(1, (int) Math.ceil(Math.abs(dy) / 0.4f));
        float sdy = dy / steps;

        for (int i = 0; i < steps; i++) {
            float ny = position.y + sdy;
            if (!collides(world, position.x, ny, position.z)) {
                position.y = ny;
                continue;
            }
            if (sdy < 0) {
                // Приземление: прижаться к верху блока, камера доезжает мягко
                float snapped = (float) (Math.floor(ny) + 1.0);
                if (snapped <= position.y) {
                    eyeSmoothOffset -= (snapped - position.y);
                    position.y = snapped;
                }
                onGround = true;
            }
            velocityY = 0.0f;
            return;
        }
    }

    /** Пересекается ли коробка игрока в точке (x, y, z) с твёрдыми блоками. */
    private boolean collides(World world, float x, float y, float z) {
        float e = 1e-4f;
        int minX = (int) Math.floor(x - HALF_WIDTH + e);
        int maxX = (int) Math.floor(x + HALF_WIDTH - e);
        int minY = (int) Math.floor(y + e);
        int maxY = (int) Math.floor(y + HEIGHT - e);
        int minZ = (int) Math.floor(z - HALF_WIDTH + e);
        int maxZ = (int) Math.floor(z + HALF_WIDTH - e);

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    if (world.isSolidForPhysics(bx, by, bz)) return true;
                }
            }
        }
        return false;
    }

    /** Перекрывает ли игрок ячейку блока (чтобы нельзя было ставить блок в себя). */
    public boolean intersectsBlock(int bx, int by, int bz) {
        return position.x + HALF_WIDTH > bx && position.x - HALF_WIDTH < bx + 1
                && position.y + HEIGHT > by && position.y < by + 1
                && position.z + HALF_WIDTH > bz && position.z - HALF_WIDTH < bz + 1;
    }
}
