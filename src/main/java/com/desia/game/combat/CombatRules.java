package com.desia.game.combat;

import java.util.Map;

public record CombatRules(
        DefenseModel defenseModel,
        double defenseK,
        double defenseMinMult,
        double baseCritChance,
        double critMultiplier,
        double minHit,
        double blindMultiplier,
        boolean shieldBypassDefense,
        double dotPercent,
        Map<String, Map<String, Double>> elementMatrix
) {
}
