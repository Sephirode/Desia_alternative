package com.desia.game.loop;

import com.desia.game.combat.CombatEngine;
import com.desia.game.combat.CombatMenu;
import com.desia.game.combat.CombatMenuOption;
import com.desia.game.combat.CombatParticipant;
import com.desia.game.combat.CombatRules;
import com.desia.game.combat.CombatStats;
import com.desia.game.combat.CombatAction;
import com.desia.game.combat.DefenseModel;
import com.desia.game.combat.StatCalculator;
import com.desia.game.io.JsonLoader;
import com.desia.game.model.ConfigData;
import com.desia.game.model.ConsumablesData;
import com.desia.game.model.EnemiesData;
import com.desia.game.model.SkillsData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TextLoopRunner {
    public LoopResult runDemo(Path dataDir) throws IOException {
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
        ChapterPlan plan = ChapterGenerator.generate(42L);
        PlayerState player = new PlayerState("Hero", 1, 1, 0, true);

        GameLoopLog log = new GameLoopLog();
        List<MenuEvent> history = new java.util.ArrayList<>();
        PlayerState current = player;
        for (ChapterNode node : plan.nodes()) {
            if (node.type() == ChapterNodeType.BATTLE || node.type() == ChapterNodeType.BOSS) {
                runBattle(engine, enemies, log);
            }
            log.info("Progress=" + node.index() + "/12");
            log.info("Node=" + node.type());
            history.add(new MenuEvent("hub", HubAction.PROCEED, true));
            current = new PlayerState(current.name(), current.level(), current.chapter(), node.index(), true);
        }
        return new LoopResult(current, history, log);
    }

    private void runBattle(CombatEngine engine, EnemiesData enemies, GameLoopLog log) {
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
        CombatStats playerStats = new CombatStats(30, 10, 5, 3, 2, 1, 5, 0.05);
        CombatParticipant player = new CombatParticipant("player", "Hero", "neutral", playerStats,
                playerStats.maxHp(), playerStats.maxMp(), 0, Map.of());

        CombatMenu menu = CombatMenu.defaultMenu();
        String menuLabels = menu.options().stream()
                .map(CombatMenuOption::label)
                .reduce((left, right) -> left + "/" + right)
                .orElse("");
        log.info("BattleMenu=" + menuLabels);

        Map<String, CombatAction> actions = new HashMap<>();
        actions.put(player.id(), CombatAction.basicAttack(enemyParticipant.id()));
        actions.put(enemyParticipant.id(), CombatAction.basicAttack(player.id()));

        engine.runRound(List.of(player, enemyParticipant), actions);
        log.info("BattleResolved=" + enemy.id());
    }
}
