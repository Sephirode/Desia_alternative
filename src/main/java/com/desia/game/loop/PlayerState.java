package com.desia.game.loop;

public record PlayerState(
        String name,
        int level,
        int chapter,
        int progressIndex,
        boolean alive
) {
}
