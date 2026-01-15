package com.desia.game.loop;

import java.util.List;

public record LoopResult(PlayerState player, List<MenuEvent> menuHistory, GameLoopLog log) {
}
