package com.desia.game.model;

import java.util.List;
import java.util.Map;

public record SkillsData(int version, Map<String, Skill> skills) {
    public record Skill(
            String name,
            String description,
            String element,
            String category,
            String target,
            Integer mpCost,
            List<Component> components,
            List<StatusEffect> statusEffects,
            List<String> specialTags
    ) {
    }

    public record Component(String kind, String damageType, List<Term> terms) {
    }

    public record Term(String stat, double coef) {
    }

    public record StatusEffect(String status, String target, Double chance, Integer durationTurns) {
    }
}
