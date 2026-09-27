# FGM Plus

A comprehensive expansion for [Wildfire's Female Gender Mod](https://www.curseforge.com/minecraft/mc-mods/female-gender-neoforge) (Forge 1.20.1).

## Features

### Per-player bust shaping (synced)

Shape data (perkiness + per-axis scaling) is stored **per player** and synced through
FGM's own network pipeline, so everyone in the world sees your shape — just like the
vanilla FGM sliders. Persisted to `config/fgmplus/shapes/<uuid>.json`.

- **Perkiness** (-30° to +60°): a vertical angle that cancels or reverses FGM's hardcoded
  35° droop, so breasts can sit higher and perkier. Bounce physics still apply on top.
- **Per-axis scaling** (0.5–3.0 each): independent width / height / depth multipliers.
  The above-cap real-scale growth applies to height and depth; width is controlled
  only by the slider and stays vanilla by default.

### Automatic anti-clip

When scaling or angle would push the model through the player's back, the excess is
pulled back automatically (capped at 4px). The Shape Studio shows the live recovery
percentage.

### Shape Studio GUI

Open the wardrobe (G key) → **Shape Studio** button. Sliders for all three axes and
perkiness, a reset button, the anti-clip indicator, and the hurt sound manager.
Changes apply to your player and sync to everyone.

### Custom hurt sound

FGM hardcodes its female hurt sound; many players don't like it. Drop any number of
`.ogg` files into `config/fgmplus/sounds/` — every file plays, one picked at random
per hurt, just like FGM's own two-damage-ogg setup (file names are free-form;
Chinese, spaces and uppercase all work). Uses a built-in hidden resource pack; the
**Preview** button in the Shape Studio chains a full resource reload, so newly
added/removed/renamed files are picked up on the next preview without restarting.
Client-side only — each player hears their own copy.

## Requirements

- Minecraft 1.20.1 + Forge 47+
- [Wildfire's Female Gender Mod](https://www.curseforge.com/minecraft/mc-mods/female-gender-neoforge) 1.20.1-3.0.1 or newer (3.x)

Mixed-version play is safe: clients or servers without FGM Plus simply ignore the
extra sync bytes and see the vanilla look.

## Config

`fgmplus-common.toml` keeps the FGM slider limit overrides (bust size max, offset
limits, bounce/floppy multiplier limits). The global scaling keys (`bustScaleGain`,
`bustWidthScaleGain`, `bustScaleMax`) were retired in 1.1.0 — scaling is per-player
shape data now.

## Credits

Based on **Female Plastic Surgery** by [Rinko1231](https://github.com/Rinko1231/Female-Plastic-Surgery-for-WFG) (upstream no longer maintained; this mod continues as a standalone mod). Licensed under **GPLv3**.
