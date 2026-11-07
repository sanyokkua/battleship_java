# Battleship

A two-player Battleship web application. Create a game, share an invitation, arrange your fleet and play in a browser. The Java backend owns the rules and game state; the React frontend displays each player's permitted view. Games and sessions are held in memory and disappear when the backend restarts.

## Get started

Install Java **25** and Node.js **24** (the checks require those major versions), or just Docker (see below). From the repository root:

```sh
./scripts/local-build.sh     # install exact dependencies, build, run every test and quality gate (~10 minutes)
./scripts/local-deploy.sh    # run the packaged app; prints the URL to open on this machine and on your LAN
```

`local-deploy.sh` starts the backend and a same-origin web server (no CORS). It serves HTTPS with a self-signed certificate on your LAN address so phones can play; `./scripts/local-deploy.sh localhost` serves plain `http://localhost:4173` for this machine only. Stop it with Ctrl+C. Games and sessions are held in memory and are lost when it stops.

No Java or Node installed? `./scripts/local-build.sh --docker` runs the same gate inside a Docker build.

Docker images (built from the root `Dockerfile`, nothing is pushed): `scripts/build-docker-frontend.sh`, `scripts/build-docker-backend.sh` and `scripts/build-docker-render.sh [--smoke]`. The last one builds the single frontend + backend `linux/amd64` image for a manual [Render deployment](docs/render-deployment.md); Render terminates HTTPS and forwards HTTP to the image.

## Documentation

- [Project guide](docs/index.md): architecture, game lifecycle, API, security, data and configuration.
- [Developer guide](docs/development.md): repository layout, making changes, testing and debugging.
- [Local operation](docs/local-operation.md): commands, Docker images, LAN setup, ports and SonarQube.
- [Render deployment](docs/render-deployment.md): AMD64 image build, manual publishing and service configuration.
- [API contract guide](contracts/README.md) and [OpenAPI contract](contracts/openapi.yaml): authoritative wire formats, examples and client rules.

The active rewrite uses `rewrite_prod_ready` as its development baseline; `master` contains the historical application. Specifications live in [`openspec/specs/`](openspec/specs/) (workflow and rules in [`AGENTS.md`](AGENTS.md) and [`openspec/config.yaml`](openspec/config.yaml)); the original feature plans are archived in [`openspec/changes/archive/`](openspec/changes/archive/). The product intent is in [`docs/intent.md`](docs/intent.md) and the visual reference is [`docs/design/naval-command-center-mockup.html`](docs/design/naval-command-center-mockup.html).

## License

[License](LICENSE).
