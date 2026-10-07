# Changelog

## 0.20.0

More of the shared network.

- Functional Storage's Ender Drawers: a frequency linked on main and on a side world is one drawer whose contents live on main. The side world keeps a stack (or half the drawer) at hand and main tops it up when it runs low; what goes in there beyond half again as much goes home. Before the side world stops or is reset, everything goes home. Creative drawers never give across.
- Mekanism's QIO: a QIO frequency on a side world with the same name, owner and security as one on main sends everything in it home, as much as main has room for. Before the side world stops, all of it.
- Waystones: main's waystones are in a side world's list (the ones the player has found on main). Choosing one moves the player home, standing at that waystone, free like the portal. In the side world's list they carry a dimension of their own, so a jump there can never land inside the side world.
- Moves to a named place: a layer can tell main where an arriving player stands (used by the waystones).

## 0.19.0

The shared network: the mods' own wireless things work between the servers of the network (`sync.players` on both). Each mod has a layer that only loads when the mod is there.

- Mekanism's Quantum Entangloporter: a frequency with the same name, owner and security that has entangloporters on two servers is one frequency. Items, fluids, chemicals and energy flow to the side whose entangloporters empty the buffer and otherwise even out; heat evens out.
- AE2's Quantum Network Bridge: a ring with one singularity of a pair on main and one with the other on a side world links the networks. The side world sees main's ME storage as a drive of its own and can take out of it (fetched first, a stack ahead after that; unused things go back after a minute) and put into it (sent at once; what main has no room for stays usable on the side world for a while). Channels, autocrafting and each side's own storage stay where they are. Before a side world stops or is reset, everything it holds goes back to main.
- Flux Networks: main's networks are made on the side worlds with the same number, name, colour, owner, members and password, and follow main's changes. Plugs on one server feed points on the other that still want energy, storages give to the other side's points, and what plugs have left fills the other side's storages. Networks made on a side world get numbers from a million up.
- Powah's Ender Cells and Ender Gates: a player's channel with cells on two servers is one channel; energy flows to the side that draws from it and otherwise evens out.
- FTB Quests: a player's team progress travels with them, and what they do on a side world (tasks, completed quests, claimed rewards) comes back into their team on main. While they are away both sides exchange it every ten seconds, each taking the larger progress and every claimed reward of the other, so nothing can be claimed twice.
- Nothing doubles and nothing is lost on the way: what a server gives has already left its world and waits in a saved outbox until the other answers. Gives are numbered per start of the server and taken exactly once and in order; a world that was reset sends back everything it never took.
- `/kw share` (operators) shows each layer, the outbox and the other servers. `/kw move <players>` (operators) sends players to the portal's other side without the portal.

## 0.18.0

The way into the mining world, and players moving between the servers of the network (launcher 0.7.0 or later, `public.host` set, `transfers=true` and `sync.players=true` on both servers).

