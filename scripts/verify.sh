#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
timeout 30 docker info >/dev/null
mkdir -p .local/maven .local/results
run_id=$(date -u +%Y%m%dT%H%M%S)-$$
results=$(realpath -m "${P20_RESULTS_DIR:-$root/.local/results/$run_id}")
case "$results" in "$root/.local/"*) ;; *) echo 'Results must stay under this project .local directory.' >&2; exit 2 ;; esac
[ ! -e "$results/maven.log" ] && [ ! -e "$results/acceptance.log" ] || { echo 'Refusing to overwrite an existing verification run.' >&2; exit 2; }
mkdir -p "$results"
export P20_SOURCE_SHA=$(git rev-parse HEAD)
export P20_SOURCE_TREE=$(git rev-parse HEAD^{tree})
export P20_SOURCE_DIRTY=$(test -z "$(git status --porcelain)" && echo false || echo true)
export P20_RESULTS_DIR="$results"
if [ "${1:-}" = --packaged ]; then
  shift
  mode=all
  if [ "${1:-}" = --mode ] && [ -n "${2:-}" ]; then mode=$2; shift 2; fi
  [ $# -eq 0 ] || { echo 'Unexpected packaged-mode arguments' >&2; exit 2; }
  finish_packaged(){
    rc=$?
    bash scripts/cleanup.sh >"$results/cleanup.log" 2>&1 || rc=1
    tail -4 "$results/cleanup.log"
    printf 'Packaged verification exit=%s; results=%s\n' "$rc" "$results"
    exit "$rc"
  }
  trap finish_packaged EXIT
  bash scripts/demo.sh start >"$results/start.log" 2>&1 || { tail -35 "$results/start.log"; exit 1; }
  python3 scripts/acceptance.py --base-url http://127.0.0.1:8220 --results "$results" --mode "$mode" >"$results/acceptance.log" 2>&1 || { tail -35 "$results/acceptance.log"; exit 1; }
  cat "$results/acceptance.log"
  exit 0
fi
build_image=$(python3 -c 'import json; d=json.load(open("config/toolchain-lock.json")); x=d["images"][0]; print(x["repository"].removeprefix("library/")+":"+x["tag"]+"@"+x["indexDigest"])')
docker build -f Dockerfile.tooling -t p20-stock-tooling:local . >"$results/tooling.log" 2>&1 || { tail -30 "$results/tooling.log"; exit 1; }
build_image=p20-stock-tooling:local
args=(--rm --network host --user "$(id -u):$(id -g)" --group-add "$(stat -c %g /var/run/docker.sock)"
  -e HOME=/workspace/.local/maven -e MAVEN_USER_HOME=/workspace/.local/maven -e MAVEN_CONFIG=/workspace/.local/maven
  -e TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1 -e P20_SOURCE_SHA -e P20_SOURCE_TREE -e P20_SOURCE_DIRTY
  -e P20_RESULTS_DIR="/workspace/${results#"$root/"}"
  -v "$root:/workspace" -v /var/run/docker.sock:/var/run/docker.sock -w /workspace)
if [ ! -f mvnw ]; then
  docker run "${args[@]}" "$build_image" mvn -B -N org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper -Dmaven=3.10.0 -Dtype=only-script >"$results/bootstrap.log" 2>&1 || { tail -40 "$results/bootstrap.log"; exit 1; }
  curl --fail --location --silent --show-error https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip -o .local/maven-3.10.0.zip
  expected=$(python3 -c 'import json; print(json.load(open("config/toolchain-lock.json"))["mavenDistribution"]["sha512"])')
  printf '%s  .local/maven-3.10.0.zip\n' "$expected" | sha512sum -c -
  checksum=$(sha256sum .local/maven-3.10.0.zip | cut -d ' ' -f1)
  printf '\ndistributionSha256Sum=%s\n' "$checksum" >>.mvn/wrapper/maven-wrapper.properties
fi
if [ "${1:-}" = --format ]; then
  shift
  docker run "${args[@]}" "$build_image" ./mvnw -B -Dmaven.repo.local=/workspace/.local/maven/repository spotless:apply "$@" >"$results/format.log" 2>&1 || { tail -40 "$results/format.log"; exit 1; }
  tail -8 "$results/format.log"
  exit 0
fi
set +e
python3 -m unittest discover -s tests -v >"$results/python-tests.log" 2>&1
export P20_PYTHON_EXIT=$?
docker run "${args[@]}" "$build_image" ./mvnw -B -Dmaven.repo.local=/workspace/.local/maven/repository spotless:check verify "$@" >"$results/maven.log" 2>&1
rc=$?
set -e
full_suite=true
for argument in "$@"; do case "$argument" in -Dit.test=*|-Dtest=*) full_suite=false ;; esac; done
python3 scripts/report.py capture --results "$results" --exit-code "$rc" --full-suite "$full_suite" >"$results/capture.log" 2>&1 || { cat "$results/capture.log"; rc=1; }
if [ "$P20_PYTHON_EXIT" -ne 0 ]; then tail -25 "$results/python-tests.log"; rc=1; fi
tail -45 "$results/maven.log"
printf 'Verification exit=%s; full log=%s\n' "$rc" "$results/maven.log"
exit "$rc"
