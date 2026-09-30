<p align="center"><img src="docs/logo.png" width="128" alt="Stillpoint Launcher logo"></p>

<h1 align="center">Stillpoint Launcher</h1>

<p align="center">A calm, minimal home screen for Android. One important thing at a time.</p>

<p align="center">
  <a href="https://github.com/cloudit24/stillpoint/releases/latest"><img src="https://img.shields.io/github/v/release/cloudit24/stillpoint?label=version&color=1BA1E2" alt="Latest version"></a>
  <a href="https://github.com/cloudit24/stillpoint/releases"><img src="https://img.shields.io/github/downloads/cloudit24/stillpoint/total?color=1BA1E2" alt="Downloads"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-1BA1E2" alt="Android 8.0 or newer">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-1BA1E2" alt="GPL-3.0"></a>
</p>

<p align="center">
  <a href="https://github.com/cloudit24/stillpoint/releases/latest/download/Stillpoint-Launcher.apk"><b>⬇ Download the latest APK</b></a>
  &nbsp;·&nbsp;
  <a href="CHANGELOG.md">All versions</a>
</p>

---

## Download and install

1. **[Download Stillpoint-Launcher.apk](https://github.com/cloudit24/stillpoint/releases/latest/download/Stillpoint-Launcher.apk)** on your phone (Android 8.0 or newer).
2. Open it. If Android asks, allow your browser to install apps.
3. Press Home, pick **Stillpoint Launcher**, choose **Always**.

After that, updates come inside the app: **Settings → Updates**, with a notice when a new version is out.
Every older version is listed in the **[changelog](CHANGELOG.md)** and on the **[Releases](https://github.com/cloudit24/stillpoint/releases)** page, newest first.

## What it does

**Home**
- A headline that flips between a greeting, screen time, weather and the Hijri and Tamil dates.
- A clean ring beside it: the current prayer and the time left, or battery, or time left today.
- Your choice of calendar, battery and network under it; recently used and pinned apps below.
- Every swipe, double-tap and bottom shortcut can open any app or action.
- Home is a fixed frame: new things never push others off the screen ([design rules](docs/DESIGN.md)).

**The Shelf** (swipe right)
- Notes, tasks and projects, and widgets from any app, side by side, resized and moved by dragging.

**Islamic prayer** (optional)
- Prayer times calculated on the phone, prayer and iqama alerts, a Qibla compass that ticks as you turn,
  the moon phase, and an edge light that shows the prayer time running out.

**Productivity**
- Tasks from the phone, Tasks.org, OpenTasks or your own Project Hub; calendar from the phone or a calendar link.
- Focus mode that blocks chosen apps for a set time.

**Your look**
- Accent colours, grey or accent-tinted icons, five fonts, and Stillpoint widgets for any launcher.

## Private by design

No accounts, no analytics, no ads, no Google Play Services. Settings never leave the phone.
It goes online only for features you switch on, and **every address can be changed to your own**
(Settings → System → Online sources):

| Feature | Default source | What is sent |
|---|---|---|
| Weather and city search | [Open-Meteo](https://open-meteo.com) (open source) | The city's position, rounded to about 10 km |
| Gold price | Dubai City of Gold board, Swissquote spot, or your own address | Nothing about you |
| Currency rates | [Frankfurter](https://frankfurter.dev) (open source) | A currency code |
| Public IP | [ipify](https://www.ipify.org) (open source) | Nothing |
| Updates | GitHub Releases | Nothing |
| Project Hub, calendar link | Your own server | What you add or tick off |

Prayer times, Qibla, moon phase and dates are calculated on the phone.

## Two builds

| Build | Where | Updates |
|---|---|---|
| `github` | This page | Inside the app |
| `fdroid` | F-Droid (planned) | Through F-Droid |

They are signed with different keys, so switching from one to the other needs an uninstall.

## Build from source

GitHub Actions builds both APKs on every push. Locally: open the folder in Android Studio and build the
`githubDebug` or `fdroidDebug` variant.

**Releasing:** bump `versionCode` and `versionName` in `app/build.gradle.kts`, add the version to
[CHANGELOG.md](CHANGELOG.md), commit, then push a tag `v<versionName>`. CI signs the APK and publishes the
release with that changelog section as its notes.

## Permissions

| Permission | Why |
|---|---|
| Usage access | Screen time, most used and recently used apps, data usage |
| Calendar | Today's events (optional) |
| Notifications | Prayer alerts, lock screen info, update notice (optional) |
| Exact alarms | Prayer and iqama alerts on the minute |
| Internet | Only the features above that you switch on |
| Install packages | `github` build only: installing an update you chose |
| Accessibility service | Double-tap to lock and swipe for notifications only; cannot read the screen |

## Known limits

- Focus mode blocks apps opened from this launcher only.
- The background is plain black; wallpaper isn't shown.
- Gold prices don't include jewellery making charges.

## License

GPL-3.0, see [LICENSE](LICENSE). Fonts are under the SIL Open Font License ([docs/FONTS.md](docs/FONTS.md)).

Made by **[cloudit24](https://github.com/cloudit24)**.
