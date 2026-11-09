# Design system - "colour, motion and mind"

## Colour psychology (see `ui/theme/Color.kt`)
| Colour | Meaning in the app |
|---|---|
| Indigo (primary) | trust + focus: main actions, selection |
| Teal (secondary) | calm progress: gradient partner of indigo, aurora backdrop |
| Amber | attention without alarm: due today/tomorrow, "in review", WIP at limit |
| Green | reward: reserved for *Done*, success, strong password |
| Red | urgency: only overdue, destructive actions, WIP exceeded - rare so it always counts |
| Slate | neutral "not started" (To Do) - must not compete with active work |
Dark theme = deep blue-black (not pure black) with lifted accents: less glare.

**Measured contrast (WCAG 2.x, reproduce with `python3 tools/contrast_check.py`):** body/secondary text ≥ 5.9:1 (light) and ≥ 7.8:1 (dark); primary button text 6.3:1 / 7.3:1;
small coloured tags ≥ 5.7:1 (light) and ≥ 7.1:1 (dark). The original 500-level amber (2.2:1) and green (3.3:1) failed as text on white, so the light theme
uses the 600/700 shades for anything drawn as text or under white text.

## Psychology → UI
* **Von Restorff** - one gradient CTA per screen.
* **Zeigarnik / goal gradient** - progress ring on every board card, per-column counters.
* **Peak-end** - dropping a card into a Done column bursts a ring + confetti and a haptic tick.
* **Hick's law** - the hold menu has ≤7 entries; board menus are role-filtered so people only see what they can do.
* **Fitts's law** - primary actions bottom/right (FAB, bottom bar, "+ Create issue" under each column); icon buttons are 36-48dp.
* **Immediate feedback** - optimistic moves, springy press states, tick/cross per password rule *as you type*,
  strength meter, haptics on pick-up and when entering another column.
* **Error prevention** - buttons stay disabled until input is valid; destructive dialogs name what will be lost;
  deleting a non-empty column forces "move or delete cards".
* **Recognition over recall** - Jira vocabulary (keys, priorities, status colours), role chips, relative times.

## Motion (`ui/theme/Theme.kt → Motion`)
Springs for anything touched (pick-up, drop, press, selection), short tweens for colour/opacity, page transitions
slide+fade (320 ms). `rememberReduceMotion()` turns decorative motion (aurora, shimmer, confetti, transitions) off when the
system "remove animations" setting is on. Loading uses shimmer skeletons rather than spinners.

## Accessibility
Contrast-measured palettes (above), content descriptions on avatars/glyphs/icons, `selectable` radio semantics for the theme picker,
text scales with system font size, no meaning conveyed by colour alone (priority has glyphs, due state has text).
