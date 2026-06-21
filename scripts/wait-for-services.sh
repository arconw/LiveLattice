#!/usr/bin/env sh
set -eu

service="${1:-}"
timeout_seconds="${2:-120}"

if [ -z "$service" ]; then
  echo "usage: scripts/wait-for-services.sh <service> [timeout_seconds]" >&2
  exit 2
fi

deadline=$(( $(date +%s) + timeout_seconds ))

while [ "$(date +%s)" -lt "$deadline" ]; do
  status="$(docker compose ps --format json "$service" 2>/dev/null | sed -n 's/.*"Health":"\([^"]*\)".*/\1/p' | head -n 1)"
  if [ "$status" = "healthy" ]; then
    exit 0
  fi
  sleep 2
done

docker compose ps "$service"
exit 1

