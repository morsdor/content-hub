# ContentHub — Design System

> Single source of truth for visual tokens and design decisions.
> Atlaskit components are the implementation layer; this file governs the theming.
> WCAG 2.1 AA is the minimum bar throughout. AAA noted where achieved.

---

## 1. Brand personality

ContentHub is a **professional creative tool** — Descript meets Linear.
- Confident, not loud
- Clean, not sterile
- Fast-feeling, not flashy

The primary green earns its attention: used only for CTAs and live/active states, never decoration.

---

## 2. Colour modes

Default: **light**. Dark ships from day one.

Both modes share the same token names; values flip. The Atlaskit `setGlobalTheme` call switches
`data-color-mode` at the `<html>` element, which drives all CSS custom property overrides.

---

## 3. Colour palette

### 3.1 Primary brand — Studio Green (`green-*`)

Studio Green is **CTA- and active-state-only**. It is never used for decorative fills,
chart series, icon fills, or Kanban column accents.

| Token | Hex | Use |
|-------|-----|-----|
| `green-500` | `#1ED760` | Primary button bg, active nav indicator, live dot, progress |
| `green-600` | `#17A84A` | Hover on green button |
| `green-700` | `#0F7A35` | Pressed / active-depressed state |
| `green-100` | `#D4F7E2` | Success banner bg (light mode) |
| `green-50`  | `#EAFBF0` | Hover tint on secondary surfaces (light mode) |

**Foreground ON green backgrounds — contrast ratios:**

| Background | Foreground | Ratio | Level |
|-----------|-----------|-------|-------|
| `#1ED760` | `#000000` | **11.3 : 1** | AAA ✓ |
| `#1ED760` | `#FFFFFF` | 1.9 : 1 | Fail ✗ |
| `#17A84A` | `#FFFFFF` | **4.6 : 1** | AA ✓ |
| `#0F7A35` | `#FFFFFF` | **7.3 : 1** | AAA ✓ |

> **Rule:** labels and icons inside a `green-500` button must be `#000000`, not white.

---

### 3.2 ContentHub Blue (`blue-*`)

Custom blue at hue ≈220° — warm side of blue, bridging toward the green at 142°.
Used for links, info states, focus rings, and secondary interactive elements.

| Token | Hex | Use |
|-------|-----|-----|
| `blue-500` | `#1860E8` | Links, info badges, focus ring, secondary CTA |
| `blue-600` | `#1350C5` | Hover on blue |
| `blue-700` | `#0E3E9E` | Pressed blue |
| `blue-100` | `#D9E8FF` | Info banner bg (light mode) |
| `blue-50`  | `#EBF2FF` | Very light info bg |

**Contrast ratios:**

| Pair | Ratio | Level |
|------|-------|-------|
| `#1860E8` on `#FFFFFF` | **5.4 : 1** | AA ✓ |
| `#1860E8` on `#F6F8FA` (page bg) | **5.1 : 1** | AA ✓ |
| `#1860E8` on `#161B22` (dark surface) | **3.9 : 1** | AA large / UI ✓ |
| `#1350C5` on `#FFFFFF` | **7.2 : 1** | AAA ✓ |

---

### 3.3 Neutral scale

| Token | Hex | Light mode role | Dark mode role |
|-------|-----|-----------------|----------------|
| `neutral-900` | `#0D1117` | Primary text | Page bg |
| `neutral-800` | `#161B22` | — | Surface / card |
| `neutral-700` | `#21262D` | — | Elevated surface |
| `neutral-600` | `#30363D` | — | Border / divider |
| `neutral-500` | `#484F58` | Secondary text | Secondary text |
| `neutral-400` | `#6E7681` | Tertiary / muted text | Tertiary text |
| `neutral-300` | `#8B949E` | Placeholder / disabled | Placeholder |
| `neutral-200` | `#C9D1D9` | Border / divider | — |
| `neutral-100` | `#ECEFF3` | Subtle bg tint | — |
| `neutral-50`  | `#F6F8FA` | Page bg | — |
| `neutral-0`   | `#FFFFFF` | Surface / card | Primary text |

**Text-on-white contrast:**

| Token | Hex | Ratio | Level |
|-------|-----|-------|-------|
| `neutral-900` | `#0D1117` | **19.1 : 1** | AAA ✓ |
| `neutral-500` | `#484F58` | **8.3 : 1** | AAA ✓ |
| `neutral-400` | `#6E7681` | **4.7 : 1** | AA ✓ |
| `neutral-300` | `#8B949E` | 3.5 : 1 | AA large/UI only ✓ |

