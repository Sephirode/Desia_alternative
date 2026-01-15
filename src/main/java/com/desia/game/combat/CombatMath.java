package com.desia.game.combat;

public final class CombatMath {
    private CombatMath() {
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double defenseMultiplier(DefenseModel model, double defense, double k, double minMult) {
        return switch (model) {
            case LOL -> 100.0 / (100.0 + defense);
            case CURVE -> Math.max(minMult, 1.0 / (1.0 + defense * k));
            case LINEAR_CAP -> clamp(1.0 - defense * k, minMult, 1.0);
        };
    }

    public static AccuracyResult accuracy(double attackerSpeed, double targetSpeed, double minHit, double blindMult,
                                          boolean isBlind) {
        if (attackerSpeed <= 0 || targetSpeed <= 0) {
            return new AccuracyResult(minHit, false);
        }
        double ratio = attackerSpeed / targetSpeed;
        double baseChance = ratio >= 1.0 ? 1.0 : ratio;
        double adjusted = isBlind ? baseChance * blindMult : baseChance;
        double clamped = clamp(adjusted, minHit, 1.0);
        return new AccuracyResult(clamped, clamped >= 1.0);
    }

    public static double criticalMultiplier(double critChance, double baseChance, double critMult, double roll) {
        double chance = clamp(critChance + baseChance, 0.0, 1.0);
        return roll < chance ? critMult : 1.0;
    }
}
