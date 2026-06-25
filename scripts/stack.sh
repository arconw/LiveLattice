#!/usr/bin/env bash
set -Eeuo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="${COMPOSE_FILE:-$root_dir/compose.yaml}"

compose() {
  docker compose --project-directory "$root_dir" -f "$compose_file" "$@"
}

usage() {
  cat <<'EOF'
usage: scripts/stack.sh <command> [service] [args...]

commands:
  start [service...]       Start the stack or selected services
  stop [service...]        Stop the stack or selected services without deleting volumes
  status [service...]      Show container status
  shell <service> [cmd...] Open a shell or run a command inside a service container
  rebuild [service...]     Rebuild and start the stack or selected services
  restart [service...]     Restart the stack or selected services
  logs [service...]        Follow logs
  services                 List compose services
  help                     Show this help

examples:
  scripts/stack.sh start
  scripts/stack.sh status
  scripts/stack.sh shell frontend
  scripts/stack.sh shell postgres psql -U livelattice -d livelattice
  scripts/stack.sh rebuild frontend
EOF
}

command="${1:-help}"
shift || true

case "$command" in
  start)
    compose up -d "$@"
    ;;
  stop)
    compose stop "$@"
    ;;
  status)
    compose ps "$@"
    ;;
  shell)
    service="${1:-}"
    if [ -z "$service" ]; then
      echo "usage: scripts/stack.sh shell <service> [cmd...]" >&2
      echo >&2
      compose config --services >&2
      exit 2
    fi
    shift

    if ! compose config --services | grep -Fxq "$service"; then
      echo "unknown service: $service" >&2
      echo >&2
      compose config --services >&2
      exit 2
    fi

    if [ "$#" -gt 0 ]; then
      compose exec "$service" "$@"
    else
      compose exec "$service" sh -lc 'if command -v bash >/dev/null 2>&1; then exec bash; fi; exec sh'
    fi
    ;;
  rebuild)
    if [ "$#" -gt 0 ]; then
      compose build "$@"
      compose up -d --no-deps "$@"
    else
      compose up -d --build
    fi
    ;;
  restart)
    compose restart "$@"
    ;;
  logs)
    compose logs -f "$@"
    ;;
  services)
    compose config --services
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    echo "unknown command: $command" >&2
    echo >&2
    usage >&2
    exit 2
    ;;
esac
