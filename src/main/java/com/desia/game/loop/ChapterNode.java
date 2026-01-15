package com.desia.game.loop;

public record ChapterNode(int index, ChapterNodeType type) {
    public ChapterNode {
        if (index < 1) {
            throw new IllegalArgumentException("index must be >= 1");
        }
    }
}
