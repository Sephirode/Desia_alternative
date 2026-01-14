

# 0) 공통 규칙

## ID 규칙

* 모든 콘텐츠는 `id`로 참조한다. (표시 이름 `name`과 분리)
* `id`는 영문/숫자/언더스코어 권장: `goblin_shaman`, `skill_fireball` 등
* **중복 id 금지**, **참조 무결성 필수**(없는 id를 참조하면 로딩 실패)

## 수치 규칙

* `chance`: 0.0 ~ 1.0
* `duration_turns`: 1 이상 정수
* 스탯은 기본적으로 `>= 0` (예외: 디버프/패널티는 modifier에서 음수 허용)

## enum(문자열 상수) 목록

* `element`: `neutral | fire | water | air | earth | lightning | arcane`
* `damage_type`: `physical | magic | true`
* `skill_category`: `attack | heal | shield | buff | debuff | utility`
* `target`: `self | enemy | ally | all_enemies | all_allies | random_enemy | random_ally`
* `tier`: `minion | elite | boss | hidden_boss`
* `slot`: `helmet | chest | cloak | legs | boots | ring | weapon_1h | weapon_2h`

---

# 1) config.json (전역 룰)

## 목적

* 방어 공식, 원소 상성, 치명타 배율, 링 슬롯 수 등 **밸런스/룰 파라미터를 전부 코드 밖으로**.

## 구조(스펙)

```json
{
  "version": 1,
  "defense_model": {
    "type": "CURVE", 
    "k": 0.02,
    "min_mult": 0.2
  },
  "crit": { "base_chance": 0.05, "mult": 1.5 },
  "accuracy": { "min_hit": 0.05, "blind_mult": 0.7 },
  "shield": { "bypass_defense": true },
  "equipment_rules": { "ring_slots": 2 },
  "elements": {
    "multiplier": {
      "fire": { "water": 0.8, "earth": 1.0, "air": 1.0, "fire": 1.0, "lightning": 1.0, "arcane": 1.0, "neutral": 1.0 },
      "water": { "fire": 1.2, "earth": 1.2, "lightning": 0.9, "water": 1.0, "air": 1.0, "arcane": 1.0, "neutral": 1.0 },
      "earth": { "water": 0.8, "lightning": 1.2, "...": 1.0 },
      "lightning": { "water": 1.2, "earth": 0.8, "...": 1.0 },
      "air": { "...": 1.0 },
      "arcane": { "...": 1.0 },
      "neutral": { "...": 1.0 }
    }
  }
}
```

### 방어 모델 타입 제안

* `LOL`: `mult = 100/(100+def)` (스케일 큰 게임용)
* `CURVE`: `mult = max(min_mult, 1/(1 + def*k))` (스케일 작은 게임용 권장)
* `LINEAR_CAP`: `mult = clamp(1 - def*k, min_mult, 1)`

---

# 2) skills.json

## 목적

* 스킬은 “풀(pool)”이고, 실제 사용 가능 여부는 **개체가 배정받은 스킬 목록**으로 결정.

## 구조(스펙)

```json
{
  "version": 1,
  "skills": {
    "<skill_id>": {
      "name": "표시명",
      "description": "플레이버 텍스트",
      "element": "fire",
      "category": "attack",
      "target": "enemy",
      "mp_cost": 10,

      "components": [
        {
          "kind": "damage",
          "damage_type": "magic",
          "terms": [
            { "stat": "constant", "coef": 15 },
            { "stat": "self_spell_power", "coef": 0.8 }
          ]
        }
      ],

      "status_effects": [
        { "status": "burn", "target": "enemy", "chance": 0.3, "duration_turns": 3 }
      ],

      "special_tags": ["TAG_ALWAYS_HIT"]
    }
  }
}
```

## terms.stat 허용 목록(필수)

* `constant`
* 자기: `self_attack | self_spell_power | self_defense | self_magic_resist | self_speed | self_hp | self_max_hp | self_missing_hp | self_mp | self_max_mp | self_missing_mp | self_spent_mp`
* 상대: `target_hp | target_max_hp | target_missing_hp | target_mp | target_max_mp | target_missing_mp | target_defense | target_magic_resist | target_speed`

> 이렇게 해두면 “처형/어벤저/마나버스트” 같은 걸 특수공식 없이 구현 가능.

---

# 3) enemies.json

## 목적

* 적은 “현재 스탯”을 저장하지 않는다.
* **1레벨 기준 스탯 + 성장률**로 계산한다.

## 구조(스펙)

```json
{
  "version": 1,
  "enemies": [
    {
      "id": "goblin",
      "name": "고블린",
      "tier": "minion",
      "element": "neutral",
      "base_level": 1,

      "base_stats": {
        "max_hp": 20, "max_mp": 0,
        "attack": 2, "spell_power": 0,
        "defense": 0, "magic_resist": 0,
        "speed": 3, "crit_chance": 0.02
      },

      "growth_per_level": {
        "max_hp": 2.1, "max_mp": 0.2,
        "attack": 0.3, "spell_power": 0.0,
        "defense": 0.05, "magic_resist": 0.02,
        "speed": 0.03
      },

      "rewards": { "xp": 5, "gold_from_xp_ratio": 0.8 },

      "skill_assignment": {
        "base": ["skill_scratch"],
        "by_level": [
          { "min_level": 10, "add": ["skill_throw_stone"] },
          { "min_level": 20, "add": ["skill_panic_shout"] }
        ]
      },

      "status_resistance": {
        "panic": { "mult": 1.0 },
        "freeze": { "mult": 1.0 }
      }
    }
  ]
}
```

