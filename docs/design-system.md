# Macrosaurus design system

## Direction

Macrosaurus uses an **athletic editorial** style: warm, tactile surfaces and
strong sports-poster typography around a calm data-first nutrition interface.
The mascot acts as a coach in onboarding, guidance, empty states, and wins. It
does not compete with dense nutrient tables.

The product voice is direct, supportive, and never shaming. Prefer “A total,
not a verdict” and “Track consistently, not perfectly” over language about good
or bad food.

## Tokens

| Role | Value | Usage |
|---|---:|---|
| Canvas | `#f5f3e6` | Warm application background |
| Evergreen | `#1e4d2b` | Primary actions and structure |
| Deep evergreen | `#0d2e1a` | Navigation and high-contrast panels |
| Athletic green | `#43b05c` | Progress and positive states |
| Lime | `#77d177` | Highlights on dark surfaces |
| Orange | `#ff8c2a` | Energy, calls to action, celebrations |
| Teal | `#00a89d` | Secondary information |
| Charcoal | `#242824` | Primary text |

DM Sans is the body family. Barlow Condensed is reserved for display headings,
metrics, and short athletic labels. Both are self-hosted through Fontsource.

## Component principles

- Minimum interactive height is 44 px; common mobile controls are 46–50 px.
- Use React Aria for buttons, dialogs, and future composite widgets so keyboard
  and focus behavior stays consistent.
- Every input has a visible label. Placeholder text is an example, never the
  only label.
- Dialogs are reserved for confirmation or focused decisions. Mobile feature
  flows should remain normal routes so browser back and reload work.
- Tables collapse into stacked rows rather than creating horizontal page scroll.
- Green conveys progress; it does not imply that food is morally “good.”

## Mascot asset

`web/src/assets/mascot/dino-mark-v2.webp` is the optimized compact dinosaur-head brand mark
used in navigation and the PWA manifest. It deliberately uses a warm cream badge
background so the character remains intact and legible at small sizes.

`web/src/assets/mascot/coach.webp` is the optimized full-body coach used by the
application. Focused, proud, and goofy busts provide distinct empty, success,
and recoverable-error states. Alpha-matted PNGs and chroma-key sources live
beside them for future asset work. The character was derived
from the supplied Macrosaurus graphical profile. It contains no embedded text so
the interface retains accessible, crisp typography. Use an empty alt attribute
when decorative and “Macrosaurus, your personal macro coach” when the character
communicates the page purpose.

Generation prompt summary: a friendly muscular green dinosaur in black lifting
gear, orange spikes, bold sports-mascot outlines, full-body thumbs-up, and no
lettering. The built-in image generator produced a flat magenta-keyed source;
the installed chroma-key helper created the larger alpha character assets. The
head-mark chroma matte failed validation, so the final app badge uses a generated
cream background rather than shipping a damaged transparent edge.

## Accessibility

- Target WCAG 2.2 AA contrast and keyboard access.
- Orange and lime are accents; do not use them as small text on cream.
- Preserve visible focus rings and semantic headings.
- Respect `prefers-reduced-motion`.
- Charts require a text summary and accessible point labels.
- Missing nutrient data is displayed as unknown/absent, not zero.
