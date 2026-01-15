package com.desia.game.model;

import java.util.List;
import java.util.Map;

public record SpecialsData(int version, Map<String, Special> specials) {
    public record Special(String description, List<String> hooks, Object params) {
    }
}
