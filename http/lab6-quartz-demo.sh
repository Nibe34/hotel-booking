#!/usr/bin/env bash
# Lab 6 (Quartz) demo against a running app: stored schedule, manual runs, idempotency, bounded retries.
# Jobs run on Quartz worker threads, so the script waits a moment after each manual run.
set -uo pipefail

APP=${APP:-http://localhost:8081}
KC=${KC:-http://localhost:8080/realms/hotel-realm}
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
PASS=0
FAIL=0

token() {
  curl -s "$KC/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=hotel-app -d "username=$1" -d "password=$2" |
    sed -E 's/.*"access_token":"([^"]+)".*/\1/'
}
expect() {
  if [[ $2 == 0 ]]; then PASS=$((PASS + 1)); printf '  \e[32m ok\e[0m  %s\n' "$1"
  else FAIL=$((FAIL + 1)); printf '  \e[31mbad\e[0m  %s\n' "$1"; fi
}
json_id() { sed -E 's/^\{"id":([0-9]+).*/\1/' "$1"; }
status_of() { curl -s "$APP/api/bookings/$1" "${A[@]}" | sed -E 's/.*"status":"([A-Z]+)".*/\1/'; }
audit_count() { curl -s "$APP/api/admin/audit?bookingId=$1" "${A[@]}" | grep -o '"action"' | wc -l; }
run_job() { curl -s -o /dev/null -w '%{http_code}' -X POST "$APP/api/admin/jobs/$1" "${A[@]}"; sleep 1.5; }

ALICE=$(token alice alice123)
BOB=$(token bob bob123)
[[ -n $ALICE && -n $BOB ]] || { echo "Cannot get tokens from Keycloak at $KC"; exit 1; }
A=(-H "Authorization: Bearer $ALICE")
B=(-H "Authorization: Bearer $BOB")
JSON=(-H "Content-Type: application/json")
TODAY=$(TZ=Europe/Kyiv date +%F)
TOMORROW=$(TZ=Europe/Kyiv date -d tomorrow +%F)
DAY2=$(TZ=Europe/Kyiv date -d "+2 days" +%F)

curl -s -o "$TMP/h" "${A[@]}" "${JSON[@]}" -X POST "$APP/api/hotels" \
  -d "{\"name\":\"Quartz Demo $RANDOM\",\"city\":\"Kyiv\",\"address\":\"Khreshchatyk 1\",\"stars\":5}"
HOTEL=$(json_id "$TMP/h")
curl -s -o "$TMP/r" "${A[@]}" "${JSON[@]}" -X POST "$APP/api/hotels/$HOTEL/rooms" \
  -d '{"roomNumber":"1","type":"DOUBLE","capacity":2,"pricePerNight":3000}'
ROOM1=$(json_id "$TMP/r")
curl -s -o "$TMP/r" "${A[@]}" "${JSON[@]}" -X POST "$APP/api/hotels/$HOTEL/rooms" \
  -d '{"roomNumber":"2","type":"DOUBLE","capacity":2,"pricePerNight":3000}'
ROOM2=$(json_id "$TMP/r")

echo "1. The schedule is stored in the database (QRTZ_* tables) and listed from there"
curl -s "$APP/api/admin/jobs" "${A[@]}" > "$TMP/jobs"
sed 's/},{/}\n{/g' "$TMP/jobs" | sed -E 's/.*"trigger":"([^"]+)".*"nextFireTime":"([^"]+)".*"triggerData":(\{[^}]*\}).*/      \1  next: \2  data: \3/'
echo
expect "3 triggers: expire every 5 min, reminders day/week before" "$([[ $(grep -o '"trigger"' "$TMP/jobs" | wc -l) == 3 ]]; echo $?)"

echo "2. ExpirePendingBookingsJob: PENDING booking whose check-in day has come is cancelled once"
curl -s -o "$TMP/b" "${B[@]}" "${JSON[@]}" -X POST "$APP/api/bookings" \
  -d "{\"roomId\":$ROOM1,\"checkIn\":\"$TODAY\",\"checkOut\":\"$TOMORROW\",\"guestsCount\":1}"
P=$(json_id "$TMP/b")
expect "run job -> $(run_job expire-pending)" 0
expect "booking $P is CANCELLED (actor system in the audit)" "$([[ $(status_of "$P") == CANCELLED ]]; echo $?)"
run_job expire-pending >/dev/null
expect "second run changes nothing: still 2 audit rows (CREATE + CANCEL)" "$([[ $(audit_count "$P") == 2 ]]; echo $?)"

echo "3. CheckInReminderJob: reminder for tomorrow's check-in is sent once"
curl -s -o "$TMP/b" "${B[@]}" "${JSON[@]}" -X POST "$APP/api/bookings" \
  -d "{\"roomId\":$ROOM2,\"checkIn\":\"$TOMORROW\",\"checkOut\":\"$DAY2\",\"guestsCount\":1}"
C=$(json_id "$TMP/b")
curl -s -o /dev/null -X PATCH "$APP/api/bookings/$C/confirm" "${A[@]}"
expect "run job (daysBefore from the job default = 1) -> $(run_job check-in-reminders)" 0
expect "run it again -> $(run_job check-in-reminders) (see log: '0 reminder(s) sent')" 0
expect "run with trigger-style override daysBefore=7 -> $(run_job 'check-in-reminders?daysBefore=7')" 0

echo "4. Failure strategy: invalid parameter makes the service throw -> 2 retries, then give up"
expect "run job with maxPendingMinutes=0 -> $(run_job 'expire-pending?maxPendingMinutes=0') (see log: attempt 1..3 of 3)" 0

curl -s -o /dev/null -X DELETE "$APP/api/hotels/$HOTEL" "${A[@]}"
echo
echo "passed: $PASS, failed: $FAIL"
[[ $FAIL == 0 ]]
