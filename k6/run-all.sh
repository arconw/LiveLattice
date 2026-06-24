set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
profile="${K6_PROFILE:-smoke}"
scripts="${K6_SCRIPTS:-smoke.js rest-api.js dashboard-queries.js import-export.js search.js notifications.js audit-log.js background-jobs.js canvas-collaboration.js websocket-reconnect.js kafka-lag.js}"
export K6_DOCKER_USER="${K6_DOCKER_USER:-$(id -u):$(id -g)}"
mkdir -p "${script_dir}/reports"
reports=()

for script in ${scripts}; do
  name="${script%.js}"
  report="${name}-${profile}.json"
  docker compose -f "${script_dir}/compose.k6.yaml" run --rm \
    -e K6_PROFILE="${profile}" \
    k6 run --summary-export "/scripts/reports/${report}" "/scripts/${script}"
  reports+=("${report}")
done

summary="${script_dir}/reports/summary-${profile}.json"
{
  printf '{\n'
  printf '  "profile": "%s",\n' "${profile}"
  printf '  "status": "passed",\n'
  printf '  "generatedAt": "%s",\n' "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  printf '  "reports": [\n'
  for index in "${!reports[@]}"; do
    suffix=","
    if [ "${index}" -eq "$((${#reports[@]} - 1))" ]; then
      suffix=""
    fi
    printf '    "reports/%s"%s\n' "${reports[$index]}" "${suffix}"
  done
  printf '  ]\n'
  printf '}\n'
} > "${summary}"
cp "${summary}" "${script_dir}/reports/summary.json"
