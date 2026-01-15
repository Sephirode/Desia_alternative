package com.desia.game.combat;

import com.desia.game.model.ConsumablesData;
import com.desia.game.model.SkillsData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class CombatEngine {
    private final CombatRules rules;
    private final SkillsData skillsData;
    private final ConsumablesData consumablesData;
    private final Random random;

    public CombatEngine(CombatRules rules, SkillsData skillsData, ConsumablesData consumablesData, long seed) {
        this.rules = rules;
        this.skillsData = skillsData;
        this.consumablesData = consumablesData;
        this.random = new Random(seed);
    }

    public CombatRoundResult runRound(List<CombatParticipant> participants, Map<String, CombatAction> actions) {
        List<Combatant> orderInput = new ArrayList<>();
        for (CombatParticipant participant : participants) {
            orderInput.add(new Combatant(participant.id(), 1, participant.stats(), participant.currentHp()));
        }
        List<Combatant> order = TurnOrder.sortByInitiative(orderInput);
        CombatLog log = new CombatLog();
        Map<String, CombatParticipant> lookup = indexParticipants(participants);

        log.info("Phase=STANDBY");
        for (Combatant combatant : order) {
            CombatParticipant actor = lookup.get(combatant.id());
            if (actor == null || !actor.isAlive()) {
                continue;
            }
            if (isActionBlocked(actor)) {
                log.info("Blocked=" + actor.id());
                continue;
            }
            CombatAction action = actions.getOrDefault(actor.id(), CombatAction.basicAttack(null));
            executeAction(actor, action, lookup, log);
            if (isBattleEnded(lookup)) {
                log.info("Phase=BATTLE_END");
                return new CombatRoundResult(new ArrayList<>(lookup.values()), log, true);
            }
        }

        log.info("Phase=END");
        for (CombatParticipant participant : lookup.values()) {
            participant.tickStatuses(log);
        }
        boolean ended = isBattleEnded(lookup);
        return new CombatRoundResult(new ArrayList<>(lookup.values()), log, ended);
    }

    private void executeAction(CombatParticipant actor, CombatAction action,
                               Map<String, CombatParticipant> participants, CombatLog log) {
        log.info("Action=" + actor.id() + ":" + action.type());
        if (action.type() == CombatActionType.ESCAPE) {
            log.info("Escape=" + actor.id());
            return;
        }
        CombatParticipant target = action.type() == CombatActionType.ITEM && action.targetId() == null
                ? actor
                : participants.get(action.targetId());
        if (target == null) {
            log.info("TargetMissing=" + actor.id());
            return;
        }
        switch (action.type()) {
            case BASIC_ATTACK -> applyDamageAction(actor, target, baseAttackDamage(actor), DamageType.PHYSICAL,
                    actor.element(), log);
            case SKILL -> applySkill(actor, target, action.skillId(), log);
            case ITEM -> applyItem(actor, target, action.itemId(), log);
            case ESCAPE -> log.info("Escape=" + actor.id());
        }
    }

    private void applySkill(CombatParticipant actor, CombatParticipant target, String skillId, CombatLog log) {
        if (skillId == null || skillsData == null || skillsData.skills() == null) {
            log.info("SkillMissing=" + actor.id());
            return;
        }
        SkillsData.Skill skill = skillsData.skills().get(skillId);
        if (skill == null) {
            log.info("SkillMissing=" + actor.id());
            return;
        }
        if (skill.mpCost() != null && actor.currentMp() < skill.mpCost()) {
            log.info("MpInsufficient=" + actor.id());
            return;
        }
        if (skill.mpCost() != null) {
            actor.spendMp(skill.mpCost());
        }
        if (skill.components() == null) {
            return;
        }
        for (SkillsData.Component component : skill.components()) {
            if (component == null || component.kind() == null) {
                continue;
            }
            switch (component.kind()) {
                case "damage" -> {
                    double value = SkillDamageCalculator.evaluateTerms(component, actor, target);
                    DamageType type = parseDamageType(component.damageType());
                    applyDamageAction(actor, target, value, type, skill.element(), log);
                }
                case "heal" -> {
                    double value = SkillDamageCalculator.evaluateTerms(component, actor, target);
                    actor.heal(value);
                    log.info("Heal=" + actor.id() + " " + value);
                }
                case "shield" -> {
                    double value = SkillDamageCalculator.evaluateTerms(component, actor, target);
                    actor.addShield(value);
                    log.info("Shield=" + actor.id() + " " + value);
                }
                default -> log.info("ComponentSkip=" + component.kind());
            }
        }
        if (skill.statusEffects() != null) {
            for (SkillsData.StatusEffect effect : skill.statusEffects()) {
                if (effect == null || effect.status() == null) {
                    continue;
                }
                double chance = effect.chance() == null ? 1.0 : effect.chance();
                double resist = target.resistanceMultiplier(effect.status());
                double roll = random.nextDouble();
                if (roll <= chance * resist) {
                    int duration = effect.durationTurns() == null ? 1 : effect.durationTurns();
                    target.addStatus(new StatusEffectInstance(effect.status(), duration, rules.dotPercent()));
                    log.info("Status=" + target.id() + ":" + effect.status());
                }
            }
        }
    }

    private void applyDamageAction(CombatParticipant actor, CombatParticipant target, double rawDamage,
                                   DamageType type, String element, CombatLog log) {
        AccuracyResult accuracy = CombatMath.accuracy(actor.stats().speed(), target.stats().speed(),
                rules.minHit(), rules.blindMultiplier(), target.hasStatus("blind"));
        if (!accuracy.guaranteedHit() && random.nextDouble() > accuracy.chance()) {
            log.info("Miss=" + actor.id());
            return;
        }
        double critMult = CombatMath.criticalMultiplier(actor.stats().critChance(), rules.baseCritChance(),
                rules.critMultiplier(), random.nextDouble());
        double critDamage = rawDamage * critMult;
        DamageResult result = DamageCalculator.applyDamage(critDamage, type, actor.stats(), target.stats(),
                rules.defenseModel(), rules.defenseK(), rules.defenseMinMult(),
                element, rules.elementMatrix(), target.element(), false);
        target.applyDamage(result.beforeMitigation(), result.mitigationMultiplier(), rules.shieldBypassDefense());
        log.info("Damage=" + target.id() + " " + result.amount());
    }

    private void applyItem(CombatParticipant actor, CombatParticipant target, String itemId, CombatLog log) {
        if (itemId == null || consumablesData == null || consumablesData.consumables() == null) {
            log.info("ItemMissing=" + actor.id());
            return;
        }
        ConsumablesData.Consumable item = consumablesData.consumables().get(itemId);
        if (item == null || item.effects() == null) {
            log.info("ItemMissing=" + actor.id());
            return;
        }
        for (ConsumablesData.Effect effect : item.effects()) {
            if (effect == null || effect.type() == null) {
                continue;
            }
            switch (effect.type()) {
                case "heal_hp" -> {
                    double heal = resolveFlatPercent(effect.flat(), effect.percentMaxHp(), target.stats().maxHp());
                    target.heal(heal);
                    log.info("ItemHealHp=" + target.id() + " " + heal);
                }
                case "heal_mp" -> {
                    double heal = resolveFlatPercent(effect.flat(), effect.percentMaxMp(), target.stats().maxMp());
                    target.spendMp(-heal);
                    log.info("ItemHealMp=" + target.id() + " " + heal);
                }
                case "add_shield" -> {
                    double shield = resolveFlatPercent(effect.flat(), effect.percentMaxHp(), target.stats().maxHp());
                    target.addShield(shield);
                    log.info("ItemShield=" + target.id() + " " + shield);
                }
                case "remove_status" -> {
                    if (effect.statuses() != null) {
                        for (String status : effect.statuses()) {
                            target.removeStatus(status);
                            log.info("ItemRemoveStatus=" + target.id() + ":" + status);
                        }
                    }
                }
                case "apply_status" -> {
                    if (effect.status() != null) {
                        double chance = effect.chance() == null ? 1.0 : effect.chance();
                        if (random.nextDouble() <= chance * target.resistanceMultiplier(effect.status())) {
                            int duration = effect.durationTurns() == null ? 1 : effect.durationTurns();
                            target.addStatus(new StatusEffectInstance(effect.status(), duration, rules.dotPercent()));
                            log.info("ItemStatus=" + target.id() + ":" + effect.status());
                        }
                    }
                }
                case "revive" -> {
                    if (!target.isAlive()) {
                        double percent = effect.percentMaxHp() == null ? 0.2 : effect.percentMaxHp();
                        target.heal(target.stats().maxHp() * percent);
                        log.info("ItemRevive=" + target.id());
                    }
                }
                default -> log.info("ItemEffectSkip=" + effect.type());
            }
        }
    }

    private boolean isBattleEnded(Map<String, CombatParticipant> participants) {
        return participants.values().stream().filter(CombatParticipant::isAlive).count() <= 1;
    }

    private boolean isActionBlocked(CombatParticipant actor) {
        return actor.hasStatus("stun") || actor.hasStatus("freeze") || actor.hasStatus("panic");
    }

    private Map<String, CombatParticipant> indexParticipants(List<CombatParticipant> participants) {
        Map<String, CombatParticipant> lookup = new HashMap<>();
        for (CombatParticipant participant : participants) {
            lookup.put(participant.id(), participant);
        }
        return lookup;
    }

    private double baseAttackDamage(CombatParticipant actor) {
        return actor.stats().attack();
    }

    private double resolveFlatPercent(Double flat, Double percent, double max) {
        double flatValue = flat == null ? 0 : flat;
        double percentValue = percent == null ? 0 : percent;
        return flatValue + max * percentValue;
    }

    private DamageType parseDamageType(String damageType) {
        if (damageType == null) {
            return DamageType.PHYSICAL;
        }
        return switch (damageType) {
            case "magic" -> DamageType.MAGIC;
            case "true" -> DamageType.TRUE;
            default -> DamageType.PHYSICAL;
        };
    }
}
