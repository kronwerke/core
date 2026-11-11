# Changelog

## 0.39.5

- The /kw hub opens on every server. In the farm world, the Nether and the End it only said that season, goals and places are in the main world; now main builds the hub for the player who asked and the side world shows it, with the same goals, numbers and rank. Giving to the goal and the whitelist still need the main world and say so in the hub's own line.
- AE2: taking something out of a terminal that is still in main's storage (network linked over a quantum bridge between servers) takes one click: the click that came too early is done again when the things are here. Seen on the real servers before: the first click on the gold gave nothing, the second took it.

## 0.39.4

- Flux Networks: networks reach the other servers at all. Flux Networks starts the number counter of a new world at a random number up to 500 million; Core only raised it when it was below the server's block of numbers, so on the real servers main counted from 351730000 and the farm world from 364540000, and what was made with such a number belonged to no server and was never sent. The counter is now put into the server's block on the first tick, on every server. Networks that already have such a number stay where they are.
- Powah: an ender channel is as large on every server as on the one where it is largest. A channel only gets room from energy cells put into an ender cell, so a cell placed in the farm world showed 0/0 FE and took nothing while the same channel had room at home.

## 0.39.3

- The farm world runs with 60 percent of the mob caps of the other servers (`mobs.capScale`, by the launcher's role), on top of ServerCore's own caps.

## 0.39.2

- Flux Networks: a network made on any server exists on all of them. Before, only main's networks went to the others; one made in the Nether or the mining world stayed there, and the list on main never showed it. Every server makes its networks in a block of numbers of its own, tells the others, and a change or a deletion made elsewhere goes to the server the network was made on and from there to everyone.

## 0.39.1

- Taking something out of a QIO window that only another server has takes one click, as always: the click that came too early is done again as soon as the things are here (seen on the real servers with 0.39.0: the first click asked for the cobblestone on main and gave nothing, the second took it).
- Nuclear Radiation's table of tag bindings is copied once a second instead of once per item it is asked about: its JEI page asks about every item of the game, which was the largest single part of JEI's start at every login on the test client.

## 0.39.0

- Mekanism's QIO is one storage on every server of the network. A dashboard, an exporter or a crafting window sees everything a frequency holds on any server, and what is taken comes from the server that has it, a split second later. The things stay in the drives they were put in: every server tells the others what its own drives hold, and each shows that through a drive that only shows (the mirror), so Mekanism itself counts, lists, filters and searches the whole storage. What is asked for lands in a small transit drive; what is not used after ten seconds, and what is put in where the real drives are full or missing, goes to a server with room. Before, everything a side world's drives held was sent to main, and a dashboard in the Nether showed nothing of main's.
- The creative tabs are not filled again after a move between servers (one to two seconds of standstill right at the arrival).
- The test player comes back by itself 30 seconds after a restart or a kick.

## 0.38.5

- The chat stays while a player moves between the servers: the client empties it at every disconnect, so each move wiped what had just been said.
- Anyone who just arrived from another server is left alone by a Mekanism teleporter whose other end is on another server, until they step out of its frame. main puts a player who comes home where they left, and for one who left through a teleporter that is inside its frame: the test player came home through the Farmwelt's portal and was sent straight back.

## 0.38.4

- A player who comes out of a Mekanism teleporter from another server is not sent straight back: Mekanism keeps arrivals from bouncing by a list in the teleporter that it clears of everyone who is not standing there, and a player from another server is not standing there yet for seconds. On the real servers the test player went back and forth between main and the Farmwelt until one teleporter was removed.
- The mining world is called Farmwelt (Farm World) in the advancement's title.
- The test player's click moves the mouse, lets two frames draw and presses then: screens that work out what is under the mouse while they draw (Core's language choice) took an immediate press as a miss.

## 0.38.3

- The test player's clicks go the screen's own way (the camera's window has no display and reports no size, so the mouse handler's sums came out as nothing), and its standstill records are shorter.

## 0.38.2

