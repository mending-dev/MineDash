# Theming

The panel has a light and a dark theme. A module follows both automatically if it uses the CSS variables of the panel instead of fixed colours.

## Variables

| Variable | Use | Light | Dark |
| --- | --- | --- | --- |
| `--md-bg` | Page background | `#f8fafc` | `#0b0f14` |
| `--md-surface` | Cards, panels | `#ffffff` | `#11161d` |
| `--md-surface-2` | Second level: headers, hover, table stripes | `#f1f5f9` | `#1a212b` |
| `--md-border` | Lines and borders | `#e2e8f0` | `#263040` |
| `--md-text` | Main text | `#0f172a` | `#e6edf3` |
| `--md-muted` | Secondary text | `#64748b` | `#8b98a9` |
| `--md-accent` | Primary buttons, links, focus | `#059669` | `#10b981` |
| `--md-accent-hover` | Hover state of accent | `#047857` | `#34d399` |
| `--md-accent-fg` | Text on top of the accent colour | `#ffffff` | `#022c22` |
| `--md-danger` | Errors, delete | `#dc2626` | `#f87171` |
| `--md-warning` | Warnings | `#b45309` | `#fbbf24` |
| `--md-info` | Information | `#0369a1` | `#38bdf8` |
| `--md-radius` | Corner radius of cards | `0.75rem` | `0.75rem` |
| `--md-font-mono` | Monospace font | system monospace | system monospace |

The values are the defaults of the current version and may change between versions. **Use the variable names, not the values.**

The dark theme is active when the `<html>` element has the class `dark`. You do not need to handle it: the variables change by themselves.

## Use them

```css
.notes li {
    background: var(--md-surface);
    border: 1px solid var(--md-border);
    border-radius: var(--md-radius);
    color: var(--md-text);
}
.notes small { color: var(--md-muted); }
.notes button {
    background: var(--md-accent);
    color: var(--md-accent-fg);
}
.notes button:hover { background: var(--md-accent-hover); }
.notes .error { color: var(--md-danger); }
.notes code { font-family: var(--md-font-mono); }
```

If you need a colour with transparency, use `color-mix`:

```css
background: color-mix(in srgb, var(--md-accent) 12%, transparent);
```

## Fonts and size

The page uses the font of the panel. Do not set `font-family` on your root element, except `var(--md-font-mono)` for code. Use `rem` or `em` for sizes so the page scales together with the rest.

## Build for the dark theme

- Do not hard-code `#fff` or `#000`. A white box on a dark page looks broken.
- Check contrast in both themes. Use the theme toggle of the panel.
- Icons and SVG graphics: use `currentColor` so they take the text colour.
- Charts: pick series colours that work on both backgrounds. A good start is `--md-accent`, `--md-info`, `--md-warning`.

## Layout

The page of your module is shown in the main area, next to the sidebar. It is as wide as the window allows. Choose your own `max-width` for text-heavy pages (the Notes example uses `40rem`). On phones the area is narrow, so use flexible layouts (`flex`, `grid`, `minmax`) and no fixed widths.

Cards for the overview page are different: the panel draws the frame and the title, you only fill the inside. See [Widgets](widgets.md).
