package com.desia.game.combat;

import com.desia.game.model.EnemiesData;

public final class StatCalculator {
    private StatCalculator() {
    }

    public static EnemyInstance createEnemyInstance(EnemiesData.Enemy enemy, int level) {
        int resolvedLevel = Math.max(level, enemy.baseLevel() == null ? 1 : enemy.baseLevel());
        CombatStats stats = computeEnemyStats(enemy, resolvedLevel);
        return new EnemyInstance(enemy, resolvedLevel, stats);
    }

    public static CombatStats computeEnemyStats(EnemiesData.Enemy enemy, int level) {
        EnemiesData.Stats base = enemy.baseStats();
        EnemiesData.Stats growth = enemy.growthPerLevel();
        int baseLevel = enemy.baseLevel() == null ? 1 : enemy.baseLevel();
        int levelDelta = Math.max(0, level - baseLevel);

        return new CombatStats(
                applyGrowth(base.maxHp(), growth.maxHp(), levelDelta),
                applyGrowth(base.maxMp(), growth.maxMp(), levelDelta),
                applyGrowth(base.attack(), growth.attack(), levelDelta),
                applyGrowth(base.spellPower(), growth.spellPower(), levelDelta),
                applyGrowth(base.defense(), growth.defense(), levelDelta),
                applyGrowth(base.magicResist(), growth.magicResist(), levelDelta),
                applyGrowth(base.speed(), growth.speed(), levelDelta),
                base.critChance() == null ? 0 : base.critChance()
        );
    }

    private static double applyGrowth(Double base, Double growth, int delta) {
        double baseValue = base == null ? 0 : base;
        double growthValue = growth == null ? 0 : growth;
        return baseValue + growthValue * delta;
    }
}
