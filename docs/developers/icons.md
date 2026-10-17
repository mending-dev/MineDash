# Icons

A module can have an SVG icon. It is shown in the sidebar next to the name, and in the list on the Modules page. Without an icon the panel shows the first letter of the module name in the same box.

## Three ways to set it

### 1. A file named `icon.svg`

Put `icon.svg` in the root of the jar (in a Gradle project: `src/main/resources/icon.svg`). Nothing else is needed. The Console and Files modules do this.

### 2. A path in `module.json`

```json
{ "icon": "assets/logo.svg" }
```

The path is relative to the root of the jar and must end in `.svg`. It must not start with `/` and must not contain `..` or `\`.

### 3. Inline markup in `module.json`

If the value starts with `<`, it is treated as the SVG markup itself:

```json
{
  "icon": "<svg viewBox=\"0 0 24 24\"><path d=\"M4 4h16v16H4z\"/></svg>"
}
```

Handy for tiny icons. Remember to escape the quotes in JSON.

If you give both `icon` and an `icon.svg`, `icon` wins.

## Drawing the icon

- Use a **square** `viewBox`, `0 0 24 24` is the default if you leave it out.
- Draw with `currentColor`. The panel sets the colour (the text colour of the sidebar, which is different in light and dark mode). If the SVG has no `fill`, `fill="currentColor"` is added for you.
- For line icons, set the line style on the root:

  ```svg
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
       stroke-linecap="round" stroke-linejoin="round">
    <polyline points="4 17 10 11 4 5"/>
    <line x1="12" y1="19" x2="20" y2="19"/>
  </svg>
  ```

- The icon is shown at about 60% of the box, so leave a little room to the edges of the viewBox only if you want a bigger gap.
- `width`, `height`, `x`, `y` and `id` on the root element are ignored. The panel sets the size.
- Icon sets such as Lucide, Feather or Heroicons produce files that work as they are (check their license).

### Example: the Console icon

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor"
     stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <polyline points="4 17 10 11 4 5"/>
  <line x1="12" y1="19" x2="20" y2="19"/>
</svg>
```

(Shown for explanation. The icon in the repository may be drawn slightly differently.)

## What is allowed

The panel does not trust icons. It reads the SVG and writes it again from a list of allowed parts. Everything else is dropped.

| Allowed elements | `svg`, `g`, `defs`, `path`, `circle`, `ellipse`, `rect`, `line`, `polyline`, `polygon`, `linearGradient`, `radialGradient`, `stop`, `clipPath`, `mask` |
| --- | --- |
| **Allowed attributes** | `id`, `href` (to `#ids` inside the icon), `d`, `points`, `cx`, `cy`, `r`, `rx`, `ry`, `x`, `y`, `x1`, `y1`, `x2`, `y2`, `fx`, `fy`, `width`, `height`, `transform`, `fill`, `fill-opacity`, `fill-rule`, `clip-rule`, `opacity`, `stroke`, `stroke-width`, `stroke-linecap`, `stroke-linejoin`, `stroke-miterlimit`, `stroke-dasharray`, `stroke-dashoffset`, `stroke-opacity`, `clip-path`, `mask`, `clipPathUnits`, `maskUnits`, `maskContentUnits`, `offset`, `stop-color`, `stop-opacity`, `gradientUnits`, `gradientTransform`, `spreadMethod`, `viewBox` (on the root) |

Not allowed, and removed: `<script>`, `<style>`, `<image>`, `<use>`, `<text>`, `<foreignObject>`, `<a>`, `<filter>`, `<animate>`, `<set>`, event handlers like `onclick`, the `style` attribute, links to other files, and external references.

**Convert text to paths** in your design tool, since `<text>` is not allowed. Replace CSS classes by plain attributes.

Limits:

| Limit | Value |
| --- | --- |
| Size of the markup | 32 KB |
| Size of an icon file in the jar | 64 KB |
| Elements | 200 |
| Nesting depth | 16 |
| `DOCTYPE` / entities | Not allowed |

The ids inside your icon get a prefix with the module id, so two icons that both use `id="a"` do not affect each other. References like `url(#a)` and `href="#a"` are rewritten to match.

## When the icon is rejected

The module still works. It gets the letter box instead, and the log shows a warning with the reason:

```text
[MineDash] Module 'notes': icon ignored, <reason>
```

Typical reasons: the file is not in the jar, it is too large, or it is not valid XML (an unclosed tag, a missing `xmlns` is fine). Open the file in a browser to check that it is valid SVG.

## Check it

Build, copy the jar, restart. The icon should be in the sidebar and on the Modules page, and keep its colour in both themes.
