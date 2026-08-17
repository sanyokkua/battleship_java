# Data Model: Synthesized Audio Feedback

**Feature**: [spec.md](./spec.md)

This feature adds no backend entity, REST field, SSE payload, or persisted game
record. The following are frontend-only state and test contracts.

## BattleOutcome

The finite outcome vocabulary used by the classifier and audio port.

| Value | Meaning | Source evidence |
|---|---|---|
| `MISS` | A newly shot cell has no ship. | Current cell `hasShot` transition and `ship == null`. |
| `HIT` | A newly shot ship cell does not provide evidence that a ship was destroyed. | Current cell has a ship and no applicable sink transition. |
| `DESTROYED` | The newly observed shot completed a ship. | Fleet board: newly sunk ship set; target board: opponent alive-ship count decreased. |

## GameplaySnapshotBaseline

The previous full `ResponseGameplayStateDto` used to classify a current state.

- `null` before the first state is applied.
- First state becomes the baseline and returns no feedback events.
- Each processed current state replaces the baseline, including an equivalent
  duplicate state so repeated push/refetch delivery remains idempotent.
- Baseline ownership is local to the mounted gameplay screen; a remount starts
  a new baseline and therefore does not replay shots already present in its
  first snapshot.
- Feedback-equivalent snapshots have identical board dimensions, coordinates,
  `hasShot` values, visible ship data, and player/opponent alive-ship counts.
  Names, turn/readiness, winner, alive-cell counts, availability, and other
  unrelated metadata are ignored.

## GameplayFeedbackEvent

An internal value returned by the pure classifier for one real newly shot cell.

| Field | Type/values | Rule |
|---|---|---|
| `outcome` | `BattleOutcome` | Determines the synthesized recipe. |
| `board` | `player` or `opponent` | Identifies whether the event came from the player's fleet or visible target board; useful for preserving incoming toast/highlight behavior and tests. |
| `cellKey` | stable row/column key | Identifies the newly shot cell for deduplication and deterministic assertions; not sent over the network. |

Events are ordered by `playerField` row-major traversal followed by
`opponentField` row-major traversal. Normal transport produces one outgoing
target-board change per local shot; incoming fleet-board batching may contain
several changed cells. A target-board state with an alive-ship decrease marks
the row-major first newly shot ship-bearing candidate as `DESTROYED`; other
newly shot visible ship cells in synthetic malformed batches remain `HIT`.

## FleetBoardDiff

The player's full-visibility board comparison.

- A cell is newly shot only when `previous.hasShot === false` and
  `current.hasShot === true`.
- `computeSunkShipIds(previous)` and `computeSunkShipIds(current)` identify
  newly completed ships.
- Cells adjacent to a newly completed ship that become shot with no ship are
  moat reveals and produce no event.
- A non-moat empty cell is `MISS`; a ship cell in a newly completed ship is
  `DESTROYED`; all other newly shot ship cells are `HIT`.
- Existing pure sunk/moat helpers must remain behaviorally identical after any
  extraction from `Board.tsx`.

## TargetBoardDiff

The opponent board as visible to the current player.

- Unshot opponent ship cells have `ship == null`, so ship-completion helpers
  must not be applied to this field.
- A newly shot empty visible cell is `MISS`.
- A newly shot visible ship cell is `HIT` unless
  `current.opponentNumberOfAliveShips < previous.opponentNumberOfAliveShips`;
  the decrease makes the row-major first newly shot ship-bearing cell
  `DESTROYED` and leaves other newly shot visible ship cells as `HIT`.
- A cell that becomes visible at game completion while `hasShot` stays false is
  ignored.

## SoundPreference

Browser-local user preference, separate from the game-session keys.

| State | Behavior |
|---|---|
| Missing key | Load as enabled. |
| Stored `true` | Sound requests may be played after unlock. |
| Stored `false` | Sound requests are ignored while the toggle shows off. |
| Toggle change | Persist immediately and update the accessible pressed state. |
| Game cleanup | Remains stored; `clearGameData` must not remove it. |

The storage key is an implementation constant owned by `AudioPreferences`; its
exact string is not a public contract, but it must be dedicated and tested.

## AudioLifecycle

Best-effort runtime state owned by the audio provider/service.

```text
uncreated --user gesture--> created/suspended or running
created/suspended --resume succeeds--> running
created/suspended --resume rejects--> unavailable/no-op
running --play(outcome)--> scheduled nodes -> stopped/cleaned
any state --unmount/dispose--> closed or safely detached
```

- `play` is a no-op when disabled, uncreated, unavailable, or not resumable.
- Context creation/resume errors are caught internally.
- Visibility recovery may retry an existing context but never counts as the
  initial user gesture.
- No lifecycle state is sent to the backend or persisted as game state.
