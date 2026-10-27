# Mob Forms ("become the mob")

`/morph` opens the form picker: every supported mob as its vanilla
spawn egg — click one and you become it, no selection needed. `/entity
morph` still works from a shift-right-click selection (or `/entity morph
<entity-uuid>`), `/entity unmorph` turns you back, and there is also a
**Morph** button on the dashboard.

## What "become the mob" really means

**With LibsDisguises installed, morphing shows the real mob.**
Everyone else sees the mob, and you see it yourself in F5 through the
self-disguise view (it can lag a tick behind you — normal for a
server-side disguise). Without it, morph dresses you as the mob instead,
and the costume is visible to everyone including yourself in F5:

- **Health, damage, speed, size**: the mob's max health, attack damage,
  movement speed and body scale (`generic.scale`, so an enderman towers and
  a chicken is tiny).
- **Flight**: flyers (blaze, ghast, phantom, vex, wither, ender dragon,
  allay-family) grant creative-style flight, restored afterwards.
- **Fire immunity**: blaze-style mobs get Fire Resistance while morphed.
- **Abilities on F** (your swap-hands key): signature moves with vanilla-
  tuned cooldowns. **Shift+F** is the second ability where one exists.
- **Death drops**: dying while morphed drops the mob's vanilla loot table
  on top of your normal drops, as if the mob itself died.
- **Sounds**: ambient cry on morph, signature sounds per ability.

## Ability list

| Form | F (primary) | Shift+F (secondary) |
|---|---|---|
| Evoker | Summon 3 vexes (they guard you via allegiance) — 5s | 16-block fang line — 2s |
| Blaze | 3-fireball burst — 5s | — |
| Sheep | Graze: eats grass below into dirt, restores hunger — 1s | Birth a lamb — 20s |
| Creeper | Explode (power 3, **breaks blocks**) — 30s | — |
| Enderman | Teleport 16 blocks (pearl rules, 5 damage) — 1s | — |
| Skeleton | Fire arrow from quiver (plain or nasty-tipped) — 1.2s | — |
| Stray | Same quiver, slowness-heavy tips — 1.2s | — |
| Pillager / Piglin | Fire arrow from quiver — 1.5s | — |
| Drowned | Throw loyalty-3 trident (consumed, resupplied per morph) — 2s | — |
| Witch | Throw a random hex splash (harming/poison/slowness/weakness) — 2.5s | — |
| Husk | Dash — melee hits inflict hunger — 3s | — |
| Zombie | Strength II for 10s — 60s | — |
| Spider / Cave spider | Pounce (leap) — 3s | — |
| Slime | Slam: AoE damage + knockback — 5s | — |
| Ghast | Fireball — 2.5s | — |
| Wither | Wither skull — 2s | BLUE wither skull — 3s |
| Ender dragon | Dragon fireball — 2.5s | — |
| Anything else | Dash forward — 3s | — |

Cooldowns are tuned to feel like vanilla, not frame-exact ports. Creeper
explosions damage you too and break blocks — that is what creepers do.

## Form loadouts

Some forms hand you gear on morph so the fantasy works out of the box.
Everything given goes into empty slots or your main hand only — your own
items are never touched — and unmorphing removes exactly what was given:

- **Skeleton / Stray / Pillager / Piglin**: a full-inventory quiver of
  arrows. Roughly a quarter are tipped, and only with nasty effects
  (poison, slowness, weakness, hunger) — never buffs. Strays lean heavily
  into slowness arrows. Firing consumes one arrow per shot.
- **Drowned**: a Loyalty 3 trident, sometimes with Impaling/Unbreaking/
  Mending. Throwing consumes it; morph again for a fresh one.
- **Piglin brute**: an unbreakable golden axe, sometimes Sharpness.
  **Vindicator**: an iron axe, sometimes enchanted. **Wither skeleton**:
  a plain stone sword. These are melee loadouts; F stays a dash.
- **Witch**: drinkable buff potions (healing, regen, swiftness, fire
  resistance, night vision, water breathing) plus a hotbar row mixing
  the same buffs with throwable hex splashes. Used splashes resupply
  every ~6 seconds while morphed.

## Costumes

Each form wears its mob's outfit while morphed: mob heads (dragon,
creeper, zombie, skeletons, piglin), dyed-leather "skin" suits (slime
green, enderman black, sheep white, blaze orange...), elytra wings for
the dragon, and hand props (totem for the evoker, TNT for the creeper,
grass block for the enderman, nether star for the wither, wheat for the
sheep, milk for the cow, a wand for the witch). Costume pieces only fill
empty armor slots and an empty main hand — your gear is stashed, never
deleted — and unmorphing puts it all back. Dying morphed drops your real
gear plus the mob's loot table, never the costume. Ambient particles
(ender pearls, blaze flames, ghast smoke, dragon breath) run while morphed.

## Disguise setup (optional, recommended)

Real mob visuals need two extra plugins on the server, pinned to these
versions for Paper 1.21.11:

- **LibsDisguises 11.0.14** (free edition) — the disguise engine.
- **PacketEvents 2.11.1** — its networking dependency (ProtocolLib is
  *not* needed on this version line).

EntityStudio soft-depends on LibsDisguises: install it and morphs use
real disguises automatically; without it everything still works in
costume mode. No config needed either way.

## Rules and limits

- Living mobs only. Boats, items, armor stands and players are refused.
- Morphing re-validates the session and checks `entitystudio.morph`
  (default: op). Unmorph needs only the morph node.
- Switching forms restores you first — stats never stack.
- Cooldowns show on the action bar instead of spamming chat.
- Unmorphing mid-air grants a few seconds of Slow Falling so flyers do
  not crater on the way out of a form.
- Quitting, dying or server restart clears the form; stats are restored on
  the next login if a restore was pending.
- Summoned vexes are allegiance-owned by you (guard profile): your other
  managed mobs treat them as family under the friendly-fire rules.
- No custom animations: abilities reuse vanilla sounds/particles where
  they exist. True model animation needs a client-side mod.
