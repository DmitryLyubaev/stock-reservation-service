#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
base=http://127.0.0.1:8220
action=${1:-}
case "$action" in
  start)
    mkdir -p .local
    if [ ! -f .local/demo.env ]; then
      (umask 077; python3 -c 'import secrets; print("P20_DB_PASSWORD="+secrets.token_hex(32))' >.local/demo.env)
    fi
    if [ -z "$(compose ps --quiet api)" ] && [ -n "$(ss -ltnH 'sport = :8220')" ]; then
      echo 'Port 8220 is occupied; no unrelated service was stopped.' >&2; exit 1
    fi
    docker build --build-arg SOURCE_SHA="$P20_SOURCE_SHA" --build-arg SOURCE_TREE="$P20_SOURCE_TREE" --build-arg SOURCE_DIRTY="$P20_SOURCE_DIRTY" -t p20-stock-api:local .
    compose up --detach
    python3 -c 'import sys; sys.path.insert(0,"scripts"); from acceptance import ready; ready("http://127.0.0.1:8220")'
    echo "Project 20 ready: $base (run seed only for an empty dataset)"
    ;;
  seed)
    compose exec -T db psql -X -v ON_ERROR_STOP=1 -U p20_demo -d project20 <scripts/seed.sql
    ;;
  reserve)
    quantity=${2:-1}; order=${3:-DEMO-ORDER}; product=${4:-DEMO-RED}; key=${5:-$(python3 -c 'import uuid; print(uuid.uuid4().hex)')}
    body=$(python3 -c 'import json,sys; print(json.dumps(dict(quantity=int(sys.argv[1]),order_reference=sys.argv[2],product_code=sys.argv[3])))' "$quantity" "$order" "$product")
    curl --silent --show-error --fail-with-body -H 'Content-Type: application/json' -H "Idempotency-Key: $key" --data "$body" "$base/api/reservations"; echo
    ;;
  confirm|cancel|expire)
    id=${2:?Reservation UUID required}; key=${3:-$(python3 -c 'import uuid; print(uuid.uuid4().hex)')}
    python3 -c 'import uuid,sys; uuid.UUID(sys.argv[1])' "$id"
    curl --silent --show-error --fail-with-body -H 'Content-Type: application/json' -H "Idempotency-Key: $key" --data '{}' "$base/api/reservations/$id/$action"; echo
    ;;
  show)
    if [ -n "${2:-}" ]; then
      python3 -c 'import uuid,sys; uuid.UUID(sys.argv[1])' "$2"
      curl --silent --show-error --fail-with-body "$base/api/reservations/$2"; echo
    else
      curl --silent --show-error --fail-with-body "$base/api/inventory/DEMO-RED"; echo
    fi
    ;;
  stop) exec bash "$root/scripts/cleanup.sh" ;;
  *) echo 'Usage: demo.sh start|seed|reserve [quantity order product key]|confirm|cancel|expire UUID [key]|show [UUID]|stop' >&2; exit 2 ;;
esac
