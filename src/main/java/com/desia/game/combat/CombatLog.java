package com.desia.game.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CombatLog {
    private final List<String> entries = new ArrayList<>();

    public void info(String message) {
        entries.add(message);
    }

    public List<String> entries() {
        return Collections.unmodifiableList(entries);
    }
}
