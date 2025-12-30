from __future__ import annotations

import json
import random
from pathlib import Path
from typing import Dict, List

from .battle_engine import BattleEngine
from .data_loader import DataBundle, DataValidationError, load_data_bundle
from .effects import EffectsEngine
from .models import Player, Stats

SAVE_DIR = Path(__file__).resolve().parents[1] / "saves"
SAVE_DIR.mkdir(exist_ok=True)
RANKING_FILE = SAVE_DIR / "ranking.json"


def _load_ranking() -> List[Dict[str, object]]:
    if not RANKING_FILE.exists():
        return []
    return json.loads(RANKING_FILE.read_text(encoding="utf-8"))


def _save_ranking(entries: List[Dict[str, object]]) -> None:
    RANKING_FILE.write_text(json.dumps(entries, ensure_ascii=False, indent=2), encoding="utf-8")


def _save_game(slot: int, player: Player, chapter: int) -> None:
    payload = {"player": player.to_dict(), "chapter": chapter}
    (SAVE_DIR / f"slot_{slot}.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )


def _load_game(slot: int) -> Dict[str, object] | None:
    path = SAVE_DIR / f"slot_{slot}.json"
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def _list_saves() -> List[int]:
    return [slot for slot in range(1, 4) if (SAVE_DIR / f"slot_{slot}.json").exists()]


def _delete_save(slot: int) -> None:
    path = SAVE_DIR / f"slot_{slot}.json"
    if path.exists():
        path.unlink()


def _build_player(data: DataBundle, name: str) -> Player:
    base = data.config["player_base"]
    stats = Stats.from_dict(base["stats"])
    player = Player(
        name=name,
        level=int(base["level"]),
        stats=stats,
        current_hp=stats.max_hp,
        current_mp=stats.max_mp,
        current_shield=stats.max_shield,
        element="neutral",
        xp=int(base["xp"]),
        gold=int(base["gold"]),
        inventory={"작은 체력 포션": 2, "작은 마나 포션": 1},
        equipment={},
        unlocked_skills=list(data.config.get("starting_skills", [])),
        base_stats=stats.to_dict(),
    )
    return player


def _apply_equipment(player: Player, data: DataBundle) -> Stats:
    base_stats = player.base_stats or player.stats.to_dict()
    bonus_stats: Dict[str, float] = {key: 0.0 for key in base_stats}
    set_counts: Dict[str, int] = {}
    for item_name in player.equipment.values():
        equipment = data.equipment.get(item_name)
        if not equipment:
            continue
        for key, value in equipment.stats.items():
            bonus_stats[key] = bonus_stats.get(key, 0) + value
        if equipment.set_name:
            set_counts[equipment.set_name] = set_counts.get(equipment.set_name, 0) + 1
    for set_name, count in set_counts.items():
        bonuses = data.equipment_sets.get(set_name, {})
        for threshold, stats in bonuses.items():
            if count >= threshold:
                for key, value in stats.items():
                    bonus_stats[key] = bonus_stats.get(key, 0) + value
    merged = {key: base_stats.get(key, 0) + bonus_stats.get(key, 0) for key in base_stats}
    return Stats.from_dict(merged)


def _choose_player_skill(player: Player, data: DataBundle) -> str:
    while True:
        print("\n[행동 선택] 1. 기본 공격 2. 스킬 3. 도주")
        choice = input("선택: ").strip()
        if choice == "1":
            return "기본 공격"
        if choice == "3":
            if random.random() < 0.5:
                print("도주 성공!")
                return "도주"
            print("도주 실패!")
            return "기본 공격"
        if choice == "2":
            skills = player.unlocked_skills
            for idx, skill_name in enumerate(skills, start=1):
                skill = data.skills.get(skill_name)
                if not skill:
                    continue
                mp_cost = skill.mp_cost if isinstance(skill.mp_cost, (int, float)) else skill.mp_cost.get("flat", 0)
                mp_note = "(MP 부족)" if mp_cost > player.current_mp else ""
                print(f"{idx}. {skill_name} {mp_note}")
            print("0. 돌아가기")
            selected = input("스킬 선택: ").strip()
            if selected == "0":
                continue
            if selected.isdigit() and 1 <= int(selected) <= len(skills):
                return skills[int(selected) - 1]
        print("잘못된 입력입니다.")


def _battle_loop(player: Player, data: DataBundle, chapter: int) -> int:
    effects = EffectsEngine(data.specials, data.status_definitions)
    engine = BattleEngine(effects, data.config, rng=random.Random())
    effective_stats = _apply_equipment(player, data)
    player.stats = effective_stats
    player.current_hp = min(player.current_hp, player.stats.max_hp)
    player.current_mp = min(player.current_mp, player.stats.max_mp)
    enemy_template = random.choice(list(data.enemies.values()))
    enemy_level = max(enemy_template.base_level, player.level)
    enemy = enemy_template.create_instance(enemy_level)
    logs = engine.run_battle(
        player=player,
        enemy=enemy,
        skill_lookup=lambda name: data.skills[name],
        choose_player_skill=lambda actor: _choose_player_skill(player, data),
    ).logs
    for line in logs:
        print(line)
    if getattr(player, "escaped", False):
        print("전투에서 도주했다.")
        setattr(player, "escaped", False)
        return chapter
    if player.is_alive() and not enemy.is_alive():
        reward_xp = getattr(enemy, "xp_reward", enemy_level * 5)
        reward_gold = int(reward_xp * 0.8)
        player.xp += reward_xp
        player.gold += reward_gold
        print(f"전리품: XP {reward_xp}, 골드 {reward_gold}")
    if not player.is_alive():
        entries = _load_ranking()
        entries.append({"name": player.name, "level": player.level, "chapter": chapter})
        _save_ranking(entries)
    return chapter


def _show_ranking() -> None:
    entries = _load_ranking()
    print("\n=== 랭킹 ===")
    if not entries:
        print("등록된 기록이 없습니다.")
        return
    for idx, entry in enumerate(entries, start=1):
        print(f"{idx}. {entry['name']} - 레벨 {entry['level']} (챕터 {entry['chapter']})")


def _show_help() -> None:
    print("\n도움말:")
    print("- 전투는 턴제입니다. 스피드가 높은 순서로 행동합니다.")
    print("- 스킬은 MP를 소모하며, MP가 부족하면 사용할 수 없습니다.")
    print("- 상태이상 피해는 턴 종료 후 적용됩니다.")


def main() -> None:
    try:
        data = load_data_bundle()
    except DataValidationError as exc:
        print(f"데이터 오류: {exc}")
        return

    while True:
        print("\n[1 시작 2 불러오기 3 랭킹 4 도움말 5 종료]")
        choice = input("선택: ").strip()
        if choice == "1":
            name = input("플레이어 이름을 입력하세요: ").strip() or "모험가"
            player = _build_player(data, name)
            chapter = 1
            chapter = _battle_loop(player, data, chapter)
            print("\n[1 저장 2 종료]")
            post_choice = input("선택: ").strip()
            if post_choice == "1":
                slot = int(input("저장 슬롯(1~3): ").strip() or "1")
                _save_game(slot, player, chapter)
                print("저장 완료!")
            continue
        if choice == "2":
            saves = _list_saves()
            print(f"저장 슬롯: {saves if saves else '없음'}")
            sub = input("불러오기 슬롯(1~3) 또는 D로 삭제: ").strip().lower()
            if sub == "d":
                slot = int(input("삭제할 슬롯: ").strip() or "1")
                _delete_save(slot)
                print("삭제 완료")
                continue
            if sub.isdigit():
                slot = int(sub)
                payload = _load_game(slot)
                if not payload:
                    print("해당 슬롯에 저장이 없습니다.")
                    continue
                player = Player.from_dict(payload["player"])
                chapter = int(payload.get("chapter", 1))
                _battle_loop(player, data, chapter)
            continue
        if choice == "3":
            _show_ranking()
            continue
        if choice == "4":
            _show_help()
            continue
        if choice == "5":
            print("게임을 종료합니다.")
            break
        print("잘못된 입력입니다.")


if __name__ == "__main__":
    main()
