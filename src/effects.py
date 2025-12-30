from __future__ import annotations

import random
from dataclasses import dataclass, field
from typing import Dict, List, Optional

from .models import ActiveStatus, Entity


@dataclass
class EffectContext:
    attacker: Entity
    target: Entity
    spent_mp: float
    raw_damage: float = 0.0
    final_damage: float = 0.0
    logs: List[str] = field(default_factory=list)
    force_hit: bool = False
    force_miss: bool = False


class EffectsEngine:
    def __init__(self, specials: Dict[str, Dict[str, object]], status_defs: Dict[str, Dict[str, object]]):
        self.specials = specials
        self.status_defs = status_defs

    def apply_status(self, target: Entity, status_id: str, duration: int, source: Optional[str] = None) -> None:
        definition = self.status_defs.get(status_id)
        if definition is None:
            return
        for active in target.statuses:
            if active.status_id == status_id:
                active.remaining_turns = max(active.remaining_turns, duration)
                return
        target.statuses.append(ActiveStatus(status_id=status_id, remaining_turns=duration, source=source))

    def tick_statuses(self, entity: Entity) -> None:
        entity.statuses = [status for status in entity.statuses if status.remaining_turns > 0]
        for status in entity.statuses:
            status.remaining_turns -= 1

    def status_blocks_action(self, entity: Entity) -> bool:
        for status in entity.statuses:
            definition = self.status_defs.get(status.status_id, {})
            skip_chance = definition.get("skip_chance")
            if skip_chance and random.random() < skip_chance:
                return True
        return False

    def accuracy_multiplier(self, entity: Entity) -> float:
        multiplier = 1.0
        for status in entity.statuses:
            definition = self.status_defs.get(status.status_id, {})
            status_multiplier = definition.get("accuracy_multiplier")
            if status_multiplier:
                multiplier *= float(status_multiplier)
        return multiplier

    def damage_taken_multiplier(self, entity: Entity) -> float:
        multiplier = 1.0
        for status in entity.statuses:
            definition = self.status_defs.get(status.status_id, {})
            bonus = definition.get("damage_taken_multiplier")
            if bonus:
                multiplier *= float(bonus)
        return multiplier

    def execute_special_tag(self, tag_id: str, hook: str, context: EffectContext) -> None:
        tag = self.specials.get(tag_id)
        if not tag or tag.get("hook") != hook:
            return
        tag_type = tag.get("type")
        if tag_type == "force_hit":
            context.force_hit = True
        elif tag_type == "force_miss":
            context.force_miss = True
        elif tag_type == "life_steal":
            ratio = float(tag.get("ratio", 0))
            heal_amount = max(context.final_damage * ratio, 0)
            context.attacker.current_hp = min(
                context.attacker.current_hp + heal_amount, context.attacker.stats.max_hp
            )
            context.logs.append(f"{context.attacker.name}가 흡혈로 {heal_amount:.1f} 회복했다!")
        elif tag_type == "shield_cap_boost":
            amount = float(tag.get("amount", 0))
            context.attacker.stats.max_shield += amount
            context.logs.append(f"{context.attacker.name}의 최대 보호막이 {amount:.0f} 증가했다!")

    def status_damage(self, entity: Entity) -> List[str]:
        logs: List[str] = []
        for status in entity.statuses:
            definition = self.status_defs.get(status.status_id, {})
            damage_term = definition.get("damage_per_turn")
            if not damage_term:
                continue
            coef = float(damage_term.get("coef", 0))
            stat = damage_term.get("stat")
            if stat == "target_max_hp":
                amount = entity.stats.max_hp * coef
            else:
                amount = 0
            amount = max(amount, 0)
            entity.current_hp = max(entity.current_hp - amount, 0)
            logs.append(f"{entity.name}가 {status.status_id} 피해로 {amount:.1f} 체력을 잃었다!")
        return logs
