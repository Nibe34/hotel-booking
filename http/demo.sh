#!/usr/bin/env bash
# Runs the main lab scenarios with curl and checks HTTP status codes.
# Same scenarios as hotel-booking.http, for a terminal-only demo.
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

json_id() { sed -E 's/^\{"id":([0-9]+).*/\1/' "$BODY"; }

ALICE=$(token alice alice123)
BOB=$(token bob bob123)
[[ -n $ALICE && -n $BOB ]] || { echo "Cannot get tokens from Keycloak at $KC"; exit 1; }
A=(-H "Authorization: Bearer $ALICE")
B=(-H "Authorization: Bearer $BOB")
JSON=(-H "Content-Type: application/json")

echo "AC1: CRUD"
check "list hotels (public)" 200 "$APP/api/hotels"
check "create hotel as alice" 201 -X POST "$APP/api/hotels" "${A[@]}" "${JSON[@]}" \
  -d '{"name":"Demo Hotel","city":"Kharkiv","address":"Sumska 1","stars":4}'
HOTEL=$(json_id)
check "get hotel $HOTEL" 200 "$APP/api/hotels/$HOTEL"
check "update hotel $HOTEL" 200 -X PUT "$APP/api/hotels/$HOTEL" "${A[@]}" "${JSON[@]}" \
  -d '{"name":"Demo Hotel","city":"Kharkiv","address":"Sumska 5","stars":5}'
check "create room in hotel $HOTEL" 201 -X POST "$APP/api/hotels/$HOTEL/rooms" "${A[@]}" "${JSON[@]}" \
  -d '{"roomNumber":"12","type":"DOUBLE","capacity":2,"pricePerNight":2100}'
ROOM=$(json_id)
check "update room $ROOM" 200 -X PUT "$APP/api/rooms/$ROOM" "${A[@]}" "${JSON[@]}" \
  -d '{"roomNumber":"12","type":"SUITE","capacity":3,"pricePerNight":3500}'
check "validation error" 400 -X POST "$APP/api/hotels" "${A[@]}" "${JSON[@]}" \
  -d '{"name":"","city":"Kyiv","address":"x","stars":9}'
check "delete room $ROOM" 204 -X DELETE "$APP/api/rooms/$ROOM" "${A[@]}"
check "delete hotel $HOTEL" 204 -X DELETE "$APP/api/hotels/$HOTEL" "${A[@]}"
check "deleted hotel is gone" 404 "$APP/api/hotels/$HOTEL"

echo "AC5: JWT"
check "no token" 401 "$APP/api/bookings/my"
check "invalid token" 401 "$APP/api/bookings/my" -H "Authorization: Bearer garbage"
check "valid token, no session" 200 "$APP/api/bookings/my" "${B[@]}"

echo "AC6: SecurityFilterChain"
check "bob POST /api/hotels" 403 -X POST "$APP/api/hotels" "${B[@]}" "${JSON[@]}" \
  -d '{"name":"Bob","city":"Kyiv","address":"x","stars":1}'
check "bob DELETE /api/rooms/1" 403 -X DELETE "$APP/api/rooms/1" "${B[@]}"
check "bob GET /api/admin/stats" 403 "$APP/api/admin/stats" "${B[@]}"
check "alice GET /api/admin/stats" 200 "$APP/api/admin/stats" "${A[@]}"

echo "Bookings"
CHECK_IN=$(date -d '+40 days' +%F)
CHECK_OUT=$(date -d '+43 days' +%F)
check "available rooms" 200 "$APP/api/rooms/available?checkIn=$CHECK_IN&checkOut=$CHECK_OUT&guests=2"
check "bob books room 2" 201 -X POST "$APP/api/bookings" "${B[@]}" "${JSON[@]}" \
  -d "{\"roomId\":2,\"checkIn\":\"$CHECK_IN\",\"checkOut\":\"$CHECK_OUT\",\"guestsCount\":2}"
BOOKING=$(json_id)
check "overlapping booking" 409 -X POST "$APP/api/bookings" "${A[@]}" "${JSON[@]}" \
  -d "{\"roomId\":2,\"checkIn\":\"$CHECK_IN\",\"checkOut\":\"$CHECK_OUT\",\"guestsCount\":1}"
check "bob gets own booking $BOOKING" 200 "$APP/api/bookings/$BOOKING" "${B[@]}"

echo "AC7: @PreAuthorize"
check "bob GET /api/bookings (all)" 403 "$APP/api/bookings" "${B[@]}"
check "alice GET /api/bookings (all)" 200 "$APP/api/bookings" "${A[@]}"
check "bob confirms booking" 403 -X PATCH "$APP/api/bookings/$BOOKING/confirm" "${B[@]}"
check "alice confirms booking" 200 -X PATCH "$APP/api/bookings/$BOOKING/confirm" "${A[@]}"
check "bob deletes booking" 403 -X DELETE "$APP/api/bookings/$BOOKING" "${B[@]}"
check "bob cancels own booking" 200 -X PATCH "$APP/api/bookings/$BOOKING/cancel" "${B[@]}"
check "alice deletes booking" 204 -X DELETE "$APP/api/bookings/$BOOKING" "${A[@]}"

echo "AC4: OIDC"
check "UI without session redirects to login" 302 "$APP/ui/index.html"

echo
echo "passed: $PASS, failed: $FAIL"
[[ $FAIL == 0 ]]
