package com.desia.game.model;

import java.util.List;
import java.util.Map;

public record ConsumablesData(int version, Map<String, Consumable> consumables) {
    public record Consumable(
            String name,
            String description,
            String rarity,
            Integer price,
            Double sellRatio,
            Boolean usableInBattle,
            List<Effect> effects,
            List<String> specialTags
    ) {
    }

    public record Effect(
            String type,
            Double flat,
            Double percentMaxHp,
            Double percentMaxMp,
            String status,
            Double chance,
            Integer durationTurns,
            String target,
            List<String> statuses,
            List<Modifier> modifiers,
            List<Modifier> mods,
            String skillId,
            Double mult
    ) {
    }

    public record Modifier(String stat, Double flat, Double percent) {
    }
}
