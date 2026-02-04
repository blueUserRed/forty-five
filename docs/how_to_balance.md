
# How to balance

_This document explains how to adjust run/encounter generation, difficulty, enemies and 
related properties_

## Terminology
- **major difficulty:** <br />
    an integer >= 0 describing the difficulty of an encounter/area/run. Enemies are chosen based
    on the major difficulty, so e.g. an encounter with major difficulty 2 could have one difficulty 2
    enemy or two difficulty 1 enemies.
    The major difficulty may be adjusted in the generation progress. For example, if there are
    difficult modifiers on an encounter, the major difficulty may be lowered by one.
- **unadjusted major difficulty:** <br />
    Like the major difficulty, but it is never adjusted during the generation progress. The unadjusted major
    difficulty of an encounter will always match the major difficulty of the area it was generated from.
    It is useful for controlling when certain elements (enemies, modifiers, etc.) unlock, e.g. when you
    want an enemy group to spawn only after the player has reached the second area. It controls which enemy groups or
    run/encounter modifiers are allowed to spawn, which rewards the player can get, which cards can
    appear in shops, etc.
- **minor difficulty** <br />
    a floating point number, where 0 < f < 2. It can be understood as a multiplier, meaning a minor difficulty
    of 1 would leave the encounter unchanged, a difficulty less than one would make it easier, and a difficulty
    greater than one makes the encounter harder. It mainly affects how much health the enemy has, how much
    damage it deals and how much shield it can give itself. It does not affect special actions of
    enemies. The game uses the minor difficulty to counterbalance modifiers that make an encounter easier/harder,
    or to make encounters more difficult as the player gets near the end of a road. It can also increase the 
    difficulty of an encounter, when no difficult enough enemies are available
    (should only happen when major difficulty > 12).
- **enemy groups/variants** <br />
    each enemy group (e.g. "Witch", "Outlaw") consists of multiple variants (e.g. "Outlaw-1" or "Witch-2").
    Each variant of the group has associated difficulty. The encounter generator picks enemy variants so that
    the sum of their difficulties matches the major difficulty of the encounter. While the minor difficulty can
    only change enemies by modifying their health/damage, this system allows for more interesting changes as the
    major difficulty increases. For example, enemy variants can differ in all their properties, including e.g.
    special actions.


## run_generator_config.onj

This file contains most of the available configuration for the encounter/run generation.

It can be found at: assets/config/run_generator_config.onj <br />
schema file: assets/onjschemas/run_generator_config.onjschema

### Configuring Run Modifiers

<br />

#### Run Modifier Pools:
onj key:
```
runModifierPools: [
    {
        majorDifficulty: int,
        modifierProbability: float,
        modifiers: string[]
    },
    ...
],
```
Run Modifier pools control what run modifiers a run of a given unadjusted major difficulty can have
and how likely they are.

Each pool is defined for a (unadjusted) major difficulty. If there isn't a pool for a certain difficulty,
the game will fall back onto the next lowest difficulty. <br />
`modifiers`: the modifiers available at this difficulty. <br />
`modifierProbability` (between 0 and 1): how likely a modifier is to spawn. The probability is rolled
n times, where n is the maximum amount of modifiers a run can have.

<br />

#### Increasing likelihood of a Run Modifier spawning in a biome:

onj key:
```
runModifierProbabilityIncreases: [
    {
        biome: string,
        makeMoreLikely: string[]
    },
    ...
],
```
`makeMoreLikely` is a list of all run modifiers that are more likely to appear in `biome`.
A modifier that appears once in the list is twice as likely to appear, if a modifier is added twice,
it is three times as likely to appear, etc.
This does **not** make a run more likely to have a modifier, it just makes a run that already gets a 
modifier more likely to get one out of the matching biome.

<br />

####  Blacklisting run modifier combinations

onj key:
```
runModifierBlacklist: string[][],
```
`runModifierBlacklist` is typically a list of pairs, but the arrays can have an arbitrary length.

If two run modifiers are in the same list, that means that they can never spawn together.

<br />

#### maximum amount of run modifiers

onj key:
```
runModifiersMax: int,
```

The maximum amount of modifiers a run can have.

<br />

### Configuring Run rewards

<br />

#### Run reward pools

onj key:
````
runRewardPools: [
    {
        majorDifficulty: Int,
        maxRewards: Int,
        rewardProbability: float,
        rewards: $RunReward[][] // 2-Dimensional Array!
    }
],
````
Reward pools control what and how many rewards a run of a given unadjusted major difficulty can have.

