#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_PUBLIC="$ROOT_DIR/../backend/src/main/resources/public"

(
  cd "$ROOT_DIR"
  elm make "src/Main.elm" --optimize --output="public/elm.js"
)

mkdir -p "$BACKEND_PUBLIC"
cp "$ROOT_DIR/public/index.html" \
   "$ROOT_DIR/public/styles.css" \
   "$ROOT_DIR/public/app.js" \
   "$ROOT_DIR/public/elm.js" \
   "$BACKEND_PUBLIC/"

if [[ -f "$ROOT_DIR/public/sw.js" ]]; then
  cp "$ROOT_DIR/public/sw.js" "$BACKEND_PUBLIC/"
fi
