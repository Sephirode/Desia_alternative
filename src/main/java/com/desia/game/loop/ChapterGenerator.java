package com.desia.game.loop;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ChapterGenerator {
    private ChapterGenerator() {
    }

    public static ChapterPlan generate(long seed) {
        Random random = new Random(seed);
        List<ChapterNode> nodes = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            nodes.add(new ChapterNode(i, randomType(random)));
        }
        nodes.add(new ChapterNode(12, ChapterNodeType.BOSS));
        return new ChapterPlan(nodes);
    }

    private static ChapterNodeType randomType(Random random) {
        int roll = random.nextInt(3);
        return switch (roll) {
            case 0 -> ChapterNodeType.BATTLE;
            case 1 -> ChapterNodeType.SHOP;
            default -> ChapterNodeType.EVENT;
        };
    }
}
