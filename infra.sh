#!/usr/bin/env bash
# Starts/stops Postgres + Keycloak with plain podman (no compose provider needed).
# With docker/podman compose available, `compose.yaml` does the same.
set -euo pipefail
cd "$(dirname "$0")"

case "${1:-up}" in
  up)
    podman volume exists hotel-pgdata || podman volume create hotel-pgdata
    podman run -d --replace --name hotel-postgres -p 5432:5432 \
      -e POSTGRES_DB=hotel_booking -e POSTGRES_USER=appuser -e POSTGRES_PASSWORD=appuser \
      -v hotel-pgdata:/var/lib/postgresql/data \
      docker.io/library/postgres:17
    podman run -d --replace --name hotel-keycloak -p 8080:8080 \
      -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
      -v ./keycloak/hotel-realm.json:/opt/keycloak/data/import/hotel-realm.json:ro,Z \
      quay.io/keycloak/keycloak:26.0 start-dev --import-realm
    ;;
  down)
    podman rm -f hotel-postgres hotel-keycloak
    ;;
  reset-db)
    podman rm -f hotel-postgres
    podman volume rm -f hotel-pgdata
    ;;
  *)
    echo "usage: $0 [up|down|reset-db]" >&2
    exit 1
    ;;
esac
