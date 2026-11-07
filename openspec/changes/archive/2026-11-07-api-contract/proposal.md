## Why

Frontend and backend need one consistent, stack-neutral contract that reflects every game stage without duplication and
puts all processing on the backend ("the UI only reflects the backend state"). The first the original workflow attempt (65 requirements,
six convergence rounds, 109 tasks, a custom validation product) was judged over-engineered by the owner and replaced on
2026-09-20 by a lean, single-file contract. This change migrates the finalized the original workflow feature `the original 001-api-contract artifacts`
(spec.md, plan.md, tasks.md) into OpenSpec; the deliverable `contracts/openapi.yaml` already exists and is the wire authority.

## What Changes

- Capture the 25 requirements (R01-R25), 4 success criteria (S1-S4) and 5 user stories of the feature as five `api-*`
  capability specs: `api-contract-document`, `api-game-model`, `api-privacy-security`, `api-reliability-errors`,
  `api-contract-gate`.
- Carry over the task list with its checked state (all complete) and the Lean-assurance design from plan.md.
- Provenance is preserved from the source: `[user]` = stated by the owner; `[agent]` = proposed by the agent and approved
  by the owner on 2026-09-20. Each requirement text keeps its original ID and provenance tag.
- Out of scope (unchanged): accounts, spectators, chat, matchmaking, bots, rankings, durable history, databases, multiple
  replicas, recovery after a restart, salvo rules, rematch, claiming a win against a vanished opponent, conditional reads,
  recovery of a join whose answer was lost before the cookie arrived.
- No new behavior and no code change; the migration is documentation only.

## Impact

- New capabilities: `api-contract-document`, `api-game-model`, `api-privacy-security`, `api-reliability-errors`, `api-contract-gate`.
- Affected artifacts: `contracts/openapi.yaml`, `contracts/README.md`, `contracts/package.json`, `contracts/redocly.yaml` (already delivered).
- Downstream: features 002-backend and 003-frontend implement against this contract.
