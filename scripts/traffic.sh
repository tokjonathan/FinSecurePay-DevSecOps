#!/usr/bin/env bash
# Generates traffic against the FinSecure API to populate the dashboard with live data/ 
# or burst of invalid transactions meant for triggering the FinSecureHighRejectionRate alert.
#
# 2 forms of usage:
#   ./scripts/traffic.sh <EXTERNAL-IP> normal    # mixed valid/invalid traffic (dashboard demo)
#   ./scripts/traffic.sh <EXTERNAL-IP> attack    # ~5 invalid requests/sec for 4 minutes (fires the alert)
set -euo pipefail

IP="${1:?Usage: $0 <EXTERNAL-IP> [normal|attack]}"
MODE="${2:-normal}"
URL="http://${IP}/validate"

VALID='{"fromAccount":"ACC12345","toAccount":"ACC67890","amount":250.00,"currency":"SGD"}'
INVALID='{"fromAccount":"ACC12345","toAccount":"ACC12345","amount":-5,"currency":"JPY"}'

send() {
  curl -s -o /dev/null -w "%{http_code} " -X POST "$URL" -H "Content-Type: application/json" -d "$1"
}

if [ "$MODE" = "attack" ]; then
  echo "Sending invalid transactions to $URL for 4 minutes (Ctrl+C to stop)..."
  END=$((SECONDS + 240))
  while [ $SECONDS -lt $END ]; do
    send "$INVALID"
    sleep 0.2
  done
else
  echo "Sending mixed traffic to $URL (Ctrl+C to stop)..."
  while true; do
    send "$VALID"; send "$VALID"; send "$VALID"; send "$INVALID"
    curl -s -o /dev/null "http://${IP}/health"
    sleep 1
  done
fi
echo; echo "Done."
