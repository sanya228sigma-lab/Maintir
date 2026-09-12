package org.maintir.world;

/**
 * Состояние двери: положение панели (закрыта/открыта), плавная анимация поворота.
 * Хранится в реестре World по координатам НИЖНЕЙ ячейки двери.
 * Степень открытия frac плавно стремится к цели: открывающиеся двери не «щелкают».
 */
public class Door {
    /** Нижняя ячейка двери (мировые координаты). */
    public final int wx, wy, wz;

    /** Край петли по Z: 0 — петля на грани z=0, 1 — на грани z=1. */
    private final float pivotZ;

    private boolean open = false;
    private float frac = 0f;

    /** Секунд на полное открытие/закрытие. */
    public static final float SWING_SEC = 0.4f;

    public Door(int wx, int wy, int wz) {
        this(wx, wy, wz, 0f);
    }

    public Door(int wx, int wy, int wz, float pivotZ) {
        this.wx = wx;
        this.wy = wy;
        this.wz = wz;
        this.pivotZ = pivotZ;
    }

    public void toggle() {
        open = !open;
    }

    public boolean isOpen() {
        return open;
    }

    /** Глобус открытия 0..1 (для физики: открыта, если почти достигла). */
    public float openFrac() {
        return frac;
    }

    /** Текущий угол поворота панели в радианах (0 — закрыта, ~90° — открыта). */
    public double angleRad() {
        return frac * Math.PI * 0.5;
    }

    /** Край петли в координатах ячейки (0 или 1). */
    public float pivotZ() {
        return pivotZ;
    }

    public void update(float dt) {
        float target = open ? 1f : 0f;
        if (frac == target) return;
        float step = dt / SWING_SEC;
        if (target > frac) frac = Math.min(target, frac + step);
        else frac = Math.max(target, frac - step);
    }
}