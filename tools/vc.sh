#!/bin/sh
# Drive the Pleos Virtual Console from the shell (needs `adb forward tcp:8088 tcp:8088`).
# The console ignores writes unless a browser session heartbeat is live, so send one first.
# Usage: vc.sh gear Park|Reverse|Neutral|Drive
#        vc.sh odo <km> | vc.sh soc <0-100> | vc.sh speed <kph> | vc.sh state
# The console refuses an odometer value lower than the current one (traveled_distance_must_not_decrease).
# Exits 1 when a write is refused or the console cannot be reached, so a failed setup step is not silent.
C=http://127.0.0.1:8088
case "$1" in gear|odo|soc|speed|state) ;; *) sed -n '2,7p' "$0"; exit 1 ;; esac
curl -fsS -m 5 -X POST "$C/browser/session?client=vc-sh" -o /dev/null || exit 1

send() {
  case "$1" in
    gear)  curl -sS --fail-with-body -m 5 -X PUT "$C/vehicle/gear?gear=$2" ;;
    odo)   curl -sS --fail-with-body -m 5 -X PUT "$C/vehicle/odometer?traveled_distance_km=$2" ;;
    soc)   curl -sS --fail-with-body -m 5 -X PUT "$C/vehicle/ev-battery?field=soc&value=$2" ;;
    speed) curl -sS --fail-with-body -m 5 -X PUT "$C/vehicle/controls?speed_kph=$2" ;;
    state) curl -sS --fail-with-body -m 5 "$C/vehicle/state" ;;
  esac
}

out="$(send "$@")" || { printf '%s\n' "$out"; exit 1; }
printf '%s\n' "$out"
# Only writes are checked: a state dump may contain an "error" field of its own.
case "$1:$out" in
  state:*) ;;
  *'"error"'*) echo "vc.sh: console refused: $*" >&2; exit 1 ;;
esac
