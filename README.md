# Battleship rewrite

This repository is the greenfield Battleship rewrite. The development baseline is the
`rewrite_prod_ready` branch; `master` is historical context and is not the implementation baseline.

The first product is the API contract under [`contracts/`](contracts/): one OpenAPI document that both
the later backend and any frontend implement against. The backend is the model and a client is only a
view of it, so the contract contains no application code, game engine, persistence or UI.

## Contract

- The contract: [`contracts/openapi.yaml`](contracts/openapi.yaml)
- Guide (phases, client rules, privacy, versioning): [`contracts/README.md`](contracts/README.md)
- Governance: [`.specify/memory/constitution.md`](.specify/memory/constitution.md)
- Feature artifacts: [`specs/001-api-contract/`](specs/001-api-contract/)

Gate:

```sh
cd contracts
npm ci
npm run check
```

It lints the document, validates every embedded example against its schema and generates TypeScript
types. `npm run docs` builds an optional HTML reference into `contracts/dist/`. There is no
repository-wide product gate until the backend and frontend features add their own.
