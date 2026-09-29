```
 _  __                                 _        
| |/ /_ __ ___  _ ____      _____ _ __| | _____ 
| ' /| '__/ _ \| '_ \ \ /\ / / _ \ '__| |/ / _ \
| . \| | | (_) | | | \ V  V /  __/ |  |   <  __/
|_|\_\_|  \___/|_| |_|\_/\_/ \___|_|  |_|\_\___|
                                                
                          c o r e
```

**The server mod behind the Kronwerke community server: whitelist slots for streamers, goals the whole server works on, and progression that unlocks in stages.**

[![ci](https://github.com/kronwerke/core/actions/workflows/ci.yml/badge.svg)](https://github.com/kronwerke/core/actions/workflows/ci.yml)
![status](https://img.shields.io/badge/status-early-orange)
![java](https://img.shields.io/badge/java-21-blue)
![neoforge](https://img.shields.io/badge/neoforge-1.21.1-blue)
![licence](https://img.shields.io/badge/licence-MIT-green)
[![made by](https://img.shields.io/badge/made%20by-Elchi-black)](https://github.com/Elchi-dev)

## Overview

Kronwerke is a Minecraft server where a handful of streamers each bring some of their viewers. Season 1 ran on a hybrid server with plugins and fell over regularly, and the players who knew modded Minecraft were at the endgame while everyone else was still lost. This mod replaces the plugins and fixes the second problem: the server progresses as a community.

## The trick

Nothing important is unlocked by an individual. The server has a list of goals, everyone can pay into the current one, and when it is reached the next stage opens for every player at once.

```
  players deposit items          goal reached          stage unlocked
  ─────────────────────►  ████████████░░░  ────►  commands run for everyone
   /kw deposit, spawn block     boss bar                 (Chapters, titles, rewards)
```

Whitelist slots work the same way in miniature: each streamer has a budget of invites and manages it in game, or through the [Discord bot](https://github.com/kronwerke/bot), which talks to this mod over RCON.

## Parts

| Part | What it does |
| --- | --- |
| `slot` | Slot budgets per streamer, invites and revokes, kept in sync with the vanilla whitelist |
| `goal` | Goals with tech and magic pillars, scaling by activity, hold point before the event, starter kits, boss bar |
| `command` | `/kw` for players, streamers and admins, `/kw test` for test servers |
| `config` | `kronwerke-common.toml`: default slots, whitelist enforcement, boss bar, chat announcements |

## Quick look

```
./gradlew build
cp build/libs/kronwerke-*.jar /path/to/server/mods/
```

On first start the mod writes `config/kronwerke/goals.json` with the first two stages. A goal looks like this:

```json
{
  "id": "stage2",
  "title": "The Brass Engine",
  "description": "Brass and mana. Machines that work while you sleep.",
  "requires": ["stage1"],
  "holdAt": 0.98,
  "scale": true,
  "pillars": [
    { "id": "tech", "title": "Tech", "items": [
      { "item": "create:brass_ingot", "base": 4000 },
      { "item": "create:precision_mechanism", "base": 300 }
    ]},
    { "id": "magic", "title": "Magic", "items": [
      { "item": "botania:mana_pearl", "base": 1500 },
      { "item": "botania:terrasteel_ingot", "base": 100 }
    ]}
  ],
  "stages": ["kronwerke:stage2"],
  "onComplete": ["say Stage 2 is open."],
  "starterKit": [ { "item": "create:brass_ingot", "count": 16 } ]
}
```

- **Pillars.** Every item of every pillar has to reach its target. An item is an id or a `#tag`.
- **Stages.** Chapters stage ids. Granted to everyone online when the goal completes, and to everyone else on their next login, so nobody misses a stage by being offline.
- **Scaling.** When a goal becomes active, each base amount is multiplied by `clamp(active / basePlayers, minFactor, maxFactor)`, where `active` is the number of players with at least `minHours` of play in the last `days` (all in the config). The targets are then fixed for that goal. `/kw admin goal rescale` recomputes them.
- **Hold point.** At `holdAt` of the total, the obelisk stops taking deposits and the boss bar turns purple. `/kw admin goal open <goal>` lifts the hold for the event; the last items go in and the goal completes.
- **Starter kit.** Given once to every player after the goal completes, including players who join later.

| Command | Who | What |
| --- | --- | --- |
| `/kw invite <player>` | streamer | Whitelist a viewer using one of your slots |
| `/kw revoke <player>` | streamer | Remove a viewer you invited |
| `/kw slots` | streamer | Your slots and who has them |
| `/kw deposit [all]` | everyone | Pay the item in your hand (or your whole inventory) into the active goal |
| `/kw goals` | everyone | All goals and their progress |
| `/kw top [goal]` | everyone | Top contributors |
| `/kw admin slots <streamer> <n>` | op | Set base slots |
| `/kw admin bonus <streamer> <n>` | op | Add or remove bonus slots |
| `/kw admin list` | op | All streamers and their invites |
| `/kw admin goal reload\|complete\|reset\|open\|rescale` | op | Manage goals |
| `/kw admin goal progress <goal> "<item>" <n>` | op | Set the progress of one item (quote the item id) |
| `/kw admin active` | op | How many players count as active for scaling |
| `/kw admin invite\|revoke <streamer> <player>` | op, the bot | Slots on behalf of a streamer; one line, `OK` or `ERR` |
| `/kw admin goals json` | op, the bot | Every goal with state, progress and top five, as `OK <json>` |
| `/kw test join\|leave\|give\|inv\|deposit\|kits\|list` | op, test servers | Server side test players, see below |

### Testing without a client

With `testCommands = true` in `kronwerke-common.toml`, `/kw test` creates test players that exist only as inventory and data: `/kw test join Anna`, `/kw test give Anna create:andesite_alloy 200`, `/kw test deposit Anna all`, `/kw test kits Anna`. That is how deposits, the hold point, completion, stages and starter kits are checked over RCON on a headless server. Never turn it on in a season.

## Planned

- The obelisk: a block at spawn to deposit into, with a screen showing both pillars and the top contributors, and an item input side so factories can feed it.
- A screen for streamers to manage their slots.
- Rewards for the top contributors of each goal.

## Non-goals

- Replacing FTB Quests or Chapters. This mod triggers them, it does not lock items itself.
- A general permission system. LuckPerms does that.
- Anything client side that the server does not need. Screens are the exception.

## Status

Early. Slots and goals work and were tested on a dedicated server with the full pack: invite, revoke, deposit, pillars, hold point, release, completion, prerequisites, starter kits, admin commands, and the bot's RCON commands with the bot's own RCON client. No screens and no obelisk block yet. Not yet used in a season.

## Docs

| Page | What |
| --- | --- |
| `CHANGELOG.md` | What changed per version |
| `config/kronwerke/goals.json` | Written on first start, the format is above |

## Licence

MIT. See `LICENSE`.

Made by [Elchi](https://github.com/Elchi-dev)
