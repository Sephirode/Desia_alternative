package com.desia.game.loop;

public record MenuEvent(String screen, HubAction action, boolean backAvailable) {
}
