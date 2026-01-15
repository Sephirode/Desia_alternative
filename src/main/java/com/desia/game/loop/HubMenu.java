package com.desia.game.loop;

import java.util.List;

public record HubMenu(List<HubAction> actions) {
    public HubMenu {
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("actions must not be empty");
        }
        if (!actions.contains(HubAction.BACK)) {
            throw new IllegalArgumentException("actions must include BACK");
        }
    }

    public static HubMenu defaultMenu() {
        return new HubMenu(List.of(
                HubAction.PROCEED,
                HubAction.STATUS,
                HubAction.INVENTORY,
                HubAction.EQUIPMENT,
                HubAction.SAVE,
                HubAction.BACK
        ));
    }
}
