package com.desia.game.combat;

public record CombatMenuOption(CombatActionType type, String label) {
    public CombatMenuOption {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }
}
