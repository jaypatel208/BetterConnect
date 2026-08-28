# Design system

**Status:** specification. Nothing here is built yet.

## What we are aiming at

The apps this is measured against are Stripe, Airbnb and Spotify. What they have in common is
not a look — it is that the interface is *quiet* and the content is loud. Generous space, a
tight type scale with a real display tier, one accent colour used sparingly, depth expressed by
layering rather than by shadows, and motion that behaves like a physical object rather than a
timed fade.

What we are explicitly not doing: a 1990s information layout (dense forms, boxes inside boxes,
a label to the left of every value, a toolbar of equal-weight buttons) repainted in modern
colours. If a screen would still read as a settings dialog in greyscale with the corners
squared off, the layout is wrong and no palette fixes it.

The other half of the brief is that **this is used at 60 km/h on a motorcycle**. Glanceability
is not an accessibility afterthought here, it is the product.

## Foundation

Built on `androidx.compose.material3` including the Expressive APIs — motion physics, shape
morphing, adaptive layout — because re-implementing accessibility semantics, touch targets,
focus order and adaptive behaviour by hand is how design systems quietly become inaccessible.

But Material is the engine, not the surface. Everything is wrapped:

```kotlin
RideConnectTheme {
    // RcColors  RcType  RcShape  RcSpacing  RcMotion  RcElevation
    // RcButton  RcCard  RcSurface  RcListRow  RcStatusPill  RcMetric …
}
```

`core:designsystem` should be the only module that depends on `material3`. Feature code that
reaches past the wrapper is a defect.

This is convention today, not enforcement: the diag screens predate the design system and still
import `material3` directly, so the Compose convention plugin still puts it on every module's
classpath. Once those screens are migrated, move the `material3` dependency out of
`AndroidComposeConventionPlugin` and into `core/designsystem/build.gradle.kts` — then the
boundary is a compile error rather than a review comment.

Expressive components are behind `@ExperimentalMaterial3ExpressiveApi`. Opt in once, inside
`core:designsystem`, never in a feature.

## Colour

- Ramps derived in **OKLCH**, not HSL — perceptually even steps, so a "one step darker" token is
  actually one step darker at every hue.
- **One accent.** Everything else is neutral. A second accent has to earn its place by encoding
  a real semantic distinction, not by decorating.
- Surfaces are near-neutral with a faint tint of the accent hue, so the greys feel deliberate
  rather than washed.
- **Dark mode is grounded at `#0B0B0C`, not `#000000`;** light mode's page is not `#FFFFFF`.
  Pure endpoints kill the layering system — there is nothing left to recede or advance into.
- **Semantic tokens only.** `RcColors.surfaceElevated`, `contentMuted`, `accentPressed`,
  `warning`, `linkDown`. Never `grey700`, never a raw hex outside the token file.
- Support dynamic colour as an opt-in that maps onto the same semantic tokens; never let it
  reach components directly.

## Depth

The 2026 answer, and the reason this does not look like 2019 Material: **spatial depth through
layering**, not a shadow stack.

1. Background → surface → elevated surface, distinguished by luminance and tint.
2. Hairline borders (`0.5–1 dp`, low-contrast) to separate co-planar regions.
3. Exactly one soft ambient shadow, reserved for things genuinely floating above the page —
   a sheet, a menu, the FAB. Nothing else casts.

Not neumorphism. Not a drop shadow on every card.

## Type

- A **variable font** with a genuine display tier — the scale needs weight and optical-size axes,
  not just five sizes of the same face.
- **Distance and ETA are the heroes.** Tabular figures, tight tracking, large optical size. On
  the ride screen the distance numeral is the largest thing on the display by a wide margin.
- Tabular figures everywhere a number changes in place, so the layout does not jitter.
- Steps are distinct enough to read as a hierarchy at a glance: `display / title / body / label`,
  with real gaps between them. Adjacent steps that differ by 2 sp are not a hierarchy.
- Everything must survive `fontScale = 2.0`.

## Motion

- **Spring specs only.** `RcMotion` exposes named springs; duration-based tweens are banned for
  anything that moves in space. Fades and colour transitions may be timed.
- Material 3 Expressive's motion-physics system is the default — 21 M3 components already use it,
  so wrapping them inherits it for free.
- Screen transitions use Nav3 `SharedTransitionScope` shared elements. A card that becomes a
  screen should visibly be the same object.
- Microinteractions confirm state (a value committed, a frame sent, a link established). Motion
  that only decorates gets cut.
- Honour reduced-motion: springs collapse to a cross-fade, never to nothing.

## Layout

- **4 dp base grid.** `RcSpacing` = 4 / 8 / 12 / 16 / 24 / 32 / 48. No other values.
- **Thumb-zone first.** Primary actions live in the bottom third. The top of the screen is for
  information, not for buttons you are expected to press while moving.
- **One primary action per screen.** If there appear to be two, one of them is secondary.
- Content-first: no chrome that does not carry information. No toolbar of equal-weight buttons.
- Adaptive by default — `WindowSizeClass`-aware, and correct on a folded and unfolded device,
  because API 37 removed the large-screen opt-out.

## Ride mode

A dedicated high-contrast surface variant for use while the bike is moving.

- Larger type, higher contrast, fewer elements, no secondary information.
- Touch targets 64 dp, not 48 dp — gloves.
- Legible in direct sun and at night without a separate manual switch.
- Nothing on it that requires reading a sentence.

## The caption rule

<!--
Failure: the stock Bajaj app's directions are accurate but its icons are unreadable at speed.
C (fork/keep left) and I (turn left) are both a left-leaning arrow off a central stem, differing
only by a thin second branch. At 60 km/h the rider cannot tell "turn right" from "stay right".
Why: this is the entire reason this product exists. It is a design requirement, not a nicety.
Outcome: docs/IMPLEMENTATION.md §4. Blocked on tracker D4 — our text does not render yet.
-->

**Every manoeuvre icon is paired with a short imperative text caption** in the cluster's text
field: `KEEP RIGHT`, not a fork glyph on its own.

- **Under ~12 characters** so it does not scroll. Long text marquees on this cluster, and a
  scrolling caption is worse than a static word while riding. (The exact non-scrolling width is
  unmeasured — field test session 3 item 3.)
- **The verb beats the street name.** `TURN LEFT` before `MG ROAD`.
- Transport limit is 32 characters and `[A-Za-z0-9. ]` only.

## Accessibility floors — enforced, not aspired to

These are test assertions, not guidelines:

- Touch targets ≥ 48 dp (≥ 64 dp in ride mode).
- Contrast ≥ 4.5:1 for every foreground/background token pair, in both themes. A unit test walks
  the token matrix and fails the build on a violation.
- Every interactive element has a content description or a merged semantic label; every screen
  passes a TalkBack traversal test.
- Correct at `fontScale = 2.0` with no clipping or overlap.
- Reduced-motion respected.
- No information carried by colour alone — the link-down state has a shape and a label too.

## Verification

- **Roborazzi** screenshot tests on the JVM for every design-system component and every screen,
  in light and dark, at default and 2.0 font scale. Baselines committed; a diff fails CI.
- A **theme catalogue screen** behind the `diag` flavour showing every token and component in
  both themes, so the system can be reviewed in one place on a real device.
