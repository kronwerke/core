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

Whitelist slots work the same way in miniature: each streamer has a budget of invites and manages it in game.

## Parts

| Part | What it does |
| --- | --- |
| `slot` | Slot budgets per streamer, invites and revokes, kept in sync with the vanilla whitelist |
| `goal` | Goals loaded from `config/kronwerke/goals.json`, progress and contributions saved with the world, boss bar |
| `command` | `/kw` for players, streamers and admins |
| `config` | `kronwerke-common.toml`: default slots, whitelist enforcement, boss bar, chat announcements |

## Quick look

```
./gradlew build
cp build/libs/kronwerke-*.jar /path/to/server/mods/
```

On first start the mod writes `config/kronwerke/goals.json` with two example goals. A goal looks like this:

```json
{
  "id": "age1_cobble",
  "title": "Foundation of the Kronwerk",
  "description": "Bring cobblestone to the spawn.",
  "item": "#c:cobblestones",
  "amount": 10000,
  "requires": [],
  "onComplete": ["chapters grant @a age1"]
}
```

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
| `/kw admin goal reload\|complete\|reset\|progress` | op | Manage goals |

## Planned

- A block at spawn to deposit into, with a screen showing progress and the top contributors.
- A screen for streamers to manage their slots.
- Discord: membership check on join, whitelist sync, goal announcements.
- Rewards for the top contributors of each goal.

## Non-goals

- Replacing FTB Quests or Chapters. This mod triggers them, it does not lock items itself.
- A general permission system. LuckPerms does that.
- Anything client side that the server does not need. Screens are the exception.

## Status

Early. Slots and goals work and were tested on a dedicated server with the full pack: invite, revoke, deposit, completion, prerequisites, admin commands. No screens yet. Not yet used in a season.

## Docs

| Page | What |
| --- | --- |
| `CHANGELOG.md` | What changed per version |
| `config/kronwerke/goals.json` | Written on first start, the format is above |

## Licence

MIT. See `LICENSE`.

Made by [Elchi](https://github.com/Elchi-dev)
