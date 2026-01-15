# Desia_alternative

텍스트 RPG 프로토타입을 위한 데이터 중심 설계 리포지토리입니다.

## IntelliJ 프로젝트 시작 가이드 (Java)

이 저장소는 **Gradle 기반 Java 프로젝트 구조**를 사용합니다. IntelliJ에서 프로젝트 루트를 열면 자동으로 인식됩니다.

### 1) 프로젝트 폴더 구조

```
Desia_alternative/
├─ build.gradle
├─ settings.gradle
├─ data/                       # 게임 데이터(JSON)
├─ src/
│  └─ main/
│     └─ java/
│        └─ com/desia/game/
│           ├─ Main.java
│           ├─ io/
│           │  ├─ DataPaths.java
│           │  └─ JsonLoader.java
│           ├─ model/
│           │  ├─ ConfigData.java
│           │  ├─ SkillsData.java
│           │  ├─ EnemiesData.java
│           │  ├─ ConsumablesData.java
│           │  ├─ EquipmentData.java
│           │  └─ SpecialsData.java
│           └─ validation/
│              ├─ DataValidator.java
│              ├─ ValidationError.java
│              └─ ValidationResult.java
```

### 2) 데이터 파일 배치 규칙

`data/` 폴더에 **PLAN1-2 스펙을 따르는 JSON** 파일을 둡니다. 실행 시 `./data`를 읽으므로,
**반드시 저장소 루트에서 실행**해야 합니다.

* `data/config.json`
* `data/skills.json`
* `data/enemies.json`
* `data/consumables.json`
* `data/equipment.json`
* `data/specials.json`

### 3) IntelliJ에서 실행

1. IntelliJ → **Open** → 저장소 루트 선택.
2. Gradle 프로젝트로 로드(자동 감지).
3. `src/main/java/com/desia/game/Main.java` 실행.

> 실행 시 `Validation OK` 로그가 출력되면 데이터 로딩/검증이 정상입니다.

## 데이터 샘플

`data/` 폴더에 PLAN1-2 스펙을 따른 최소 샘플을 추가했습니다.

* `data/config.json`
* `data/skills.json`
* `data/enemies.json`
* `data/consumables.json`
* `data/equipment.json`
* `data/specials.json`

현재 JSON 로딩은 **내장 파서**를 사용하므로 외부 라이브러리 없이 동작합니다.

다음 단계는 전투 코어 엔진(텍스트 버전) 기반 구현입니다.

## 챕터 진행 구조

각 챕터는 **12개의 진행도(1/12 ~ 12/12)** 로 구성되며, 진행도 타입은 전투/상점/이벤트 중 하나입니다.
단, **마지막 12/12는 반드시 보스 전투**가 되도록 강제합니다.
