from __future__ import annotations

import random
from dataclasses import dataclass
from typing import Callable, List, Optional, Sequence

from .effects import EffectContext, EffectsEngine
from .models import Entity, Skill


@dataclass
class BattleResult:
    winner: str
    logs: List[str]


class BattleEngine:
    def __init__(self, effects: EffectsEngine, config: dict, rng: Optional[random.Random] = None):
        self.effects = effects
        self.config = config
        self.rng = rng or random.Random()

    def run_battle(
        self,
        player: Entity,
        enemy: Entity,
        skill_lookup: Callable[[str], Skill],
        choose_player_skill: Callable[[Entity], str],
        verbose: bool = True,
    ) -> BattleResult:
        logs: List[str] = []
        round_count = 1
        while player.is_alive() and enemy.is_alive():
            if getattr(player, "escaped", False):
                break
            logs.append(f"-- 턴 {round_count} --")
            turn_logs = self._run_turn(player, enemy, skill_lookup, choose_player_skill)
            logs.extend(turn_logs)
            round_count += 1
        if getattr(player, "escaped", False):
            winner = "도주"
        else:
            winner = player.name if player.is_alive() else enemy.name
        if verbose:
            logs.append(f"전투 종료! 승리: {winner}")
        return BattleResult(winner=winner, logs=logs)

    def _run_turn(
        self,
        player: Entity,
        enemy: Entity,
        skill_lookup: Callable[[str], Skill],
        choose_player_skill: Callable[[Entity], str],
    ) -> List[str]:
        logs: List[str] = []
        actors = sorted([player, enemy], key=lambda e: e.stats.speed, reverse=True)
        actions = []
        for actor in actors:
            if actor is player:
                actions.append((actor, choose_player_skill(actor)))
            else:
                actions.append((actor, self._choose_enemy_skill(actor, skill_lookup)))
        for actor, skill_name in actions:
            if not player.is_alive() or not enemy.is_alive():
                break
            target = enemy if actor is player else player
            logs.extend(self._resolve_action(actor, target, skill_name, skill_lookup))
            if getattr(player, "escaped", False):
                break
        logs.extend(self._apply_status_damage([player, enemy]))
        self.effects.tick_statuses(player)
        self.effects.tick_statuses(enemy)
        return logs

    def _choose_enemy_skill(self, enemy: Entity, skill_lookup: Callable[[str], Skill]) -> str:
        available = []
        for skill_name in enemy.skills:
            skill = skill_lookup(skill_name)
            cost = self._resolve_mp_cost(enemy, skill)
            if cost <= enemy.current_mp:
                weight = int(enemy.skill_weights.get(skill_name, 1))
                available.append((skill_name, weight))
        if not available:
            return "기본 공격"
        total_weight = sum(weight for _, weight in available)
        pick = self.rng.uniform(0, total_weight)
        running = 0.0
        for name, weight in available:
            running += weight
            if pick <= running:
                return name
        return available[-1][0]

    def _resolve_action(
        self, attacker: Entity, defender: Entity, skill_name: str, skill_lookup: Callable[[str], Skill]
    ) -> List[str]:
        logs: List[str] = []
        if skill_name == "도주":
            logs.append(f"{attacker.name}가 도주했다!")
            setattr(attacker, "escaped", True)
            return logs
        if self.effects.status_blocks_action(attacker):
            logs.append(f"{attacker.name}는 상태이상으로 행동하지 못했다!")
            return logs
        skill = skill_lookup(skill_name)
        cost = self._resolve_mp_cost(attacker, skill)
        if cost > attacker.current_mp:
            logs.append(f"{attacker.name}의 MP가 부족해 기본 공격을 사용한다!")
            skill = skill_lookup("기본 공격")
            cost = self._resolve_mp_cost(attacker, skill)
        attacker.current_mp = max(attacker.current_mp - cost, 0)
        logs.append(f"{attacker.name}의 행동: {skill.name}")
        targets = self._select_targets(attacker, defender, skill)
        action_logs, _ = self._resolve_skill(attacker, targets, skill, cost)
        logs.extend(action_logs)
        for tag in skill.special_tags:
            context = EffectContext(attacker=attacker, target=defender, spent_mp=cost)
            self.effects.execute_special_tag(tag, "after_resolve", context)
            logs.extend(context.logs)
        return logs

    def _resolve_skill(
        self, attacker: Entity, targets: Sequence[Entity], skill: Skill, spent_mp: float
    ) -> tuple[list[str], List[EffectContext]]:
        logs: List[str] = []
        contexts: List[EffectContext] = []
        for target in targets:
            context = EffectContext(attacker=attacker, target=target, spent_mp=spent_mp)
            for tag in skill.special_tags:
                self.effects.execute_special_tag(tag, "pre_accuracy", context)
            if self._check_hit(attacker, target, context):
                logs.append(f"{attacker.name}의 공격이 적중했다!")
                damage_logs = self._apply_components(attacker, target, skill, context)
                logs.extend(damage_logs)
                status_logs = self._apply_status_effects(attacker, target, skill)
                logs.extend(status_logs)
                for tag in skill.special_tags:
                    self.effects.execute_special_tag(tag, "per_target", context)
                logs.extend(context.logs)
            else:
                logs.append(f"{attacker.name}의 공격이 빗나갔다!")
            contexts.append(context)
        return logs, contexts

    def _check_hit(self, attacker: Entity, defender: Entity, context: EffectContext) -> bool:
        if context.force_hit:
            return True
        if context.force_miss:
            return False
        if defender.stats.speed <= 0:
            accuracy = 1.0
        else:
            ratio = attacker.stats.speed / defender.stats.speed
            accuracy = ratio if ratio < 1 else 1.0
        accuracy *= self.effects.accuracy_multiplier(attacker)
        accuracy = max(accuracy, float(self.config.get("accuracy_min", 0.05)))
        return self.rng.random() < accuracy

    def _apply_components(self, attacker: Entity, defender: Entity, skill: Skill, context: EffectContext) -> List[str]:
        logs: List[str] = []
        for component in skill.components:
            amount = self._calculate_component(attacker, defender, component.terms, context)
            if component.kind == "damage":
                raw_damage = self._apply_element_multiplier(amount, skill.element, defender.element)
                raw_damage *= self.effects.damage_taken_multiplier(defender)
                context.raw_damage = raw_damage
                damage_after_shield = self._apply_shield(defender, raw_damage, logs)
                final_damage = self._apply_defense(defender, damage_after_shield, component.damage_type)
                context.final_damage = final_damage
                defender.current_hp = max(defender.current_hp - final_damage, 0)
                logs.append(f"{defender.name}가 {final_damage:.1f} 피해를 받았다!")
                if defender.current_hp <= 0:
                    logs.append(f"{defender.name}가 쓰러졌다!")
            elif component.kind == "heal":
                heal_amount = max(amount, 0)
                defender.current_hp = min(defender.current_hp + heal_amount, defender.stats.max_hp)
                logs.append(f"{defender.name}가 {heal_amount:.1f} 회복했다!")
            elif component.kind == "shield":
                shield_amount = max(amount, 0)
                defender.stats.max_shield += shield_amount * 0.1
                defender.current_shield = min(
                    defender.current_shield + shield_amount, defender.stats.max_shield
                )
                logs.append(f"{defender.name}에게 보호막 {shield_amount:.1f}이 생성되었다!")
        return logs

    def _calculate_component(
        self, attacker: Entity, defender: Entity, terms: List[dict], context: EffectContext
    ) -> float:
        total = 0.0
        for term in terms:
            stat = term["stat"]
            coef = float(term["coef"])
            if stat == "constant":
                value = coef
            elif stat == "self_attack":
                value = attacker.stats.attack * coef
            elif stat == "self_magic":
                value = attacker.stats.magic * coef
            elif stat == "self_max_hp":
                value = attacker.stats.max_hp * coef
            elif stat == "self_missing_hp":
                value = (attacker.stats.max_hp - attacker.current_hp) * coef
            elif stat == "target_hp":
                value = defender.current_hp * coef
            elif stat == "target_max_hp":
                value = defender.stats.max_hp * coef
            elif stat == "target_missing_hp":
                value = (defender.stats.max_hp - defender.current_hp) * coef
            elif stat == "self_spent_mp":
                value = context.spent_mp * coef
            else:
                value = 0
            total += value
        return total

    def _apply_element_multiplier(self, amount: float, element: str, target_element: str) -> float:
        table = self.config.get("element_multipliers", {})
        multiplier = 1.0
        element_table = table.get(element, {})
        multiplier = float(element_table.get(target_element, 1.0))
        return amount * multiplier

    def _apply_shield(self, defender: Entity, raw_damage: float, logs: List[str]) -> float:
        if defender.current_shield <= 0:
            return raw_damage
        absorbed = min(defender.current_shield, raw_damage)
        defender.current_shield -= absorbed
        logs.append(f"{defender.name}의 보호막이 {absorbed:.1f} 흡수했다!")
        return max(raw_damage - absorbed, 0)

    def _apply_defense(self, defender: Entity, damage: float, damage_type: str) -> float:
        if damage_type == "true":
            return damage
        if damage_type == "physical":
            defense = defender.stats.defense
        else:
            defense = defender.stats.magic_resist
        reduction = 100 / (100 + defense) if defense >= 0 else 1
        return max(damage * reduction, 0)

    def _apply_status_effects(self, attacker: Entity, target: Entity, skill: Skill) -> List[str]:
        logs: List[str] = []
        for effect in skill.status_effects:
            if effect.target == "self":
                actual_target = attacker
            else:
                actual_target = target
            chance = effect.chance
            tier = getattr(actual_target, "tier", None)
            resistance = self.config.get("status_resistance", {}).get(tier, {})
            if effect.status in resistance:
                chance *= float(resistance[effect.status])
            if chance <= 0:
                logs.append(f"{actual_target.name}는 {effect.status}에 면역이다!")
                continue
            if self.rng.random() <= chance:
                self.effects.apply_status(actual_target, effect.status, effect.duration, source=attacker.name)
                logs.append(f"{actual_target.name}에게 {effect.status} 효과가 부여되었다!")
        return logs

    def _apply_status_damage(self, entities: Sequence[Entity]) -> List[str]:
        logs: List[str] = []
        for entity in entities:
            logs.extend(self.effects.status_damage(entity))
        return logs

    def _select_targets(self, attacker: Entity, defender: Entity, skill: Skill) -> List[Entity]:
        if skill.target == "self":
            return [attacker]
        if skill.target == "aoe":
            return [defender]
        return [defender]

    def _resolve_mp_cost(self, attacker: Entity, skill: Skill) -> float:
        if isinstance(skill.mp_cost, dict):
            mode = skill.mp_cost.get("mode")
            if mode == "all_current_mp":
                return attacker.current_mp
            return float(skill.mp_cost.get("flat", 0))
        return float(skill.mp_cost)
