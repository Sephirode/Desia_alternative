package com.desia.game.combat;

import com.desia.game.model.SkillsData;

public final class SkillDamageCalculator {
    private SkillDamageCalculator() {
    }

    public static double evaluateTerms(SkillsData.Component component, CombatParticipant self,
                                       CombatParticipant target) {
        if (component.terms() == null) {
            return 0;
        }
        double total = 0;
        for (SkillsData.Term term : component.terms()) {
            if (term == null) {
                continue;
            }
            total += term.coef() * resolveTermValue(term.stat(), self, target);
        }
        return total;
    }

    private static double resolveTermValue(String stat, CombatParticipant self, CombatParticipant target) {
        if (stat == null) {
            return 0;
        }
        return switch (stat) {
            case "constant" -> 1.0;
            case "self_attack" -> self.stats().attack();
            case "self_spell_power" -> self.stats().spellPower();
            case "self_defense" -> self.stats().defense();
            case "self_magic_resist" -> self.stats().magicResist();
            case "self_speed" -> self.stats().speed();
            case "self_hp" -> self.currentHp();
            case "self_max_hp" -> self.stats().maxHp();
            case "self_missing_hp" -> self.stats().maxHp() - self.currentHp();
            case "self_mp" -> self.currentMp();
            case "self_max_mp" -> self.stats().maxMp();
            case "self_missing_mp" -> self.stats().maxMp() - self.currentMp();
            case "self_spent_mp" -> self.stats().maxMp() - self.currentMp();
            case "target_hp" -> target.currentHp();
            case "target_max_hp" -> target.stats().maxHp();
            case "target_missing_hp" -> target.stats().maxHp() - target.currentHp();
            case "target_mp" -> target.currentMp();
            case "target_max_mp" -> target.stats().maxMp();
            case "target_missing_mp" -> target.stats().maxMp() - target.currentMp();
            case "target_defense" -> target.stats().defense();
            case "target_magic_resist" -> target.stats().magicResist();
            case "target_speed" -> target.stats().speed();
            default -> 0.0;
        };
    }
}