- The Grubenrahmen: dark slate bound in riveted iron with blue crystal veins, textures with labPBR maps for shader packs (normal, height, metal, glowing veins). In the pack, connected textures through Athena.
- The Minenportal: a frame of Grubenrahmen like the Nether's (inside 2 to 21 wide, 3 to 21 high), lit with a source gem (an amethyst shard without Ars Nouveau). Blue light, gold sparks, its own sound.
- Standing in the portal for two and a half seconds moves a player to the other server: the player file (inventory, ender chest, effects, every mod's attachments), advancements, statistics and Sophisticated Backpacks' contents are handed over, and only when the target has written them is the client sent there (Minecraft's transfer). From main the portal leads to the side world (`network.portalTarget`, default mining), from a side world home to main, in front of the portal the player went through.
- The way there is drawn: light streams up and a beam comes down while the portal charges, then stars rushing past toward the name of the target for as long as the reconnect takes, then a white flash that clears over the new world.
- A side world builds its arrival place once per world at its spawn: a platform with a portal home. Its spawn protection only covers that place.
- A side world lets in only the players it expects (operators may always). It sends a player's state home every 30 seconds, when they log out there and when it stops, so main always has the newest; main keeps its own waystones (`network.keepOnMain`).
- Items thrown into the portal come out on the other side: at the arrival place, or at home in front of the thrower's portal.
- A world reset by the launcher sends everyone home first. The tab list counts down to the next reset.
- Tiefgräber get Haste in the mining world.
- The advancement "Ab in die Minenwelt" for the quest book.
- Fix: the obelisk's effects of one server no longer show in another server's world after a move.

## 0.17.0

One network of servers through the launcher's bus (launcher 0.5.1 or later, `bus.port` set, the servers in one `network`). What is shared is set in the launcher's console.

- Chat travels to the other servers with the sender's server in front, in its colour, and the rank glyph. With `sync.chat=radius` only players within the radius hear a line.
- Joins and leaves show on the other servers, in each player's language.
- The tab list shows every player of the network: the others with their rank, skin, ping and their server's name. The footer counts the whole network and the players here.
- Roles: the launcher's `-Dlauncher.role`. `main` (or no launcher) owns season, goals, slots and the obelisk as before. Any other role is a side world: no obelisk, no season catch up, no goal tracking, `/kw` only says where things are, the slot menu points to main. main sends it the streamers and the running goal for its tab list.
- A side world sends away players who join it directly (operators may); `network.directJoin` opens it.

## 0.16.1

- `kw admin roster json`: every streamer and granted player with their slots, override, bonus and invited players, for the web console.
- Deposits in `kw admin goals json` carry the plain player name, without the rank in front.
- The rite lifts players more calmly: the vertical pull is damped and the wave is small, so the circling no longer bobs up and down.

## 0.16.0

- The rite is ninety seconds in five acts. Stillness: the hum stops, black bars close in, the picture darkens, a riser swells. The pull: the sky tears, the beam comes down out of the tear, blocks of the ground around the plinth are torn up and spiral round the beam faster and higher (the clients draw copies of the real blocks there; the ground itself stays), players within 28 blocks lift off and circle the beam, the air around the crystal bends like light round a heavy star (`shaders/post/lens`), the view is drawn in. The burst: everything is drawn in and stops, then the crystal bursts in slow motion, the obelisk breaks apart in the air, the spiral is flung out, the players are thrown outward and sink down slowly, the view is punched out. The return: the pieces of the obelisk fly back and join, the new tier is built, arcs of light leap from the pylons to the crystal, the players and the ground. The galaxy: the roll call, the fanfare, five volleys of fireworks; the torn sky stays open longer with every stage.
- The obelisk's own beam (`shaders/core/beam`) replaces the beacon's everywhere: a white core in a glow of its colour, threads of energy streaming up, a spiral and rings travelling along it, timed in real seconds so it stays smooth in slow motion.
- Three new sounds for the rite: the storm of the pull, the implosion before the burst, the burst itself.
- With an Iris shader pack in use the galaxy is drawn after the world, since the pack paints its own sky over anything drawn with the sky; the rite's post effects run after all of its geometry.
- `fewerFlashes` softens the flash, the shake, the lens, the arcs and the field of view punch.

## 0.15.6

- The clouds sit at 448 instead of 192, since the overworld builds up to 608 (`cloudHeight` in the client config). They are drawn with their own fog and far plane, so they spread across the whole sky instead of a small patch overhead.
- A client setting `fewerFlashes` for players sensitive to flashing light; the coming rite honours it.

## 0.15.5

- The champion of each pillar, the player who gave it the most, wears the crown on its pedestal: their head turns above the lines with their name under it, the skin fetched in the background.
- The gauge is a hollow column now, eight sheets on a ring around the item and the lines, so the pedestal's text stays readable through it.

## 0.15.4

- The gauges: above each pillar's pedestal stands a column of light in the pillar's colour, as tall as the pillar is full, so the balance of stone, tech and magic can be read at the obelisk itself. The pillar furthest behind flickers. The pedestal lines are a little larger.

## 0.15.3

- The sleeping stone has its own picture: after a day without a gift the lanterns, embers, pylons, arch stones, shards, the crown's glints and the paving's runes go out (every tier block carries `lit`), dust sifts down the trunk, one slow heartbeat sounds every thirty seconds, the hum is a murmur. The first gift after that is the awakening: the lights come back ring by ring from the plinth outward with a sound for each ring, runes run up the trunk, and everyone near reads "Der Stein erwacht".
- `/kw admin obelisk sleep` puts the stone to sleep for a preview; the next gift wakes it.
- The lantern's glass is dark in the base texture, so a lantern without its light looks out.

## 0.15.2

- The gift is a moment now. The item given flies from the giver's hand to the crystal (`GiftPayload` to everyone within eighty blocks), spinning, with a trail of light in its pillar's colour, and is taken in; the amount rises from the crystal in that colour, with the item's name for anything beyond a handful; a ring of light runs out over the pavement, wider for a bigger gift. Gifts from the intake or a feeder rise out of the plinth instead of a hand.

## 0.15.1

- The obelisk notices the players. Whoever looks straight at the crystal for two seconds from within thirty blocks is answered: the crystal flares towards them like a lens catching the light, runes drift their way and the stone whispers once (`obelisk.whisper`, synthesized like the other sounds).
- The crowd: the server tells the clients how many stand around the plinth. Three or more are greeted with a chord of chimes, one note per player, and a ring of runes; the shards, the ground rings and the signal grow livelier with the number.
- The whispers: when nothing has come in for ten minutes and someone is near, every two to four minutes runes drift down the trunk and the players nearby read which pillar is furthest behind.
- The arch stone drawn again: fitted stone with a sunk panel and a violet inclusion, instead of scribbled veins.

## 0.15.0

- Three blocks for the grounds, in place of the vanilla ones the tiers used: the paving, engraved slabs with a rune that glows faintly, for the inner ring of the pavement; the flagstone, two courses of fitted stone, for the outer ring and the third stage's ring; the shard, violet glass with cyan facets and a sliding light, on top of the arches. The shards shed a few motes.
- The masonry of the obelisk drawn again (`tools/textures/core.py`): the plinth's sides carry sunk panels under their brass crown and its top is laid in slabs, the trunk reads as fitted stone with corner pins and a cold seam, the tip, cap, band, intake mouth, pedestals and the board follow the same hand.

## 0.14.1

- The thin sky: from the second stage the veil above the obelisk is worn, and a faint patch of the galaxy shows through it day and night, growing with the stage and placed above the obelisk from wherever the player stands (the galaxy shader takes a `Center`).
- During the intake the four pylons fire their light into the crystal, brighter as the burst nears.
- At the burst, twelve cracks of light run out from the plinth across the ground, following the terrain, and fade while the obelisk reforms.
- The signal stands out more by day.

## 0.14.0

- The obelisk is seen from far away. The server tells every client where it stands and what state it is in (`StatePayload`), and the client draws the far effects from that: the signal, ribbons of the obelisk's colour that wind up from the crystal into the sky, higher and wider with every stage, faint by day and bright by night; the aurora; and during the rite the great beam, so a player on the other side of the map sees the column come down and the gold pillar rise.
- Rune rings on the pavement: two circles of glyphs and ticks turn against each other in the obelisk's colour, bright at night, and every gift makes them flare by its size.
- The torn sky has more to show whichever way one looks: two bands of dust and stars cross the whole sky, six planets hang around the viewer, two of them with rings that pass in front of the planet and hide behind it, and faint clouds fill the dark between.
- The fog of a high tier no longer turns the night sky to mud; the tint stays light after dark.

## 0.13.3

- Life on the plinth: ember stones breathe smoke and sparks, lanterns shed glowing motes, the crown sparkles, arch stones leak a little void, the brass glints now and then. Every three minutes a shard leaves its orbit, flies down to one of the pedestals, has a look and returns.
- `kw admin season json` carries `tier` and `slumbering` for the bot and the website.

## 0.13.2

- The rite in its final order: the sky tears open first, during the riser, the beam comes down out of the tear, and the burst runs in slow motion (the server drops to a quarter speed for ten of its ticks) with a white gold flash over the whole picture.
- The crystal notices who comes close: the shards hurry and the crystal leans towards the viewer.
- `bossBarRadius` in the config shows the goal's boss bar only near the obelisk; 0 (default) shows it everywhere.

## 0.13.1

- The obelisk has its own voice: a hum that loops and rises with the goal, a riser that carries the intake of the rite to the burst, the tear of the sky, and a fanfare for the roll call, all synthesized by `tools/sounds/synth.py`.
- Sneaking with an empty hand at the obelisk shows your ledger: what you gave per pillar and which item most, your rank among the hands, your daily offering streak.
- A hand deposit sends the item flying from the giver into the trunk. Lifting the hold before the event announces "Der Obelisk erwacht" to everyone with a flare, so the streams can turn to it.
- The third stage adds a third band of runes to the trunk. A preview tier left over from before a restart is taken back when the server starts.

## 0.13.0

- The sky tears open when a stage completes: every player in the obelisk's dimension sees a galaxy instead of the sky, drawn by the mod's own shader (a spiral with core, arms, dust lanes and star fields, three lit planets, one with a ring), opening from the zenith with a brass edge and closing again after twenty seconds per stage reached. While it is open the air darkens and the whole picture is graded cooler with a vignette (post shader `veil`). Late joiners get the open sky too.
- The rite's great beam: during the intake a column of light comes down from the sky into the crystal, after the burst a gold beam three blocks wide wraps the whole trunk and tightens back to the ordinary beam while the new tier is built. The burst and every huge gift send a shockwave over the screen (post shader `shockwave`: the picture is pushed outward in a ring with a brass glow) and shake the camera.
- The tiers are built from the obelisk's own blocks with glow layers: brass inlay for the paving lines, pylons with a cyan core and pulsing lanterns, ember stones with breathing crimson veins, arch stones with amethyst veins, a gilded crown with glinting glyphs. `tools/textures/tiers.py` draws them.
- Rune glyph particles rise from the rune bands, the pulse of a deposit and the intake of the rite.
- From the third stage the air near the obelisk takes the colour of its tier (amber, then violet, gold when done); from the fourth stage curtains of aurora hang over the obelisk at night, and players on the plinth weigh half.
- Shader packs (Iris or Oculus) may override the mod's shaders; the obelisk then falls back to what the pack allows.

## 0.12.0

- The obelisk grows with every completed stage: paving with brass lines, pylons with lanterns, an outer ring of blackstone with crying obsidian, buttresses of deepslate and amethyst climbing to the trunk, a crown of end rods under the cap. What stood there before is remembered and comes back on a rebuild. `/kw admin obelisk tier <n>` previews a tier, `tier auto` follows the goals again.
- When a goal completes the obelisk holds a rite, about forty seconds: everything freezes, the trunk lights segment by segment with a rising scale, the crystal bursts white with thunder and lightning on the tip and a title for everyone, a dome of light runs over the ground, the crystal reforms in gold while the new tier is built block by block, the wall is carved anew, the pedestals name the top three hands of each pillar, fireworks in the pillar colours, and a line about what the next stage brings. `/kw admin obelisk rite` rehearses it without completing anything.
- Deposits are answered by their size. Every item has its own note (the same item always sounds the same, more of it plays an arpeggio); a real delivery sends a pulse of light up the trunk; a large one gives the giver a title and tells everyone nearby; a huge one tells the server, with a ring of light over the ground. The crystal flares by the size of the gift.
- While the goal waits for the event the shards freeze, the crystal trembles and a heartbeat sounds every four seconds. The stone hums with a pitch and volume that rise with the goal. Every five to ten minutes a rune misfires. After a day without a gift the obelisk slumbers: the crystal sinks and slows, the boss bar says so, and the first gift wakes it with a flare.
- Daily offering: the first deposit of a player each day counts for a streak; seven days in a row light an ember that follows the player around spawn.
- Who gave what is kept per item, so pillar rankings and a personal ledger are possible.

## 0.11.0

- The crystal on the obelisk floats and turns above the tip with three shards circling it, and the beam rises from its point. Crystal, shards and beam take their colour from the goal: cyan while it runs and warming to gold as it fills, purple while it waits for the event, gold when it is done, a quiet blue-grey when nothing is active. A deposit makes the crystal flare. The server tells the top block the progress along with the pedestals.
- The rune bands are animated: the glyph pulses and a light runs through it. The crystal texture shimmers. Glowing motes rise from the runes, sparks drift from the crystal.
- `tools/textures/obelisk.py` draws the animated textures.

## 0.10.0

- Every screen sits on the same frame: a slate panel with a brass border, a header band with the name of the screen and a close cross, tooltips on every button, a short grow-in when it opens. The sprites come from `tools/textures/gui.py` and live under `textures/gui/sprites`, nine-sliced so they stay sharp at any size.
- The hub shows the five stages as a path of rings on the left, the running one breathing, and the selected stage on the right: its progress ring, its description and, while it runs, every item with icon, count and a shimmering bar, grouped by pillar. Hovering an item tells how much is missing. Clicking a stage shows it.
- The admin panel has the same frame with a tab rail, rings for the goals, player heads in the team and player lists, and a tooltip on every action.
- The whitelist screen shows the slots as a row of brass marks, the invited players in slots, and a styled name field. The language question is two cards.
- The hub payload carries each item's id and each goal's description for the icons and the text.

## 0.9.0

- The obelisk is nineteen blocks tall: a stepped 9x9 plinth, a 3x3 trunk with two glowing rune bands, a cap, the tip and the crystal. Four pedestals at the corners show the last item of the stone, tech and magic pillar and of any deposit, with the pillar's progress above it, and a trail of light runs from the pedestal to the trunk.
- The leaderboard wall carves the top contributors of the running goal into dark stone in gold letters; `/kw admin obelisk board` puts it up in front of you. The feeder zone grows to ten blocks.
- `/kw admin` opens a panel with five tabs: overview (season, test world, own bypass and game mode), goals, team, obelisk and players. Buttons run the matching `/kw` command as the player and show its answer; the ones that cannot be undone need a second click.
- The season is in preparation until `/kw admin season start` (or the panel) resets every goal, leaderboard and starter kit; players who were offline lose their goal stages on their next join. `/kw admin season json` for the bot.
- Text fits its column everywhere, the hub scrolls when the goals do not fit, and the screens no longer dim the background twice.

## 0.8.2

- Bosses on the list get more health and damage per player near them and, with two or more players, lightning, a shockwave and a rage every few seconds. The Chaos Guardian fight is explained once to a player who comes close.
- `/kw testworld` builds and enters the screenshot world; the rows come from `rows.json` in the pack, one row per tick. `/kw testworld rebuild` builds everything again, from the console as well.

## 0.8.1

- `/kw` opens the hub: the five goals with bars and the running goal's items, deposits from there. `/kw team` lists every streamer with slots, changes allowances, takes places away and moves players between streamers.

## 0.8.0

- The obelisk core builds a plinth, a step, the shaft segments and the crystal with a beacon style beam around itself; `/kw admin obelisk build` places it. Machines feed the obelisk through the intake block, credited to its owner; containers no longer become feeders.
- Inside the spawn protection, the zone around the obelisk (`feederRadius`) lets anyone place intakes, hoppers, pipes and belts and take back their own blocks.
- The client asks for the language after the first join. Bedrock, end portals, reinforced deepslate and the Cataclysm altars survive every explosion.

## 0.7.3

- `/kw menu` opens a screen with the slots, the invited players with heads and online dots, a remove button each and a field to invite the next one. `/kw` alone lists the commands with clickable lines; the slot messages are German.

## 0.7.2

- Owner, admin, streamer and member as scoreboard teams with the Nautical Ranks glyphs as prefix, so the badge shows in the tab list, above heads and in chat. The tab list header carries the season, the footer the running goal and the player count.

## 0.7.1

- A square around the overworld spawn (`spawn.radius`) where only operators build: no breaking or placing, no explosion damage, no mob griefing, no hostile spawns, no PvP. Containers next to the obelisk stay allowed.

## 0.7.0

- The obelisk gets a block of its own, unbreakable outside creative mode, registering itself when an operator places it. Every player receives a waystone and warp dust on the first join. Dimensions open with a stage (Nether with 2, End with 4). Player facing messages are German with translatable keys, and items in goals are shown by name instead of id.

## 0.6.2

- FTB Quests could fail to load a large quest book ("this.wrapped is null") when the entries of the reward tables pushed its object map over the fill limit while it was being read. Core makes FTB Quests read from a copy, so the size of the book no longer matters.

## 0.6.1

- The whitelist is only switched on for a dedicated server. In singleplayer the world kicked its own player as not whitelisted.
- Server list scanners that drop the socket during a status ping no longer fill the log with a sixty line stack trace about an unknown disconnect packet. Such a connection is closed quietly.

## 0.6.0

- Goal items can be `"fixed": true`: their amount stays the same whatever the player count. Meant for milestone items, where thirty and twelve players should both build the same eight.
- Goal items can have a `"weight"`: the points one item is worth on the bar (default 1). The bar, the hold point and the percentages count points, so eight milestone items can carry as much of a goal as a few thousand ingots. A deposit never pushes the bar past the hold point, which means milestones worth more than what is left before the hold wait for the event.
- `kw admin goals json` also gives each item's `weight` and a goal's `recent` deposits (the last twenty, newest first, one line per player and item within ten seconds) for the stream overlay on the website. They live in memory and start empty after a restart.
- Locked items are veiled: wherever items are drawn they get a dark cover with a question mark, and their tooltip says "???" and which stage opens them, like the unknown items of SevTech. The action bar messages do not name them either.
- JEI keeps the items of the next stage in its list, veiled, so everyone can see what is coming and look at the recipes. Later stages stay hidden until they are next.
- English names of the Kronwerke milestone items for the server, so the goals json names them.

## 0.5.0

- Locked items can be picked up again. Chapters dropped every item of a stage the player does not have out of the inventory once a second and refused to pick it up, so loot from chests and mobs lay on the ground until it despawned. Now, as in SevTech, a locked item can be carried and stored but not held, worn or used: one that lands in a hand or an armour slot is moved into the inventory, and only dropped when there is no room. The action bar says which stage it belongs to, and so does its tooltip.
- Using, placing or hitting with a locked item in hand is refused.
- German texts for these messages and for Chapters' crafting messages.
- `/kw test slots` and `/kw test audit` for checking this on a test server.

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