Each pool is defined for a (unadjusted) major difficulty. If there isn't a pool for a certain difficulty,
the game will fall back onto the next lowest difficulty. <br />
``maxRewards``: specifies how many rewards a run of a given difficulty can have. <br />
`rewardProbability`: (between 0 and 1): how likely it is that a reward is generated. The probability is rolled
``maxRewards`` times. <br />
``rewards``: The first dimension of the array specifies different types of rewards. The second specifies different
variants of the same reward. Each type of reward can only spawn once per run. For example, one type of reward
may be the Cash-Reward. Different variants of the Cash-Reward could specify different amounts of cash the player
when they complete the run. If a run already has a reward from the cash-group, it can't get a second one from the
same group, so a run with two cash rewards is impossible.

<br />

### Configuring Encounter modifier

<br />

#### Encounter Modifier pools

onj key:
````
encounterModifierPools: [
    {
        majorDifficulty: int,
        modifierProbability: float,
        maxModifiers: int,
        modifiers: string[]
    },
    ...
],
````
Encounter Modifier pools control what encounter modifiers an encounter of a given unadjusted major difficulty can have
and how likely they are.

Each pool is defined for a (unadjusted) major difficulty. If there isn't a pool for a certain difficulty,
the game will fall back onto the next lowest difficulty. <br />
`modifiers`: the modifiers available at this difficulty. <br />
``maxModifiers``: how many modifiers an encounter at this difficulty can have. <br />
`modifierProbability` (between 0 and 1): how likely a modifier is to spawn. The probability is rolled
``maxModifiers`` times. <br />
If an encounter already has encounter modifier (e.g. because a run modifier generated them), the probability
will only be rolled ``maxModifiers`` - n times, where n is the amount of modifiers already present.

<br />

####  Blacklisting encounter modifier combinations

onj key:
```
encounterModifierBlacklist: string[][],
```
`encounterModifierBlacklist` is typically a list of pairs, but the arrays can have an arbitrary length.

If two encounter modifiers are in the same list, that means that they can never spawn together.

<br />

### Enemy Spawn Configuration

<br />

#### Enemy Pools

onj key:
````
enemies: [
    {
        majorDifficulty: int,
        amount: int[2],
        allowedEnemies: string[],
    },
    ...
]
````
Enemy pools control what enemy groups can spawn at a given unadjusted major difficulty, and how many enemies an
encounter can have.

Each pool is defined for a (unadjusted) major difficulty. If there isn't a pool for a certain difficulty,
the game will fall back onto the next lowest difficulty. <br />
``amount``: a range of numbers, from which the amount of enemies is randomly picked. The lower bound must be at least
one, the upper bound must be at most three. <br />
``allowedEnemies``: A list of enemy groups that can spawn at the given unadjusted major difficulty.

<br />

#### Aside: The enemy selection algorithm

This sections aims to explain how the game selects the enemies for an encounter, so that the next configuration
parameters can be more easily understood.


> [!NOTE]
> Some details have been left out.

<br />

**Step 1:** The game looks up what enemy groups are allowed to appear based on the current unadjusted major
difficulty and stores all their variants in a list. <br />
**Step 2:** The game randomly selects the amount of enemies for the encounter based on the range specified in the
pool. <br />
**Step 3:** Let n be the amount of enemies for the encounter. The game generates all combinations of n
enemy variants such that their difficulties add up the (adjusted!) major difficulty. E.g. If n is 2, and the difficulty
is 3, possible combinations would be: (Pyro-2, Outlaw-1), (Outlaw-2, Outlaw-1), (Witch-1, Pyro-2), etc. <br />
**Step 4:** The game awards each configuration points, based on how 'interesting' it is, plus some randomness.
(more blow) <br />
**Step 5:** The configuration with the most points is selected as the winner and the enemies are used for the encounter.

<br />

**How points are awarded:** <br />
A configuration where all enemies are from different groups receives 100 points, if they are all from the same group,
0 points are awarded. For a configuration of three enemies, if two match, but not three, 50 points are awarded. <br />
Points are also awarded if the enemy variants have different difficulties, based on the same schema as above. The points
a configuration can achieve are: 40, 20, or 0. <br />
Bonus points are awarded if a specific enemy appears in a specific biome. This can be configured. <br />
Each configuration receives a random amount of bonus points. This can also be configured.

<br />

#### Enemy - Biome bonus points

onj key:
````
enemyProbabilityIncrease: [
    {
        enemy: string,
        biome: string,
        bonusPoints: int,
    },
    ...
],
````
Configures how many bonus points an enemy generates when they appear in a specific biome. <br />
``enemy`` is the name of an enemy group, not the variant!

<br />

#### Configuring the randomness of enemy selection

onj key:
````
enemyProbabilityRandomPoints: int,
````

