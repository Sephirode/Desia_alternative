package com.desia.game.combat;

import com.desia.game.model.EnemiesData;

public record EnemyInstance(
        EnemiesData.Enemy enemy,
        int level,
        CombatStats stats
) {
}
