import random
import unittest

from src.battle_engine import BattleEngine
from src.data_loader import load_data_bundle
from src.effects import EffectsEngine
from src.models import Player, Stats


class SmokeBattleTest(unittest.TestCase):
    def test_auto_battle_runs(self) -> None:
        data = load_data_bundle()
        effects = EffectsEngine(data.specials, data.status_definitions)
        rng = random.Random(42)
        engine = BattleEngine(effects, data.config, rng=rng)

        base = data.config["player_base"]
        stats = Stats.from_dict(base["stats"])
        player = Player(
            name="테스터",
            level=int(base["level"]),
            stats=stats,
            current_hp=stats.max_hp,
            current_mp=stats.max_mp,
            current_shield=stats.max_shield,
            element="neutral",
            xp=0,
            gold=0,
            inventory={},
            equipment={},
            unlocked_skills=list(data.config.get("starting_skills", [])),
            base_stats=stats.to_dict(),
        )

        def choose_skill(entity):
            usable = []
            for skill_name in player.unlocked_skills:
                skill = data.skills[skill_name]
                cost = skill.mp_cost if isinstance(skill.mp_cost, (int, float)) else skill.mp_cost.get("flat", 0)
                if cost <= player.current_mp:
                    usable.append(skill_name)
            return rng.choice(usable) if usable else "기본 공격"

        enemies = list(data.enemies.values())
        for _ in range(10):
            enemy_template = rng.choice(enemies)
            enemy = enemy_template.create_instance(enemy_template.base_level)
            result = engine.run_battle(
                player=player,
                enemy=enemy,
                skill_lookup=lambda name: data.skills[name],
                choose_player_skill=choose_skill,
                verbose=False,
            )
            self.assertIn(result.winner, {player.name, enemy.name, "도주"})
            player.current_hp = player.stats.max_hp
            player.current_mp = player.stats.max_mp
            player.statuses.clear()


if __name__ == "__main__":
    unittest.main()
