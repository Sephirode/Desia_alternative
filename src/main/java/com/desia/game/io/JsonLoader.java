package com.desia.game.io;

import com.desia.game.model.ConfigData;
import com.desia.game.model.ConsumablesData;
import com.desia.game.model.EnemiesData;
import com.desia.game.model.EquipmentData;
import com.desia.game.model.SpecialsData;
import com.desia.game.model.SkillsData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JsonLoader {
    private JsonLoader() {
    }

    public static Object load(Path path) throws IOException {
        String content = Files.readString(path);
        return SimpleJsonParser.parse(content);
    }

    @SuppressWarnings("unchecked")
    public static <T> T readValue(Path path, Class<T> type) throws IOException {
        Object root = load(path);
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException("Root JSON value must be an object.");
        }
        Map<String, Object> map = (Map<String, Object>) root;
        if (type.equals(ConfigData.class)) {
            return type.cast(mapConfig(map));
        }
        if (type.equals(SkillsData.class)) {
            return type.cast(mapSkills(map));
        }
        if (type.equals(EnemiesData.class)) {
            return type.cast(mapEnemies(map));
        }
        if (type.equals(ConsumablesData.class)) {
            return type.cast(mapConsumables(map));
        }
        if (type.equals(EquipmentData.class)) {
            return type.cast(mapEquipment(map));
        }
        if (type.equals(SpecialsData.class)) {
            return type.cast(mapSpecials(map));
        }
        throw new IllegalArgumentException("Unsupported model type: " + type.getName());
    }

    private static ConfigData mapConfig(Map<String, Object> root) {
        int version = asInt(root.get("version"));
        Map<String, Object> defense = asMap(root.get("defense_model"));
        Map<String, Object> crit = asMap(root.get("crit"));
        Map<String, Object> accuracy = asMap(root.get("accuracy"));
        Map<String, Object> shield = asMap(root.get("shield"));
        Map<String, Object> equipmentRules = asMap(root.get("equipment_rules"));
        Map<String, Object> elements = asMap(root.get("elements"));
        Map<String, Object> multiplier = asMap(elements.get("multiplier"));

        ConfigData.DefenseModel defenseModel = new ConfigData.DefenseModel(
                asString(defense.get("type")),
                asDouble(defense.get("k")),
                asDouble(defense.get("min_mult"))
        );
        ConfigData.Crit critData = new ConfigData.Crit(
                asDouble(crit.get("base_chance")),
                asDouble(crit.get("mult"))
        );
        ConfigData.Accuracy accuracyData = new ConfigData.Accuracy(
                asDouble(accuracy.get("min_hit")),
                asDouble(accuracy.get("blind_mult"))
        );
        ConfigData.Shield shieldData = new ConfigData.Shield(asBoolean(shield.get("bypass_defense")));
        ConfigData.EquipmentRules rules = new ConfigData.EquipmentRules(asInt(equipmentRules.get("ring_slots")));
        ConfigData.Elements elementsData = new ConfigData.Elements(asDoubleMatrix(multiplier));

        return new ConfigData(version, defenseModel, critData, accuracyData, shieldData, rules, elementsData);
    }

    private static SkillsData mapSkills(Map<String, Object> root) {
        int version = asInt(root.get("version"));
        Map<String, Object> skillsMap = asMap(root.get("skills"));
        Map<String, SkillsData.Skill> skills = new HashMap<>();
        for (Map.Entry<String, Object> entry : skillsMap.entrySet()) {
            Map<String, Object> skillMap = asMap(entry.getValue());
            List<SkillsData.Component> components = mapSkillComponents(skillMap);
            List<SkillsData.StatusEffect> effects = mapStatusEffects(skillMap);
            List<String> tags = asStringList(skillMap.get("special_tags"));
            SkillsData.Skill skill = new SkillsData.Skill(
                    asString(skillMap.get("name")),
                    asString(skillMap.get("description")),
                    asString(skillMap.get("element")),
                    asString(skillMap.get("category")),
                    asString(skillMap.get("target")),
                    asNullableInt(skillMap.get("mp_cost")),
                    components,
                    effects,
                    tags
            );
            skills.put(entry.getKey(), skill);
        }
        return new SkillsData(version, skills);
    }

    private static List<SkillsData.Component> mapSkillComponents(Map<String, Object> skillMap) {
        List<Object> list = asList(skillMap.get("components"));
        List<SkillsData.Component> components = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> componentMap = asMap(item);
            List<SkillsData.Term> terms = new ArrayList<>();
            List<Object> termList = asList(componentMap.get("terms"));
            if (termList != null) {
                for (Object termObj : termList) {
                    Map<String, Object> termMap = asMap(termObj);
                    terms.add(new SkillsData.Term(
                            asString(termMap.get("stat")),
                            asDouble(termMap.get("coef"))
                    ));
                }
            }
            components.add(new SkillsData.Component(
                    asString(componentMap.get("kind")),
                    asString(componentMap.get("damage_type")),
                    terms
            ));
        }
        return components;
    }

    private static List<SkillsData.StatusEffect> mapStatusEffects(Map<String, Object> skillMap) {
        List<Object> list = asList(skillMap.get("status_effects"));
        List<SkillsData.StatusEffect> effects = new ArrayList<>();
        if (list == null) {
            return effects;
        }
        for (Object item : list) {
            Map<String, Object> effectMap = asMap(item);
            effects.add(new SkillsData.StatusEffect(
                    asString(effectMap.get("status")),
                    asString(effectMap.get("target")),
                    asNullableDouble(effectMap.get("chance")),
                    asNullableInt(effectMap.get("duration_turns"))
            ));
        }
        return effects;
    }

    private static EnemiesData mapEnemies(Map<String, Object> root) {
        int version = asInt(root.get("version"));
        List<Object> list = asList(root.get("enemies"));
        List<EnemiesData.Enemy> enemies = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> enemyMap = asMap(item);
            EnemiesData.Stats baseStats = mapStats(asMap(enemyMap.get("base_stats")), true);
            EnemiesData.Stats growthStats = mapStats(asMap(enemyMap.get("growth_per_level")), false);
            EnemiesData.Rewards rewards = mapRewards(asMap(enemyMap.get("rewards")));
            EnemiesData.SkillAssignment assignment = mapSkillAssignment(asMap(enemyMap.get("skill_assignment")));
            Map<String, Object> resistanceMap = asMap(enemyMap.get("status_resistance"));
            Map<String, EnemiesData.StatusResistance> resistances = new HashMap<>();
            if (resistanceMap != null) {
                for (Map.Entry<String, Object> entry : resistanceMap.entrySet()) {
                    Map<String, Object> resistance = asMap(entry.getValue());
                    resistances.put(entry.getKey(), new EnemiesData.StatusResistance(
                            asNullableDouble(resistance.get("mult"))
                    ));
                }
            }
            enemies.add(new EnemiesData.Enemy(
                    asString(enemyMap.get("id")),
                    asString(enemyMap.get("name")),
                    asString(enemyMap.get("tier")),
                    asString(enemyMap.get("element")),
                    asNullableInt(enemyMap.get("base_level")),
                    baseStats,
                    growthStats,
                    rewards,
                    assignment,
                    resistances
            ));
        }
        return new EnemiesData(version, enemies);
    }

    private static EnemiesData.Stats mapStats(Map<String, Object> statsMap, boolean includeCrit) {
        if (statsMap == null) {
            return null;
        }
        Double critChance = includeCrit ? asNullableDouble(statsMap.get("crit_chance")) : null;
        return new EnemiesData.Stats(
                asNullableDouble(statsMap.get("max_hp")),
                asNullableDouble(statsMap.get("max_mp")),
                asNullableDouble(statsMap.get("attack")),
                asNullableDouble(statsMap.get("spell_power")),
                asNullableDouble(statsMap.get("defense")),
                asNullableDouble(statsMap.get("magic_resist")),
                asNullableDouble(statsMap.get("speed")),
                critChance
        );
    }

    private static EnemiesData.Rewards mapRewards(Map<String, Object> rewardsMap) {
        if (rewardsMap == null) {
            return null;
        }
        return new EnemiesData.Rewards(
                asNullableInt(rewardsMap.get("xp")),
                asNullableDouble(rewardsMap.get("gold_from_xp_ratio"))
        );
    }

    private static EnemiesData.SkillAssignment mapSkillAssignment(Map<String, Object> assignmentMap) {
        if (assignmentMap == null) {
            return null;
        }
        List<String> base = asStringList(assignmentMap.get("base"));
        List<EnemiesData.SkillLevelAdd> byLevel = new ArrayList<>();
        List<Object> entries = asList(assignmentMap.get("by_level"));
        if (entries != null) {
            for (Object entry : entries) {
                Map<String, Object> entryMap = asMap(entry);
                byLevel.add(new EnemiesData.SkillLevelAdd(
                        asNullableInt(entryMap.get("min_level")),
                        asStringList(entryMap.get("add"))
                ));
            }
        }
        return new EnemiesData.SkillAssignment(base, byLevel);
    }

    private static ConsumablesData mapConsumables(Map<String, Object> root) {
        int version = asInt(root.get("version"));
        Map<String, Object> itemsMap = asMap(root.get("consumables"));
        Map<String, ConsumablesData.Consumable> items = new HashMap<>();
        for (Map.Entry<String, Object> entry : itemsMap.entrySet()) {
            Map<String, Object> itemMap = asMap(entry.getValue());
            List<ConsumablesData.Effect> effects = mapConsumableEffects(itemMap);
            List<String> tags = asStringList(itemMap.get("special_tags"));
            items.put(entry.getKey(), new ConsumablesData.Consumable(
                    asString(itemMap.get("name")),
                    asString(itemMap.get("description")),
                    asString(itemMap.get("rarity")),
                    asNullableInt(itemMap.get("price")),
                    asNullableDouble(itemMap.get("sell_ratio")),
                    asNullableBoolean(itemMap.get("usable_in_battle")),
                    effects,
                    tags
            ));
        }
        return new ConsumablesData(version, items);
    }

    private static List<ConsumablesData.Effect> mapConsumableEffects(Map<String, Object> itemMap) {
        List<Object> effectsList = asList(itemMap.get("effects"));
        List<ConsumablesData.Effect> effects = new ArrayList<>();
        if (effectsList == null) {
            return effects;
        }
        for (Object effectObj : effectsList) {
            Map<String, Object> effectMap = asMap(effectObj);
            effects.add(new ConsumablesData.Effect(
                    asString(effectMap.get("type")),
                    asNullableDouble(effectMap.get("flat")),
                    asNullableDouble(effectMap.get("percent_max_hp")),
                    asNullableDouble(effectMap.get("percent_max_mp")),
                    asString(effectMap.get("status")),
                    asNullableDouble(effectMap.get("chance")),
                    asNullableInt(effectMap.get("duration_turns")),
                    asString(effectMap.get("target")),
                    asStringList(effectMap.get("statuses")),
                    mapModifiers(effectMap.get("modifiers")),
                    mapModifiers(effectMap.get("mods")),
                    asString(effectMap.get("skill_id")),
                    asNullableDouble(effectMap.get("mult"))
            ));
        }
        return effects;
    }

    private static List<ConsumablesData.Modifier> mapModifiers(Object value) {
        List<Object> list = asList(value);
        if (list == null) {
            return null;
        }
        List<ConsumablesData.Modifier> modifiers = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = asMap(item);
            modifiers.add(new ConsumablesData.Modifier(
                    asString(map.get("stat")),
                    asNullableDouble(map.get("flat")),
                    asNullableDouble(map.get("percent"))
            ));
        }
        return modifiers;
    }

    private static EquipmentData mapEquipment(Map<String, Object> root) {
        int version = asInt(root.get("version"));
        Map<String, Object> equipmentMap = asMap(root.get("equipment"));
        Map<String, EquipmentData.Equipment> equipment = new HashMap<>();
        for (Map.Entry<String, Object> entry : equipmentMap.entrySet()) {
            Map<String, Object> itemMap = asMap(entry.getValue());
            equipment.put(entry.getKey(), new EquipmentData.Equipment(
                    asString(itemMap.get("name")),
                    asString(itemMap.get("description")),
                    asString(itemMap.get("rarity")),
                    asString(itemMap.get("slot")),
                    asNullableInt(itemMap.get("price")),
                    asNullableDouble(itemMap.get("sell_ratio")),
                    asNullableBoolean(itemMap.get("unique")),
                    mapStatsBlock(asMap(itemMap.get("stats"))),
                    asStringList(itemMap.get("granted_skills")),
                    asString(itemMap.get("set_name")),
                    asStringList(itemMap.get("special_tags"))
            ));
        }
        Map<String, EquipmentData.EquipmentSet> sets = new HashMap<>();
        Map<String, Object> setsMap = asMap(root.get("sets"));
        if (setsMap != null) {
            for (Map.Entry<String, Object> entry : setsMap.entrySet()) {
                Map<String, Object> setMap = asMap(entry.getValue());
                List<EquipmentData.SetBonus> bonuses = new ArrayList<>();
                List<Object> bonusList = asList(setMap.get("bonuses"));
                if (bonusList != null) {
                    for (Object bonusObj : bonusList) {
                        Map<String, Object> bonusMap = asMap(bonusObj);
                        bonuses.add(new EquipmentData.SetBonus(
                                asNullableInt(bonusMap.get("pieces")),
                                mapStatsBlock(asMap(bonusMap.get("stats"))),
                                asStringList(bonusMap.get("special_tags"))
                        ));
                    }
                }
                sets.put(entry.getKey(), new EquipmentData.EquipmentSet(
                        asString(setMap.get("name")),
                        asStringList(setMap.get("pieces")),
                        bonuses
                ));
            }
        }
        return new EquipmentData(version, equipment, sets);
    }

    private static EquipmentData.StatsBlock mapStatsBlock(Map<String, Object> stats) {
        if (stats == null) {
            return null;
        }
        Map<String, Double> flat = asDoubleMap(asMap(stats.get("flat")));
        Map<String, Double> percent = asDoubleMap(asMap(stats.get("percent")));
        return new EquipmentData.StatsBlock(flat, percent);
    }

    private static SpecialsData mapSpecials(Map<String, Object> root) {
        int version = asInt(root.get("version"));
        Map<String, Object> specialsMap = asMap(root.get("specials"));
        Map<String, SpecialsData.Special> specials = new HashMap<>();
        for (Map.Entry<String, Object> entry : specialsMap.entrySet()) {
            Map<String, Object> specialMap = asMap(entry.getValue());
            specials.put(entry.getKey(), new SpecialsData.Special(
                    asString(specialMap.get("description")),
                    asStringList(specialMap.get("hooks")),
                    specialMap.get("params")
            ));
        }
        return new SpecialsData(version, specials);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        throw new IllegalArgumentException("Expected object but found " + value.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            return (List<Object>) value;
        }
        throw new IllegalArgumentException("Expected array but found " + value.getClass().getSimpleName());
    }

    private static Map<String, Map<String, Double>> asDoubleMatrix(Map<String, Object> value) {
        Map<String, Map<String, Double>> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            result.put(entry.getKey(), asDoubleMap(asMap(entry.getValue())));
        }
        return result;
    }

    private static Map<String, Double> asDoubleMap(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            result.put(entry.getKey(), asNullableDouble(entry.getValue()));
        }
        return result;
    }

    private static List<String> asStringList(Object value) {
        List<Object> list = asList(value);
        if (list == null) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            result.add(asString(item));
        }
        return result;
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }

    private static int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(asString(value));
    }

    private static Integer asNullableInt(Object value) {
        if (value == null) {
            return null;
        }
        return asInt(value);
    }

    private static double asDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(asString(value));
    }

    private static Double asNullableDouble(Object value) {
        if (value == null) {
            return null;
        }
        return asDouble(value);
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(asString(value));
    }

    private static Boolean asNullableBoolean(Object value) {
        if (value == null) {
            return null;
        }
        return asBoolean(value);
    }
}
