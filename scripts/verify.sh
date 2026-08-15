#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$repo_root"

if ! git diff --quiet HEAD -- docs/openapi.json; then
  echo "docs/openapi.json has uncommitted changes; review or commit them before verification." >&2
  exit 1
fi

mvn clean verify

python3 -c 'import json, subprocess; from pathlib import Path; p = Path("docs/openapi.json"); current = json.loads(p.read_text(encoding="utf-8")); baseline_bytes = subprocess.check_output(["git", "show", "HEAD:docs/openapi.json"]); baseline = json.loads(baseline_bytes); p.write_bytes(baseline_bytes) if current == baseline else p.write_text(json.dumps(current, indent=2) + "\n", encoding="utf-8")'

if ! python3 -c 'import json, subprocess, sys; from pathlib import Path; current = json.loads(Path("docs/openapi.json").read_text(encoding="utf-8")); baseline = json.loads(subprocess.check_output(["git", "show", "HEAD:docs/openapi.json"])); sys.exit(current != baseline)'; then
  echo "Maven generated an OpenAPI change; review and commit docs/openapi.json before claiming done." >&2
  exit 1
fi

frontend_node_dir="$repo_root/frontend/node"
if [[ ! -x "$frontend_node_dir/node" || ! -x "$frontend_node_dir/npm" ]]; then
  echo "Maven did not install the pinned frontend runtime under frontend/node." >&2
  exit 1
fi

export PATH="$frontend_node_dir:$PATH"

npm --prefix frontend run test
npm --prefix frontend run lint

npm --prefix frontend run test:e2e
npm --prefix frontend run test:e2e:live
