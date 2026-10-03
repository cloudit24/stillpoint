#!/usr/bin/env bash
# Installs the debug build, seeds sample notes, tasks and favorites, and captures the main screens.
set -x
P=com.cloudit24.stillpoint
A=$P/.MainActivity
mkdir -p shots
adb install -r "$(find app/build/outputs/apk/github/debug -name '*.apk' | head -1)"
adb shell cmd alarm set-timezone Asia/Dubai || adb shell setprop persist.sys.timezone Asia/Dubai
adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true

# A tidy status bar.
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 0942
demo battery -e level 87 -e plugged false
demo network -e wifi show -e level 4 -e mobile show -e datatype 4g -e level 4
demo notifications -e visible false

# Launcher keys of a few stock apps, for Favorites and senior-mode tiles.
key() {
  local r; r=$(adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$1" | tail -1 | tr -d '\r')
  case "$r" in */*) local pkg=${r%%/*} cls=${r#*/}; [[ $cls == .* ]] && cls=$pkg$cls; echo "$pkg/$cls#0";; esac
}
KEYS=()
for p in com.google.android.dialer com.android.dialer com.google.android.apps.messaging com.android.chrome com.android.camera2 com.google.android.contacts com.android.contacts com.android.settings com.google.android.calendar com.android.deskclock; do
  k=$(key $p); [ -n "$k" ] && KEYS+=("$k")
done
printf '%s\n' "${KEYS[@]}" > keys.txt

python3 - <<'PY'
import json, time
from xml.sax.saxutils import escape
keys = [k for k in open('keys.txt').read().split('\n') if k]
now = int(time.time() * 1000); day = int(time.time() // 86400)
notes = [{"id": now - 7200000, "text": "Call the bank about the new card", "color": 0},
         {"id": now - 86400000 * 2, "text": "Wi-Fi: Stillpoint-5G", "color": 1},
         {"id": now - 600000, "text": "Gift ideas: a book, a watch", "color": 2}]
tasks = [{"id": 1, "text": "Pay the electricity bill", "done": False, "due": day, "remind": 18 * 60},
         {"id": 2, "text": "Renew car insurance", "done": False, "due": day + 3, "remind": -1},
         {"id": 3, "text": "Book a dentist visit", "done": False, "due": -1, "remind": -1},
         {"id": 4, "text": "Buy milk", "done": True, "due": -1, "remind": -1}]
city = {"name": "Dubai", "country": "United Arab Emirates", "lat": 25.2048, "lon": 55.2708}
def s(k, v): return f'    <string name="{k}">{escape(v, {chr(34): "&quot;"})}</string>\n'
def b(k, v): return f'    <boolean name="{k}" value="{str(v).lower()}" />\n'
xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
xml += s("notes", json.dumps(notes)) + s("tasks", json.dumps(tasks)) + s("weather_city", json.dumps(city))
xml += b("prayer_on", True) + s("accent_style", "SOFT") + s("favorites", json.dumps(keys[:6]))
xml += s("tile_sizes", ";".join(f"{k}={i}" for k, i in zip(keys[:6], [1, 1, 0, 0, 0, 0])))
xml += s("senior_apps", "\n".join(keys[:6])) + s("senior_call_name", "Mum") + s("senior_call_number", "+971500000000")
open('seed.xml', 'w').write(xml + "</map>\n")
open('seed_senior.xml', 'w').write(xml + b("senior_mode", True) + "</map>\n")
PY

seed() {
  adb shell am force-stop $P
  adb push "$1" /data/local/tmp/seed.xml && adb shell chmod 644 /data/local/tmp/seed.xml
  adb shell run-as $P mkdir -p shared_prefs
  adb shell run-as $P cp /data/local/tmp/seed.xml shared_prefs/stillpoint.xml
}
shot() { sleep "${2:-4}"; adb exec-out screencap -p > "shots/$1.png"; }
read W H < <(adb shell wm size | tail -1 | sed 's/.*: //; s/x/ /')
MID=$((H / 2))
left() { adb shell input swipe $((W * 85 / 100)) $MID $((W * 15 / 100)) $MID 250; }
right() { adb shell input swipe $((W * 15 / 100)) $MID $((W * 85 / 100)) $MID 250; }
home() { adb shell am start -n $A -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; sleep 2; }

seed seed.xml
adb shell cmd package set-home-activity $A || true
adb shell am start -n $A; shot 01-home 8
left; shot 02-apps
for i in 1 2 3; do left; shot "03-apps-$i" 3; done
home; right; shot 04-shelf
adb shell am start -n $A --ez open_settings true; shot 05-settings
home

seed seed_senior.xml
adb shell am start -n $A; shot 06-senior 8
ls -l shots
