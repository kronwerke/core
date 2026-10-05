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
  ──────────────────────►  ████████████░░░  ────►  commands run for everyone
   obelisk, feeders, /kw deposit  boss bar               (Chapters, titles, rewards)
```

Whitelist slots work the same way in miniature: each streamer has a budget of invites and manages it in game, or through the [Discord bot](https://github.com/kronwerke/bot), which talks to this mod over RCON.

## Parts

| Part | What it does |
| --- | --- |
| `slot` | Slot budgets per streamer, invites and revokes, kept in sync with the vanilla whitelist |
| `goal` | Goals with tech and magic pillars, scaling by activity, hold point before the event, starter kits, boss bar |
| `obelisk` | The obelisk block, the deposit point at spawn and the feeder containers around it |
| `command` | `/kw` for players, streamers and admins, `/kw test` for test servers |
| `privacy` | Deletes rotated server logs after `logDays` (30), since they hold names and addresses |
| `lock` | Locked items the SevTech way: they can be picked up and carried, but not held, worn or used. One in a hand or armour slot is moved into the inventory (dropped only when it is full), with a line in the action bar saying which stage it belongs to; the tooltip says the same. Replaces Chapters' audit, which dropped every locked item and refused to pick it up again. On the client, locked items are veiled (a dark cover with a question mark, "???" as the name), and JEI shows the next stage's items veiled while later stages stay hidden |
| `compat` | On clients: hands Chapters' stage locks to JEI in one batch instead of one call per item, which froze joining for minutes |
| `config` | `kronwerke-common.toml`: default slots, whitelist enforcement, boss bar, chat announcements, feeder radius and interval, log retention |

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
      { "item": "create:brass_ingot", "base": 2000 },
      { "item": "create:precision_mechanism", "base": 150, "weight": 5 },
      { "item": "kronwerke:brass_heart", "base": 8, "fixed": true, "weight": 300 }
    ]},
    { "id": "magic", "title": "Magic", "items": [
      { "item": "botania:mana_pearl", "base": 600, "weight": 2 },
      { "item": "botania:terrasteel_ingot", "base": 50, "weight": 20 }
    ]}
  ],
  "stages": ["kronwerke:stage2"],
  "onComplete": ["say Stage 2 is open."],
  "starterKit": [ { "item": "create:brass_ingot", "count": 16 } ]
}
```

- **Pillars.** Every item of every pillar has to reach its target. An item is an id or a `#tag`.
- **Stages.** Chapters stage ids. Granted to everyone online when the goal completes, and to everyone else on their next login, so nobody misses a stage by being offline.
- **Scaling.** When a goal becomes active, each base amount is multiplied by `clamp(active / basePlayers, minFactor, maxFactor)`, where `active` is the number of players with at least `minHours` of play in the last `days` (all in the config). The targets are then fixed for that goal. `/kw admin goal rescale` recomputes them. Items with `"fixed": true` keep their base amount, for milestone items that every server builds the same number of.
- **Weight.** The bar counts points, not items: every item is worth its `weight` (1 when left out). A handful of hard milestone items can carry as much of a goal as thousands of ingots.
- **Hold point.** At `holdAt` of the points, the obelisk stops taking deposits and the boss bar turns purple. `/kw admin goal open <goal>` lifts the hold for the event; the last items go in and the goal completes.
- **Starter kit.** Given once to every player after the goal completes, including players who join later.

### The obelisk

The obelisk is a build: the `kronwerke:obelisk` core in the middle of a 5x5 plinth, a 3x3 step, four shaft segments and the crystal on top with a beacon style beam, eight blocks tall and unbreakable outside creative mode. An operator places the core (creative tab Operator Utilities, or `/give`) or runs `/kw admin obelisk build <pos>`, and the rest appears around it; breaking the core in creative mode removes the build. `/kw admin obelisk set <pos>` still turns any other block into the obelisk. Every block of the build takes deposits.

Machines feed the obelisk through the **intake** (`kronwerke:obelisk_intake`, functional blocks tab): a pedestal a player places within `feederRadius` of the core. Whatever a pipe, hopper or belt pushes into it goes into the goal under that player's name; what the goal does not want is refused, so the pipe keeps it. The old behaviour where any container next to the obelisk became a feeder is off (`obelisk.containerFeeders`).

- **Right click** it to hand in the stack in your hand. Sneak and right click to hand in everything in your inventory that the goal takes. When nothing fits, it says what it wants.
- **Feeders (off by default).** With `containerFeeders`, a chest, barrel or any other container placed within `feederRadius` blocks of the obelisk becomes a feeder of the player who placed it, up to `feedersPerPlayer`, drained every `feederInterval` seconds. The intake replaces this.
- The hold point applies to feeders too: at 98 percent they stop being emptied until the event.
- Players need build rights next to the obelisk to place a feeder. Admins can also set one with `/kw admin obelisk feeder <pos> <player>`.

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
| `/kw admin obelisk set <pos>\|clear\|info` | op | Where the obelisk is, and its feeders |
| `/kw admin obelisk feeder <pos> <player>`, `unfeeder <pos>` | op | Add or remove a feeder by hand |
| `/kw admin obelisk drain` | op | Empty the feeders now |
| `/kw admin bypass [on\|off] [player]`, `bypass list` | op | Every stage a goal grants, for testing; off takes back what the community has not earned yet. Without arguments it toggles your own |
| `/kw admin invite\|revoke <streamer> <player>` | op, the bot | Slots on behalf of a streamer; one line, `OK` or `ERR` |
| `/kw admin grant <player> [slots]` | op, the bot | Whitelist without a streamer's slot, with slots of their own (streamers, Season 1 players) |
| `/kw admin ungrant <player>` | op, the bot | Take that place back, and every slot the player gave |
| `/kw admin goals json` | op, the bot | Every goal with state, progress and top five, as `OK <json>` |
| `/kw test join\|leave\|give\|inv\|slots\|audit\|deposit\|kits\|list` | op, test servers | Server side test players, see below |

