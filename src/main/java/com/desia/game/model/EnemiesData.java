package com.desia.game.model;

import java.util.List;
import java.util.Map;

public record EnemiesData(int version, List<Enemy> enemies) {
    public record Enemy(
            String id,
            String name,
            String tier,
            String element,
            Integer baseLevel,
            Stats baseStats,
            Stats growthPerLevel,
            Rewards rewards,
            SkillAssignment skillAssignment,
            Map<String, StatusResistance> statusResistance
    ) {
    }

    public record Stats(
            Double maxHp,
            Double maxMp,
            Double attack,
            Double spellPower,
            Double defense,
            Double magicResist,
            Double speed,
            Double critChance
    ) {
    }

    public record Rewards(Integer xp, Double goldFromXpRatio) {
    }

    public record SkillAssignment(List<String> base, List<SkillLevelAdd> byLevel) {
    }

    public record SkillLevelAdd(Integer minLevel, List<String> add) {
    }

    public record StatusResistance(Double mult) {
    }
}