- JEI stays up while a player moves between the servers of the network. It stopped at the logout and built everything again at the login, which stood the whole client still in the middle of the travel animation (25 seconds on the test client, most of the time of a move). The pack and its recipes are the same on every server, so on a move JEI is kept; a server with a different number of recipes makes it start again as before. Enchantments and the other data-driven registries are sent anew by every server: an object of the old connection that JEI still holds is found by its name when it is sent to the server.
- The quest book of a login no longer takes the old copy apart quest by quest (each one searched all 3 500 others): the old copy is only marked as gone.
- Mekanism's frequencies are one list on every server: a teleporter, QIO or entangloporter frequency made or deleted on one server is there or gone on the others a second later, with name, owner and security. Before, a frequency had to be made by hand with the same name on both sides.
- Mekanism's teleporter and portable teleporter reach a teleporter on another server: the servers tell each other where their active teleporters stand, and a teleport to one of another server moves the player there, in front of that teleporter, for the usual energy.
- A move to a place on another server (a waystone, a teleporter) from main remembers where the player stood, for a way back that names no place.

## 0.38.1

- The test player's pictures are JPEGs that fit through the console's API, its timeline keeps one line per standstill (the stacks go to `stalls.log`), `log REGEX` reads the client's own log, and one line takes several commands.

## 0.38.0

