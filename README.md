# P2P — Wireless Channel Logistics (Forge 1.20.1)

> **Version note:** this repository hosts both supported versions. You are on the `1.20.1` (Forge) branch — the current `1.21.1` branch is the NeoForge 1.21.1 build. See the branch dropdown above to switch.

P2P lets you bind container faces across your base — and across dimensions — into typed, wireless channels that move items, fluids, and energy with no buffer in between.

## Features

- **Typed channels** — `ITEM` / `FLUID` / `ENERGY`, chosen when the channel is created and locked afterwards.
- **Cross-dimension transfer** — endpoints in different dimensions talk over the same channel.
- **Per-channel rate & speed** — tune throughput (amount per tick) and execution cadence independently.
- **Filters** — per-endpoint whitelist / blacklist, NBT matching, item tags and regex.
- **Networks & groups** — organise channels; admins can see and enter other players' networks.
- **Capability cache + adaptive backoff** — scales to many channels and members without per-tick stalls.

## Requirements

- Minecraft `1.20.1`
- Forge `47.4.x`
- Client **and** server both required

## Building

```bash
./gradlew build
```

The built jar is placed in `build/libs/`.

`libs/emi-1.1.24+1.20.1+forge.jar` is vendored as a compile-only dependency for the filter editor (EMI by Emi, MIT — <https://modrinth.com/mod/emi>); any compatible EMI build works.

## License

GPL-3.0-only. See [LICENSE](LICENSE).