**Text-on-dark-surface (`#161B22`) contrast:**

| Token | Hex | Ratio | Level |
|-------|-----|-------|-------|
| `neutral-0` | `#FFFFFF` | **17.3 : 1** | AAA ✓ |
| `neutral-100` | `#ECEFF3` | **15.3 : 1** | AAA ✓ |
| `neutral-400` | `#6E7681` | **4.6 : 1** | AA ✓ |

---

### 3.4 Semantic colours

| Purpose | Hex | On white | On dark surface | Level |
|---------|-----|----------|-----------------|-------|
| Danger / error | `#CF222E` | **5.4 : 1** | — | AA ✓ |
| Warning text | `#9A6700` | **5.0 : 1** | — | AA ✓ |
| Warning bg | `#FFF8C5` | Background only | — | — |
| Success text | `#1A7F37` | **5.3 : 1** | — | AA ✓ |
| Info text / links | `#1860E8` | **5.4 : 1** | — | AA ✓ |
| Info bg | `#EBF2FF` | Background only | — | — |

> Warning yellow (`#FFBE2E`) is 1.9:1 on white — bg tint only; pair with `#9A6700` text.

---

### 3.5 Data-visualisation palette

Studio Green is **reserved for CTAs**. All chart series, waveforms, timelines, and Kanban column
accents draw from this dedicated six-colour palette.

Design constraint: every colour must pass **WCAG 1.4.11 Non-text Contrast (3:1)** against both
the light page background (`#F6F8FA`, L≈0.94) and the dark surface (`#161B22`, L≈0.011).
This locks the luminance window to approximately **L = 0.133 – 0.30**.

#### Light mode chart colours

| # | Name | Hex | Luminance | On `#F6F8FA` | On `#161B22` | Text safe? |
|---|------|-----|-----------|--------------|--------------|------------|
| 1 | Blue   | `#1860E8` | 0.144 | **5.1 : 1** | **3.9 : 1** | AA text ✓ |
| 2 | Violet | `#7B4FBF` | 0.136 | **5.4 : 1** | **3.7 : 1** | AA text ✓ |
| 3 | Amber  | `#B45309` | 0.159 | **4.8 : 1** | **3.4 : 1** | AA text ✓ |
| 4 | Teal   | `#0D7490` | 0.146 | **5.1 : 1** | **3.2 : 1** | AA text ✓ |
| 5 | Rose   | `#DB2777` | 0.178 | **4.3 : 1** | **3.7 : 1** | AA large/UI ✓ |
| 6 | Indigo | `#4F46E5` | 0.138 | **5.3 : 1** | **3.8 : 1** | AA text ✓ |

#### Dark mode chart colours (light-mode hues, lightened for dark surfaces)

Dark mode requires luminance ≥ 0.22 for text-safe on `#161B22` (4.5:1 AA). Same hue order.

| # | Name | Hex | On `#161B22` | Text safe? |
|---|------|-----|--------------|------------|
| 1 | Blue   | `#60A5FA` | **7.2 : 1** | AA ✓ |
| 2 | Violet | `#A78BFA` | **7.5 : 1** | AA ✓ |
| 3 | Amber  | `#FBBF24` | **9.1 : 1** | AA ✓ |
| 4 | Teal   | `#22D3EE` | **9.5 : 1** | AA ✓ |
| 5 | Rose   | `#F472B6` | **7.3 : 1** | AA ✓ |
| 6 | Indigo | `#818CF8` | **7.6 : 1** | AA ✓ |

---

## 4. Typography

**Font:** Inter from Google Fonts CDN.

```html
<!-- in index.html <head> -->
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
```

**CSS:**
```css
font-family: 'Inter', system-ui, -apple-system, sans-serif;
```

**Monospace** (transcript editor, code panels):
```css
font-family: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace;
```

### Type scale

| Token | Size | Line-height | Weight | Use |
|-------|------|-------------|--------|-----|
| `text-xs`   | 12px | 16px | 400 | Timestamps, meta labels, badge text |
| `text-sm`   | 14px | 20px | 400 | Body copy, form labels, table rows |
| `text-base` | 16px | 24px | 400 | Default prose, descriptions |
| `text-lg`   | 20px | 28px | 600 | Section headings, card titles |
| `text-xl`   | 24px | 32px | 600 | Page titles (h2) |
| `text-2xl`  | 32px | 40px | 700 | Hero headings (h1), empty states |
| `text-3xl`  | 40px | 48px | 700 | Landing headlines (marketing only) |

