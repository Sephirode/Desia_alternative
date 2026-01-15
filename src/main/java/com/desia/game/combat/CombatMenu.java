package com.desia.game.combat;

import java.util.List;

public record CombatMenu(List<CombatMenuOption> options) {
    public CombatMenu {
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("options must not be empty");
        }
    }

    public static CombatMenu defaultMenu() {
        return new CombatMenu(List.of(
                new CombatMenuOption(CombatActionType.BASIC_ATTACK, "공격"),
                new CombatMenuOption(CombatActionType.SKILL, "스킬"),
                new CombatMenuOption(CombatActionType.ITEM, "아이템"),
                new CombatMenuOption(CombatActionType.ESCAPE, "도주하기")
        ));
    }
}
