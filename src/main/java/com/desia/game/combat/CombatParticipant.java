package com.desia.game.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CombatParticipant {
    private final String id;
    private final String name;
    private final String element;
    private final CombatStats stats;
    private final Map<String, Double> statusResistance;
    private double currentHp;
    private double currentMp;
    private double currentShield;
    private final List<StatusEffectInstance> statuses = new ArrayList<>();

    public CombatParticipant(String id, String name, String element, CombatStats stats,
                             double currentHp, double currentMp, double currentShield,
                             Map<String, Double> statusResistance) {
        this.id = id;
        this.name = name;
        this.element = element;
        this.stats = stats;
        this.currentHp = currentHp;
        this.currentMp = currentMp;
        this.currentShield = currentShield;
        this.statusResistance = statusResistance;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String element() {
        return element;
    }

    public CombatStats stats() {
        return stats;
    }

    public double currentHp() {
        return currentHp;
    }

    public double currentMp() {
        return currentMp;
    }

    public double currentShield() {
        return currentShield;
    }

    public List<StatusEffectInstance> statuses() {
        return statuses;
    }

    public boolean isAlive() {
        return currentHp > 0;
    }

    public void spendMp(double amount) {
        currentMp = Math.max(0, Math.min(stats.maxMp(), currentMp - amount));
    }

    public void heal(double amount) {
        currentHp = Math.min(stats.maxHp(), currentHp + amount);
    }

    public void addShield(double amount) {
        currentShield = Math.max(0, currentShield + amount);
    }

    public void applyDamage(double beforeMitigation, double mitigation, boolean shieldBypassDefense) {
        if (currentHp <= 0) {
            return;
        }
        double shieldDamage = shieldBypassDefense ? beforeMitigation : beforeMitigation * mitigation;
        if (currentShield > 0) {
            double absorbed = Math.min(currentShield, shieldDamage);
            currentShield -= absorbed;
            beforeMitigation -= shieldBypassDefense ? absorbed : absorbed / mitigation;
        }
        double remainingDamage = beforeMitigation * mitigation;
        currentHp = Math.max(0, currentHp - remainingDamage);
    }

    public void addStatus(StatusEffectInstance status) {
        statuses.add(status);
    }

    public void removeStatus(String status) {
        statuses.removeIf(effect -> effect.status().equals(status));
    }

    public boolean hasStatus(String status) {
        return statuses.stream().anyMatch(effect -> effect.status().equals(status));
    }

    public double resistanceMultiplier(String status) {
        if (statusResistance == null) {
            return 1.0;
        }
        return statusResistance.getOrDefault(status, 1.0);
    }

    public void tickStatuses(CombatLog log) {
        if (statuses.isEmpty()) {
            return;
        }
        List<StatusEffectInstance> remaining = new ArrayList<>();
        for (StatusEffectInstance status : statuses) {
            if (isDot(status.status())) {
                double damage = stats.maxHp() * status.dotPercent();
                currentHp = Math.max(0, currentHp - damage);
                log.info("DOT=" + id + ":" + status.status() + " " + damage);
            }
            if (status.remainingTurns() > 1) {
                remaining.add(new StatusEffectInstance(status.status(), status.remainingTurns() - 1,
                        status.dotPercent()));
            } else {
                log.info("StatusEnd=" + id + ":" + status.status());
            }
        }
        statuses.clear();
        statuses.addAll(remaining);
    }

    private boolean isDot(String status) {
        return "burn".equals(status) || "poison".equals(status) || "bleed".equals(status);
    }
}