- A move between the servers of the network is silent. Before, the server a player left said "left the game" to everyone (the note that it was a move got lost on the way to the other servers) and the one they arrived on said "joined the game". Now nobody is told, as nobody is told when someone changes the dimension on one server; joining and leaving the network reads the same on every server, without a server's name in front. A move that ends nowhere (the client never arrives) is said as a leaving after 30 seconds.
- The travel overlay no longer waits for every screen to close before it clears: a window that opened on arrival sat invisible under the stars until the player pressed Escape. Only the screens of the reconnect itself hold it back now.
- A player's stages hold in every world of the network. Chapters keeps them in the player's FTB team, and every server has its own teams, so a stage given on main (by a goal, or all of them by the admin bypass) was unknown in the side worlds: whoever went to the mining world stood at stage 1 again. Now a player who moves takes their stages along, main sends the stages everybody has by now with its state and a side world gives them to whoever is there, and `/kw admin bypass` works in a side world (it threw there before): it sets the stages there and tells main, which keeps the list.
- The test player: `KronwerkeTest` logs in like the camera (without a Mojang account, only over loopback), but is a player like any other. Its client (`-Dkronwerke.probe=true`, the camera bootstrap's tester mode) is driven by lines in `kronwerke/probe/in.txt` (chat, commands, keys, mouse, slots, pictures of the frame) and keeps a timeline of what a player would see: every screen, world and connection change, the travel overlay's phases, every chat line, and every time the game stood still with the code that held it up. A move sends a client on loopback to the other server's loopback address.

## 0.37.11

- `/kw testworld use` clears the item's cooldown first: its hand never ticks, so the cooldown Create's belt sets after the first of its two clicks never ran out and the second click did nothing.
- A scene file's `#area` line clears every entity there but players, not only the tagged ones: an assembled contraption carries no tag.
- Removing an obelisk block that is not the server's obelisk (the screenshot world has some) no longer touches the real obelisk's tiers. Without an obelisk set it threw, with one set it ran the tier cleanup in the wrong dimension and emptied the record of what the tiers had replaced.
- `/kw cam clear` leaves the test world's scenes (`kronwerke/shots/world`) alone. It deleted part of them, failed on the folder and never reached the pictures.

## 0.37.10

- The Omi tag no longer shows a box between its icon and the name: the space after the icon inherited the icon's own font, which has no space. The same went for a name appended to the badge in `/kwtag list`.

## 0.37.9

- The screenshot world no longer comes with the pack. `/kw testworld pull [ref]` fetches the scenes from the `shots-world` branch of the pack repository into `kronwerke/shots/world`, `/kw testworld apply [name]` builds them, one file per tick, and writes what a failed command answered into a `.log` next to its file. A changed scene needs no pack update and no restart. `/kw testworld` and `/kw testworld rebuild` use these scenes when they are there.
- `/kw testworld place <name> <pos> [rotation]` places a structure file of that folder (the Ponder scenes of Create, for one), turned like a structure block turns it.
- `/kw testworld use <pos> <face> <item> [sneak]` uses a tool on a block the way a player would: the hammer that forms an Immersive Engineering multiblock. The stage locks do not apply to it.

## 0.37.0

- The camera: a Minecraft client without a screen (kronwerke/camera) that takes the quest book's pictures on the real server. With `-Dkronwerke.camera=true` the client runs on GLFW's null platform with an OSMesa context (rendering on the CPU, no X server, no GPU), hides HUD, signs and the floating labels of the screenshot world, keeps itself connected, and on the server's word takes a picture once the world around it has rendered, scales it down and sends it back as PNG.
- The server lets the player `KronwerkeCam` in without a Mojang account, but only over loopback; from outside that name goes through the normal login and gets the real account's UUID. The camera passes the whitelist, the start gate and the queue, counts as no active player, gets no kit, is always a spectator, and joins and leaves without a chat line.
- `/kw cam` (status), `/kw cam watch` (look through the camera), `/kw cam shot <name> <pos> <yaw> <pitch> [fov] [width height]`, `/kw cam run [prefix]` (every shot of `data/kronwerke/shots/shots.json`), `/kw cam stop`. Pictures go to `kronwerke/shots/<name>.png`.
- The language picker no longer opens on the camera.

## 0.36.2

- Entities also stay when they are teleported into the level of a dimension on another server (`/tp` and mods that copy an entity into another level instead of calling changeDimension). Found in the live test of 0.36.1: a pig sent with `/tp` into the Nether vanished into main's copy of it.

## 0.36.1

- Modular Routers' Sender Module Mk3 does not send into a dimension on another server ("bad dimension"); the items stay in the router.

## 0.36.0

- An animal, item, cart or any other entity that a mod sends into a dimension played on another server stays where it is. Each server still has its own copy of every dimension, which nobody can visit, so whatever went there was lost. Portals already kept everything but players on their side; this covers mods' own teleports.
- Create no longer lays a track through a Nether or End portal into a dimension on another server: the track would end in this server's unvisitable copy and a train would vanish there with its cargo. The track does not connect, as at a portal Create cannot use.

## 0.35.0

- A new stage no longer reloads every data pack (CORE-39). The recipes of the last load are filtered again for the stage and handed to the recipe manager, which takes milliseconds; before, the server stood still for 5 to 6 seconds even when empty, and every client's JEI rebuilt. The mods' own reload listeners run after it, mostly off the server thread, so mods with their own recipe lists (Mekanism, Create and others) build them anew. Clients always get every recipe now, the held back ones too, so a new stage sends them nothing; JEI and the recipe book show the recipes as before the machine locks, and Chapters still hides the locked items. `/kw admin locks refilter <stage>` filters for another stage as a test; the real stage comes back within seconds.
- Mekanism machines say which stage an item needs when a player clicks it into a slot and only a held back recipe would take it. Mekanism checks its slots against its own recipe list, so until now the slot refused the item without a word.

## 0.34.0

- Tags by hand: `/kwtag <player> <tag>` (also `/kw admin tag`) gives a player any icon of the Nautical Ranks pack (32 of them) or one of Kronwerke's own, with a fitting name colour. It changes at once in the tab list, in chat and above the head, without a relog, on every server of the network, and survives restarts (`config/kronwerke/tags.json`, main repeats the list to the side worlds). `/kwtag <player> auto` goes back to the automatic rank, `/kwtag list` shows who has one, `/kwtag tags` shows all. A tagged player keeps the place of their automatic rank in the tab list.
- First own tag: Omi, in the style of the seav icons, turquoise, from Core's font `kronwerke:tags`.

## 0.33.0

- A singleplayer or LAN world plays like a normal modpack: every stage opens at the first login, machines craft everything, the first steps that need the obelisk count as done, and a one time message says that the obelisk, the season and the whitelist only exist on the server. A world the game runs itself is told apart from a real server by where it runs (the client's integrated server).

## 0.32.1

- A move from main that arrives nowhere (the client could not connect to the other server) is told to the team online after 30 seconds and logged. The player's next login on main brings them back as before.
- The admin panel fits a window 240 pixels high: the overview and server tabs press their rows together instead of running into the footer.

## 0.32.0

- The admin panel lists the players of the whole network, with the server they are on; a kick reaches them there. On the Nether, End and mining servers `/kw admin` opens the panel with the network's players (teleport, game mode and spawn for those on that server, kick for everyone); season, goals and slots stay on main. (R3-16)

## 0.31.0

Round 2 and 3 of the production review.

- Reports from a side world to main (a logout there, a server stop, the checkpoint every 30 seconds) are delivered until main confirms it wrote them, also across a restart of either server: they wait in `kronwerke/home_reports.json` and go out again every ten seconds. A player main thinks is away on a side world that is up waits a moment at the login while main asks that server for their newest state ("Deine Daten kommen gerade noch aus dem Nether"); after its answer, or 20 seconds without one, they come in. Main writes who is away at once, not at the next autosave. Checkpoints carry the backpack contents of the same moment. (R3-01)
- After a pause the season goes on with "Season fortsetzen" (`/kw admin season resume`): same number, nothing reset. Once a season has run, a start always needs `confirm`, paused or not, and the panel's "Neue Season" needs the new number typed. (R3-02)
- During the travel freeze the hub's actions (deposits) and commands do nothing. (R2-01)
- A heavy last item no longer fills a goal past its hold: before the event a deposit stops short of the total, and if a single piece fills it, the goal waits full at the hold until the event. (R2-02)
- A frozen traveller's refused pickup of a locked item is not turned back on. (R3-09)
- Boss scaling counts its extra attacks by game time, so a reloaded boss keeps them. (R2-11)
- The tag index keeps entities in the order they were added, like vanilla's lookup. (R2-15)
- Machines with a fake player (deployers, placers, mob crushers) go by the stages open on the server: they cannot use a later stage's tools and weapons, and are never locked out once a stage is open. (R2-05)
- A pipe or farm feeding the obelisk is told in chat when it has been quiet for ten seconds, or once a minute while it runs, not every three seconds; the boss bar is sent at most once a second. (R2-08)
- /kw undo of the gift that started today's streak takes the day back. (R2-13)
- After the planned start time the start gate says "Gleich geht es los" instead of a time in the past. (R3-15)
- A rider in a Nether or End portal is told to get off, as at the mining portal. (R2-06)
- A player waiting on the Nether or End server for main (main restarting, a respawn on main) cannot walk off, build or be hurt; on the End server they wait on the End's platform, not in an overworld nobody plays. (R2-07)
- A season start first copies Kronwerke's own data (goals, obelisk, season, slots) to `kronwerke/season-backups/`, since the launcher's backup runs after the reset. (R2-03)
- Quest progress from a side world goes to main only when it changed; otherwise a short question, and main answers only with news. Members' quest books are resynced only after a merge that changed something. (R2-14)
- With slots managed in Discord the slot menu has no "Entfernen" and the server refuses a removal from there, like an invite. (R3-07)
- Bans, pardons, kicks and operator changes made in game count on every server of the network (without selectors). (R3-05)
- Waystones of the Nether and End servers show on every server, and choosing one moves the player there, as main's already did. (R3-06)
- An item thrown through the mining portal counts as delivered when the other server has it in its world (`kw.items.ok`); if that answer does not come, it drops back out of the portal here. Items unknown there come back. (R3-10)

## 0.30.0

- The first steps in the quest book check what the player really did. Core gives hidden advancements (`kronwerke:steps/claimed`, `team`, `obelisk`, `deposited`) when the player's team claimed a chunk, the player is in a team, stood at the obelisk (within 16 blocks) or deposited something; the quests use them instead of a tick box. They show nowhere else.

## 0.29.0

- `/kw admin lag [seconds]` (15 by default, on every server): for that long the server times every entity tick, every machine tick and every chunk tick and adds them up per chunk. Then it names the ten costliest chunks with milliseconds per tick, split into entities, machines and chunk ticks, the four biggest causes in each, the FTB Chunks team that claimed it and whether a chunk loader keeps it loaded. A click on a line puts the teleport there into the chat box. The report also goes into the log. Outside a scan nothing is timed.
- Why a machine did not craft shows where the player looks: as a banner over the machine's own window when it is open (whichever mod made it), otherwise as text floating over the machine, altar or ritual block for five seconds, for everyone within 8 blocks. The text names what is locked and from which stage ("Steel Ingot gibt es hier erst ab Stufe 3"). Players without Core get the action bar as before.
- Botania's Flügel Tiara check runs only for players who wear one or still have its flight (Kristi's idea): the wearers are kept in a set that follows Curios' change event, the login and a check every five seconds.

## 0.28.0

- Tag index for command selectors (`[performance] tagIndex`, on): a selector with a positive tag, like `@e[type=marker,tag=x]`, looks only at the entities that carry that tag instead of every entity of the dimension. Incendium and Tool Trims run about ten such selectors every tick; with 50 players in the Nether they were 15 percent of the tick. Type, box, limit and every other part of the selector are tested as before, so the result is the same. The index follows the entities the level tracks, and every entity's tag set reports its changes (commands, NBT, mods). `/kw admin tagindex` compares the index with a full scan, on every server.

## 0.27.3

- Load test on a Nether or End server: the bots join in that dimension. A player without saved data joins in the overworld whatever level it was made for, so the bots stood in the dimension server's own overworld, which nobody generated; they now get a player file with their dimension and position before they join, as a real player arriving from another server does. `loadtest stop` releases the spawn ticket in the bots' dimension, not in the overworld.

## 0.27.2

- The weeping well guard asks whether each chunk is loaded (`getChunkNow`). 0.27.1 still used `hasChunksAt`, which on a server only says that a ticket will keep the chunk loaded, so chunks still on their way from disk passed and Malum waited for them on the server thread: with 50 players in a pregenerated Nether, 57 percent of the time in ticks over half a second, and stalls of up to 58 seconds.

## 0.27.1

- Malum's weeping well check no longer loads chunks on the server thread. The guard from 0.25 checked 8 blocks around the player, but Lodestone's search reaches up to 26 blocks on the positive side; with 50 players flying through new Nether terrain the tick waited 60 seconds for such a chunk and the watchdog stopped the server. The guard now covers every chunk the search touches.

## 0.27.0

The Nether and the End on servers of their own, and every finding of the production review.

Network:
- The Nether and the End run on servers of their own (`network.dimensionServers`). Portals, teleports and respawns into them move the player there; the arriving server finds or builds the portal, the End platform or the bed in its own world after loading the chunks in the background. A player who logs out there comes back there. Without such a server on the bus the dimension stays on main.
- A player who arrives from another server is not kicked "for floating too long" in the first 20 seconds, while a slow client still loads the world under them.
- A player is frozen between the travel snapshot and the transfer (no damage, no pickups, no menus); a late or failed move is cancelled at the target and the player keeps everything. A backpack thrown through a portal takes its contents.
- A direct join to a side world is turned away at the door; main only takes a player file from the server it sent the player to. A world reset is called off when the players cannot be sent away (`evacuate_failed`).
- Shared quest and network data is acknowledged only after the receiver saved it; the spread autosave resumes its queue after a restart. Quest resets carry a generation.
- Network chat: `/kw ignore <name>` hides a player's chat on every server.

Goals and the obelisk:
- The item that crosses a goal's hold point goes in whole: goals no longer stall at 96 to 98 percent (with a test over the shipped goals.json). goals.json is checked before it replaces the goals in use.
- Stages, starter kits, the next goal and the monsters' new strength come at the rite's reveal; the rite warns first, lifts nobody who sneaks, sits in a menu or is in creative, and slows nothing down server wide.
- Deposits: batched feedback, "deposit all" asks once and keeps hotbar and offhand, `/kw undo` gives the last ten seconds back. Leaderboards count points. A boss bar per goal, `/kw bar` hides it. Admins see a rescale preview before they confirm.
- The hub shows each item's weight and share, the next stage as an estimate to stock up, the top hands of finished stages, and your own points, rank and daily streak.

Locks and protection:
- Machine locks also hold recipes that make a locked fluid or chemical (by bucket or `locks.fluids`); an open main result keeps its recipe even with a later byproduct. A warning comes before the recipes reload.
- Locked items in Curios and Accessories slots go back to the inventory; locked weapons do not hit. Dimension locks hold riders, items and mobs too and explain themselves at a lit portal.
- Spawn protection covers the obelisk zone, entities and indirect damage; only intakes, hoppers, pipes and belts go at the obelisk. Obelisk tiers keep player blocks.

Places and the season:
- Only players with a granted place hand out slots; stray entries are cleaned up; menu actions are rate limited; names resolve off the server thread. In game invites are off by default and confirm the account first. The bot's commands take UUIDs and find streamers by UUID.
- Season start needs a confirm while a season runs and asks the launcher for a backup first. The season json names the planned start and time zone.
- Settings changed on the server live in `kronwerke/overrides.properties` and survive pack updates (`/kw admin locks` writes there).

English:
- Every player screen and chat line reads from the lang files: the hub, the whitelist menu, the queue, the tab footer, the goal bar, the rite, the obelisk ledger and the player commands. Messages travel as components, so each client shows its own language. Goal titles, descriptions and pillars come from the pack's lang files. The language screen says the quest book is German for now.

Admin panel:
- A server tab (start gate, beta, list, queue limit, locks, scaling, network), places in the team tab with kinds, grant, Season 1, bonus and take back, players with their server and a kick.

## 0.26.0

Server:
- The join queue. When the network (main and the side worlds together) holds `queueLimit` players (50), a joining player is no longer turned away: the connection waits in its configuration phase, the client shows the queue screen with the place, and the join goes on by itself when a place is free. Nobody reconnects. Operators, owners, streamers and players coming back from a side world never wait. `/kw admin queue` shows the queue, `/kw admin queue limit <n>` changes the limit in game or from the console. Vanilla's max-players turns nobody away while the queue is on, and the server list shows the network's players out of the queue's limit.
- Stage locks for machines. Every recipe whose result belongs to a stage that is not open is held back when the recipes load (19,280 of 45,072 in stage 1): no machine, ritual, autocrafter or crafting grid makes it any more, not only the crafting table. Whoever tries, or the players next to the machine, read in the action bar which stage it needs. When a goal completes, the recipes load again once its rite is over. Admins with the bypass still craft by hand; `/kw admin locks off` turns the machine locks off for testing.
- `/kw admin dumprecipes` writes every recipe with its main result for the pack's progression checker.

Client:
- The queue screen.

## 0.25.0

Server:
- Big blasts run much faster. Primed TNT is asked with one ray instead of 27 how much of it an explosion sees (only its knockback depends on it, `cheapTntExposure`), and Iron Furnaces, which looked up every broken block once for every pushed entity to find exploding million furnaces, does that once per explosion now. In the 29,000 TNT test these two were most of the explosion time.
- Crowded animals cost less. A mob that decided not to flee, and a baby that decided not to follow its parent, ask again after a quarter second instead of every other tick (`goalPauseTicks`); both searched every entity around, which made a pen of 100 animals cost 26 ms per tick.
- Malum looks for a weeping well next to a player only in chunks that are loaded; right after a join or a teleport it used to load them on the server thread.
- Messages to the launcher's bus are written by a thread of their own; a large player handoff no longer holds up the tick while the launcher reads it. On shutdown the last messages still go out.
- The boss check on every entity tick no longer builds a text of the entity's name each time.

Client:
- The pack's key layout without clashes (from the pack's tools/keys/layout.py). Default Options only sets it in new instances, so Core moves every key that still sits on a mod default or on the earlier pack layout to the new one, once; keys a player set stay.
- A JourneyMap plugin that fails as mapping starts or stops is skipped instead of breaking what called it (on a server transfer that was the "Network Protocol Error").

