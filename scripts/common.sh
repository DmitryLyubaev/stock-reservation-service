#!/usr/bin/env bash
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
case "$(uname -s)" in
  MINGW*|MSYS*)
    winroot=$(cygpath -m "$root")
    wslroot=$(wsl.exe wslpath -u "$winroot" | tr -d '\r')
    MSYS_NO_PATHCONV=1 exec wsl.exe --exec bash "$wslroot/scripts/$(basename "$0")" "$@" ;;
esac
cd "$root"
if [ -f .git ] && grep -Eq '^gitdir: [A-Za-z]:' .git; then
  export GIT_DIR=$(wslpath -u "$(sed 's/^gitdir: //' .git | tr -d '\r')")
  export GIT_WORK_TREE="$root"
fi
export P20_SOURCE_SHA=$(git rev-parse HEAD)
export P20_SOURCE_TREE=$(git rev-parse HEAD^{tree})
export P20_SOURCE_DIRTY=$(test -z "$(git status --porcelain)" && echo false || echo true)
compose(){
  if [ -f "$root/.local/demo.env" ]; then
    timeout 30 docker compose --project-name p20-stock-reservation --file "$root/compose.yaml" --env-file "$root/.local/demo.env" "$@"
  else
    P20_DB_PASSWORD=unused-for-cleanup timeout 30 docker compose --project-name p20-stock-reservation --file "$root/compose.yaml" "$@"
  fi
}