Each enemy configuration will be awarded random points in the range from 0 to ``enemyProbabilityRandomPoints``.
If this property is set to 0, the enemy configurations will be perfectly deterministic. This will, however, result
in quite boring encounters, because the game will always pick the 'ideal' configuration, which will always be the same
under the same circumstances. Adding some randomness breaks this pattern up and allows less 'ideal' configurations to
be picked as well.

<br />

#### Configuring how the minor difficulty affects enemies

onj keys:
`````
enemyHealthAdjustment: float,
enemyDamageAdjustment: float,
`````

Both numbers are generally in the range from 0 to 1. <br />
These properties control how aggressively the health/damage of an enemy changes as the minor difficulty changes.
<br />
Changes to the health/damage are achieved by multiplying them with a specific factor. The factor is calculated with
the formula below:
````
1 + ((minorDifficulty - 1) * adjustment)
````
where ``adjustment`` is the either ``enemyHealthAdjustment`` or ``enemyDamageAdjustment``, based on the context.
The higher the adjustment-value, the more impact the minor difficulty has on the enemies.

#### Difficulty Scaling

onj key:
````
difficultyScaling: {
    limited: {
        relativeMin: float,
        relativeMax: float,
        scaling: $DifficultyScaling,
    },
    constructed: {
        relativeMin: float,
        relativeMax: float,
        scaling: $DifficultyScaling,
    },
}
````
The difficulty scaling can be configured separately for limited and constructed runs. <br />
``relativeMin`` is the adjustment at the start of the run, ``relativeMax`` is the adjustment at the end of the
run. All encounters between the start and the end will have a value between the two. The value is added to the minor
difficulty. <br />
``scaling`` dictates how the values of the encounters between the start and the end are calculated. There are currently
two options: 
- ``$LinearScaling``: difficulty increases linearly from the start to the end. Formula: ``(max - min) * percent + min``
- ``$PowerScaling``: difficulty stays low for most of the run and then increases sharply towards the end. This Scaling
    option takes an additional parameter ``power``. The higher the value of ``power``, the more pronounced this 
    behaviour will be. If power == 1, the scaling will be linear again. Formula: ``(max - min) * percent ^ power + min``.

<br />

## enemies.onj

This file configures which enemies exist, their properties and how they behave.

It can be found at: assets/config/enemies.onj <br />
schema file: assets/onjschemas/enemies.onjschema

<br />

### Enemy Groups

onj key:
````
enemyGroups: [
    {
        groupName: string,
        variants: [
            {
                name: string,
                majorDifficulty: int
            },
            ...
        ]
    },
    ...
],
````

This array specifies what enemies groups and what variants they have. Each variant also specifies its difficulty.

### Modifying enemy variants

The file contains three variables with arrays of enemy variants, one for the Outlaw, the Pyro and the Witch. If a new
enemy variant is added here, it must also be added to its group in ``enemyGroups``.
<br />
Here are some important keys for balancing:
- ``baseHealth``: the health of the enemy (can be adjusted according to the minor difficulty)
- ``brain``: controls the behaviour of the enemy. ``$NewEnemyBrain`` for all enemies currently in the game.
    Some keys of the brain are laid out below.
- ``normalSpecialActionWeight`` (between 0 and 1): how likely the enemy is to perform a normal action instead of a special action
    (higher number -> more normal actions). When deciding what action an enemy performs, this is rolled first.
- ``damageShieldWeight`` (between 0 and 1): if the game rolled in favor of a normal actions, this decides how likely the enemy is to deal
    damage instead of giving itself shield. (higher number -> more likely to do damage)
- ``baseDamage``: a range of numbers from which the game randomly picks the amount of the damage the enemy deals.
    (can be adjusted according to the minor difficulty)
- ``baseShield``: a range of numbers from which the game randomly picks the amount of shield the enemy will give itself.
    (can be adjusted according to the minor difficulty)
- ``actions``: the special actions the enemy can perform.

### Configuring the special actions

Some important keys for balancing:
- ``weight``: The higher the weight, the more likely the enemy is to perform the action (if the
    ``normalSpecialActionWeight`` was rolled in favor of a special action). If two actions have the same weight, they
    are equally likely. If one has twice the weight, it is two times more likely, etc.
- ``showProbability`` (between 0 and 1): how likely the action is to be shown instead of hidden.
- ``maxExecutions`` (optional key): how often the action can be executed at most in an encounter. If the key is absent,
    the action can be executed an arbitrary amount of times.
- ``action``: the action itself.

<br />
<br />

------------------

<br />

### Important:

These configurations do ***not*** affect special runs (like the tutorial) or progress runs. <br />
These runs are designed and configured manually. 
<br />
<br />
The files for these runs can be found here: <br />
assets/maps/runs
assets/maps/static_maps