### status_resistance 규칙

* 기본값(미기재): `mult = 1.0`
* 보스 정책 예:

  * boss: `panic.mult = 0` (면역)
  * elite: `panic.mult = 0.5` (확률 반감)

---

# 4) consumables.json

## 목적

* 소모품 효과는 “타입 기반 effect 리스트”로 통일.
* 후반에도 쓸모 있게 **고정 + % 혼합**을 기본 전략으로.

## 구조(스펙)

```json
{
  "version": 1,
  "consumables": {
    "<item_id>": {
      "name": "소형 체력 포션",
      "description": "…",
      "rarity": "common",
      "price": 30,
      "sell_ratio": 0.7,
      "usable_in_battle": true,

      "effects": [
        { "type": "heal_hp", "flat": 30, "percent_max_hp": 0.2 }
      ],

      "special_tags": []
    }
  }
}
```

## effects.type 목록(권장 최소)

* `heal_hp` `{flat, percent_max_hp}`
* `heal_mp` `{flat, percent_max_mp}`
* `add_shield` `{flat, percent_max_hp}`
* `remove_status` `{statuses:[...]}`  // burn/poison/bleed/freeze/stun/panic/blind
* `apply_status` `{status, chance, duration_turns, target}`
* `apply_buff` `{duration_turns, modifiers:[{stat, flat, percent}] }`
* `revive` `{percent_max_hp}` (필요 시)
* `escape_bonus` `{flat}` 또는 `{mult}`
* `perm_stats` `{mods:[{stat, flat}]}` (희귀 영구강화)
* `cast_skill` `{skill_id}` (스크롤류)

---

# 5) equipment.json

## 목적

* 슬롯/스탯 보정/세트/스킬 부여/특수효과를 **전부 데이터화**
* 장비 고유효과는 `special_tags`로 처리

## 구조(스펙)

```json
{
  "version": 1,
  "equipment": {
    "<equip_id>": {
      "name": "플레임 소드",
      "description": "…",
      "rarity": "rare",
      "slot": "weapon_1h",
      "price": 220,
      "sell_ratio": 0.7,
      "unique": false,

      "stats": {
        "flat": { "attack": 6, "speed": 1, "max_hp": 0, "max_shield": 0 },
        "percent": { "attack": 0.0, "spell_power": 0.0 }
      },

      "granted_skills": ["skill_flame_blade"],
      "set_name": "dragon_set",
      "special_tags": ["TAG_ELEMENT_DAMAGE_BONUS_FIRE_10"]
    }
  },

  "sets": {
    "dragon_set": {
      "name": "드래곤 세트",
      "pieces": ["dragon_helmet", "dragon_chest", "dragon_legs", "dragon_boots"],
      "bonuses": [
        { "pieces": 2, "stats": { "percent": { "max_hp": 0.05 } }, "special_tags": [] },
        { "pieces": 4, "stats": { "percent": { "all": 0.05 } }, "special_tags": ["TAG_BUFF_DRAGON_POWER"] }
      ]
    }
  }
}
```

### 무기 슬롯 검증 룰(필수)

* `weapon_2h` 장착 시 `weapon_1h` 장착 불가
* `weapon_1h`는 최대 2개

### ring 슬롯

* `config.json.equipment_rules.ring_slots`로 제한(2 또는 4)

---

# 6) specials.json (스페셜 태그 레지스트리)

## 목적

* “스탯/terms만으로 구현이 어려운 특수효과”를 태그로 분리
* 실행 타이밍(hook) 명시 → 전투 파이프라인에 끼워넣기 쉬움

## 구조(스펙)

```json
{
  "version": 1,
  "specials": {
    "TAG_ALWAYS_HIT": {
      "description": "필중",
      "hooks": ["pre_accuracy"],
      "params": {}
    },
    "TAG_ELEMENT_DAMAGE_BONUS_FIRE_10": {
      "description": "화염 속성 피해 +10%",
      "hooks": ["pre_damage"],
      "params": { "element": "fire", "mult": 1.1 }
    },
    "TAG_BUFF_DRAGON_POWER": {
      "description": "전투 중 드래곤의 힘 버프 부여",
      "hooks": ["on_battle_start"],
      "params": { "duration_turns": 5, "mods": [{ "stat": "attack", "percent": 0.1 }] }
    }
  }
}
```

## hooks 표준(권장)

* 전투 흐름: `on_battle_start | pre_action | pre_accuracy | pre_damage | pre_shield | post_damage | post_action | on_kill | on_death | on_turn_end`
* 장비/패시브: `on_equip | on_unequip`

---

# 7) save.json (저장 데이터)

## 구조(스펙)

