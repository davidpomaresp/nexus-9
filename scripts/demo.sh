#!/usr/bin/env bash
# Runs the solar-degradation scenario against a local backend (docker compose up).
set -euo pipefail

API=${API:-http://localhost:8080/api}

id=$(curl -s -X POST "$API/simulations" -H 'Content-Type: application/json' \
  -d '{"seed": 42, "scenario": "solar-degradation"}' | sed -E 's/.*"id":"([^"]+)".*/\1/')
echo "Simulation $id"

for ticks in 150 200 50 50 150; do
  curl -s -X POST "$API/simulations/$id/advance?ticks=$ticks" > /dev/null
  echo
  echo "== $(curl -s "$API/simulations/$id")"
  curl -s "$API/simulations/$id/modules" | grep -oE '"module":"[A-Z]+","status":"[A-Z]+"'
done

echo
echo "== Open incidents"
curl -s "$API/simulations/$id/incidents?status=OPEN" | grep -oE '"ruleCode":"[A-Z_]+"|"openedAtTick":[0-9]+' | paste - -
