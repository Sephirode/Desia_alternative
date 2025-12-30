from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional


@dataclass
class Stats:
    max_hp: float
    max_mp: float
    attack: float
    magic: float
    defense: float
    magic_resist: float
    speed: float
    max_shield: float = 0.0

    def to_dict(self) -> Dict[str, float]:
        return {
            "max_hp": self.max_hp,
            "max_mp": self.max_mp,
            "attack": self.attack,
            "magic": self.magic,
            "defense": self.defense,
            "magic_resist": self.magic_resist,
            "speed": self.speed,
            "max_shield": self.max_shield,
        }

    @classmethod
    def from_dict(cls, data: Dict[str, float]) -> "Stats":
        return cls(
            max_hp=float(data.get("max_hp", 0)),
            max_mp=float(data.get("max_mp", 0)),
            attack=float(data.get("attack", 0)),
            magic=float(data.get("magic", 0)),
            defense=float(data.get("defense", 0)),
            magic_resist=float(data.get("magic_resist", 0)),
            speed=float(data.get("speed", 0)),
            max_shield=float(data.get("max_shield", 0)),
        )


@dataclass
class ActiveStatus:
    status_id: str
    remaining_turns: int
    source: Optional[str] = None


@dataclass
class SkillComponent:
    kind: str
    damage_type: str
    terms: List[Dict[str, float]]


@dataclass
class SkillStatusEffect:
    status: str
    target: str
    chance: float
    duration: int


@dataclass
class Skill:
    name: str
    element: str
    category: str
    target: str
    mp_cost: object
    components: List[SkillComponent] = field(default_factory=list)
    status_effects: List[SkillStatusEffect] = field(default_factory=list)
    special_tags: List[str] = field(default_factory=list)


@dataclass
class EquipmentItem:
    name: str
    slot: str
    rarity: str
    description: str
    stats: Dict[str, float]
    price: int = 0
    set_name: Optional[str] = None
    special_tags: List[str] = field(default_factory=list)


@dataclass
class ConsumableItem:
    name: str
    description: str
    rarity: str
    price: int
    use: Dict[str, bool]
    effects: List[Dict[str, object]]


@dataclass
class Entity:
    name: str
    level: int
    stats: Stats
    current_hp: float
    current_mp: float
    current_shield: float = 0.0
    element: str = "neutral"
    statuses: List[ActiveStatus] = field(default_factory=list)
    skills: List[str] = field(default_factory=list)
    skill_weights: Dict[str, int] = field(default_factory=dict)

    def is_alive(self) -> bool:
        return self.current_hp > 0

    def to_dict(self) -> Dict[str, object]:
        return {
            "name": self.name,
            "level": self.level,
            "stats": self.stats.to_dict(),
            "current_hp": self.current_hp,
            "current_mp": self.current_mp,
            "current_shield": self.current_shield,
            "element": self.element,
            "statuses": [
                {"status_id": status.status_id, "remaining_turns": status.remaining_turns}
                for status in self.statuses
            ],
            "skills": self.skills,
            "skill_weights": self.skill_weights,
        }

    @classmethod
    def from_dict(cls, data: Dict[str, object]) -> "Entity":
        stats = Stats.from_dict(data.get("stats", {}))
        statuses = [
            ActiveStatus(status["status_id"], int(status["remaining_turns"]))
            for status in data.get("statuses", [])
        ]
        return cls(
            name=str(data.get("name", "")),
            level=int(data.get("level", 1)),
            stats=stats,
            current_hp=float(data.get("current_hp", stats.max_hp)),
            current_mp=float(data.get("current_mp", stats.max_mp)),
            current_shield=float(data.get("current_shield", 0)),
            element=str(data.get("element", "neutral")),
            statuses=statuses,
            skills=list(data.get("skills", [])),
            skill_weights=dict(data.get("skill_weights", {})),
        )


@dataclass
class Player(Entity):
    xp: int = 0
    gold: int = 0
    inventory: Dict[str, int] = field(default_factory=dict)
    equipment: Dict[str, str] = field(default_factory=dict)
    unlocked_skills: List[str] = field(default_factory=list)
    base_stats: Dict[str, float] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, object]:
        base = super().to_dict()
        base.update(
            {
                "xp": self.xp,
                "gold": self.gold,
                "inventory": self.inventory,
                "equipment": self.equipment,
                "unlocked_skills": self.unlocked_skills,
                "base_stats": self.base_stats,
            }
        )
        return base

    @classmethod
    def from_dict(cls, data: Dict[str, object]) -> "Player":
        entity = Entity.from_dict(data)
        return cls(
            name=entity.name,
            level=entity.level,
            stats=entity.stats,
            current_hp=entity.current_hp,
            current_mp=entity.current_mp,
            current_shield=entity.current_shield,
            element=entity.element,
            statuses=entity.statuses,
            skills=entity.skills,
            xp=int(data.get("xp", 0)),
            gold=int(data.get("gold", 0)),
            inventory=dict(data.get("inventory", {})),
            equipment=dict(data.get("equipment", {})),
            unlocked_skills=list(data.get("unlocked_skills", [])),
            base_stats=dict(data.get("base_stats", entity.stats.to_dict())),
        )


@dataclass
class EnemyTemplate:
    enemy_id: str
    name: str
    tier: str
    element: str
    description: str
    xp_reward: int
    base_level: int
    base_stats: Stats
    growth_per_level: Stats
    skills: List[Dict[str, object]]

    def stats_for_level(self, level: int) -> Stats:
        delta = max(level - self.base_level, 0)
        return Stats(
            max_hp=self.base_stats.max_hp + self.growth_per_level.max_hp * delta,
            max_mp=self.base_stats.max_mp + self.growth_per_level.max_mp * delta,
            attack=self.base_stats.attack + self.growth_per_level.attack * delta,
            magic=self.base_stats.magic + self.growth_per_level.magic * delta,
            defense=self.base_stats.defense + self.growth_per_level.defense * delta,
            magic_resist=self.base_stats.magic_resist + self.growth_per_level.magic_resist * delta,
            speed=self.base_stats.speed + self.growth_per_level.speed * delta,
            max_shield=self.base_stats.max_shield + self.growth_per_level.max_shield * delta,
        )

    def create_instance(self, level: int) -> Entity:
        stats = self.stats_for_level(level)
        entity = Entity(
            name=self.name,
            level=level,
            stats=stats,
            current_hp=stats.max_hp,
            current_mp=stats.max_mp,
            current_shield=stats.max_shield,
            element=self.element,
            skills=[skill["name"] for skill in self.skills],
            skill_weights={skill["name"]: int(skill.get("weight", 1)) for skill in self.skills},
        )
        setattr(entity, "tier", self.tier)
        setattr(entity, "xp_reward", self.xp_reward)
        return entity
