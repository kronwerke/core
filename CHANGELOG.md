# Changelog

## 0.2.0

- Goals have pillars (tech, magic, anything) and every item of every pillar has to fill.
- Goal amounts scale with the number of active players when the goal becomes active. Play sessions are recorded for that.
- Hold point: a goal stops taking deposits at a set fraction until an admin opens it for the event.
- Starter kits per goal, given once to every player after completion, late joiners included.
- `/kw goals` shows pillars and per item progress. New admin commands: `goal open`, `goal rescale`, `active`.
- Goal progress is set per item: `/kw admin goal progress <goal> "<item>" <n>`.

## 0.1.0

First version.

- Whitelist slots per streamer: `/kw invite`, `/kw revoke`, `/kw slots`. Admins set base and bonus slots.
- Community goals from `config/kronwerke/goals.json`: item or tag, amount, prerequisites, commands on completion.
- `/kw deposit`, `/kw goals`, `/kw top`, boss bar with the active goal.
- The vanilla whitelist is turned on at start when `enforceWhitelist` is set.