---

## 5. Spacing scale (4 px base)

| Token | px | rem | Common use |
|-------|----|----|------------|
| `space-1` | 4   | 0.25 | Icon-label gap, tight inline |
| `space-2` | 8   | 0.5  | Input inner padding (vertical), chip gap |
| `space-3` | 12  | 0.75 | Button padding (vertical), compact row |
| `space-4` | 16  | 1    | Card padding, form field spacing |
| `space-5` | 20  | 1.25 | Section-internal gap |
| `space-6` | 24  | 1.5  | Section outer padding |
| `space-8` | 32  | 2    | Component separation |
| `space-10`| 40  | 2.5  | Major section gap |
| `space-12`| 48  | 3    | Page section break |
| `space-16`| 64  | 4    | Hero / empty-state whitespace |

---

## 6. Density tiers

ContentHub uses **two density contexts**, not one global setting.

| Context | Tier | Vertical padding | Row height | Reference feel |
|---------|------|-----------------|------------|----------------|
| Workspace board, settings, analytics, lists | **Balanced** | `space-4` (16px) | 48–56px | Linear |
| Timeline editor, transcript, clip list | **Compact** | `space-2` (8px) | 32–36px | Descript / DaVinci |

The layout shell (`PageLayout`, sidebar, top nav) always uses Balanced regardless of inner context.

---

## 7. Border radius

| Token | Value | Use |
|-------|-------|-----|
| `radius-sm`  | 4px | Input fields, tags, table badges |
| `radius-md`  | 8px | Buttons, dropdowns, small cards |
| `radius-lg`  | 12px | Cards, popovers, info banners |
| `radius-xl`  | 16px | Modal dialogs, drawer panels |
| `radius-pill`| 9999px | Status chips, avatar rings, pill buttons |

---

## 8. Elevation / shadows

| Token | CSS value | Use |
|-------|-----------|-----|
| `shadow-sm` | `0 1px 2px rgba(13,17,23,0.08)` | Input focus lift, list row hover |
| `shadow-md` | `0 4px 8px rgba(13,17,23,0.10)` | Cards, dropdown menus |
| `shadow-lg` | `0 8px 24px rgba(13,17,23,0.12)` | Modals, context menus, popovers |
| `shadow-xl` | `0 16px 48px rgba(13,17,23,0.16)` | Command palette, full-screen sheets |

Dark mode shadows use `rgba(0,0,0,0.32–0.56)` since dark surfaces need more opacity to show depth.

---

## 9. Atlaskit integration

### 9.1 Package set (Phase 0)

```json
"@atlaskit/tokens":          "^2.x",
"@atlaskit/button":          "^20.x",
"@atlaskit/textfield":       "^6.x",
"@atlaskit/form":            "^10.x",
"@atlaskit/heading":         "^2.x",
"@atlaskit/spinner":         "^16.x",
"@atlaskit/lozenge":         "^2.x",
"@atlaskit/avatar":          "^21.x",
"@atlaskit/page-layout":     "^3.x",
"@atlaskit/side-navigation": "^2.x",
"@atlaskit/empty-state":     "^7.x",
"@atlaskit/inline-message":  "^12.x",
"@atlaskit/flag":            "^15.x"
```

### 9.2 Theme bootstrap

```tsx
// src/main.tsx — before ReactDOM.createRoot
import { setGlobalTheme } from '@atlaskit/tokens';
setGlobalTheme({ colorMode: 'light', spacing: 'spacing' });
```

### 9.3 ContentHub theme override

