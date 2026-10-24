#!/usr/bin/env bash
# Lab 5 (async) demo against a running app: fire-and-forget e-mail, two calls combined, fallback, overload.
# Watch the application log at the same time: mails and documents are processed on threads "hotel-async-N".
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
# timed <outfile> <curl args...>  ->  prints "<status> <seconds>"
timed() { local out=$1; shift; curl -s -o "$out" -w '%{http_code} %{time_total}' "$@"; }
faster_than() { awk -v t="$1" -v max="$2" 'BEGIN { exit !(t < max) }'; }

ALICE=$(token alice alice123)
BOB=$(token bob bob123)
[[ -n $ALICE && -n $BOB ]] || { echo "Cannot get tokens from Keycloak at $KC"; exit 1; }
A=(-H "Authorization: Bearer $ALICE")
B=(-H "Authorization: Bearer $BOB")
JSON=(-H "Content-Type: application/json")

curl -s -o "$TMP/h" "${A[@]}" "${JSON[@]}" -X POST "$APP/api/hotels" \
  -d "{\"name\":\"Async Demo $RANDOM\",\"city\":\"Odesa\",\"address\":\"Derybasivska 1\",\"stars\":4}"
HOTEL=$(sed -E 's/^\{"id":([0-9]+).*/\1/' "$TMP/h")
curl -s -o "$TMP/r" "${A[@]}" "${JSON[@]}" -X POST "$APP/api/hotels/$HOTEL/rooms" \
  -d '{"roomNumber":"7","type":"DOUBLE","capacity":2,"pricePerNight":2000}'
ROOM=$(sed -E 's/^\{"id":([0-9]+).*/\1/' "$TMP/r")

echo "1. Fire-and-forget: the booking response does not wait for the ~1 s e-mail"
read -r code secs < <(timed "$TMP/b" -X POST "$APP/api/bookings" "${B[@]}" "${JSON[@]}" \
  -d "{\"roomId\":$ROOM,\"checkIn\":\"2032-01-01\",\"checkOut\":\"2032-01-04\",\"guestsCount\":2}")
BOOKING=$(sed -E 's/^\{"id":([0-9]+).*/\1/' "$TMP/b")
expect "POST /api/bookings -> $code in ${secs}s (< 0.8 s, mail is sent later on hotel-async-*)" \
  "$([[ $code == 201 ]] && faster_than "$secs" 0.8; echo $?)"

echo "2. Two @Async calls (~1 s rendering + ~0.7 s exchange rate) combined with thenCombine"
read -r code secs < <(timed "$TMP/i" "$APP/api/bookings/$BOOKING/invoice?currency=USD" "${B[@]}")
echo "      $(cat "$TMP/i")"
expect "invoice in USD -> $code in ${secs}s (< 1.5 s: the calls overlapped, sequential would be ~1.7 s)" \
  "$([[ $code == 200 ]] && faster_than "$secs" 1.5 && grep -q convertedTotal "$TMP/i"; echo $?)"

echo "3. A failing async call does not fail the request (exceptionally -> fallback)"
read -r code secs < <(timed "$TMP/x" "$APP/api/bookings/$BOOKING/invoice?currency=XYZ" "${B[@]}")
echo "      $(cat "$TMP/x")"
expect "invoice in XYZ -> $code with a note instead of the converted price" \
  "$([[ $code == 200 ]] && grep -q '"note"' "$TMP/x"; echo $?)"

echo "4. Overload: 40 parallel invoice requests on a bounded executor"
for i in $(seq 1 40); do
  curl -s -o /dev/null -w '%{http_code}\n' "$APP/api/bookings/$BOOKING/invoice?currency=EUR" "${B[@]}" &
done > "$TMP/codes"
wait
echo "      status codes: $(sort "$TMP/codes" | uniq -c | tr -s ' ' | tr '\n' ',')"
expect "some requests answered 503 'try again later' (rejected or timed out)" "$(grep -q 503 "$TMP/codes"; echo $?)"
expect "no request ended with 500" "$(! grep -q 500 "$TMP/codes"; echo $?)"

curl -s -o /dev/null -X DELETE "$APP/api/hotels/$HOTEL" "${A[@]}"
echo
echo "passed: $PASS, failed: $FAIL"
[[ $FAIL == 0 ]]
