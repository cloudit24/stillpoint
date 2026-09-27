# Stillpoint

Minimal Android launcher. Kotlin + Jetpack Compose. No analytics, no Google Play Services.

## Two builds

| Build | Where | Updates |
|---|---|---|
| `github` | GitHub Releases | In-app: Settings > Updates |
| `fdroid` | F-Droid | Through F-Droid |

Both builds go online **only** for features you switch on (all off by default):

| Feature | Source | What is sent |
|---|---|---|
| Weather | [Open-Meteo](https://open-meteo.com) (open source, CC BY 4.0) | The chosen city's location, rounded to ~10 km. No GPS permission |
| Gold price | Swissquote public quotes (XAU/USD) | Nothing about you |
| Currency rates | [Frankfurter](https://frankfurter.dev) (open source, ECB rates); AED and SAR use the official peg | Currency code |
| Update check (`github` only) | GitHub Releases API | Nothing about you |

The builds are signed with different keys, so switching from one to the other needs an uninstall.

## Build

GitHub Actions builds both APKs on every push to `main` (download them from the run's *Artifacts*).

Locally: open the folder in Android Studio, then Build > Build APK(s). Pick the `githubDebug` or `fdroidDebug` variant.

### Releasing

1. In `app/build.gradle.kts`, bump `versionCode` and `versionName`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Commit, then tag and push: `git tag v0.2.0 && git push origin v0.2.0`.
4. CI checks that the tag matches `versionName`, signs the GitHub build with the release key (repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and publishes a GitHub Release. Installed copies then see the update.

The in-app updater only works once the repository is public.

## First run

1. Press Home, pick Stillpoint, choose Always.
2. Long-press home > Usage access > allow Stillpoint.
3. Optional: Settings > Gesture service. On sideloaded installs (Android 13+) enable App info > menu > Allow restricted settings first.
4. Optional: Show today's calendar (grants READ_CALENDAR).

## Gestures

Every gesture and both bottom shortcuts can be changed in Settings > Gestures and shortcuts. Each can be set to All apps, Widgets, Phone, Camera, Focus, Settings, Notifications, Lock screen, any app, or nothing.

| Gesture | Default |
|---|---|
| Swipe left | All apps |
| Swipe right | Widgets |
| Swipe up | All apps |
| Swipe down | Nothing |
| Double-tap | Nothing |
| Bottom-left / bottom-right | Focus / Phone |
| Long-press empty area | Settings (fixed) |

## App list

| | |
|---|---|
| Tabs | Most used (last 7 days), Recent (by install date), All, Favorites |
| All | Alphabetical, with a letter bar on the right: touch or drag it to jump |
| Favorites | Loose favorites plus your own folders. Tap a folder to open; long-press it to rename or delete |
| Long-press an app | Pin to home, Add/Remove Favorites, Move to folder, Hide, App info, Uninstall |

## Home

- Clock styles: Minimal, Bold, Classic serif, Retro flip, Retro LCD, Analog.
- Optional: animated weather next to the clock, gold price (currency, karat, per gram / ounce), live network speed and RAM.
- Tap the weather or gold price to refresh it.
- Most-used or pinned apps, as a text list or icons only (Settings > Home screen).
- *Home app size* sets the text size; icons scale with it. In icons-only mode, long-press an icon to see its name.
- Tasks live on the widget page (swipe right): tap to toggle done, long-press to delete.

## Permissions

| Permission | Purpose |
|---|---|
| PACKAGE_USAGE_STATS | Screen time, most-used apps |
| READ_CALENDAR | Today's agenda (optional) |
| REQUEST_DELETE_PACKAGES | Uninstall from the app list |
| INTERNET | Opt-in weather, gold price, and (`github` build) update check |
| REQUEST_INSTALL_PACKAGES | `github` build only: installing an update you chose |
| Accessibility service | Lock and notification shade only. Receives no events, cannot read screen content |
| `<queries>` launcher and widget intents | Scoped package visibility instead of QUERY_ALL_PACKAGES |

## Limitations

- Focus mode blocks launches from this launcher only. Apps opened from notifications, recents or links are not blocked.
- Plain black background; wallpaper is not shown.
- Widgets are stacked full-width at their minimum height; no drag-to-resize.
- Most used is based on Android's daily usage buckets, so the 7-day window is approximate.
- The gold price is the market spot price converted to your karat and unit; shop prices add making charges.
- For F-Droid, the gold price uses a non-free network service (Swissquote), so expect the NonFreeNet anti-feature label.

## License

GPL-3.0. See [LICENSE](LICENSE).
