# TickAlign Client

Fabric client mod that reduces your effective latency by ~25ms on supported servers by timing outgoing packets to server tick boundaries.

## How it works

Minecraft servers process actions in 50ms ticks. Without TickAlign, your packets arrive at random points in the tick cycle — on average you wait 25ms before your action gets processed. TickAlign learns the server's tick timing and delays your combat packets by a few milliseconds so they arrive right before the next tick boundary, cutting that wait to ~1-3ms.

**What gets aligned:** attacks, block place/break, item use, sprint toggles, hotbar switches

**What doesn't get delayed:** movement, chat, keepalive — these always pass through immediately

## Server Whitelist

**TickAlign is server-whitelisted.** It only activates on servers you explicitly allow in the config. On any other server, the mod does nothing — no packets are modified, no data is sent, it sits completely idle. This means it won't interfere with anti-cheats or cause issues on servers that don't run the TickAlign server plugin.

Default whitelist: `thehomies.playwithbao.com`

Edit the whitelist in Mod Menu > TickAlign Settings > Server Whitelist, or in `config/tickalign.json`.

## Features

- Adaptive safety margin — tightens on stable connections, loosens on jittery ones
- Connection quality grading — EXCELLENT / GOOD / FAIR / POOR, auto-disables on POOR
- Phase drift prediction — pre-compensates for clock drift between syncs
- Packet coalescing — batches related packets (attack + swing) to the same send time
- HUD overlay — shows RTT, quality grade, safety margin, and aligned packet count
- Keybind toggle — press backslash to enable/disable mid-game
- Cloth Config GUI — full settings screen via Mod Menu

## Requirements

- Fabric Loader
- Fabric API
- Cloth Config
- A server running the TickAlign server plugin

## Installation

Drop the JAR into your mods folder. The mod is completely optional — the server works fine without it. Players with the mod get ~25ms lower effective latency on combat actions.

## License

MIT