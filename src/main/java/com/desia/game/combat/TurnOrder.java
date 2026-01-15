package com.desia.game.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TurnOrder {
    private static final Comparator<Combatant> COMPARATOR = Comparator
            .comparingDouble((Combatant combatant) -> combatant.stats().speed()).reversed()
            .thenComparingInt(Combatant::level).reversed()
            .thenComparingDouble(Combatant::currentHp).reversed();

    private TurnOrder() {
    }

    public static List<Combatant> sortByInitiative(List<Combatant> combatants) {
        List<Combatant> ordered = new ArrayList<>(combatants);
        ordered.sort(COMPARATOR);
        return ordered;
    }
}
