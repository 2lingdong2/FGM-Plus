# FGM Plus

Real bust scaling and per-player breast shaping for [Wildfire's Female Gender Mod](https://github.com/FemaleGenderMod/FemaleGenderMod). Based on Female Plastic Surgery by Rinko1231 (GPLv3).

**Targets** (single repo, one `mod_version` for all):

| Target | Upstream | Jar |
|---|---|---|
| Forge 1.20.1 | FGM 3.1 (1.20.1-3.x) | `fgmplus-forge-1.20.1-<ver>.jar` |
| Fabric 1.21.11 | FGM 5.0.0-Beta.3+1.21.11 | `fgmplus-fabric-1.21.11-<ver>.jar` |

## What it does

- **Real scale above the vanilla cap** — bust sizes past the 0.8 slider limit grow the actual breast geometry instead of only shifting its position.
- **Shape Studio** — a per-player editor (entry button in FGM's wardrobe): three-axis scale, perkiness (counteracts FGM's droop), body-space position offsets, live preview, synced between players carrying the mod.
- **Anti-clip back flattening** — breast vertices beyond the torso-back plane are clamped onto it per-vertex, so extreme sizes never poke through the back; jacket and armor layers follow.
- **Widened FGM sliders** — configurable caps for bust size, offsets, bounce and floppy multipliers.
- **Custom hurt sounds** — drop *.ogg files into `config/fgmplus/sounds/`; they replace FGM's female hurt sound with a random pick per play (preview button in the Shape Studio).

## Repository layout

See [ARCHITECTURE.md](ARCHITECTURE.md) (e33chat / AtomChat style: repo-root identity, `versions/targets.json` matrix, `shared/` neutral layer, per-loader `platforms/`).

```
bash tools/build_all.sh     # guard gate + build every target
bash tools/collect_jars.sh  # collect artifacts into dist/<version>/
```

Deployable jars land in `dist/<mod_version>/`.

## License

GPLv3, same as the original. See LICENSE.
