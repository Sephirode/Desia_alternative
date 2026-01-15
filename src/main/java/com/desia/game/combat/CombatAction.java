package com.desia.game.combat;

public record CombatAction(CombatActionType type, String skillId, String itemId, String targetId) {
    public static CombatAction basicAttack(String targetId) {
        return new CombatAction(CombatActionType.BASIC_ATTACK, null, null, targetId);
    }

    public static CombatAction skill(String skillId, String targetId) {
        return new CombatAction(CombatActionType.SKILL, skillId, null, targetId);
    }

    public static CombatAction item(String itemId, String targetId) {
        return new CombatAction(CombatActionType.ITEM, null, itemId, targetId);
    }

    public static CombatAction escape() {
        return new CombatAction(CombatActionType.ESCAPE, null, null, null);
    }
}
