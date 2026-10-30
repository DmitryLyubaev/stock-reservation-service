#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
ids=$(docker ps --all --quiet --filter label=com.docker.compose.project=p20-stock-reservation)
for identity in $ids; do
  [ "$(docker inspect "$identity" --format '{{index .Config.Labels "io.project20.demo"}}')" = true ] || { echo 'Refusing to stop an unlabelled container in Project 20 namespace.' >&2; exit 1; }
done
compose down --timeout 10
python3 - <<'PY'
import json,os,subprocess
from pathlib import Path
remaining=subprocess.check_output(["docker","ps","--all","--quiet","--filter","label=com.docker.compose.project=p20-stock-reservation"],text=True,timeout=20).split()
volumes=subprocess.check_output(["docker","volume","ls","--quiet","--filter","label=com.docker.compose.project=p20-stock-reservation"],text=True,timeout=20).split()
result={"remaining_owned_containers":len(remaining),"retained_volumes":volumes}
if os.environ.get("P20_RESULTS_DIR"):
    Path(os.environ["P20_RESULTS_DIR"],"cleanup.json").write_text(json.dumps(result,indent=2)+"\n")
print(json.dumps(result))
assert not remaining, "Owned containers remain after cleanup"
PY
