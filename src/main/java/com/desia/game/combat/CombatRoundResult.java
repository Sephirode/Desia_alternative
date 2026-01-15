package com.desia.game.combat;

import java.util.List;

public record CombatRoundResult(List<CombatParticipant> participants, CombatLog log, boolean battleEnded) {
}
