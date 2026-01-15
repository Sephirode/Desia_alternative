package com.desia.game.loop;

import java.util.List;

public record ChapterPlan(List<ChapterNode> nodes) {
    public ChapterPlan {
        if (nodes == null || nodes.size() != 12) {
            throw new IllegalArgumentException("chapter must have exactly 12 nodes");
        }
        ChapterNode last = nodes.get(nodes.size() - 1);
        if (last.type() != ChapterNodeType.BOSS) {
            throw new IllegalArgumentException("last node must be BOSS");
        }
    }
}
