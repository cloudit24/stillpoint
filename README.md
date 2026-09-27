# Stillpoint

Minimal Android launcher. Kotlin + Jetpack Compose. No INTERNET permission, no analytics, no Google Play Services.

## Build

1. Open the folder in Android Studio (Ladybug or newer). It generates the Gradle wrapper on first sync.
2. Build > Generate Signed App Bundle / APK > APK > release.
3. `adb install app-release.apk`

## First run

1. Press Home, pick Stillpoint, choose Always.
2. Long-press home > Usage access > allow Stillpoint.
3. Optional: Settings > Gesture service. On sideloaded installs (Android 13+) enable App info > menu > Allow restricted settings first.
4. Optional: Show today's calendar (grants READ_CALENDAR).

## Gestures

| Gesture | Action |
|---|---|
| Swipe up | App list with search (keyboard opens) |
| Swipe left | Widget page. Add widget / Edit to remove. Swipe right or Back to return |
| Long-press empty area | Settings |
| Double-tap | Lock screen (gesture service required) |
| Swipe down | Notification shade (gesture service required) |
| Long-press app in list | Pin, Add to Favorites, Hide, App info, Uninstall |
| Tap Favorites on home | Open / close the favorites folder |
| Long-press app in Favorites | Remove it from Favorites |
| Tap task / long-press task | Toggle done / delete |

## Permissions

| Permission | Purpose |
|---|---|
| PACKAGE_USAGE_STATS | Screen time, most-used apps |
| READ_CALENDAR | Today's agenda (optional) |
| REQUEST_DELETE_PACKAGES | Uninstall from the app list |
| Accessibility service | Lock and notification shade only. Receives no events, cannot read screen content |
| `<queries>` launcher intent | Scoped package visibility instead of QUERY_ALL_PACKAGES |

## Limitations

- Focus mode blocks launches from this launcher only. Apps opened from notifications, recents or links are not blocked.
- Category grouping uses the developer-declared category. Many apps declare none and land in Other.
- Plain black background; wallpaper is not shown.
- Widgets are stacked full-width at their minimum height; no drag-to-resize.

## License

GPL-3.0. See [LICENSE](LICENSE).
