package org.maintir.world;

/** Режим игры: творческий (полёт, бесконечные блоки) или выживание (добыча, ХП). */
public enum GameMode {
    CREATIVE("ТВОРЧЕСКИЙ"),
    SURVIVAL("ВЫЖИВАНИЕ");

    public final String displayName;

    GameMode(String displayName) {
        this.displayName = displayName;
    }

    public GameMode next() {
        return this == CREATIVE ? SURVIVAL : CREATIVE;
    }
}