## 0.24.2

Server:
- TNT can no longer hang the server. Thousands of TNT going off in the same tick each checked the line of sight to every other TNT around, which kept the server in one tick until the watchdog stopped it (29,000 TNT: 60 seconds, then a crash, and the saved TNT crashed every restart again). Explosions now get 25 ms per tick (`tntMsPerTick`); TNT due after that waits a tick and explodes then. Each explosion stays vanilla, a big blast just takes a few seconds longer.
- Explosions cast half the rays: each explosion asked twice per entity how much of it it can see (for damage and for knockback), 27 rays each time. The second question now gets the first answer when nothing changed in between, which gives exactly the vanilla result.

Client:
- The language question comes once the world is loaded and the player stands in it. Shown at the join, it sat on top of seconds of loading work and looked frozen.
- Items on the obelisk pedestals turn smoothly, and every obelisk effect animates smoothly: animations ran on the full game time as a float, which after some weeks only moves in quarter ticks. They now run on the game time modulo an hour.
- The portal to the mining world works again: on the transfer JourneyMap stops mapping, and Create Ore Excavation's map plugin then tried to save its veins into a folder that no longer existed. The error broke the transfer packet and ended in "Network Protocol Error". It is now caught, and the transfer goes on.
- The Nautical Ranks resource pack (rank icons in the tab list and chat) is always on, added by Core as a required pack. Default Options only switched it on at a client's very first start, so instances started before never got it.

