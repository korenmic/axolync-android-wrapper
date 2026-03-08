#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "$ROOT/index.html" ]]; then
  python3 -m http.server --directory "$ROOT" 4173
else
  cd "$ROOT/repo/axolync-browser"
  npm ci
  npm run dev -- --host 0.0.0.0 --port 4173
fi
