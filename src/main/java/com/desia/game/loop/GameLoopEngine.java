package com.desia.game.loop;

import java.util.ArrayList;
import java.util.List;

public final class GameLoopEngine {
    public LoopResult runChapter(ChapterPlan plan, PlayerState player, HubMenu menu) {
        GameLoopLog log = new GameLoopLog();
        List<MenuEvent> history = new ArrayList<>();
        PlayerState current = player;
        HubMenu activeMenu = menu == null ? HubMenu.defaultMenu() : menu;

        log.info("ChapterStart=" + current.chapter());
        for (ChapterNode node : plan.nodes()) {
            NodeOutcome outcome = runNode(node, log, activeMenu, history);
            if (!outcome.continueChapter()) {
                log.info("ChapterEnd=" + node.index());
                break;
            }
            current = new PlayerState(current.name(), current.level(), current.chapter(), node.index(), current.alive());
            if (node.type() == ChapterNodeType.BOSS) {
                log.info("ChapterClear=" + current.chapter());
            }
        }
        return new LoopResult(current, history, log);
    }

    private NodeOutcome runNode(ChapterNode node, GameLoopLog log, HubMenu menu, List<MenuEvent> history) {
        log.info("Progress=" + node.index() + "/12");
        log.info("Node=" + node.type());
        logMenu(menu, history);
        return switch (node.type()) {
            case BATTLE -> handleBattle(node, log);
            case SHOP -> handleShop(node, log);
            case EVENT -> handleEvent(node, log);
            case BOSS -> handleBoss(node, log);
        };
    }

    private void logMenu(HubMenu menu, List<MenuEvent> history) {
        for (HubAction action : menu.actions()) {
            history.add(new MenuEvent("hub", action, menu.actions().contains(HubAction.BACK)));
        }
    }

    private NodeOutcome handleBattle(ChapterNode node, GameLoopLog log) {
        log.info("BattleStart=" + node.index());
        log.info("BattleEnd=" + node.index());
        return new NodeOutcome(node, true);
    }

    private NodeOutcome handleShop(ChapterNode node, GameLoopLog log) {
        log.info("ShopEnter=" + node.index());
        log.info("ShopExit=" + node.index());
        return new NodeOutcome(node, true);
    }

    private NodeOutcome handleEvent(ChapterNode node, GameLoopLog log) {
        log.info("EventEnter=" + node.index());
        log.info("EventExit=" + node.index());
        return new NodeOutcome(node, true);
    }

    private NodeOutcome handleBoss(ChapterNode node, GameLoopLog log) {
        log.info("BossStart=" + node.index());
        log.info("BossEnd=" + node.index());
        return new NodeOutcome(node, true);
    }
}
