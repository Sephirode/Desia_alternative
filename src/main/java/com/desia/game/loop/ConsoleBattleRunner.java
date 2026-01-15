package com.desia.game.loop;

import com.desia.game.combat.CombatAction;
import com.desia.game.combat.CombatEngine;
import com.desia.game.combat.CombatMenu;
import com.desia.game.combat.CombatParticipant;
import com.desia.game.combat.CombatRoundResult;
import com.desia.game.combat.CombatRules;
import com.desia.game.combat.CombatStats;
import com.desia.game.combat.DefenseModel;
import com.desia.game.combat.StatCalculator;
import com.desia.game.io.JsonLoader;
import com.desia.game.model.ConfigData;
import com.desia.game.model.ConsumablesData;
import com.desia.game.model.EnemiesData;
import com.desia.game.model.SkillsData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public final class ConsoleBattleRunner {
    public void run(Path dataDir) throws IOException {
        ConfigData config = JsonLoader.readValue(dataDir.resolve("config.json"), ConfigData.class);
        SkillsData skills = JsonLoader.readValue(dataDir.resolve("skills.json"), SkillsData.class);
        EnemiesData enemies = JsonLoader.readValue(dataDir.resolve("enemies.json"), EnemiesData.class);
        ConsumablesData consumables = JsonLoader.readValue(dataDir.resolve("consumables.json"), ConsumablesData.class);

        CombatRules rules = new CombatRules(
                DefenseModel.valueOf(config.defenseModel().type()),
                config.defenseModel().k(),
                config.defenseModel().minMult(),
                config.crit().baseChance(),
                config.crit().mult(),
                config.accuracy().minHit(),
                config.accuracy().blindMult(),
                config.shield().bypassDefense(),
                0.05,
                config.elements().multiplier()
        );

        CombatEngine engine = new CombatEngine(rules, skills, consumables, 42L);
        runBattle(engine, skills, consumables, enemies);
    }

    private void runBattle(CombatEngine engine, SkillsData skills, ConsumablesData consumables, EnemiesData enemies) {
        EnemiesData.Enemy enemy = enemies.enemies().get(0);
        CombatStats enemyStats = StatCalculator.computeEnemyStats(enemy, enemy.baseLevel());
        CombatParticipant enemyParticipant = new CombatParticipant(
                enemy.id(),
                enemy.name(),
                enemy.element(),
                enemyStats,
                enemyStats.maxHp(),
                enemyStats.maxMp(),
                0,
                Map.of()
        );
        CombatStats playerStats = new CombatStats(60, 20, 7, 5, 3, 2, 6, 0.08);
        CombatParticipant player = new CombatParticipant("player", "Hero", "neutral", playerStats,
                playerStats.maxHp(), playerStats.maxMp(), 0, Map.of());

        CombatMenu menu = CombatMenu.defaultMenu();
        Scanner scanner = new Scanner(System.in);
        boolean escaped = false;
        int turn = 1;

        System.out.println("전투 시작: " + player.name() + " vs " + enemyParticipant.name());
        while (player.isAlive() && enemyParticipant.isAlive() && !escaped) {
            System.out.println("----- 턴 " + turn + " -----");
            printStatus(player, enemyParticipant);

            CombatAction playerAction = promptAction(scanner, menu, player, enemyParticipant, skills, consumables);
            CombatAction enemyAction = CombatAction.basicAttack(player.id());

            CombatRoundResult result = engine.runRound(List.of(player, enemyParticipant),
                    Map.of(player.id(), playerAction, enemyParticipant.id(), enemyAction));
            result.log().entries().forEach(System.out::println);
            escaped = result.log().entries().stream().anyMatch(entry -> entry.contains("Escape=" + player.id()));
            turn += 1;
        }

        if (escaped) {
            System.out.println("도주 성공!");
        } else if (player.isAlive()) {
            System.out.println("승리!");
        } else {
            System.out.println("패배...");
        }
    }

    private void printStatus(CombatParticipant player, CombatParticipant enemy) {
        System.out.println(player.name() + " HP " + formatValue(player.currentHp(), player.stats().maxHp())
                + " / MP " + formatValue(player.currentMp(), player.stats().maxMp()));
        System.out.println(enemy.name() + " HP " + formatValue(enemy.currentHp(), enemy.stats().maxHp())
                + " / MP " + formatValue(enemy.currentMp(), enemy.stats().maxMp()));
    }

    private String formatValue(double current, double max) {
        return String.format("%.0f/%.0f", current, max);
    }

    private CombatAction promptAction(Scanner scanner, CombatMenu menu, CombatParticipant player,
                                      CombatParticipant enemy, SkillsData skills, ConsumablesData consumables) {
        System.out.println("행동을 선택하세요:");
        for (int i = 0; i < menu.options().size(); i++) {
            System.out.println((i + 1) + ". " + menu.options().get(i).label());
        }

        int choice = readIndex(scanner, menu.options().size());
        return switch (menu.options().get(choice).type()) {
            case BASIC_ATTACK -> CombatAction.basicAttack(enemy.id());
            case SKILL -> chooseSkill(scanner, player, enemy, skills);
            case ITEM -> chooseItem(scanner, player, enemy, consumables);
            case ESCAPE -> CombatAction.escape();
        };
    }

    private CombatAction chooseSkill(Scanner scanner, CombatParticipant player, CombatParticipant enemy,
                                     SkillsData skills) {
        if (skills == null || skills.skills() == null || skills.skills().isEmpty()) {
            System.out.println("사용 가능한 스킬이 없습니다. 공격으로 대체합니다.");
            return CombatAction.basicAttack(enemy.id());
        }

        List<String> skillIds = skills.skills().entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        System.out.println("스킬을 선택하세요:");
        for (int i = 0; i < skillIds.size(); i++) {
            SkillsData.Skill skill = skills.skills().get(skillIds.get(i));
            String name = skill == null ? skillIds.get(i) : skill.name();
            System.out.println((i + 1) + ". " + name);
        }
        int choice = readIndex(scanner, skillIds.size());
        String skillId = skillIds.get(choice);
        SkillsData.Skill skill = skills.skills().get(skillId);
        String target = skill == null ? "enemy" : skill.target();
        String targetId = resolveSkillTarget(target, player.id(), enemy.id());
        return CombatAction.skill(skillId, targetId);
    }

    private CombatAction chooseItem(Scanner scanner, CombatParticipant player, CombatParticipant enemy,
                                    ConsumablesData consumables) {
        if (consumables == null || consumables.consumables() == null || consumables.consumables().isEmpty()) {
            System.out.println("사용 가능한 아이템이 없습니다. 공격으로 대체합니다.");
            return CombatAction.basicAttack(enemy.id());
        }
        List<Map.Entry<String, ConsumablesData.Consumable>> usable = new ArrayList<>();
        for (Map.Entry<String, ConsumablesData.Consumable> entry : consumables.consumables().entrySet()) {
            ConsumablesData.Consumable item = entry.getValue();
            if (item != null && Boolean.TRUE.equals(item.usableInBattle())) {
                usable.add(entry);
            }
        }
        if (usable.isEmpty()) {
            System.out.println("전투에서 사용할 아이템이 없습니다. 공격으로 대체합니다.");
            return CombatAction.basicAttack(enemy.id());
        }

        System.out.println("아이템을 선택하세요:");
        for (int i = 0; i < usable.size(); i++) {
            ConsumablesData.Consumable item = usable.get(i).getValue();
            String name = item == null ? usable.get(i).getKey() : item.name();
            System.out.println((i + 1) + ". " + name);
        }
        int choice = readIndex(scanner, usable.size());
        String itemId = usable.get(choice).getKey();
        return CombatAction.item(itemId, player.id());
    }

    private int readIndex(Scanner scanner, int size) {
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine();
            try {
                int value = Integer.parseInt(line.trim());
                if (value >= 1 && value <= size) {
                    return value - 1;
                }
            } catch (NumberFormatException ignored) {
                // 다시 입력
            }
            System.out.println("잘못된 입력입니다. 다시 선택하세요.");
        }
    }

    private String resolveSkillTarget(String target, String playerId, String enemyId) {
        if (target == null) {
            return enemyId;
        }
        return switch (target) {
            case "self", "ally", "all_allies", "random_ally" -> playerId;
            default -> enemyId;
        };
    }
}
