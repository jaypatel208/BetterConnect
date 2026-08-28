---
paths:
  - "core/designsystem/**"
---

# Design system rules

Full specification: `docs/DESIGN-SYSTEM.md`. Read it before adding a component or a token.

This module is the **only** one that should depend on `androidx.compose.material3`. Everything
it exposes is an `Rc*` wrapper; the Material component underneath is an implementation detail.

Today `AndroidComposeConventionPlugin` still puts material3 on every Compose module's classpath,
because the diag screens use it directly. When `RideConnectTheme` lands and those screens are
migrated, move the material3 dependency out of that plugin and into this module's build file —
then the compiler enforces the boundary and nobody has to remember it.

## Tokens

`RcColors` `RcType` `RcShape` `RcSpacing` `RcMotion` `RcElevation`, all provided through
`RideConnectTheme` via `CompositionLocal`.

- **Semantic names only** — `surfaceElevated`, `contentMuted`, `accentPressed`, `linkDown`.
  Never `grey700`, never `blue500`.
- Colour ramps are derived in **OKLCH**. Dark mode grounds at `#0B0B0C`; light mode's page is
  not `#FFFFFF`. Pure endpoints break the layering system.
- One accent. A second one has to encode a real semantic distinction.
- Spacing is 4 / 8 / 12 / 16 / 24 / 32 / 48 and nothing else.
- `RcMotion` exposes **named springs**. Duration tweens are for fades and colour only — never
  for anything that moves in space.

## Depth

Layering, not shadows: background → surface → elevated surface by luminance and tint, plus
hairline borders for co-planar separation. **Exactly one** soft ambient shadow, reserved for
genuinely floating elements (sheet, menu, FAB). Not neumorphism, not a shadow on every card.

## Every component ships with

1. A `@Preview` in light and dark.
2. A **Roborazzi** screenshot test — light, dark, and `fontScale = 2.0`.
3. An entry in the theme catalogue screen (`diag` flavour).
4. Contrast and touch-target assertions covered by the token matrix test.

<!--
Failure: the diag build has exactly one @Preview in the entire repo, and zero Compose UI tests
despite the dependencies being wired by AndroidComposeConventionPlugin.
Why: a design system with no rendered baseline drifts silently — nobody notices a component
regressing in dark mode until someone opens that screen at night.
Outcome: new requirement; not yet backfilled for existing components.
-->

## Floors, as assertions

- Touch targets ≥ 48 dp; ≥ 64 dp in ride mode (gloves).
- Contrast ≥ 4.5:1 for every foreground/background token pair, both themes, enforced by a unit
  test that walks the token matrix.
- Correct at `fontScale = 2.0`.
- Reduced motion respected: springs collapse to a cross-fade, never to nothing.
- No information carried by colour alone.

## Ride mode

`RcColors` has a ride variant: higher contrast, larger type, fewer elements. It is a first-class
theme state, not an afterthought — the product's actual context is a moving motorcycle.

## Opt-ins

`@ExperimentalMaterial3ExpressiveApi` is opted into here, once. Never in a feature module.