```json
{
  "version": 1,
  "player": {
    "name": "…",
    "level": 12,
    "xp": 340,
    "gold": 210,
    "base_stats": { "max_hp": 80, "max_mp": 30, "attack": 10, "spell_power": 6, "defense": 4, "magic_resist": 3, "speed": 6 },
    "current": { "hp": 70, "mp": 18, "shield": 0 },
    "stat_points": 2
  },
  "progress": { "chapter": 3, "flags": { "met_npc_x": true } },
  "inventory": {
    "consumables": { "hp_potion_s": 3, "antidote": 1 },
    "equipment": ["iron_sword", "leather_boots"]
  },
  "equipped": {
    "helmet": null, "chest": "leather_armor", "cloak": null, "legs": null, "boots": "leather_boots",
    "rings": ["ring_ruby", null],
    "weapons": ["iron_sword", null]
  },
  "learned_skills": ["skill_fireball", "skill_heal"]
}
```

---

# 8) 로더 검증 규칙 + 에러 메시지 규격(강제)

## 에러 포맷(권장)

* `E####: <file>:<path> - <message> (hint: ...)`

예시:

* `E1001: skills.json:skills.skill_fireball.mp_cost - must be >= 0`

## 필수 검증 목록

### (A) 공통

* `E1000` JSON 파싱 실패
* `E1001` 필수 필드 누락
* `E1002` 타입 불일치(숫자 대신 문자열 등)
* `E1003` enum 값 불일치
* `E1004` id 중복

### (B) 참조 무결성

* `E2001` enemies가 참조하는 `skill_id`가 skills에 없음
* `E2002` equipment.granted_skills가 skills에 없음
* `E2003` consumables.cast_skill.skill_id가 skills에 없음
* `E2004` sets.pieces의 equip_id가 equipment에 없음
* `E2005` special_tags가 specials에 없음

### (C) 수치 범위

* `E3001` chance가 0~1 범위 밖
* `E3002` duration_turns < 1
* `E3003` base_stats.max_hp <= 0
* `E3004` growth_per_level에 음수(허용 여부 옵션: 일반적으로 금지)

### (D) 스킬 수식

* `E4001` component.kind 미지원
* `E4002` damage_type 미지원
* `E4003` terms.stat 미지원
* `E4004` components가 비어있는데 category가 attack/heal/shield인 경우(효과 없음)

### (E) 장비 규칙

* `E5001` slot 미지원
* `E5002` set_name이 있는데 sets에 정의 없음
* `E5003` bonuses.pieces가 1..N 범위 밖
* (런타임 검증) `weapon_2h` + `weapon_1h` 동시 착용 시도 → 사용자 메시지 출력(에러코드 없어도 됨)

---

# 9) 최소 예시 세트(“로드 성공” 스모크 테스트용)

## skills.json (최소 1개)

```json
{
  "version": 1,
  "skills": {
    "skill_fireball": {
      "name": "파이어볼",
      "description": "불꽃 구체를 날린다.",
      "element": "fire",
      "category": "attack",
      "target": "enemy",
      "mp_cost": 8,
      "components": [
        { "kind": "damage", "damage_type": "magic",
          "terms": [ { "stat": "constant", "coef": 12 }, { "stat": "self_spell_power", "coef": 0.8 } ]
        }
      ],
      "status_effects": [ { "status": "burn", "target": "enemy", "chance": 0.25, "duration_turns": 3 } ],
      "special_tags": []
    }
  }
}
```

## enemies.json (최소 1개)

```json
{
  "version": 1,
  "enemies": [
    {
      "id": "goblin",
      "name": "고블린",
      "tier": "minion",
      "element": "neutral",
      "base_level": 1,
      "base_stats": { "max_hp": 20, "max_mp": 0, "attack": 2, "spell_power": 0, "defense": 0, "magic_resist": 0, "speed": 3 },
      "growth_per_level": { "max_hp": 2.0, "attack": 0.3, "defense": 0.05, "speed": 0.03 },
      "rewards": { "xp": 5, "gold_from_xp_ratio": 0.8 },
      "skill_assignment": { "base": [], "by_level": [] }
    }
  ]
}
```

## consumables.json (최소 1개)

```json
{
  "version": 1,
  "consumables": {
    "hp_potion_s": {
      "name": "소형 체력 포션",
      "description": "체력을 회복한다.",
      "rarity": "common",
      "price": 30,
      "sell_ratio": 0.7,
      "usable_in_battle": true,
      "effects": [ { "type": "heal_hp", "flat": 30, "percent_max_hp": 0.2 } ],
      "special_tags": []
    }
  }
}
```

## equipment.json (최소 1개)

```json
{
  "version": 1,
  "equipment": {
    "iron_sword": {
      "name": "철검",
      "description": "무난한 한손검.",
      "rarity": "common",
      "slot": "weapon_1h",
      "price": 100,
      "sell_ratio": 0.7,
      "unique": false,
      "stats": { "flat": { "attack": 3 }, "percent": {} },
      "granted_skills": [],
      "set_name": null,
      "special_tags": []
    }
  },
  "sets": {}
}
```
