# Widgets (overview cards)

A module can add cards to the **Overview** page: a counter, a small chart, a status line. Admins can show or hide each card.

## Declare the card

In `module.json`:

```json
{
  "frontend": "notes.js",
  "widgets": [
    { "id": "count", "title": "Notes", "size": "small", "permission": "notes.view" }
  ]
}
```

| Field | Description |
| --- | --- |
| `id` | Identifies the card inside your module (`a-z`, `0-9`, `_`, `-`, 1 to 32 characters). Passed to `mountWidget`. |
| `title` | Heading shown by the panel |
| `size` | `small` (one column, default), `medium` (two columns) or `large` (full width). On narrow screens the grid has fewer columns, so cards get narrower. |
| `permission` | Who sees the card. Leave it out to show it to everybody who can open the Overview page. |

Widgets need a `frontend`. Without it the module is not loaded.

## Draw the card

Export `mountWidget` from your frontend file. The panel calls it once for every card, with the `id` from `module.json`:

```ts
import type { ModuleContext } from './minedash';

export function mountWidget(widgetId: string, target: HTMLElement, ctx: ModuleContext): () => void {
    if (widgetId !== 'count') return () => {};

    const value = document.createElement('div');
    value.style.cssText = 'font-size: 2rem; font-weight: 600; color: var(--md-text);';
    value.textContent = '–';
    target.append(value);

    const refresh = (): void => {
        ctx.http
            .get<{ count: number }>('/count')
            .then((result) => (value.textContent = String(result.count)))
            .catch(() => (value.textContent = '–'));
    };
    refresh();
    const timer = window.setInterval(refresh, 15_000);

    return () => {
        window.clearInterval(timer);
        value.remove();
    };
}
```

The panel draws the card frame and the title. You fill `target`.

## Rules

- **Return a cleanup function** that stops timers and removes what you added. Cards are removed when the user hides them or leaves the page.
- Your module can have several cards. `switch (widgetId)` decides what to draw. If you get an unknown id, do nothing.
- A card has its own `ctx`, separate from the page. It has the same members.
- **Refresh sparingly.** Every open Overview page of every admin runs your timer. Every 10 to 30 seconds is usually enough. For live data open a WebSocket channel instead.
- **Handle errors in the card.** A card that throws shows its message in its own frame and the other cards keep working. A good card shows `–` or a short text instead.
- Keep the content small. Large content belongs on the module page. A link to it is a good pattern: `<a href="#/m/<id>">Open</a>`.
- Use the colour variables from [Theming](theming.md).

## Permissions

The card appears only for accounts that have the `permission` you declared. The server does not check that, it is only a filter for the panel. The data route behind the card must have its own permission:

```java
context.get("/count", "notes.view", request -> Collections.singletonMap("count", store.count()));
```

Use the same permission in both places, so a user never sees a card that can only show an error.

## Show or hide

Admins choose which cards they see with **Customize** on the Overview page. New cards are shown by default. The choice is kept in the browser of each admin, not on the server. Cards of modules that are disabled disappear by themselves.
