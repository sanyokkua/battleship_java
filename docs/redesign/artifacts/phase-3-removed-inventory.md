# Phase 3 — removed inventory

Scope: `IMPLEMENTATION_PLAN.md` Phase 3 (tasks 3.1–3.4). Strips the deprecated Bootstrap/class-component
frontend stack down to a clean, still-building baseline ahead of Phase 4 (Vite) and Phase 5 (rebuild).
No backend, API, or game-logic files were touched.

## Deleted files (20)

Class-component pages (`frontend/src/ui/pages/`), all Bootstrap-consuming and superseded by the Phase 5
rebuild against `MOCKUP.html`:
- `HomePage.tsx`
- `NewGamePage.tsx`
- `JoinGamePage.tsx`
- `PreparationPage.tsx`
- `WaitForPlayersPage.tsx`
- `GameplayPage.tsx`
- `FinishPage.tsx`
- `common/PagesCommonTypes.ts` (types used only by the pages above)

Navigation:
- `frontend/src/ui/ApplicationNavigationBar.tsx` (`react-bootstrap` `Nav`/`Navbar`)

Form elements (`frontend/src/ui/elements/forms/`):
- `JoinGameForm.tsx`
- `NewGameForm.tsx`
- `common/FormTypes.ts`

Gameplay elements (`frontend/src/ui/elements/gameplay/`):
- `Cell.tsx`
- `GameplayField.tsx`
- `common/GameplayTypes.ts`

Preparation elements (`frontend/src/ui/elements/preparation/`):
- `ButtonShip.tsx`
- `PrepCell.tsx`
- `PrepareField.tsx`
- `ShipsList.tsx`
- `Status.tsx`
- `common/PreparationTypes.ts`

All now-empty directories under `frontend/src/ui/` (`pages/`, `pages/common/`, `elements/forms/`,
`elements/forms/common/`, `elements/gameplay/`, `elements/gameplay/common/`, `elements/preparation/`,
`elements/preparation/common/`, `elements/`, `ui/`) were removed along with their contents.

Reason for all of the above: every one of these files is a class component that imports
`react-bootstrap` and/or `react-router-bootstrap` and is being fully replaced by the Phase 5 rebuild
(function components + hooks, custom CSS design system, driven by `GameAdapter`). Deleted outright —
not quarantined — since git history is the recovery path and none of these files are Phase 5 references
(`MOCKUP.html` + `SPECIFICATION.md` are).

## Edited files

- `frontend/src/App.tsx` — replaced the class component with a minimal placeholder function component.
  Keeps `react-router-dom`'s `Routes`/`Route` (that package isn't removed this phase); drops the
  `react-bootstrap` `Alert` and all 7 page imports. Renders a single catch-all route with a
  "rebuild in progress" placeholder until Phase 5 replaces it.
- `frontend/src/index.tsx` — removed `import "bootstrap/dist/css/bootstrap.min.css";`. Everything else
  (`ReactDOM`, `BrowserRouter`, `./app.css`) unchanged.
- `frontend/package.json` — removed:
  - `dependencies`: `bootstrap`, `react-bootstrap`, `react-router-bootstrap`, `prop-types`,
    `@types/react-router-bootstrap`
  - `devDependencies`: `@babel/core`, `@babel/preset-env`, `@babel/preset-react`, `webpack`,
    `webpack-cli` — confirmed unused before removal: no `.babelrc`/`babel.config.*`/`webpack.config.*`
    existed anywhere in `frontend/`, and no npm script invoked them; `react-scripts` bundles its own
    webpack/babel toolchain, so these five were inert cruft rather than real CRA scaffolding. Judgment
    call taken in the same cleanup pass even though task 3.1 only names the four SPEC §5.2 packages
    explicitly.
  - Left untouched: `react-scripts`, `react-router-dom`, `axios`, `axios-retry`, `copy-to-clipboard`,
    `react`/`react-dom`, `typescript`, testing-library packages, `web-vitals`, remaining `@types/*`,
    `eslintConfig`, `browserslist`, `proxy` — all Phase 4+ concerns.
- `frontend/package-lock.json` — regenerated via `rm -rf node_modules && npm install` against the
  trimmed `package.json`.

## Kept, untouched (Phase 5 references)

- `frontend/src/logic/ApplicationTypes.ts` — DTO/type definitions, no imports.
- `frontend/src/services/BackendRequestService.ts` — 12 axios wrapper functions, imports only
  `ApplicationTypes`.
- `frontend/src/services/GameBrowserStorage.ts` — localStorage helpers, imports only
  `ApplicationTypes`.
- `frontend/src/utils/GameUtils.ts`, `frontend/src/utils/StringUtils.ts` — `GameUtils.ts` composes the
  two kept services above; neither imports anything Bootstrap-related.
- `frontend/src/app.css` — no Bootstrap reference. Its one CSS class became unused once
  `GameplayField`/`PrepareField` were deleted, but at 7 lines it isn't worth separate churn — Phase 5
  replaces it with the real design system anyway.

None of the kept files import anything that was deleted in this phase — verified before deletion (zero
forward dependency from the keep-list into the removed set).

## Not applicable this phase (deferred to Phase 4)

Task 3.3 ("delete CRA-specific files/config not needed under Vite") had almost nothing to do now: this
repo's CRA scaffolding was already minimal — no `.env*`, `reportWebVitals.ts`, `setupTests.ts`, or
`react-app-env.d.ts` exist, and `public/index.html` has no Bootstrap CDN reference to strip. Moving
`index.html` to the project root and reconfiguring `tsconfig.json` for Vite are explicitly Phase 4's job
(`IMPLEMENTATION_PLAN.md` tasks 4.2, 4.6), not this phase's.

## Verification performed

1. `rm -rf node_modules && npm install` — succeeded (1409 packages).
2. `npm ls bootstrap react-bootstrap react-router-bootstrap prop-types` — `bootstrap`, `react-bootstrap`,
   `react-router-bootstrap` all absent; `prop-types` appears only as a transitive dependency of
   `react-scripts` → `eslint-config-react-app` → `eslint-plugin-react` (not a direct dependency of this
   project — expected and out of scope to remove).
3. `grep -rn "bootstrap\|react-bootstrap\|react-router-bootstrap\|prop-types" frontend/src frontend/package.json`
   — zero matches.
4. `npm run build` (still `react-scripts build`, Vite arrives in Phase 4) — compiled successfully;
   bundle shrank (~56 KB gzipped JS, ~28 KB gzipped CSS) from dropping Bootstrap.
5. `mvn clean install` from repo root — succeeded end-to-end (Maven `frontend-maven-plugin` →
   `maven-resources-plugin` boundary intact with the trimmed frontend).
6. `mvn test` — full backend suite green, confirming no backend files were disturbed.
