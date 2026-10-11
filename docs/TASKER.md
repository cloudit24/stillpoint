# Tasker and Stillpoint

Stillpoint raises events; Tasker does the plumbing (lights, auto-replies, your laptop). Turn it on in
Settings → Productivity → Automation. Off by default; nothing leaves the phone.

## Stillpoint → Tasker

Tasker profile: **Event → System → Intent Received**, action `com.cloudit24.stillpoint.EVENT`.

| `%event`       | When                                    | `%name`                     |
|----------------|-----------------------------------------|-----------------------------|
| `prayer_near`  | The heads-up before a prayer            | the prayer, e.g. `Asr`      |
| `prayer_time`  | The adhan                               | the prayer, or `Jumu'ah`    |
| `iqama`        | The iqama                               | the prayer                  |
| `focus_start`  | A focus profile started (hand or timer) | the profile, e.g. `Work`    |
| `focus_end`    | A focus profile ended                   | the profile                 |
| `special`      | Your special person messaged            | their name                  |
| `friday`       | Friday began · `friday_end` when it ends |                            |
| `night`        | 11 PM · `night_end` at 6 AM             |                             |
| `battery_low`  | Under 20% · `battery_low_end` when back |                             |

A rule with "Tell Tasker" sends its own trigger as the event too.

## Tasker → Stillpoint

Turn on **Accept commands from Tasker** first.

Tasker action: **System → Send Intent**, Target *Broadcast Receiver*, Package `com.cloudit24.stillpoint`,
Action `com.cloudit24.stillpoint.COMMAND`. Extras, one per line:

| Extra 1                | Extra 2             | Does                                   |
|------------------------|---------------------|----------------------------------------|
| `command:focus_start`  | `name:Work`         | starts that focus profile              |
| `command:focus_end`    |                     | ends the focus                         |
| `command:theme`        | `name:Neon Alley`   | switches theme (Stillpoint, Neon Alley, Cyberpunk) |
| `command:silent`       | `name:20`           | Do Not Disturb for 20 minutes          |
| `command:line`         | `text:Laundry done` | a line in the terminal display, 2 hours or until tapped |
| `command:calm`         |                     | cat, drone and edge motion off         |

Settings → Automation → **Copy event names** puts this on the clipboard.
