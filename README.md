# P2P Channel Network

Server-authoritative typed point-to-point channels across dimensions — items, fluids, energy and addon media.

## Repository layout

This repository hosts both supported versions as branches:

| Branch | Minecraft | Loader | Status |
| --- | --- | --- | --- |
| `1.21.1` | 1.21.1 | NeoForge 21.1.x | current |
| `1.20.1` | 1.20.1 | Forge 47.4.x | maintenance |

## Features

- **Typed channels** — `ITEM` / `FLUID` / `ENERGY` (plus addon media such as Mekanism gas, Ars Nouveau source and Botania mana via [P2P Compat](https://github.com/Kalloer1/p2p-compat)), chosen at creation and locked afterwards.
- **Cross-dimension transfer** — endpoints in different dimensions talk over the same channel.
- **Per-channel rate & speed** — tune throughput (amount per tick) and execution cadence independently.
- **Filters** — per-endpoint whitelist / blacklist, NBT matching, item tags and regex.
- **Networks & groups** — organise channels; admins can see and enter other players' networks.
- **Capability cache + adaptive backoff** — scales to many channels and members without per-tick stalls.
- **JEI / EMI** — drag items, fluids or filled buckets into channel filter markers (compile-time API; install either viewer at runtime).

## Requirements

- Minecraft `1.21.1`
- NeoForge `21.1.x`
- Client **and** server both required

## Building

```bash
./gradlew build
```

The built jar is placed in `build/libs/`.

## License

GPL-3.0-only. See [LICENSE](LICENSE).
