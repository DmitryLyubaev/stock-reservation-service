#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
[ "$P20_SOURCE_DIRTY" = false ] || { echo 'Commit implementation changes before running the source-bound study.' >&2; exit 2; }
study=$(realpath -m "${1:-$root/.local/studies/$(date -u +%Y%m%dT%H%M%S)-$$}")
case "$study" in "$root/.local/"*) ;; *) echo 'Study output must be inside the project .local directory.' >&2; exit 2 ;; esac
[ ! -e "$study/canonical" ] && [ ! -e "$study/packaged" ] || { echo 'Refusing to overwrite an existing study.' >&2; exit 2; }
mkdir -p "$study"
rc=0
P20_RESULTS_DIR="$study/canonical" bash scripts/verify.sh >"$study/canonical-console.log" 2>&1 || rc=$?
tail -8 "$study/canonical-console.log"
if [ "$rc" -eq 0 ]; then
  P20_RESULTS_DIR="$study/packaged" bash scripts/verify.sh --packaged --mode all >"$study/packaged-console.log" 2>&1 || rc=$?
  tail -10 "$study/packaged-console.log"
fi
python3 scripts/report.py --results "$study" --output "$study/report.md" || rc=$?
printf 'Study exit=%s; source=%s; results=%s\n' "$rc" "$P20_SOURCE_SHA" "$study"
exit "$rc"
