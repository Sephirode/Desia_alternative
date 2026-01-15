package com.desia.game.loop;

public record SaveData(
        String name,
        int level,
        int chapter,
        int progressIndex,
        int gold
) {
}
