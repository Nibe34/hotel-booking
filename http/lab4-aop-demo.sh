#!/usr/bin/env bash
# Lab 4 (AOP) demo against a running app: audit trail, idempotency key, rate limit, self-invocation.
# Creates its own hotel/room and deletes them at the end (the audit rows stay - that is the point of an audit).
# The rate limit is 10 bookings per minute per user, so wait a minute before running it again.
set -uo pipefail

APP=${APP:-http://localhost:8081}
KC=${KC:-http://localhost:8080/realms/hotel-realm}
BODY=$(mktemp)
trap 'rm -f "$BODY"' EXIT
PASS=0
FAIL=0

token() {
  curl -s "$KC/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=hotel-app -d "username=$1" -d "password=$2" |
    sed -E 's/.*"access_token":"([^"]+)".*/\1/'
}

# check <title> <expected status> <curl args...>
check() {
  local title=$1 expected=$2
  shift 2
  local code
  code=$(curl -s -o "$BODY" -w '%{http_code}' "$@")
  if [[ $code == "$expected" ]]; then
    PASS=$((PASS + 1))
    printf '  \e[32m%s\e[0m  %-70s\n' "$code" "$title"
  else
    FAIL=$((FAIL + 1))
    printf '  \e[31m%s\e[0m  %-70s (expected %s)\n' "$code" "$title" "$expected"
  fi
  [[ ${VERBOSE:-0} == 1 ]] && { head -c 400 "$BODY"; echo; }
}

# expect <title> <condition result 0/1>
expect() {
  if [[ $2 == 0 ]]; then
    PASS=$((PASS + 1)); printf '  \e[32m ok\e[0m  %s\n' "$1"
  else
    FAIL=$((FAIL + 1)); printf '  \e[31mbad\e[0m  %s\n' "$1"
  fi
}

json_id() { sed -E 's/^\{"id":([0-9]+).*/\1/' "$BODY"; }
audit_count() { curl -s "$APP/api/admin/audit?bookingId=$1" "${A[@]}" | grep -o '"action"' | wc -l; }
show_audit() { echo "      audit of $1: $(curl -s "$APP/api/admin/audit?bookingId=$1" "${A[@]}")"; }

ALICE=$(token alice alice123)
BOB=$(token bob bob123)
[[ -n $ALICE && -n $BOB ]] || { echo "Cannot get tokens from Keycloak at $KC"; exit 1; }
A=(-H "Authorization: Bearer $ALICE")
B=(-H "Authorization: Bearer $BOB")
JSON=(-H "Content-Type: application/json")
booking() { echo "{\"roomId\":$ROOM,\"checkIn\":\"$1\",\"checkOut\":\"$2\",\"guestsCount\":1}"; }

check "setup: hotel" 201 -X POST "$APP/api/hotels" "${A[@]}" "${JSON[@]}" \
  -d "{\"name\":\"AOP Demo $RANDOM\",\"city\":\"Lviv\",\"address\":\"Rynok 1\",\"stars\":3}"
HOTEL=$(json_id)
check "setup: room" 201 -X POST "$APP/api/hotels/$HOTEL/rooms" "${A[@]}" "${JSON[@]}" \
  -d '{"roomNumber":"1","type":"DOUBLE","capacity":2,"pricePerNight":1000}'
ROOM=$(json_id)

echo "1. @AuditBooking (@AfterReturning) - external calls through the proxy are audited"
check "bob creates booking" 201 -X POST "$APP/api/bookings" "${B[@]}" "${JSON[@]}" -d "$(booking 2030-01-01 2030-01-03)"
B1=$(json_id)
check "alice confirms booking $B1 (PATCH /{id}/confirm)" 200 -X PATCH "$APP/api/bookings/$B1/confirm" "${A[@]}"
check "bob cancels booking $B1" 200 -X PATCH "$APP/api/bookings/$B1/cancel" "${B[@]}"
show_audit "$B1"
expect "booking $B1 has 3 audit rows: CREATE by bob, CONFIRM by alice, CANCEL by bob" "$([[ $(audit_count "$B1") == 3 ]]; echo $?)"
check "failed call is not audited: confirm cancelled booking" 409 -X PATCH "$APP/api/bookings/$B1/confirm" "${A[@]}"
expect "still 3 audit rows for $B1" "$([[ $(audit_count "$B1") == 3 ]]; echo $?)"

echo "2. Self-invocation - confirmBatch calls this.confirm(id), the advice silently does not run"
check "bob creates booking" 201 -X POST "$APP/api/bookings" "${B[@]}" "${JSON[@]}" -d "$(booking 2030-02-01 2030-02-03)"
B2=$(json_id)
check "bob creates booking" 201 -X POST "$APP/api/bookings" "${B[@]}" "${JSON[@]}" -d "$(booking 2030-03-01 2030-03-03)"
B3=$(json_id)
check "alice confirms [$B2,$B3] via PATCH /confirm-batch" 200 -X PATCH "$APP/api/bookings/confirm-batch" \
  "${A[@]}" "${JSON[@]}" -d "[$B2,$B3]"
grep -q '"status":"CONFIRMED".*"status":"CONFIRMED"' "$BODY"; expect "both bookings are CONFIRMED" $?
show_audit "$B2"
expect "booking $B2 has only CREATE - no CONFIRM row (advice skipped)" "$([[ $(audit_count "$B2") == 1 ]]; echo $?)"
expect "booking $B3 has only CREATE - no CONFIRM row (advice skipped)" "$([[ $(audit_count "$B3") == 1 ]]; echo $?)"

echo "3. @Idempotent (@Around) - the same Idempotency-Key replays the first response"
KEY="demo-$RANDOM-$RANDOM"
check "bob creates booking with key $KEY" 201 -X POST "$APP/api/bookings" "${B[@]}" "${JSON[@]}" \
  -H "Idempotency-Key: $KEY" -d "$(booking 2030-04-01 2030-04-03)"
FIRST=$(json_id)
check "bob repeats the same request (client retry)" 201 -X POST "$APP/api/bookings" "${B[@]}" "${JSON[@]}" \
  -H "Idempotency-Key: $KEY" -d "$(booking 2030-04-01 2030-04-03)"
SECOND=$(json_id)
expect "same booking returned ($FIRST == $SECOND), no 409 and no second booking" "$([[ $FIRST == "$SECOND" ]]; echo $?)"
check "same request without the key is a real second attempt" 409 -X POST "$APP/api/bookings" "${B[@]}" "${JSON[@]}" \
  -d "$(booking 2030-04-01 2030-04-03)"

echo "4. @RateLimited (@Before) - at most 10 booking attempts per minute per user"
LAST=""
for i in $(seq 1 10); do
  LAST=$(curl -s -o "$BODY" -w '%{http_code}' -X POST "$APP/api/bookings" "${B[@]}" "${JSON[@]}" \
    -d "$(booking 2030-04-01 2030-04-03)")
  [[ $LAST == 429 ]] && break
done
expect "bob gets 429 after 10 attempts: $(head -c 160 "$BODY")" "$([[ $LAST == 429 ]]; echo $?)"
check "alice is not limited by bob's calls" 201 -X POST "$APP/api/bookings" "${A[@]}" "${JSON[@]}" \
  -d "$(booking 2030-05-01 2030-05-03)"

check "cleanup: delete hotel $HOTEL (bookings cascade, audit stays)" 204 -X DELETE "$APP/api/hotels/$HOTEL" "${A[@]}"
expect "audit of deleted booking $B1 is still there" "$([[ $(audit_count "$B1") == 3 ]]; echo $?)"

echo
echo "passed: $PASS, failed: $FAIL"
[[ $FAIL == 0 ]]
