package com.desia.game.validation;

import com.desia.game.io.JsonLoader;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DataValidator {
    private DataValidator() {
    }

    public static ValidationResult validateAll(Path dataDir) {
        List<ValidationError> errors = new ArrayList<>();

        ConfigData config = loadModel(dataDir.resolve("config.json"), ConfigData.class, errors);
        SkillsData skillsData = loadModel(dataDir.resolve("skills.json"), SkillsData.class, errors);
        EnemiesData enemiesData = loadModel(dataDir.resolve("enemies.json"), EnemiesData.class, errors);
        ConsumablesData consumablesData = loadModel(dataDir.resolve("consumables.json"), ConsumablesData.class, errors);
        EquipmentData equipmentData = loadModel(dataDir.resolve("equipment.json"), EquipmentData.class, errors);
        SpecialsData specialsData = loadModel(dataDir.resolve("specials.json"), SpecialsData.class, errors);

        Set<String> skillIds = extractSkillIds(skillsData);
        Set<String> specialIds = extractSpecialIds(specialsData);
        Set<String> equipmentIds = extractEquipmentIds(equipmentData);
        Set<String> setIds = extractSetIds(equipmentData);

        validateConfig(dataDir.resolve("config.json"), config, errors);
        validateSkills(dataDir.resolve("skills.json"), skillsData, specialIds, errors);
        validateEnemies(dataDir.resolve("enemies.json"), enemiesData, skillIds, errors);
        validateConsumables(dataDir.resolve("consumables.json"), consumablesData, skillIds, specialIds, errors);
        validateEquipment(dataDir.resolve("equipment.json"), equipmentData, skillIds, specialIds, equipmentIds, setIds,
                errors);
        validateSpecials(dataDir.resolve("specials.json"), specialsData, errors);

        return new ValidationResult(errors);
    }

    private static void validateConfig(Path path, ConfigData config, List<ValidationError> errors) {
        if (config == null) {
            return;
        }

        if (config.version() <= 0) {
            addError(errors, Codes.MISSING_FIELD, path, "version", "version must be >= 1");
        }
        if (config.defenseModel() == null) {
            addError(errors, Codes.MISSING_FIELD, path, "defense_model", "missing defense_model");
        } else {
            String type = config.defenseModel().type();
            if (!DEFENSE_MODELS.contains(type)) {
                addError(errors, Codes.INVALID_ENUM, path, "defense_model.type",
                        "invalid defense_model.type '" + type + "'");
            }
            if (config.defenseModel().k() <= 0) {
                addError(errors, Codes.MISSING_FIELD, path, "defense_model.k", "defense_model.k must be > 0");
            }
            if (config.defenseModel().minMult() <= 0 || config.defenseModel().minMult() > 1) {
                addError(errors, Codes.MISSING_FIELD, path, "defense_model.min_mult",
                        "defense_model.min_mult must be in (0, 1]");
            }
        }
        if (config.crit() == null) {
            addError(errors, Codes.MISSING_FIELD, path, "crit", "missing crit");
        } else {
            validateChance(path, "crit.base_chance", config.crit().baseChance(), errors);
            if (config.crit().mult() <= 0) {
                addError(errors, Codes.MISSING_FIELD, path, "crit.mult", "crit.mult must be > 0");
            }
        }
        if (config.accuracy() == null) {
            addError(errors, Codes.MISSING_FIELD, path, "accuracy", "missing accuracy");
        } else {
            validateChance(path, "accuracy.min_hit", config.accuracy().minHit(), errors);
            if (config.accuracy().blindMult() <= 0 || config.accuracy().blindMult() > 1) {
                addError(errors, Codes.MISSING_FIELD, path, "accuracy.blind_mult",
                        "accuracy.blind_mult must be in (0, 1]");
            }
        }
        if (config.shield() == null) {
            addError(errors, Codes.MISSING_FIELD, path, "shield", "missing shield");
        }
        if (config.equipmentRules() == null) {
            addError(errors, Codes.MISSING_FIELD, path, "equipment_rules", "missing equipment_rules");
        } else {
            int ringSlots = config.equipmentRules().ringSlots();
            if (!RING_SLOTS.contains(ringSlots)) {
                addError(errors, Codes.INVALID_ENUM, path, "equipment_rules.ring_slots",
                        "equipment_rules.ring_slots must be 2 or 4");
            }
        }
        if (config.elements() == null || config.elements().multiplier() == null) {
            addError(errors, Codes.MISSING_FIELD, path, "elements.multiplier", "missing elements.multiplier");
        } else {
            validateElementMatrix(path, config.elements().multiplier(), errors);
        }
    }

    private static void validateSkills(Path path, SkillsData skillsData, Set<String> specialIds,
                                       List<ValidationError> errors) {
        if (skillsData == null) {
            return;
        }

        if (skillsData.version() <= 0) {
            addError(errors, Codes.MISSING_FIELD, path, "version", "version must be >= 1");
        }
        if (skillsData.skills() == null || skillsData.skills().isEmpty()) {
            addError(errors, Codes.MISSING_FIELD, path, "skills", "skills must not be empty");
            return;
        }

        Set<String> ids = new HashSet<>();
        Iterator<String> fieldNames = skillsData.skills().keySet().iterator();
        while (fieldNames.hasNext()) {
            String id = fieldNames.next();
            SkillsData.Skill skill = skillsData.skills().get(id);
            if (!ids.add(id)) {
                addError(errors, Codes.DUPLICATE_ID, path, "skills", "duplicate skill id " + id);
            }
            if (id == null || id.isBlank()) {
                addError(errors, Codes.MISSING_FIELD, path, "skills", "skill id must not be blank");
            }
            if (skill == null) {
                addError(errors, Codes.MISSING_FIELD, path, "skills." + id, "skill is null");
                continue;
            }
            if (isBlank(skill.name())) {
                addError(errors, Codes.MISSING_FIELD, path, "skills." + id + ".name", "missing name");
            }
            if (skill.mpCost() != null && skill.mpCost() < 0) {
                addError(errors, Codes.MISSING_FIELD, path, "skills." + id + ".mp_cost", "mp_cost must be >= 0");
            }
            validateEnum(path, "skills." + id + ".element", skill.element(), ELEMENTS, errors);
            validateEnum(path, "skills." + id + ".category", skill.category(), SKILL_CATEGORIES, errors);
            validateEnum(path, "skills." + id + ".target", skill.target(), TARGETS, errors);
            validateComponents(path, id, skill.category(), skill.components(), errors);
            validateStatusEffects(path, id, skill.statusEffects(), errors);
            validateSpecialTags(path, "skills." + id + ".special_tags", skill.specialTags(), specialIds, errors);
        }
    }

    private static void validateEnemies(Path path, EnemiesData enemiesData, Set<String> skillIds,
                                        List<ValidationError> errors) {
        if (enemiesData == null) {
            return;
        }

        if (enemiesData.version() <= 0) {
            addError(errors, Codes.MISSING_FIELD, path, "version", "version must be >= 1");
        }
        if (enemiesData.enemies() == null || enemiesData.enemies().isEmpty()) {
            addError(errors, Codes.MISSING_FIELD, path, "enemies", "enemies must not be empty");
            return;
        }

        Set<String> ids = new HashSet<>();
        for (EnemiesData.Enemy enemy : enemiesData.enemies()) {
            if (enemy == null) {
                addError(errors, Codes.MISSING_FIELD, path, "enemies", "enemy entry is null");
                continue;
            }
            String id = enemy.id();
            if (isBlank(id)) {
                addError(errors, Codes.MISSING_FIELD, path, "enemies.id", "enemy id must not be blank");
            } else if (!ids.add(id)) {
                addError(errors, Codes.DUPLICATE_ID, path, "enemies", "duplicate enemy id " + id);
            }
            if (isBlank(enemy.name())) {
                addError(errors, Codes.MISSING_FIELD, path, "enemies." + id + ".name", "missing name");
            }
            validateEnum(path, "enemies." + id + ".tier", enemy.tier(), TIERS, errors);
            validateEnum(path, "enemies." + id + ".element", enemy.element(), ELEMENTS, errors);
            if (enemy.baseLevel() == null || enemy.baseLevel() < 1) {
                addError(errors, Codes.MISSING_FIELD, path, "enemies." + id + ".base_level",
                        "base_level must be >= 1");
            }
            validateBaseStats(path, id, enemy.baseStats(), errors);
            validateGrowthStats(path, id, enemy.growthPerLevel(), errors);
            validateRewards(path, id, enemy.rewards(), errors);
            validateSkillAssignment(path, id, enemy.skillAssignment(), skillIds, errors);
            validateStatusResistance(path, id, enemy.statusResistance(), errors);
        }
    }

    private static void validateConsumables(Path path, ConsumablesData consumablesData, Set<String> skillIds,
                                            Set<String> specialIds, List<ValidationError> errors) {
        if (consumablesData == null) {
            return;
        }

        if (consumablesData.version() <= 0) {
            addError(errors, Codes.MISSING_FIELD, path, "version", "version must be >= 1");
        }
        if (consumablesData.consumables() == null || consumablesData.consumables().isEmpty()) {
            addError(errors, Codes.MISSING_FIELD, path, "consumables", "consumables must not be empty");
            return;
        }

        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, ConsumablesData.Consumable> entry : consumablesData.consumables().entrySet()) {
            String id = entry.getKey();
            if (isBlank(id)) {
                addError(errors, Codes.MISSING_FIELD, path, "consumables", "consumable id must not be blank");
                continue;
            }
            if (!ids.add(id)) {
                addError(errors, Codes.DUPLICATE_ID, path, "consumables", "duplicate consumable id " + id);
            }
            ConsumablesData.Consumable consumable = entry.getValue();
            if (consumable == null) {
                addError(errors, Codes.MISSING_FIELD, path, "consumables." + id, "consumable is null");
                continue;
            }
            if (isBlank(consumable.name())) {
                addError(errors, Codes.MISSING_FIELD, path, "consumables." + id + ".name", "missing name");
            }
            if (consumable.price() != null && consumable.price() < 0) {
                addError(errors, Codes.MISSING_FIELD, path, "consumables." + id + ".price", "price must be >= 0");
            }
            if (consumable.sellRatio() != null
                    && (consumable.sellRatio() < 0 || consumable.sellRatio() > 1)) {
                addError(errors, Codes.MISSING_FIELD, path, "consumables." + id + ".sell_ratio",
                        "sell_ratio must be in [0, 1]");
            }
            validateEffects(path, id, consumable.effects(), skillIds, errors);
            validateSpecialTags(path, "consumables." + id + ".special_tags", consumable.specialTags(), specialIds,
                    errors);
        }
    }

    private static void validateEquipment(Path path, EquipmentData equipmentData, Set<String> skillIds,
                                          Set<String> specialIds, Set<String> equipmentIds, Set<String> setIds,
                                          List<ValidationError> errors) {
        if (equipmentData == null) {
            return;
        }

        if (equipmentData.version() <= 0) {
            addError(errors, Codes.MISSING_FIELD, path, "version", "version must be >= 1");
        }
        if (equipmentData.equipment() == null || equipmentData.equipment().isEmpty()) {
            addError(errors, Codes.MISSING_FIELD, path, "equipment", "equipment must not be empty");
        } else {
            Set<String> ids = new HashSet<>();
            for (Map.Entry<String, EquipmentData.Equipment> entry : equipmentData.equipment().entrySet()) {
                String id = entry.getKey();
                if (isBlank(id)) {
                    addError(errors, Codes.MISSING_FIELD, path, "equipment", "equipment id must not be blank");
                    continue;
                }
                if (!ids.add(id)) {
                    addError(errors, Codes.DUPLICATE_ID, path, "equipment", "duplicate equipment id " + id);
                }
                EquipmentData.Equipment equipment = entry.getValue();
                if (equipment == null) {
                    addError(errors, Codes.MISSING_FIELD, path, "equipment." + id, "equipment is null");
                    continue;
                }
                if (isBlank(equipment.name())) {
                    addError(errors, Codes.MISSING_FIELD, path, "equipment." + id + ".name", "missing name");
                }
                validateSlot(path, "equipment." + id + ".slot", equipment.slot(), errors);
                if (equipment.price() != null && equipment.price() < 0) {
                    addError(errors, Codes.MISSING_FIELD, path, "equipment." + id + ".price", "price must be >= 0");
                }
                if (equipment.sellRatio() != null && (equipment.sellRatio() < 0 || equipment.sellRatio() > 1)) {
                    addError(errors, Codes.MISSING_FIELD, path, "equipment." + id + ".sell_ratio",
                            "sell_ratio must be in [0, 1]");
                }
                if (equipment.stats() == null) {
                    addError(errors, Codes.MISSING_FIELD, path, "equipment." + id + ".stats", "missing stats");
                } else {
                    validateStatsBlock(path, "equipment." + id + ".stats", equipment.stats(), errors);
                }
                if (equipment.setName() != null && !equipment.setName().isBlank()
                        && !setIds.contains(equipment.setName())) {
                    addError(errors, Codes.SET_NOT_FOUND, path, "equipment." + id + ".set_name",
                            "set_name not found in sets");
                }
                validateSkillRefs(path, "equipment." + id + ".granted_skills", equipment.grantedSkills(), skillIds,
                        Codes.EQUIPMENT_SKILL_REF, errors);
                validateSpecialTags(path, "equipment." + id + ".special_tags", equipment.specialTags(), specialIds,
                        errors);
            }
        }

        if (equipmentData.sets() != null) {
            for (Map.Entry<String, EquipmentData.EquipmentSet> entry : equipmentData.sets().entrySet()) {
                String id = entry.getKey();
                if (isBlank(id)) {
                    addError(errors, Codes.MISSING_FIELD, path, "sets", "set id must not be blank");
                    continue;
                }
                EquipmentData.EquipmentSet set = entry.getValue();
                if (set == null) {
                    addError(errors, Codes.MISSING_FIELD, path, "sets." + id, "set is null");
                    continue;
                }
                if (isBlank(set.name())) {
                    addError(errors, Codes.MISSING_FIELD, path, "sets." + id + ".name", "missing name");
                }
                if (set.pieces() == null || set.pieces().isEmpty()) {
                    addError(errors, Codes.MISSING_FIELD, path, "sets." + id + ".pieces", "pieces must not be empty");
                } else {
                    for (String piece : set.pieces()) {
                        if (!equipmentIds.contains(piece)) {
                            addError(errors, Codes.SET_PIECE_REF, path, "sets." + id + ".pieces",
                                    "piece not found: " + piece);
                        }
                    }
                }
                if (set.bonuses() != null) {
                    for (EquipmentData.SetBonus bonus : set.bonuses()) {
                        if (bonus == null) {
                            addError(errors, Codes.MISSING_FIELD, path, "sets." + id + ".bonuses", "bonus is null");
                            continue;
                        }
                        if (bonus.pieces() == null || bonus.pieces() < 1
                                || (set.pieces() != null && bonus.pieces() > set.pieces().size())) {
                            addError(errors, Codes.SET_BONUS_RANGE, path, "sets." + id + ".bonuses",
                                    "bonus pieces out of range");
                        }
                        if (bonus.stats() == null) {
                            addError(errors, Codes.MISSING_FIELD, path, "sets." + id + ".bonuses.stats",
                                    "bonus missing stats");
                        } else {
                            validateStatsBlock(path, "sets." + id + ".bonuses.stats", bonus.stats(), errors);
                        }
                        validateSpecialTags(path, "sets." + id + ".bonuses.special_tags", bonus.specialTags(),
                                specialIds, errors);
                    }
                }
            }
        }
    }

    private static void validateSpecials(Path path, SpecialsData specialsData, List<ValidationError> errors) {
        if (specialsData == null) {
            return;
        }
        if (specialsData.version() <= 0) {
            addError(errors, Codes.MISSING_FIELD, path, "version", "version must be >= 1");
        }
        if (specialsData.specials() == null) {
            addError(errors, Codes.MISSING_FIELD, path, "specials", "specials missing");
            return;
        }
        for (Map.Entry<String, SpecialsData.Special> entry : specialsData.specials().entrySet()) {
            String id = entry.getKey();
            SpecialsData.Special special = entry.getValue();
            if (isBlank(id)) {
                addError(errors, Codes.MISSING_FIELD, path, "specials", "special id must not be blank");
                continue;
            }
            if (special == null) {
                addError(errors, Codes.MISSING_FIELD, path, "specials." + id, "special is null");
                continue;
            }
            if (isBlank(special.description())) {
                addError(errors, Codes.MISSING_FIELD, path, "specials." + id + ".description", "missing description");
            }
            if (special.hooks() == null || special.hooks().isEmpty()) {
                addError(errors, Codes.MISSING_FIELD, path, "specials." + id + ".hooks", "hooks must not be empty");
            } else {
                for (String hook : special.hooks()) {
                    validateEnum(path, "specials." + id + ".hooks", hook, SPECIAL_HOOKS, errors);
                }
            }
        }
    }

    private static void validateComponents(Path path, String id, String category,
                                           List<SkillsData.Component> components,
                                           List<ValidationError> errors) {
        if (components == null || components.isEmpty()) {
            if (category != null && COMPONENT_REQUIRED_CATEGORIES.contains(category)) {
                addError(errors, Codes.EMPTY_COMPONENTS, path, "skills." + id + ".components",
                        "components must not be empty");
            }
            return;
        }
        for (SkillsData.Component component : components) {
            if (component == null) {
                addError(errors, Codes.MISSING_FIELD, path, "skills." + id + ".components", "component is null");
                continue;
            }
            if (isBlank(component.kind())) {
                addError(errors, Codes.MISSING_FIELD, path, "skills." + id + ".components.kind",
                        "component missing kind");
                continue;
            }
            if (!COMPONENT_KINDS.contains(component.kind())) {
                addError(errors, Codes.UNSUPPORTED_COMPONENT_KIND, path, "skills." + id + ".components.kind",
                        "component kind unsupported");
                continue;
            }
            if ("damage".equals(component.kind())) {
                validateDamageType(path, "skills." + id + ".components.damage_type", component.damageType(), errors);
                if (component.terms() == null || component.terms().isEmpty()) {
                    addError(errors, Codes.MISSING_FIELD, path, "skills." + id + ".components.terms",
                            "damage terms must not be empty");
                    continue;
                }
                for (SkillsData.Term term : component.terms()) {
                    if (term == null) {
                        addError(errors, Codes.MISSING_FIELD, path, "skills." + id + ".components.terms",
                                "term is null");
                        continue;
                    }
                    validateTermStat(path, "skills." + id + ".components.terms.stat", term.stat(), errors);
                }
            }
        }
    }

    private static void validateStatusEffects(Path path, String id, List<SkillsData.StatusEffect> effects,
                                              List<ValidationError> errors) {
        if (effects == null) {
            return;
        }
        for (SkillsData.StatusEffect effect : effects) {
            if (effect == null) {
                addError(errors, Codes.MISSING_FIELD, path, "skills." + id + ".status_effects",
                        "status_effect is null");
                continue;
            }
            if (isBlank(effect.status())) {
                addError(errors, Codes.MISSING_FIELD, path, "skills." + id + ".status_effects.status",
                        "status_effect missing status");
            }
            validateEnum(path, "skills." + id + ".status_effects.target", effect.target(), TARGETS, errors);
            if (effect.chance() != null) {
                validateChance(path, "skills." + id + ".status_effects.chance", effect.chance(), errors);
            }
            if (effect.durationTurns() != null && effect.durationTurns() < 1) {
                addError(errors, Codes.DURATION_OUT_OF_RANGE, path, "skills." + id + ".status_effects.duration_turns",
                        "duration_turns must be >= 1");
            }
        }
    }

    private static void validateBaseStats(Path path, String id, EnemiesData.Stats stats, List<ValidationError> errors) {
        if (stats == null) {
            addError(errors, Codes.MISSING_FIELD, path, "enemies." + id + ".base_stats", "missing base_stats");
            return;
        }
        requireStat(path, id, "base_stats.max_hp", stats.maxHp(), errors);
        if (stats.maxHp() != null && stats.maxHp() <= 0) {
            addError(errors, Codes.BASE_HP_INVALID, path, "enemies." + id + ".base_stats.max_hp",
                    "base_stats.max_hp must be > 0");
        }
        requireStat(path, id, "base_stats.max_mp", stats.maxMp(), errors);
        requireStat(path, id, "base_stats.attack", stats.attack(), errors);
        requireStat(path, id, "base_stats.spell_power", stats.spellPower(), errors);
        requireStat(path, id, "base_stats.defense", stats.defense(), errors);
        requireStat(path, id, "base_stats.magic_resist", stats.magicResist(), errors);
        requireStat(path, id, "base_stats.speed", stats.speed(), errors);
        requireStat(path, id, "base_stats.crit_chance", stats.critChance(), errors);
        validateNonNegative(path, id, "base_stats.max_mp", stats.maxMp(), errors);
        validateNonNegative(path, id, "base_stats.attack", stats.attack(), errors);
        validateNonNegative(path, id, "base_stats.spell_power", stats.spellPower(), errors);
        validateNonNegative(path, id, "base_stats.defense", stats.defense(), errors);
        validateNonNegative(path, id, "base_stats.magic_resist", stats.magicResist(), errors);
        validateNonNegative(path, id, "base_stats.speed", stats.speed(), errors);
        validateNonNegative(path, id, "base_stats.crit_chance", stats.critChance(), errors);
    }

    private static void validateGrowthStats(Path path, String id, EnemiesData.Stats stats, List<ValidationError> errors) {
        if (stats == null) {
            addError(errors, Codes.MISSING_FIELD, path, "enemies." + id + ".growth_per_level",
                    "missing growth_per_level");
            return;
        }
        requireStat(path, id, "growth_per_level.max_hp", stats.maxHp(), errors);
        requireStat(path, id, "growth_per_level.max_mp", stats.maxMp(), errors);
        requireStat(path, id, "growth_per_level.attack", stats.attack(), errors);
        requireStat(path, id, "growth_per_level.spell_power", stats.spellPower(), errors);
        requireStat(path, id, "growth_per_level.defense", stats.defense(), errors);
        requireStat(path, id, "growth_per_level.magic_resist", stats.magicResist(), errors);
        requireStat(path, id, "growth_per_level.speed", stats.speed(), errors);
        validateNonNegative(path, id, "growth_per_level.max_hp", stats.maxHp(), errors);
        validateNonNegative(path, id, "growth_per_level.max_mp", stats.maxMp(), errors);
        validateNonNegative(path, id, "growth_per_level.attack", stats.attack(), errors);
        validateNonNegative(path, id, "growth_per_level.spell_power", stats.spellPower(), errors);
        validateNonNegative(path, id, "growth_per_level.defense", stats.defense(), errors);
        validateNonNegative(path, id, "growth_per_level.magic_resist", stats.magicResist(), errors);
        validateNonNegative(path, id, "growth_per_level.speed", stats.speed(), errors);
    }

    private static void validateRewards(Path path, String id, EnemiesData.Rewards rewards, List<ValidationError> errors) {
        if (rewards == null) {
            addError(errors, Codes.MISSING_FIELD, path, "enemies." + id + ".rewards", "missing rewards");
            return;
        }
        if (rewards.xp() == null || rewards.xp() < 0) {
            addError(errors, Codes.MISSING_FIELD, path, "enemies." + id + ".rewards.xp", "xp must be >= 0");
        }
        if (rewards.goldFromXpRatio() == null || rewards.goldFromXpRatio() < 0
                || rewards.goldFromXpRatio() > 1) {
            addError(errors, Codes.MISSING_FIELD, path, "enemies." + id + ".rewards.gold_from_xp_ratio",
                    "gold_from_xp_ratio must be in [0, 1]");
        }
    }

    private static void validateSkillAssignment(Path path, String id, EnemiesData.SkillAssignment assignment,
                                                Set<String> skillIds, List<ValidationError> errors) {
        if (assignment == null) {
            addError(errors, Codes.MISSING_FIELD, path, "enemies." + id + ".skill_assignment",
                    "missing skill_assignment");
            return;
        }
        validateSkillRefs(path, "enemies." + id + ".skill_assignment.base", assignment.base(), skillIds,
                Codes.ENEMY_SKILL_REF, errors);
        if (assignment.byLevel() != null) {
            for (EnemiesData.SkillLevelAdd levelAdd : assignment.byLevel()) {
                if (levelAdd == null) {
                    addError(errors, Codes.MISSING_FIELD, path, "enemies." + id + ".skill_assignment.by_level",
                            "by_level entry is null");
                    continue;
                }
                if (levelAdd.minLevel() == null || levelAdd.minLevel() < 1) {
                    addError(errors, Codes.MISSING_FIELD, path, "enemies." + id + ".skill_assignment.by_level.min_level",
                            "min_level must be >= 1");
                }
                validateSkillRefs(path, "enemies." + id + ".skill_assignment.by_level", levelAdd.add(), skillIds,
                        Codes.ENEMY_SKILL_REF, errors);
            }
        }
    }

    private static void validateStatusResistance(Path path, String id, Map<String, EnemiesData.StatusResistance> map,
                                                 List<ValidationError> errors) {
        if (map == null) {
            return;
        }
        for (Map.Entry<String, EnemiesData.StatusResistance> entry : map.entrySet()) {
            String status = entry.getKey();
            EnemiesData.StatusResistance resistance = entry.getValue();
            if (isBlank(status)) {
                addError(errors, Codes.MISSING_FIELD, path, "enemies." + id + ".status_resistance",
                        "status_resistance has blank status");
                continue;
            }
            if (resistance == null || resistance.mult() == null) {
                addError(errors, Codes.MISSING_FIELD, path, "enemies." + id + ".status_resistance." + status,
                        "missing mult");
                continue;
            }
            if (resistance.mult() < 0) {
                addError(errors, Codes.GROWTH_NEGATIVE, path, "enemies." + id + ".status_resistance." + status,
                        "mult must be >= 0");
            }
        }
    }

    private static void validateEffects(Path path, String id, List<ConsumablesData.Effect> effects,
                                        Set<String> skillIds, List<ValidationError> errors) {
        if (effects == null || effects.isEmpty()) {
            addError(errors, Codes.MISSING_FIELD, path, "consumables." + id + ".effects",
                    "effects must not be empty");
            return;
        }
        for (ConsumablesData.Effect effect : effects) {
            if (effect == null) {
                addError(errors, Codes.MISSING_FIELD, path, "consumables." + id + ".effects", "effect is null");
                continue;
            }
            if (isBlank(effect.type())) {
                addError(errors, Codes.MISSING_FIELD, path, "consumables." + id + ".effects.type",
                        "effect missing type");
                continue;
            }
            if (!EFFECT_TYPES.contains(effect.type())) {
                addError(errors, Codes.INVALID_ENUM, path, "consumables." + id + ".effects.type",
                        "effect type unsupported");
            }
            if ("apply_status".equals(effect.type())) {
                if (isBlank(effect.status())) {
                    addError(errors, Codes.MISSING_FIELD, path, "consumables." + id + ".effects.status",
                            "apply_status missing status");
                }
                validateEnum(path, "consumables." + id + ".effects.target", effect.target(), TARGETS, errors);
                if (effect.chance() != null) {
                    validateChance(path, "consumables." + id + ".effects.chance", effect.chance(), errors);
                }
                if (effect.durationTurns() != null && effect.durationTurns() < 1) {
                    addError(errors, Codes.DURATION_OUT_OF_RANGE, path, "consumables." + id + ".effects.duration_turns",
                            "duration_turns must be >= 1");
                }
            }
            if ("remove_status".equals(effect.type())) {
                if (effect.statuses() == null || effect.statuses().isEmpty()) {
                    addError(errors, Codes.MISSING_FIELD, path, "consumables." + id + ".effects.statuses",
                            "remove_status missing statuses");
                }
            }
            if ("apply_buff".equals(effect.type())) {
                if (effect.modifiers() == null || effect.modifiers().isEmpty()) {
                    addError(errors, Codes.MISSING_FIELD, path, "consumables." + id + ".effects.modifiers",
                            "apply_buff missing modifiers");
                } else {
                    validateModifiers(path, "consumables." + id + ".effects.modifiers", effect.modifiers(), errors);
                }
            }
            if ("perm_stats".equals(effect.type())) {
                if (effect.mods() == null || effect.mods().isEmpty()) {
                    addError(errors, Codes.MISSING_FIELD, path, "consumables." + id + ".effects.mods",
                            "perm_stats missing mods");
                } else {
                    validateModifiers(path, "consumables." + id + ".effects.mods", effect.mods(), errors);
                }
            }
            if ("cast_skill".equals(effect.type())) {
                if (isBlank(effect.skillId())) {
                    addError(errors, Codes.MISSING_FIELD, path, "consumables." + id + ".effects.skill_id",
                            "cast_skill missing skill_id");
                } else if (!skillIds.contains(effect.skillId())) {
                    addError(errors, Codes.CONSUMABLE_SKILL_REF, path, "consumables." + id + ".effects.skill_id",
                            "skill_id not found");
                }
            }
        }
    }

    private static void requireStat(Path path, String id, String field, Double value, List<ValidationError> errors) {
        if (value == null) {
            addError(errors, Codes.MISSING_FIELD, path, "enemies." + id + "." + field, "missing " + field);
        }
    }

    private static void validateChance(Path path, String field, double value, List<ValidationError> errors) {
        if (value < 0 || value > 1) {
            addError(errors, Codes.CHANCE_OUT_OF_RANGE, path, field, "chance must be in [0, 1]");
        }
    }

    private static void validateNonNegative(Path path, String id, String field, Double value,
                                            List<ValidationError> errors) {
        if (value != null && value < 0) {
            addError(errors, Codes.GROWTH_NEGATIVE, path, "enemies." + id + "." + field, field + " must be >= 0");
        }
    }

    private static void validateElementMatrix(Path path, Map<String, Map<String, Double>> matrix,
                                              List<ValidationError> errors) {
        for (var entry : matrix.entrySet()) {
            String element = entry.getKey();
            if (!ELEMENTS.contains(element)) {
                addError(errors, Codes.INVALID_ENUM, path, "elements.multiplier", "invalid key '" + element + "'");
                continue;
            }
            var targets = entry.getValue();
            if (targets == null) {
                addError(errors, Codes.MISSING_FIELD, path, "elements.multiplier." + element, "target map is null");
                continue;
            }
            for (var target : targets.entrySet()) {
                if (!ELEMENTS.contains(target.getKey())) {
                    addError(errors, Codes.INVALID_ENUM, path, "elements.multiplier." + element,
                            "invalid target '" + target.getKey() + "'");
                }
                if (target.getValue() == null) {
                    addError(errors, Codes.MISSING_FIELD, path, "elements.multiplier." + element + "." + target.getKey(),
                            "multiplier is null");
                }
            }
        }
    }

    private static void validateEnum(Path path, String field, String value, Set<String> allowed,
                                     List<ValidationError> errors) {
        if (isBlank(value)) {
            addError(errors, Codes.MISSING_FIELD, path, field, "value is missing");
            return;
        }
        if (!allowed.contains(value)) {
            addError(errors, Codes.INVALID_ENUM, path, field, "invalid value '" + value + "'");
        }
    }

    private static void validateDamageType(Path path, String field, String value, List<ValidationError> errors) {
        if (isBlank(value)) {
            addError(errors, Codes.MISSING_FIELD, path, field, "damage_type is missing");
            return;
        }
        if (!DAMAGE_TYPES.contains(value)) {
            addError(errors, Codes.UNSUPPORTED_DAMAGE_TYPE, path, field, "damage_type unsupported");
        }
    }

    private static void validateTermStat(Path path, String field, String value, List<ValidationError> errors) {
        if (isBlank(value)) {
            addError(errors, Codes.MISSING_FIELD, path, field, "term.stat is missing");
            return;
        }
        if (!TERM_STATS.contains(value)) {
            addError(errors, Codes.UNSUPPORTED_TERM_STAT, path, field, "term.stat unsupported");
        }
    }

    private static void validateSlot(Path path, String field, String value, List<ValidationError> errors) {
        if (isBlank(value)) {
            addError(errors, Codes.MISSING_FIELD, path, field, "slot is missing");
            return;
        }
        if (!SLOTS.contains(value)) {
            addError(errors, Codes.SLOT_UNSUPPORTED, path, field, "slot unsupported");
        }
    }

    private static void validateModifiers(Path path, String field, List<ConsumablesData.Modifier> modifiers,
                                          List<ValidationError> errors) {
        for (ConsumablesData.Modifier modifier : modifiers) {
            if (modifier == null) {
                addError(errors, Codes.MISSING_FIELD, path, field, "modifier is null");
                continue;
            }
            if (isBlank(modifier.stat())) {
                addError(errors, Codes.MISSING_FIELD, path, field + ".stat", "modifier missing stat");
            }
        }
    }

    private static void validateStatsBlock(Path path, String field, EquipmentData.StatsBlock stats,
                                           List<ValidationError> errors) {
        if (stats.flat() == null) {
            addError(errors, Codes.MISSING_FIELD, path, field + ".flat", "stats.flat missing");
        } else {
            validateStatMap(path, field + ".flat", stats.flat(), errors);
        }
        if (stats.percent() == null) {
            addError(errors, Codes.MISSING_FIELD, path, field + ".percent", "stats.percent missing");
        } else {
            validateStatMap(path, field + ".percent", stats.percent(), errors);
        }
    }

    private static void validateStatMap(Path path, String field, Map<String, Double> stats,
                                        List<ValidationError> errors) {
        for (Map.Entry<String, Double> entry : stats.entrySet()) {
            if (entry.getValue() == null) {
                addError(errors, Codes.MISSING_FIELD, path, field + "." + entry.getKey(), "stat value missing");
            } else if (entry.getValue() < 0) {
                addError(errors, Codes.GROWTH_NEGATIVE, path, field + "." + entry.getKey(), "stat must be >= 0");
            }
        }
    }

    private static void validateSpecialTags(Path path, String field, List<String> tags, Set<String> specialIds,
                                            List<ValidationError> errors) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (String tag : tags) {
            if (isBlank(tag)) {
                addError(errors, Codes.MISSING_FIELD, path, field, "special_tag is blank");
            } else if (!specialIds.isEmpty() && !specialIds.contains(tag)) {
                addError(errors, Codes.SPECIAL_TAG_REF, path, field, "special_tag not found: " + tag);
            }
        }
    }

    private static void validateSkillRefs(Path path, String field, List<String> skills, Set<String> skillIds,
                                          String errorCode, List<ValidationError> errors) {
        if (skills == null) {
            return;
        }
        for (String skillId : skills) {
            if (isBlank(skillId)) {
                addError(errors, Codes.MISSING_FIELD, path, field, "skill_id is blank");
                continue;
            }
            if (!skillIds.isEmpty() && !skillIds.contains(skillId)) {
                addError(errors, errorCode, path, field, "skill_id not found: " + skillId);
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> T loadModel(Path path, Class<T> type, List<ValidationError> errors) {
        if (!Files.exists(path)) {
            addError(errors, Codes.MISSING_FIELD, path, null, "missing file");
            return null;
        }
        try {
            return JsonLoader.readValue(path, type);
        } catch (IOException exception) {
            addError(errors, Codes.JSON_PARSE_FAIL, path, null, "failed to parse json", exception.getMessage());
            return null;
        } catch (RuntimeException exception) {
            addError(errors, Codes.TYPE_MISMATCH, path, null, "json type mismatch", exception.getMessage());
            return null;
        }
    }

    private static void addError(List<ValidationError> errors, String code, Path file, String path, String message) {
        addError(errors, code, file, path, message, null);
    }

    private static void addError(List<ValidationError> errors, String code, Path file, String path, String message,
                                 String hint) {
        String fileName = file.getFileName() == null ? file.toString() : file.getFileName().toString();
        errors.add(new ValidationError(code, fileName, path, message, hint));
    }

    private static Set<String> extractSkillIds(SkillsData skillsData) {
        if (skillsData == null || skillsData.skills() == null) {
            return Set.of();
        }
        return new HashSet<>(skillsData.skills().keySet());
    }

    private static Set<String> extractSpecialIds(SpecialsData specialsData) {
        if (specialsData == null || specialsData.specials() == null) {
            return Set.of();
        }
        return new HashSet<>(specialsData.specials().keySet());
    }

    private static Set<String> extractEquipmentIds(EquipmentData equipmentData) {
        if (equipmentData == null || equipmentData.equipment() == null) {
            return Set.of();
        }
        return new HashSet<>(equipmentData.equipment().keySet());
    }

    private static Set<String> extractSetIds(EquipmentData equipmentData) {
        if (equipmentData == null || equipmentData.sets() == null) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (String id : equipmentData.sets().keySet()) {
            if (!isBlank(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static final Set<String> ELEMENTS = Set.of(
            "neutral", "fire", "water", "air", "earth", "lightning", "arcane"
    );

    private static final Set<String> DAMAGE_TYPES = Set.of("physical", "magic", "true");
    private static final Set<String> SKILL_CATEGORIES = Set.of("attack", "heal", "shield", "buff", "debuff", "utility");
    private static final Set<String> TARGETS = Set.of(
            "self", "enemy", "ally", "all_enemies", "all_allies", "random_enemy", "random_ally"
    );
    private static final Set<String> TIERS = Set.of("minion", "elite", "boss", "hidden_boss");
    private static final Set<String> DEFENSE_MODELS = Set.of("LOL", "CURVE", "LINEAR_CAP");
    private static final Set<String> SLOTS = Set.of(
            "helmet", "chest", "cloak", "legs", "boots", "ring", "weapon_1h", "weapon_2h"
    );
    private static final Set<String> COMPONENT_KINDS = Set.of(
            "damage", "heal", "shield", "buff", "debuff", "utility"
    );
    private static final Set<String> COMPONENT_REQUIRED_CATEGORIES = Set.of("attack", "heal", "shield");
    private static final Set<String> EFFECT_TYPES = Set.of(
            "heal_hp", "heal_mp", "add_shield", "remove_status", "apply_status", "apply_buff", "revive",
            "escape_bonus", "perm_stats", "cast_skill"
    );
    private static final Set<String> SPECIAL_HOOKS = Set.of(
            "on_battle_start", "pre_action", "pre_accuracy", "pre_damage", "pre_shield", "post_damage", "post_action",
            "on_kill", "on_death", "on_turn_end", "on_equip", "on_unequip"
    );
    private static final Set<String> TERM_STATS = Set.of(
            "constant",
            "self_attack", "self_spell_power", "self_defense", "self_magic_resist", "self_speed", "self_hp",
            "self_max_hp", "self_missing_hp", "self_mp", "self_max_mp", "self_missing_mp", "self_spent_mp",
            "target_hp", "target_max_hp", "target_missing_hp", "target_mp", "target_max_mp",
            "target_missing_mp", "target_defense", "target_magic_resist", "target_speed"
    );
    private static final Set<Integer> RING_SLOTS = Set.of(2, 4);

    private static final class Codes {
        private static final String JSON_PARSE_FAIL = "E1000";
        private static final String MISSING_FIELD = "E1001";
        private static final String TYPE_MISMATCH = "E1002";
        private static final String INVALID_ENUM = "E1003";
        private static final String DUPLICATE_ID = "E1004";
        private static final String ENEMY_SKILL_REF = "E2001";
        private static final String EQUIPMENT_SKILL_REF = "E2002";
        private static final String CONSUMABLE_SKILL_REF = "E2003";
        private static final String SET_PIECE_REF = "E2004";
        private static final String SPECIAL_TAG_REF = "E2005";
        private static final String CHANCE_OUT_OF_RANGE = "E3001";
        private static final String DURATION_OUT_OF_RANGE = "E3002";
        private static final String BASE_HP_INVALID = "E3003";
        private static final String GROWTH_NEGATIVE = "E3004";
        private static final String UNSUPPORTED_COMPONENT_KIND = "E4001";
        private static final String UNSUPPORTED_DAMAGE_TYPE = "E4002";
        private static final String UNSUPPORTED_TERM_STAT = "E4003";
        private static final String EMPTY_COMPONENTS = "E4004";
        private static final String SLOT_UNSUPPORTED = "E5001";
        private static final String SET_NOT_FOUND = "E5002";
        private static final String SET_BONUS_RANGE = "E5003";

        private Codes() {
        }
    }
}
