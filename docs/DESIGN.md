# Stillpoint Launcher design rules

Humble and finished. Every screen should feel done, not busy. When in doubt, leave it out.

## The home frame

Home is five zones, top to bottom. Each has a set size. A new feature goes **inside** a zone;
zones never push each other off the screen.

| Zone | Size | When it's full |
|---|---|---|
| Headline: date, flipping card, ring | Fixed (the card has one height for every language) | Never grows |
| Info: panels, footer figures, cards | Takes the space that's left | Scrolls inside itself, with soft faded edges |
| Recent apps | One row of icons | Never grows |
| Apps | At most a third of the screen | Scrolls inside itself, with soft faded edges |
| Shortcuts: left, middle, right | One row | Never grows |

A zone only scrolls when it has to, so the home swipe gestures keep working when everything fits.

## Rules

1. **One thing per place.** Prayer lives in the ring; the Prayer widget holds the full view. Don't repeat it.
2. **Quiet by default.** Grey text, one accent colour, thin lines. The accent marks the one thing that matters now.
3. **Nothing moves unless it means something.** Animations are short (under half a second), ease out,
   and run once. No looping motion except where time is running out (edge light), and all of it stops
   when Animations is off.
4. **Fixed sizes for changing content.** Text that changes (cards, the ring) shrinks to fit its space;
   the space itself never changes.
5. **Add a setting only when people really differ.** Otherwise pick the good default.
6. **Light on the battery.** Redraw once a minute; anything faster runs only while home is on screen.
7. **Private.** Calculate on the phone. Go online only for features the user switched on.
