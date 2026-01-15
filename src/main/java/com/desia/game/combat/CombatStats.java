package com.desia.game.combat;

public record CombatStats(
        double maxHp,
        double maxMp,
        double attack,
        double spellPower,
        double defense,
        double magicResist,
        double speed,
        double critChance
) {
}