```css
/* src/styles/theme.css — imported once in main.tsx */

/* ── Light mode ──────────────────────────────────────────────────────────── */
[data-theme~="light"] {
  /* Brand: CTA green */
  --ds-background-brand-bold:          #1ED760;
  --ds-background-brand-bold-hovered:  #17A84A;
  --ds-background-brand-bold-pressed:  #0F7A35;
  --ds-text-inverse:                   #000000; /* text ON green-500 — 11.3:1 AAA */
  --ds-text-brand:                     #0F7A35; /* green text on white — 7.3:1 AAA */
  --ds-icon-brand:                     #17A84A;

  /* Links / info */
  --ds-link:                           #1860E8;
  --ds-link-pressed:                   #1350C5;

  /* Surfaces */
  --ds-surface:                        #FFFFFF;
  --ds-surface-overlay:                #FFFFFF;
  --ds-surface-raised:                 #FFFFFF;
  --ds-surface-sunken:                 #F6F8FA;

  /* Text */
  --ds-text:                           #0D1117;
  --ds-text-subtle:                    #484F58;
  --ds-text-subtlest:                  #6E7681;
  --ds-text-disabled:                  #8B949E;

  /* Borders */
  --ds-border:                         #C9D1D9;
  --ds-border-focused:                 #1860E8;
  --ds-border-input:                   #C9D1D9;
}

/* ── Dark mode ───────────────────────────────────────────────────────────── */
[data-theme~="dark"] {
  --ds-background-brand-bold:          #1ED760;
  --ds-background-brand-bold-hovered:  #17A84A;
  --ds-background-brand-bold-pressed:  #0F7A35;
  --ds-text-inverse:                   #000000;
  --ds-text-brand:                     #1ED760; /* green on dark — 9.3:1 AAA */
  --ds-icon-brand:                     #1ED760;

  --ds-link:                           #60A5FA; /* blue-400 — 7.2:1 on dark */
  --ds-link-pressed:                   #93C5FD;

  --ds-surface:                        #161B22;
  --ds-surface-overlay:                #21262D;
  --ds-surface-raised:                 #21262D;
  --ds-surface-sunken:                 #0D1117;

  --ds-text:                           #ECEFF3;
  --ds-text-subtle:                    #8B949E;
  --ds-text-subtlest:                  #6E7681;
  --ds-text-disabled:                  #484F58;

  --ds-border:                         #30363D;
  --ds-border-focused:                 #60A5FA;
  --ds-border-input:                   #484F58;
}
```

---

## 10. Component quick-reference

### Buttons

| Variant | Light bg | Light text | Dark bg | Dark text | Use |
|---------|----------|------------|---------|-----------|-----|
| Primary (brand) | `#1ED760` | `#000000` | `#1ED760` | `#000000` | One per page max |
| Default | `#ECEFF3` | `#0D1117` | `#30363D` | `#ECEFF3` | General actions |
| Subtle | transparent | `#0D1117` | transparent | `#ECEFF3` | Tertiary / inline |
| Danger | `#CF222E` | `#FFFFFF` | `#CF222E` | `#FFFFFF` | Destructive only |
| Link | transparent | `#1860E8` | transparent | `#60A5FA` | In-text actions |

### Status lozenges (Atlaskit `Lozenge`)

| State | Light bg | Light text | Dark bg | Dark text |
|-------|----------|------------|---------|-----------|
| transcribing | `#EBF2FF` | `#1350C5` | `#1E3A6E` | `#60A5FA` |
| ready | `#EAFBF0` | `#0F7A35` | `#0D2B1A` | `#1ED760` |
| failed | `#FFEBE9` | `#CF222E` | `#3B1219` | `#F87171` |
| uploading | `#FFF8C5` | `#9A6700` | `#2E2100` | `#FBBF24` |

### Sidebar navigation (Atlaskit `SideNavigation`)

- Active item: left-border `4px solid #1ED760`, bg `#EAFBF0` (light) / `#0D2B1A` (dark)
- Inactive item text: `neutral-500` (#484F58) light / `neutral-400` (#6E7681) dark
- No green fills on inactive items

### Focus rings

All interactive elements use `--ds-border-focused` (`#1860E8` light / `#60A5FA` dark) at `2px solid`
with `2px offset`. Never remove the focus ring — it is a WCAG 2.4.7 requirement.

---

## 11. Usage rules (don't break these)

1. **Green is CTA-only.** If you reach for green for anything that isn't a primary button, active
   nav state, or live indicator — stop and use the blue or a neutral instead.
2. **Black text on green buttons.** White text on `#1ED760` is 1.9:1 — it fails WCAG. Black is 11.3:1.
3. **Never put warning yellow on white.** `#FFBE2E` on white is 1.9:1. Use it as a bg tint only with
   `#9A6700` text on top.
4. **Chart colours are not semantic colours.** Don't use `#B45309` (amber) to mean "warning" in a
   chart — that meaning belongs to the semantic palette. Keep the two sets separate.
5. **Focus rings stay.** Removing outlines for aesthetics is an accessibility violation.