## 0.24.1

Client fixes:
- Giving an item to the obelisk could crash the game ("BufferBuilder was empty"): when the gift's light trail was right in front of the camera, nothing was drawn, and drawing nothing threw. Every obelisk effect now skips an empty draw.
- Choosing the language after the first join no longer reloads every resource of every mod (which froze the game until Windows said "not responding"); only the language files are reloaded.
- The creative menu no longer freezes the first time it opens: after joining, the tabs are filled a few milliseconds per tick in the background (filling all at once took over three seconds even on a fast CPU). `/kw admin tabs` shows how long each tab takes to fill.
- The obelisk's beam keeps about three pixels of width at any distance (it was thinner than a pixel from across the map and flickered) and gets a little brighter far away.

## 0.24.0

Joining and the whitelist:
- Slow PCs no longer time out while joining. The server is done with a join long before the client has built its recipe lists, tags, quest book and first chunks, and while the client does that it answers no keep-alive. A connection now gets two minutes to answer in its first three minutes (vanilla: 15 seconds) and 45 seconds after that, and both sides close a silent connection after two minutes instead of 30 seconds. Needs this Core on the client for the client side part.
- The tab list shows the live server state for everyone, refreshed every two seconds: TPS (measured), tick time and the player's own ping, green, yellow or red. Whoever lags sees whether it is the server or their connection.
- Removing someone from the whitelist through the bot no longer gets stuck when the server has already forgotten them: a removal of a player with nothing left here counts as done (it answered ERR before, and the bot kept the player forever).

