from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, List, Tuple

from .models import (
    ConsumableItem,
    EnemyTemplate,
    EquipmentItem,
    Skill,
    SkillComponent,
    SkillStatusEffect,
    Stats,
)


class DataValidationError(ValueError):
    pass


class DataBundle:
    def __init__(
        self,
        skills: Dict[str, Skill],
        enemies: Dict[str, EnemyTemplate],
        consumables: Dict[str, ConsumableItem],
        equipment: Dict[str, EquipmentItem],
        equipment_sets: Dict[str, Dict[int, Dict[str, float]]],
        specials: Dict[str, Dict[str, object]],
        status_definitions: Dict[str, Dict[str, object]],
        config: Dict[str, object],
    ) -> None:
        self.skills = skills
        self.enemies = enemies
        self.consumables = consumables
        self.equipment = equipment
        self.equipment_sets = equipment_sets
        self.specials = specials
        self.status_definitions = status_definitions
        self.config = config


DATA_DIR = Path(__file__).resolve().parents[1] / "data"


def _load_json(path: Path) -> Dict[str, object]:
    if not path.exists():
        raise DataValidationError(f"필수 데이터 파일이 없습니다: {path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise DataValidationError(f"JSON 파싱 오류: {path} -> {exc}") from exc


def _require_keys(data: Dict[str, object], keys: List[str], location: str) -> None:
    for key in keys:
        if key not in data:
            raise DataValidationError(f"{location} 누락 필드: '{key}'")


def _parse_skill(name: str, data: Dict[str, object], location: str) -> Skill:
    _require_keys(data, ["element", "category", "target", "mp_cost", "components"], location)
    components = []
    for idx, comp in enumerate(data.get("components", [])):
        comp_loc = f"{location}.components[{idx}]"
        _require_keys(comp, ["kind", "damage_type", "terms"], comp_loc)
        terms = comp.get("terms")
        if not isinstance(terms, list):
            raise DataValidationError(f"{comp_loc}.terms 는 리스트여야 합니다")
        for term_idx, term in enumerate(terms):
            term_loc = f"{comp_loc}.terms[{term_idx}]"
            _require_keys(term, ["stat", "coef"], term_loc)
        components.append(
            SkillComponent(
                kind=str(comp["kind"]),
                damage_type=str(comp["damage_type"]),
                terms=[{"stat": str(term["stat"]), "coef": float(term["coef"])} for term in terms],
            )
        )
    status_effects = []
    for idx, effect in enumerate(data.get("status_effects", [])):
        effect_loc = f"{location}.status_effects[{idx}]"
        _require_keys(effect, ["status", "target", "chance", "duration"], effect_loc)
        status_effects.append(
            SkillStatusEffect(
                status=str(effect["status"]),
                target=str(effect["target"]),
                chance=float(effect["chance"]),
                duration=int(effect["duration"]),
            )
        )
    return Skill(
        name=name,
        element=str(data["element"]),
        category=str(data["category"]),
        target=str(data["target"]),
        mp_cost=data["mp_cost"],
        components=components,
        status_effects=status_effects,
        special_tags=list(data.get("special_tags", [])),
    )


def _parse_enemies(data: Dict[str, object]) -> Dict[str, EnemyTemplate]:
    enemies = {}
    enemy_list = data.get("enemies", [])
    if not isinstance(enemy_list, list):
        raise DataValidationError("enemies.json -> 'enemies'는 리스트여야 합니다")
    for idx, enemy in enumerate(enemy_list):
        location = f"enemies.json.enemies[{idx}]"
        _require_keys(
            enemy,
            [
                "id",
                "name",
                "tier",
                "element",
                "description",
                "xp_reward",
                "base_level",
                "base_stats",
                "growth_per_level",
                "skills",
            ],
            location,
        )
        base_stats = Stats.from_dict(enemy["base_stats"])
        growth_stats = Stats.from_dict(enemy["growth_per_level"])
        skills = enemy["skills"]
        if not isinstance(skills, list):
            raise DataValidationError(f"{location}.skills 는 리스트여야 합니다")
        enemy_template = EnemyTemplate(
            enemy_id=str(enemy["id"]),
            name=str(enemy["name"]),
            tier=str(enemy["tier"]),
            element=str(enemy["element"]),
            description=str(enemy["description"]),
            xp_reward=int(enemy["xp_reward"]),
            base_level=int(enemy["base_level"]),
            base_stats=base_stats,
            growth_per_level=growth_stats,
            skills=[{"name": str(skill["name"]), "weight": int(skill.get("weight", 1))} for skill in skills],
        )
        enemies[enemy_template.enemy_id] = enemy_template
    return enemies


def _parse_consumables(data: Dict[str, object]) -> Dict[str, ConsumableItem]:
    consumables = {}
    items = data.get("consumables", {})
    if not isinstance(items, dict):
        raise DataValidationError("consumables.json -> 'consumables'는 객체여야 합니다")
    for name, item in items.items():
        location = f"consumables.json.consumables['{name}']"
        _require_keys(item, ["description", "rarity", "price", "use", "effects"], location)
        consumables[name] = ConsumableItem(
            name=name,
            description=str(item["description"]),
            rarity=str(item["rarity"]),
            price=int(item["price"]),
            use=dict(item["use"]),
            effects=list(item["effects"]),
        )
    return consumables


def _parse_equipment(data: Dict[str, object]) -> Tuple[Dict[str, EquipmentItem], Dict[str, Dict[int, Dict[str, float]]]]:
    equipment_items = {}
    equipment = data.get("equipment", {})
    if not isinstance(equipment, dict):
        raise DataValidationError("equipment.json -> 'equipment'는 객체여야 합니다")
    for name, item in equipment.items():
        location = f"equipment.json.equipment['{name}']"
        _require_keys(item, ["slot", "rarity", "description", "stats"], location)
        equipment_items[name] = EquipmentItem(
            name=name,
            slot=str(item["slot"]),
            rarity=str(item["rarity"]),
            description=str(item["description"]),
            stats={key: float(value) for key, value in item["stats"].items()},
            price=int(item.get("price", 0)),
            set_name=item.get("set_name"),
            special_tags=list(item.get("special_tags", [])),
        )
    sets = {}
    raw_sets = data.get("sets", {})
    if raw_sets:
        for set_name, bonuses in raw_sets.items():
            sets[set_name] = {
                int(count): {key: float(value) for key, value in stats.items()}
                for count, stats in bonuses.items()
            }
    return equipment_items, sets


def _parse_specials(data: Dict[str, object]) -> Tuple[Dict[str, Dict[str, object]], Dict[str, Dict[str, object]]]:
    special_tags = data.get("special_tags", {})
    status_definitions = data.get("status_definitions", {})
    if not isinstance(special_tags, dict):
        raise DataValidationError("specials.json -> 'special_tags'는 객체여야 합니다")
    if not isinstance(status_definitions, dict):
        raise DataValidationError("specials.json -> 'status_definitions'는 객체여야 합니다")
    return special_tags, status_definitions


def load_data_bundle(data_dir: Path | None = None) -> DataBundle:
    base_dir = data_dir or DATA_DIR
    skills_json = _load_json(base_dir / "Skills.json")
    enemies_json = _load_json(base_dir / "enemies.json")
    consumables_json = _load_json(base_dir / "consumables.json")
    equipment_json = _load_json(base_dir / "equipment.json")
    specials_json = _load_json(base_dir / "specials.json")
    config_json = _load_json(base_dir / "config.json")

    skills_data = skills_json.get("skills", {})
    if not isinstance(skills_data, dict):
        raise DataValidationError("Skills.json -> 'skills'는 객체여야 합니다")
    skills = {
        name: _parse_skill(name, data, f"Skills.json.skills['{name}']")
        for name, data in skills_data.items()
    }

    enemies = _parse_enemies(enemies_json)
    consumables = _parse_consumables(consumables_json)
    equipment_items, sets = _parse_equipment(equipment_json)
    specials, status_definitions = _parse_specials(specials_json)

    return DataBundle(
        skills=skills,
        enemies=enemies,
        consumables=consumables,
        equipment=equipment_items,
        equipment_sets=sets,
        specials=specials,
        status_definitions=status_definitions,
        config=config_json,
    )
