package com.desia.game.model;

import java.util.Map;

public record ConfigData(
        int version,
        DefenseModel defenseModel,
        Crit crit,
        Accuracy accuracy,
        Shield shield,
        EquipmentRules equipmentRules,
        Elements elements
) {
    public record DefenseModel(String type, double k, double minMult) {
    }

    public record Crit(double baseChance, double mult) {
    }

    public record Accuracy(double minHit, double blindMult) {
    }

    public record Shield(boolean bypassDefense) {
    }

    public record EquipmentRules(int ringSlots) {
    }

    public record Elements(Map<String, Map<String, Double>> multiplier) {
    }
}