## 0.23.12

- Loaded chunks really stay unchanged now: the light engine's "already lit" pass over every chunk read from disk flipped its light flag off and on and marked it as changed (22 300 chunks in two minutes of the load test, undoing the clean start from 0.23.5). That pass no longer marks the chunk.
- The Lootr ticker skip from 0.23.9 never took effect: Lootr reports decay and refresh as active as soon as its structure tags exist, and it ships them empty. The settings are now checked directly, with the tags counting only when they list a structure. The log says once at start whether Lootr containers keep their ticker.
- The spawn gate logs the spawn interval of each mob category once.

## 0.23.11

Chunk saving, from the 50 player load test:
- The five minute autosave writes changed chunks a few per tick (2 ms per tick and dimension) instead of all in one tick. With 50 players it froze the server for almost seven seconds every five minutes. Player, level and entity data are still saved right away; save-all, save-all flush and shutdown save every chunk at once as before. `performance.spreadAutosave` and `performance.autosaveMsPerTick` in the config.
- Light updates only mark a chunk as changed when a block that affects light changed in or next to it, or a neighbour was lit for the first time, within the last minute. The light engine settling borders of loading and unloading chunks marked 13 600 chunks in two minutes against a few hundred real changes; that light follows from what is on disk. `performance.cleanLightUpdates` in the config.
- Ars Additions no longer marks every chunk a player starts watching as changed by writing "no local weather" into it again (2 700 chunks in two minutes). Chunks with real local weather are handled as before.