### Testing without a client

With `testCommands = true` in `kronwerke-common.toml`, `/kw test` creates test players that exist only as inventory and data: `/kw test join Anna`, `/kw test give Anna create:andesite_alloy 200`, `/kw test deposit Anna all`, `/kw test kits Anna`. That is how deposits, the hold point, completion, stages and starter kits are checked over RCON on a headless server. Never turn it on in a season.

## The menus

`/kw` opens the hub: the five goals with their bars, the running one with every item and the numbers, and buttons to deposit the hand or everything, the whitelist for streamers and the team screen for operators. `/kw team` is the team screen: every streamer with their slots (plus and minus change the allowance), the players in them, and per player a button to take the place away or to move them to the streamer typed in the field. Consoles get the text help from `/kw`.

`/kw menu` (also `/kw invite` or `/kw revoke` without a name) opens a screen on the client: the slots as a row, every invited player with head, name and online dot, a remove button, and a field to invite the next one. The screen talks to the server through two packets (`net/KwNetwork`); the text commands keep working for consoles and bots. `/kw` alone lists the commands with clickable lines.

## Bosses

`bosses.scaling` lists the bosses that grow with the group (the Cataclysm and Mowzie's bosses, the Chaos Guardian, the dragon and the wither by default). Every player near the boss beyond the first adds `healthPerPlayer` (60 percent) health and `damagePerPlayer` (15 percent) damage, capped at `maxPlayers`. With two or more players the boss uses three extra attacks every `attackInterval` ticks: lightning on one player, a shockwave around itself, and a rage that heals it and makes it hit harder for a few seconds. All of it runs on events; the boss mods stay untouched (Cataclysm's licence allows no derivatives). A player who comes within 96 blocks of the Chaos Guardian gets the four lines on how the fight works, once per session.

## The test world

`/kw testworld` (operators) teleports into `kronwerke:testworld`, a void dimension the pack defines, and builds the screenshot scenes on the first visit. `/kw testworld rebuild` builds them again (from the console too) and removes the scene mobs first; `/kw testworld back` returns to spawn. The pack lists the rows of boxes with their area in `data/kronwerke/shots/rows.json`; Core loads a row's chunks and runs its function, one row per server tick, so a rebuild on the live server only stutters briefly.

## Tab list and ranks

Ranks are scoreboard teams with a glyph prefix from the Nautical Ranks resource pack the modpack ships: owner (`tab.owners` in the config), admin (operators), streamer (players with whitelist slots) and member. The tab list header shows the season and `tab.line`, the footer the running goal and the player count.

## Spawn, join kit and locked dimensions

- `spawn.radius` (96) protects the square around the overworld spawn: no breaking or placing by players without op, no block damage from explosions, no mob griefing, no hostile spawns, no PvP. Intakes next to the obelisk stay allowed. 0 turns it off.
- `spawn.blastProof` lists blocks no explosion removes anywhere (bedrock, end portal frames and portals, end gateways and reinforced deepslate are always kept; the Cataclysm altars and boss respawners by default). Patterns with `*` work.

- Every player gets the `join.kit` items once, on the first join (a waystone and warp dust by default). The client asks for the language (Deutsch or English) after the first join and remembers the answer in its config.
- `dimensions.locked` lists dimensions as `dimension=stage`. A player without the stage cannot enter, by portal or by command; creative players can. The Nether opens with stage 2, the End with stage 4 by default.
- Items in goals and messages are shown by name, tags as "Bruchstein (alle Arten)".

## Planned

- A screen for the obelisk showing both pillars and the top contributors (needs the mod on clients).
- A screen for streamers to manage their slots.
- Rewards for the top contributors of each goal.

## Non-goals

- Replacing FTB Quests or Chapters. Which items a stage locks stays in Chapters' stage files; this mod only changes what a locked item does in the inventory.
- A general permission system. Operator levels are enough for this server.
- Anything client side that the server does not need. Screens are the exception.

## Status

Early. Slots and goals work and were tested on a dedicated server with the full pack: invite, revoke, deposit, pillars, hold point, release, completion, prerequisites, starter kits, admin commands, and the bot's RCON commands with the bot's own RCON client. The obelisk and its feeders were tested the same way: a chest next to it, filled over RCON, is emptied into the goal, stops at the hold point and goes on after `open`. The stage bypass was checked over RCON as far as it goes without a player online. Locked items were checked with test players: one in the hand moves into the inventory, and is dropped when the inventory is full. Right clicking, picking up, the action bar, the veiled items and JEI's next stage, and the bypass itself need a client and wait for the beta. No screens yet. Not yet used in a season.

## Docs

| Page | What |
| --- | --- |
| `CHANGELOG.md` | What changed per version |
| `config/kronwerke/goals.json` | Written on first start, the format is above |

## Licence

MIT. See `LICENSE`.

Made by [Elchi](https://github.com/Elchi-dev)
