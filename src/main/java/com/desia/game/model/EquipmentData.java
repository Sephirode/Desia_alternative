package com.desia.game.model;

import java.util.List;
import java.util.Map;

public record EquipmentData(int version, Map<String, Equipment> equipment, Map<String, EquipmentSet> sets) {
    public record Equipment(
            String name,
            String description,
            String rarity,
            String slot,
            Integer price,
            Double sellRatio,
            Boolean unique,
            StatsBlock stats,
            List<String> grantedSkills,
            String setName,
            List<String> specialTags
    ) {
    }

    public record EquipmentSet(String name, List<String> pieces, List<SetBonus> bonuses) {
    }

    public record SetBonus(Integer pieces, StatsBlock stats, List<String> specialTags) {
    }

    public record StatsBlock(Map<String, Double> flat, Map<String, Double> percent) {
    }
}