## 0.23.10

Per tick work, from the 50 player load test:
- Natural spawning: ServerCore lets each mob category spawn only every n ticks (monsters every second tick, animals every 400th), but vanilla still counted every mob in a dimension and walked every chunk through the spawner on every tick (4 % of the tick for the count alone). On ticks where no category is due, both are skipped. Due ticks spawn exactly as before.
- Mowzie's Mobs no longer fetches, creates and saves three data attachments on every mob every tick. Ordinary mobs only tick its frost data once something froze them or they carry an effect; players and Mowzie's own mobs run as before.
- JourneyMap only rebuilds its loaded chunk overlay (every three seconds, in one go) when an operator in that dimension has the overlay on, and once more after it is switched off so the map clears.
- Lodestone's curio check (used by Malum's Necklace of the Hidden Harmony whenever anything looks for a target) is kept per item and entity for the rest of the tick.

## 0.23.9

- Lootr containers (chests, trapped chests, barrels, pots, suspicious blocks) get no server ticker while nothing is set to decay or refresh. Their server tick only counts down decay and refresh, which this pack does not use, so every loot chest in every structure ticked for nothing (1.4 % of the tick with 50 players). Shulker boxes and the client lid animation are untouched.

## 0.23.8

- Mobs that wear nothing skip the Curios slot tick, curio searches and Artifacts' per tick update. Artifacts gives zombies, skeletons, piglins and others curio slots so a few spawn wearing an artifact; for that every such mob was ticked and searched every tick. Whether a mob's slots are empty is looked at when it is first seen and every five seconds after. Mobs that wear an artifact and all players run as before, and mobs still spawn with and drop artifacts.

## 0.23.7

- Reliquary checks each mob's target for mob charms four times a second instead of every tick.
- Iron's Spellbooks' curio check (whether an entity wears its ring) is kept per item and entity for the rest of the tick. It runs whenever anything asks whether an entity is invisible to someone.

