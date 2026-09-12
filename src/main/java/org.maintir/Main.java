package org.maintir;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.maintir.debug.FPSCounter;
import org.maintir.engine.*;
import org.maintir.world.Blocks;
import org.maintir.world.GameMode;
import org.maintir.world.Inventory;
import org.maintir.world.Pig;
import org.maintir.world.Player;
import org.maintir.world.TerrainGenerator;
import org.maintir.world.World;
import org.maintir.world.WorldSave;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Main {

    public enum GameState {
        MENU, WORLDS, WORLD_SETUP, SETTINGS, GAME, PAUSED, DEATH, INVENTORY
    }

    private static GameState state = GameState.MENU;
    private static boolean escPressedLastFrame = false;
    private static boolean f7PressedLastFrame = false;
    private static boolean pauseClickLastFrame = false;
    private static boolean ePressedLastFrame = false;
    private static boolean inventoryClickLastFrame = false;

    // Инвентарь выживания
    private static final Inventory inventory = new Inventory();

    // Слоты хотбара в творческом режиме (в выживании = первые слоты инвентаря)
    private static final byte[] hotbarBlocks = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final int HOTBAR_COUNT = hotbarBlocks.length;

    // Текущая игровая сессия
    private static Path currentSaveFolder = null;   // null — новый мир, ещё не сохранялся
    private static String currentWorldName = "Мир";
    private static double saveFlashUntil = 0.0;     // показ "СОХРАНЕНО" в паузе
    private static int selectedSlot = 0;
    private static float fov = 70.0f;
    private static boolean deathClickLastFrame = false;
    private static double modeFlashUntil = 0.0; // показ тоста о смене режима

    /** Переключает режим игры (F7 или кнопка в паузе) и показывает тост. */
    private static void cycleGameMode(Player player) {
        GameMode m = player.getMode().next();
        player.setMode(m);
        System.out.println("[F7] Режим игры: " + m.displayName);
        modeFlashUntil = glfwGetTime() + 2.5;
    }

    public static void main(String[] args) {
        Window window = new Window("Maintir Voxel Engine", 1280, 720);
        window.init();

        Camera camera = new Camera();
        camera.position.set(0, 110, 0);

        World world = new World();
        Player player = new Player();

        Shader shader = new Shader("/shaders/block.vert", "/shaders/block.frag");
        TextureArray textures = new TextureArray(new String[] {
                "/Trava.png",             // 0 трава
                "/kamen_cyka_64p.png",    // 1 камень
                "/pesok_nasok.png",       // 2 песок
                "/Trava_s_naskom.png",    // 3 нарост
                "/dyb.png",               // 4 ствол
                "/listva_64X64.png",      // 5 листва
                "/doski.png",             // 6 доски
                "/Ryda1_cyka_64p.png",    // 7 железная руда
                "/lampa.png",             // 8 лампа
                "/Zemla.png",             // 9 земля
                "/dver_niz.png",          // 10 дверь (низ)
                "/dver_verh.png"          // 11 дверь (верх)
        });

        SkyRenderer.init();

        FPSCounter fpsCounter = new FPSCounter();
        float lastFrame = (float) glfwGetTime();
        float breakCooldown = 0.0f;
        float placeCooldown = 0.0f;

        while (!window.shouldClose()) {
            float currentFrame = (float) glfwGetTime();
            float deltaTime = currentFrame - lastFrame;
            lastFrame = currentFrame;

            boolean escPressed = glfwGetKey(window.getHandle(), GLFW_KEY_ESCAPE) == GLFW_PRESS;
            boolean f7Pressed = glfwGetKey(window.getHandle(), GLFW_KEY_F7) == GLFW_PRESS;
            if (f7Pressed && !f7PressedLastFrame) {
                cycleGameMode(player);
            }
            f7PressedLastFrame = f7Pressed;

            // --- 1. ГЛАВНОЕ МЕНЮ ---
            if (state == GameState.MENU) {
                glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                MainMenu.MenuAction action = MainMenu.renderMain(window.getHandle(), window.getWidth(), window.getHeight());

                if (action == MainMenu.MenuAction.OPEN_WORLDS) {
                    state = GameState.WORLDS;
                } else if (action == MainMenu.MenuAction.SETTINGS) {
                    state = GameState.SETTINGS;
                } else if (action == MainMenu.MenuAction.EXIT) {
                    glfwSetWindowShouldClose(window.getHandle(), true);
                }
            }
            // --- 2. ВЫБОР СОХРАНЁННОГО МИРА ---
            else if (state == GameState.WORLDS) {
                glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                MainMenu.MenuAction action = MainMenu.renderWorlds(window.getHandle(), window.getWidth(), window.getHeight());

                if (action == MainMenu.MenuAction.OPEN_WORLD_SETUP) {
                    currentSaveFolder = null;
                    currentWorldName = "Мир " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM HH.mm"));
                    state = GameState.WORLD_SETUP;
                } else if (action == MainMenu.MenuAction.LOAD_WORLD) {
                    loadWorld(window, camera, player, world, MainMenu.selectedWorld);
                } else if (action == MainMenu.MenuAction.BACK) {
                    state = GameState.MENU;
                }
            }
            // --- 3. НАСТРОЙКА НОВОГО МИРА ---
            else if (state == GameState.WORLD_SETUP) {
                glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                MainMenu.MenuAction action = MainMenu.renderWorldSetup(window.getHandle(), window.getWidth(), window.getHeight());

                if (action == MainMenu.MenuAction.START_GAME) {
                    // Применяем настройку мира (деревья и свиньи генерируются сами)
                    world.setWorldSettings(MainMenu.worldSeed, MainMenu.selectedBiome);
                    world.setRenderDistance(MainMenu.renderDistance);
                    world.preloadArea(camera.position, (status, progress) -> {
                        LoadingScreen.render(window.getWidth(), window.getHeight(), status, progress);
                        window.update();
                    });
                    // Ставим игрока на поверхность и сбрасываем взгляд
                    player.spawn(world, 8, 8);
                    camera.position.set(player.getPosition().x,
                            player.getEyeY(),
                            player.getPosition().z);
                    camera.yaw = -90.0f;
                    camera.pitch = -15.0f;
                    fov = 70.0f;
                    selectedSlot = 0;
                    inventory.clear();
                    // Заселяем мир свиньями
                    world.spawnInitialPigs(player.getPosition().x, player.getPosition().z);
                    // Новый мир ещё не сохранён: имя придумано при входе в этот экран
                    currentSaveFolder = null;
                    glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    state = GameState.GAME;
                } else if (action == MainMenu.MenuAction.BACK) {
                    state = GameState.WORLDS;
                }
            }
            // --- 3. НАСТРОЙКИ ---
            else if (state == GameState.SETTINGS) {
                glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                MainMenu.MenuAction action = MainMenu.renderSettings(window.getHandle(), window.getWidth(), window.getHeight());

                if (action == MainMenu.MenuAction.TOGGLE_FULLSCREEN) window.toggleFullscreen();
                else if (action == MainMenu.MenuAction.RES_1280) window.setResolution(1280, 720);
                else if (action == MainMenu.MenuAction.RES_1920) window.setResolution(1920, 1080);
                else if (action == MainMenu.MenuAction.BACK || (escPressed && !escPressedLastFrame)) state = GameState.MENU;
            }
            // --- 5. ИГРА ---
            else if (state == GameState.GAME) {
                if (escPressed && !escPressedLastFrame) {
                    state = GameState.PAUSED;
                    escPressedLastFrame = true;
                    pauseClickLastFrame = true; // не сработать кликом от стрельбы
                    continue;
                }
                if (glfwGetKey(window.getHandle(), GLFW_KEY_E) == GLFW_PRESS && !ePressedLastFrame) {
                    ePressedLastFrame = true;
                    inventoryClickLastFrame = true;
                    state = GameState.INVENTORY;
                    continue;
                }

                float dt = Math.min(deltaTime, 0.05f);
                world.advanceTime(dt); // цикл дня и ночи

                camera.handleMouse(window.getHandle());

                // Направление движения — по взгляду камеры, но строго горизонтально
                Vector3f front = camera.getFront();
                Vector3f flatFront = new Vector3f(front.x, 0, front.z);
                if (flatFront.lengthSquared() < 1e-6f) flatFront.set(0, 0, -1);
                flatFront.normalize();
                // right = cross(front, up), сплющенный
                Vector3f right = new Vector3f(-flatFront.z, 0, flatFront.x);

                player.update(window.getHandle(), dt, world, flatFront, right);
                camera.position.set(player.getPosition().x,
                        player.getEyeY(),
                        player.getPosition().z);
                player.applyViewBob(camera.position, right);

                world.update(camera.position);
                world.updatePigs(dt, camera.position);
                world.updateDoors(dt); // анимация открытия/закрытия дверей

                // Смерть в выживании: переход на экран смерти
                if (player.getMode() == GameMode.SURVIVAL && player.getHp() <= 0) {
                    state = GameState.DEATH;
                    continue;
                }

                // Ломание блоков: ЛКМ (первое нажатие сразу, дальше раз в 0.22 с)
                breakCooldown -= dt;
                boolean lmb = glfwGetMouseButton(window.getHandle(), GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
                if (lmb && breakCooldown <= 0.0f) {
                    int[] hit = world.raycastBlock(camera.position, front, 7.0f);
                    if (hit != null) {
                        byte brokenId = world.getBlockAt(hit[0], hit[1], hit[2]);
                        if (brokenId != 0 && world.breakBlockDoor(hit[0], hit[1], hit[2])) {
                            if (player.getMode() == GameMode.SURVIVAL) {
                                byte drop = brokenId;
                                if (drop == Blocks.DOOR_TOP) drop = Blocks.DOOR_BOTTOM;
                                inventory.add(drop); // добыча
                            }
                            breakCooldown = 0.22f;
                        } else {
                            breakCooldown = 0.08f;
                        }
                    } else {
                        breakCooldown = 0.08f;
                    }
                }

                // Постановка блоков: ПКМ (в выживании только из инвентаря)
                placeCooldown -= dt;
                boolean rmb = glfwGetMouseButton(window.getHandle(), GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;
                if (rmb && placeCooldown <= 0.0f) {
                    int[] hit = world.raycastBlock(camera.position, front, 7.0f);
                    if (hit != null) {
                        byte hitId = world.getBlockAt(hit[0], hit[1], hit[2]);
                        // Клик по двери открывает/закрывает её, а не ставит блок
                        if (Blocks.isDoor(hitId)) {
                            world.toggleDoor(hit[0], hit[1], hit[2]);
                            placeCooldown = 0.22f;
                        } else {
                            boolean creative = player.getMode() == GameMode.CREATIVE;
                            byte blockId = creative ? hotbarBlocks[selectedSlot] : inventory.slotId(selectedSlot);
                            boolean haveBlock = blockId > 0;
                            int px = hit[0] + hit[3];
                            int py = hit[1] + hit[4];
                            int pz = hit[2] + hit[5];
                            boolean placed = false;
                            if (haveBlock && !player.intersectsBlock(px, py, pz)) {
                                if (blockId == Blocks.DOOR_BOTTOM) {
                                    placed = world.placeDoor(px, py, pz);
                                } else if (Blocks.isStairs(blockId)) {
                                    placed = world.placeStairs(px, py, pz,
                                            player.getPosition().x, player.getPosition().z, blockId);
                                } else {
                                    placed = world.placeBlock(px, py, pz, blockId);
                                }
                            }
                            if (placed) {
                                if (!creative) inventory.consumeSlot(selectedSlot);
                                placeCooldown = 0.22f;
                            } else {
                                placeCooldown = 0.08f;
                            }
                        }
                    } else {
                        placeCooldown = 0.08f;
                    }
                }

                // Выбор слота хотбара: клавиши 1-9, Q и колесо мыши (E открывает инвентарь)
                for (int k = 0; k < HOTBAR_COUNT; k++) {
                    if (glfwGetKey(window.getHandle(), GLFW_KEY_1 + k) == GLFW_PRESS) selectedSlot = k;
                }
                if (glfwGetKey(window.getHandle(), GLFW_KEY_Q) == GLFW_PRESS) {
                    selectedSlot = (selectedSlot - 1 + HOTBAR_COUNT) % HOTBAR_COUNT;
                    placeCooldown = Math.max(placeCooldown, 0.15f);
                }
                double scroll = window.consumeScroll();
                if (scroll != 0) {
                    int dir = scroll > 0 ? -1 : 1; // колесо вверх — предыдущий слот
                    selectedSlot = Math.floorMod(selectedSlot + dir, HOTBAR_COUNT);
                }

                // Небо и туман меняются со временем суток
                Vector3f skyColor = world.getSkyColor();
                glClearColor(skyColor.x, skyColor.y, skyColor.z, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                shader.use();
                textures.bind();
                shader.setUniform("uTextureArray", 0);

                float fogEnd = world.getRenderDistance() * 30.0f - 10.0f;
                shader.setUniform("uFogColor", skyColor);
                shader.setUniform("uFogStart", fogEnd * 0.55f);
                shader.setUniform("uFogEnd", fogEnd);
                shader.setUniform("uSunLevel", world.getSunLevel());
                shader.setUniform("uViewport", (float) window.getWidth(), (float) window.getHeight());

                // Лёгкое расширение FOV при беге
                float targetFov = player.isSprinting() ? 78.0f : 70.0f;
                fov += (targetFov - fov) * Math.min(1.0f, dt * 8.0f);

                Matrix4f projection = new Matrix4f().perspective(
                        (float) Math.toRadians(fov),
                        (float) window.getWidth() / window.getHeight(),
                        0.1f, 1000.0f
                );

                shader.setUniform("projection", projection);
                shader.setUniform("view", camera.getViewMatrix());
                shader.setUniform("model", new Matrix4f());
                shader.setUniform("lightDir", world.getSunDir());

                // Солнце и луна — до мира, чтобы горы их перекрывали
                SkyRenderer.render(projection, camera.getViewMatrix(), camera.position, world.getTimeOfDay());

                // Вернуть блочный шейдер и текстуры после sky
                shader.use();
                textures.bind();

                world.render();

                // Двери: динамическая геометрия (анимация открытия). Шейдер/текстуры уже активны.
                DoorRenderer.render(world.activeDoors(), world);

                // Обводка блока, на который смотрит игрок
                int[] look = world.raycastBlock(camera.position, front, 7.0f);
                if (look != null && world.getBlockAt(look[0], look[1], look[2]) != 0) {
                    BlockOutline.draw(look[0], look[1], look[2], camera.getViewMatrix(), projection);
                }

                // Свиньи поверх мира (яркость зависит от времени суток)
                Pig.setDayBrightness(world.getSunLevel());
                world.renderPigs(camera.getViewMatrix(), projection, camera.position);

                // Хотбар поверх 3D-сцены (+ количества в выживании)
                boolean survival = player.getMode() == GameMode.SURVIVAL;
                if (survival) {
                    Hotbar.render(window.getWidth(), window.getHeight(), selectedSlot,
                            inventory.firstIds(HOTBAR_COUNT), inventory.firstCounts(HOTBAR_COUNT));
                } else {
                    Hotbar.render(window.getWidth(), window.getHeight(), selectedSlot, hotbarBlocks, null);
                }

                // --- HUD ---
                int w = window.getWidth(), h = window.getHeight();
                glDisable(GL_DEPTH_TEST);

                String modeLabel = player.getMode().displayName + " [F7]"
                        + (player.isFlying() ? " | ПОЛЁТ" : "");
                FontRenderer.drawString(modeLabel, 14, 14, 2.0f, w, h);

                // Тост-уведомление о смене режима
                if ((float) glfwGetTime() < modeFlashUntil) {
                    String flash = "РЕЖИМ: " + player.getMode().displayName;
                    float fs = 2.6f;
                    float tw = flash.length() * 6.0f * fs;
                    float fx = w / 2f - tw / 2f - 16;
                    MenuRenderer.drawFlatRect(w, h, fx, 12, tw + 32, 36, 0.0f, 0.0f, 0.0f);
                    FontRenderer.drawString(flash, fx + 16, 18, fs, w, h);
                }

                if (survival) {
                    // Шкала здоровья над хотбаром
                    float barW = Math.min(420, w * 0.4f), barH = 16;
                    float bx = (w - barW) / 2f;
                    float by = h - 52 - 52;
                    MenuRenderer.drawFlatRect(w, h, bx - 2, by - 2, barW + 4, barH + 4, 0.06f, 0.06f, 0.08f);
                    float frac = player.getHp() / Player.MAX_HP;
                    float cr = 0.85f * (1.2f - frac * 0.5f), cg = 0.15f + frac * 0.45f, cb = 0.12f;
                    MenuRenderer.drawFlatRect(w, h, bx, by, barW * frac, barH, cr, cg, cb);
                    String hpText = (int) player.getHp() + " / " + (int) Player.MAX_HP;
                    FontRenderer.drawString(hpText, bx + barW / 2f - hpText.length() * 6.0f, by + 3, 1.7f, w, h);

                    // Красная вспышка при уроне
                    double since = player.secondsSinceDamage();
                    if (since < 0.45) {
                        Hotbar.drawScreenFade(w, h, 0.8f, 0.05f, 0.05f, (float) (0.38 * (1.0 - since / 0.45)));
                    }
                }

                // Прицел поверх всего HUD
                Hotbar.drawCrosshair(w, h);

                glEnable(GL_DEPTH_TEST);

                fpsCounter.update(window.getHandle(), "Maintir Voxel Engine");
            }
            // --- 6. ПАУЗА ---
            else if (state == GameState.PAUSED) {
                glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);

                if (escPressed && !escPressedLastFrame) {
                    glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    state = GameState.GAME;
                    escPressedLastFrame = true;
                    continue;
                }

                int w = window.getWidth(), h = window.getHeight();
                glClearColor(0.05f, 0.06f, 0.08f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                glDisable(GL_DEPTH_TEST);
                MenuRenderer.init();

                double[] mx = new double[1], my = new double[1];
                glfwGetCursorPos(window.getHandle(), mx, my);
                float px = (float) mx[0], py = (float) my[0];
                boolean clickNow = glfwGetMouseButton(window.getHandle(), GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
                boolean clicked = clickNow && !pauseClickLastFrame;
                pauseClickLastFrame = clickNow;

                // Панель
                MenuRenderer.drawFlatRect(w, h, w / 2f - 260, h / 2f - 210, 520, 430, 0.10f, 0.11f, 0.15f);
                FontRenderer.drawString("ПАУЗА", w / 2f - 45, h / 2f - 185, 4.0f, w, h);

                float bx = w / 2f - 160, bw = 320, bh = 52;
                if (MenuRenderer.drawColorButton(w, h, px, py, bx, h / 2f - 100, bw, bh,
                        0.2f, 0.6f, 0.3f, "ПРОДОЛЖИТЬ", clicked)) {
                    glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    state = GameState.GAME;
                }

                boolean justSaved = (float) glfwGetTime() < saveFlashUntil;
                if (MenuRenderer.drawColorButton(w, h, px, py, bx, h / 2f - 30, bw, bh,
                        justSaved ? 0.2f : 0.3f, 0.5f, justSaved ? 0.25f : 0.6f,
                        justSaved ? "СОХРАНЕНО!" : "СОХРАНИТЬ МИР", clicked)) {
                    saveCurrentGame(camera, player, world);
                }

                if (MenuRenderer.drawColorButton(w, h, px, py, bx, h / 2f + 40, bw, bh,
                        0.7f, 0.35f, 0.2f, "СОХРАНИТЬ И В МЕНЮ", clicked)) {
                    saveCurrentGame(camera, player, world);
                    state = GameState.MENU;
                }

                // Режим игры: альтернатива F7 (для клавиатур без F-клавиш)
                if (MenuRenderer.drawColorButton(w, h, px, py, bx, h / 2f + 110, bw, bh,
                        0.3f, 0.4f, 0.6f, "РЕЖИМ: " + player.getMode().displayName, clicked)) {
                    cycleGameMode(player);
                }

                glEnable(GL_DEPTH_TEST);
            }
            // --- 7. ЭКРАН СМЕРТИ ---
            else if (state == GameState.DEATH) {
                glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);

                int w = window.getWidth(), h = window.getHeight();
                glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                glDisable(GL_DEPTH_TEST);
                MenuRenderer.init();

                // Тёмный фон с красным оттенком
                MenuRenderer.drawFlatRect(w, h, 0, 0, w, h, 0.15f, 0.02f, 0.02f);

                // Панель
                MenuRenderer.drawFlatRect(w, h, w / 2f - 260, h / 2f - 210, 520, 430, 0.12f, 0.04f, 0.04f);

                FontRenderer.drawString("ВЫ ПОГИБЛИ", w / 2f - 165, h / 2f - 185, 5.0f, w, h);

                double[] mx = new double[1], my = new double[1];
                glfwGetCursorPos(window.getHandle(), mx, my);
                float px = (float) mx[0], py = (float) my[0];
                boolean clickNow = glfwGetMouseButton(window.getHandle(), GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
                boolean clicked = clickNow && !deathClickLastFrame;
                deathClickLastFrame = clickNow;

                float bx = w / 2f - 160, bw = 320, bh = 52;

                // Кнопка "ВОЗРОДИТЬСЯ"
                if (MenuRenderer.drawColorButton(w, h, px, py, bx, h / 2f - 50, bw, bh,
                        0.3f, 0.6f, 0.2f, "ВОЗРОДИТЬСЯ", clicked)) {
                    player.resetHp();
                    player.spawn(world, (int) Math.floor(player.getPosition().x),
                            (int) Math.floor(player.getPosition().z));
                    camera.position.set(player.getPosition().x, player.getEyeY(), player.getPosition().z);
                    glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    state = GameState.GAME;
                }

                // Кнопка "ВЫЙТИ С СОХРАНЕНИЕМ"
                if (MenuRenderer.drawColorButton(w, h, px, py, bx, h / 2f + 30, bw, bh,
                        0.7f, 0.35f, 0.2f, "ВЫЙТИ С СОХРАНЕНИЕМ", clicked)) {
                    player.resetHp();
                    saveCurrentGame(camera, player, world);
                    state = GameState.MENU;
                }

                glEnable(GL_DEPTH_TEST);
            }
            // --- 7.5 ИНВЕНТАРЬ (клавиша E) ---
            else if (state == GameState.INVENTORY) {
                glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);

                boolean eDown = glfwGetKey(window.getHandle(), GLFW_KEY_E) == GLFW_PRESS;
                // Повторный E или ESC закрывает инвентарь
                if ((eDown && !ePressedLastFrame) || (escPressed && !escPressedLastFrame)) {
                    ePressedLastFrame = eDown;
                    escPressedLastFrame = escPressed; // защита от мгновенной паузы в GAME
                    InventoryGui.resetDrag();
                    glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                    state = GameState.GAME;
                    continue;
                }
                ePressedLastFrame = eDown;

                int w = window.getWidth(), h = window.getHeight();
                glClearColor(0.05f, 0.06f, 0.08f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                glDisable(GL_DEPTH_TEST);

                double[] mx = new double[1], my = new double[1];
                glfwGetCursorPos(window.getHandle(), mx, my);
                float px = (float) mx[0], py = (float) my[0];
                boolean clickNow = glfwGetMouseButton(window.getHandle(), GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
                boolean clicked = clickNow && !inventoryClickLastFrame;
                inventoryClickLastFrame = clickNow;

                boolean creative = player.getMode() == GameMode.CREATIVE;
                textures.bind(); // иконки в инвентаре берутся из TextureArray (юнит 0)
                selectedSlot = InventoryGui.render(w, h, px, py, clicked, creative,
                        inventory, hotbarBlocks, selectedSlot);

                glEnable(GL_DEPTH_TEST);
            }

            escPressedLastFrame = escPressed;
            ePressedLastFrame = glfwGetKey(window.getHandle(), GLFW_KEY_E) == GLFW_PRESS;
            window.update();
        }

        LoadingScreen.cleanup();
        Pig.cleanupAssets();
        SkyRenderer.cleanup();
        textures.cleanup();
        window.cleanup();
    }

    /** Загружает сохранённый мир по индексу в кэше MainMenu.savesCache. */
    private static void loadWorld(Window window, Camera camera, Player player, World world, int index) {
        if (index < 0 || index >= MainMenu.savesCache.size()) return;
        WorldSave.Meta meta = MainMenu.savesCache.get(index);
        try {
            WorldSave.Loaded loaded = WorldSave.load(meta.folder);

            // Сначала настройки (сбрасывают overrides), потом правки блоков из файла
            world.setWorldSettings(meta.seed, TerrainGenerator.BiomeType.valueOf(meta.biome));
            world.setRenderDistance(meta.renderDistance);
            world.importOverrides(loaded.overrides);
            world.scanDoorsFromOverrides(); // восстановить двери из сохранённых правок
            world.setTimeOfDay(meta.timeOfDay);

            player.setPosition(meta.px, meta.py, meta.pz);
            camera.position.set(player.getPosition().x, player.getEyeY(), player.getPosition().z);
            camera.yaw = meta.yaw;
            camera.pitch = meta.pitch;
            fov = 70.0f;
            selectedSlot = Math.floorMod(meta.selectedSlot, HOTBAR_COUNT);
            inventory.clear();

            // Восстанавливаем режим игры (старые миры — CREATIVE)
            try {
                player.setMode(GameMode.valueOf(meta.gameMode));
            } catch (Exception e) {
                player.setMode(GameMode.CREATIVE);
            }

            world.preloadArea(camera.position, (status, progress) -> {
                LoadingScreen.render(window.getWidth(), window.getHeight(), status, progress);
                window.update();
            });
            world.spawnInitialPigs(meta.px, meta.pz);

            currentSaveFolder = meta.folder;
            currentWorldName = meta.name;

            glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            state = GameState.GAME;
        } catch (Exception e) {
            System.err.println("Не удалось загрузить мир: " + e.getMessage());
        }
    }

    /** Сохраняет текущую сессию в папку мира. */
    private static void saveCurrentGame(Camera camera, Player player, World world) {
        try {
            WorldSave.Meta m = new WorldSave.Meta();
            m.name = currentWorldName;
            m.seed = world.getSeed();
            m.biome = world.getBiome().name();
            m.treeDensityPercent = 100; // деревья генерируются сами, поле для совместимости формата
            m.renderDistance = world.getRenderDistance();
            m.pigCount = world.getDesiredPigs();
            m.timeOfDay = world.getTimeOfDay();
            Vector3f p = player.getPosition();
            m.px = p.x;
            m.py = p.y;
            m.pz = p.z;
            m.yaw = camera.yaw;
            m.pitch = camera.pitch;
            m.selectedSlot = selectedSlot;
            m.gameMode = player.getMode().name();

            currentSaveFolder = WorldSave.saveTo(currentSaveFolder, m, world.exportOverrides());
            saveFlashUntil = glfwGetTime() + 2.5;
        } catch (Exception e) {
            System.err.println("Не удалось сохранить мир: " + e.getMessage());
        }
    }
}