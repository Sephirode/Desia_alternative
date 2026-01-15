package com.desia.game.combat;

import java.util.Map;

public final class DamageCalculator {
    private DamageCalculator() {
    }

    public static DamageResult applyDamage(double rawDamage, DamageType type, CombatStats attacker, CombatStats target,
                                           DefenseModel model, double k, double minMult, String element,
                                           Map<String, Map<String, Double>> elementMatrix, String targetElement,
                                           boolean bypassDefense) {
        double mitigation = 1.0;
        if (!bypassDefense) {
            mitigation = switch (type) {
                case PHYSICAL -> CombatMath.defenseMultiplier(model, target.defense(), k, minMult);
                case MAGIC -> CombatMath.defenseMultiplier(model, target.magicResist(), k, minMult);
                case TRUE -> 1.0;
            };
        }
        double elementMult = resolveElementMultiplier(elementMatrix, element, targetElement);
        double beforeMitigation = rawDamage * elementMult;
        double finalDamage = beforeMitigation * mitigation;
        return new DamageResult(finalDamage, beforeMitigation, mitigation);
    }

    private static double resolveElementMultiplier(Map<String, Map<String, Double>> matrix, String element,
                                                   String targetElement) {
        if (matrix == null || element == null || targetElement == null) {
            return 1.0;
        }
        Map<String, Double> row = matrix.get(element);
        if (row == null) {
            return 1.0;
        }
        return row.getOrDefault(targetElement, 1.0);
    }
}