## 0.23.6

Per mob work, from the 50 player load test:
- Apothic Enchanting asked for every animal and every nearby player, every tick, whether the player's item has an enchantment effect, and each answer ran through Apotheosis' gem and affix lookup (1.6 % of the tick). The answer per item stack and effect is kept for the rest of the tick.
- Draconic Evolution's modular armor tick only runs for players; mobs never wear it (1.1 %).
- Nuclear Radiation works out mob doses every fourth round with four times the time step, so mobs collect the same dose in larger steps. Players as before.

## 0.23.5

- Chunks read from disk start out unchanged. Vanilla marks every loaded chunk as changed while reading it, so every chunk a player passed through was serialized and written again when it unloaded, on the server thread: 15 500 such marks in two minutes of the load test against a few hundred real changes, a tenth of the tick. Real changes (blocks, block entities, attachments, light) still mark the chunk. `performance.cleanChunkLoads` in the config turns it off.

## 0.23.4

- `/kw admin dirty start|show|stop`: counts who marks chunks as changed, by the code that did it. Changed chunks are written again when they unload, which took a tenth of the tick in the load tests. Off, it costs one check per change.

## 0.23.3

- An Industrial Foregoing Infinity Nuke that was saved while exploding no longer goes off again when its chunk loads; it is dropped and its position goes to the log. Two such nukes in the season world blew 173 block craters during the load tests and stalled the server for seconds. Nukes placed and armed by players are not touched.

## 0.23.2

Mods that do work for every living entity every tick, found with a look at our spark profile by Kristi from BacoNetworks:
- Accessories (comes with the Aether) walks every mob's slots once a second instead of every tick. Players as before.
- Mahou Tsukai skips mobs without any effect; all its per entity checks are about its effects.
- Nuclear Radiation only looks at players as carriers of radioactive items, instead of every living entity within 64 blocks of every other one.

## 0.23.1

- Nuclear Radiation's scan of every loading chunk skips sections that cannot hold a radioactive block or fluid (the section palette says so). In the load test with players flying around, that scan was a fifth of the server tick. Sections with radioactive blocks are scanned as before.

## 0.23.0

- The start gate: until the season starts, only operators and the names on the gate list get onto the main server. Everyone else on the whitelist reads the start on the login screen, in German or English (`join.seasonStart` in the config, local time in Europe/Berlin). It opens by itself with `/kw admin season start`; `/kw admin gate beta on` lets everyone with a place of their own (streamers, Season 1 players) through for the beta, while the players they invited wait. `/kw admin gate on|off|allow|deny` for the rest. Never in singleplayer.
- Load test bots: `/kw admin loadtest start <standing> <flying> [radius] [blocks per tick]` adds players without a client around the spawn (Last01 and up). Standing ones stay put like players in their bases, flying ones cross the area at elytra speed and keep generating chunks. The server does everything it does for a player except writing to the network. `/kw admin loadtest stop` removes them with their player files.

## 0.22.0

- Hostile mobs grow with the stages: health plus 30, 70, 120 and 180 percent, damage plus 20, 45, 75 and 110 percent, armor plus 2 to 10 from stage 2 to 5. Every value is in the config.
- Mobs that only spawn naturally from a stage on (Eidolon's wraiths and brutes from 3, the strongest Born in Chaos mobs from 2 and 3, Iron's Spells' necromancer from 2, Mowzie's naga from 3), and Born in Chaos spawning at 55 percent so it no longer makes up half of all monsters.
- Side worlds learn the open stage from main.

## 0.21.0

Sophisticated's linked storage across servers.

- A group of linked backpacks and storage blocks (joined with the Ender Linker) is on exactly one server at a time, with everything in it. On every other server its backpacks and blocks hold nothing and let nothing in or out, so nothing put there could get lost when the group comes back.
- The group comes to where it is wanted: when a player opens it there (as soon as nobody has it open where it is; the player is told it is on its way and when it is there), when a player who carries it moves there, or when a player there carries it and it has lain unused for two minutes where it is.
- A side world sends a group home to main when nobody there carries it and it has not been used for a minute, and sends every group home before it stops or is reset.
- main keeps the copy a side world last sent of each group it holds (every ten seconds when it changed). If a side world is reset without having sent a group home, main takes the group back from that copy.
- A backpack or block linked on one server is a member of its group on every server; Sophisticated no longer fails on an endpoint it does not know.

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
