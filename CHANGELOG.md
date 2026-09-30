# Changelog

## 0.4.2

- Joining still froze the client for about eight minutes after 0.4.1. The rest was Chapters hiding every recipe that makes a locked item, fluid or chemical: it asks each of JEI's 400 recipe types, once for all items and once per fluid, and at every stage opening once per unlocked item. Those lookups are skipped. Locked things stay out of JEI's list and the server still refuses to make them; a recipe for one can show up under the uses of an open item.
- The log line after the stage locks now says how long they took: `Stage locks applied in ... ms`.

## 0.4.1

- Joining took ten minutes with this pack and ended in a timeout: Chapters hides every locked item in JEI with a call of its own, and each call rebuilds JEI's list of 25 000 ingredients. A client mixin batches those calls, so the stage locks reach JEI in one removal and one addition per ingredient type, and each item's stacks are looked up in an index instead of the whole list. Opening a stage had the same problem the other way round.
- Core is therefore installed on clients too. On the client it only does this; everything else stays on the server.

## 0.4.0

- `/kw admin bypass`: an admin gets every stage a goal grants, to test locked items, recipes and dimensions before the community gets there. `off` takes back the stages no completed goal grants. The state is saved with the goals, `bypass list` shows who has it on.
- RCON connections no longer log two lines each ("Thread RCON Client ... started" and "... shutting down"). The launcher and the bot connect every minute, which flooded the console. Warnings and errors from RCON still show.
- Rotated server logs (`logs/*.log.gz`) are deleted after `privacy.logDays` (30) days, checked at every start. They hold the names and addresses of everyone who joined.

## 0.3.0

- The obelisk: any block at spawn, set with `/kw admin obelisk set <pos>`. Right click hands in the held stack, sneak and right click everything that fits. No new block, clients need nothing.
- Feeders: a container placed next to the obelisk counts for the player who placed it. Every two seconds the mod empties what the active goal can take, so factories can pay into the goal. Radius, feeders per player and interval are in the config.
- `/kw admin obelisk info|clear|feeder|unfeeder|drain`.
- Deposits can be credited to a player who is not online.

## 0.2.0

- Goals have pillars (tech, magic, anything) and every item of every pillar has to fill.
- Goal amounts scale with the number of active players when the goal becomes active. Play sessions are recorded for that.
- Hold point: a goal stops taking deposits at a set fraction until an admin opens it for the event.
- Starter kits per goal, given once to every player after completion, late joiners included.
- `/kw goals` shows pillars and per item progress. New admin commands: `goal open`, `goal rescale`, `active`.
- Goal progress is set per item: `/kw admin goal progress <goal> "<item>" <n>`.
- Chapters stages per goal, granted on completion and on login.
- `/kw admin invite|revoke <streamer> <player>` and `/kw admin goals json` for the Discord bot over RCON. Answers start with `OK` or `ERR`.
- `/kw admin grant <player> [slots]` and `/kw admin ungrant <player>`: a place on the whitelist without a streamer's slot, with slots of the player's own (the config default when left out). For streamers and Season 1 players. Taking it back also frees every slot the player gave.
- `/kw test`: server side test players for exercising goals without a client. Off unless `testCommands` is set.

## 0.1.0

First version.

- Whitelist slots per streamer: `/kw invite`, `/kw revoke`, `/kw slots`. Admins set base and bonus slots.
- Community goals from `config/kronwerke/goals.json`: item or tag, amount, prerequisites, commands on completion.
- `/kw deposit`, `/kw goals`, `/kw top`, boss bar with the active goal.
- The vanilla whitelist is turned on at start when `enforceWhitelist` is set.
