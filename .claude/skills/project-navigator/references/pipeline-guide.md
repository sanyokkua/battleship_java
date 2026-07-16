# Build & Pipeline Guide — Where Commands and CI Live

This reference explains how build/run/test commands and continuous-integration (CI) pipelines are
declared across common build systems and CI providers. Use it to extract the authoritative commands
for a repository rather than guessing.

---

## Where build/run/test commands are declared (by build system)

| Build system | File | Build | Test | Run/Start |
|---|---|---|---|---|
| npm / yarn / pnpm | `package.json` (`scripts`) | `build` | `test` | `start` / `dev` |
| Maven | `pom.xml` | `mvn clean package` | `mvn test` | `mvn spring-boot:run` (Spring) |
| Gradle | `build.gradle(.kts)` | `./gradlew build` | `./gradlew test` | `./gradlew bootRun` (Spring) |
| Python (pip) | `pyproject.toml` / `requirements.txt` | — (interpreted) | `pytest` | `python -m <pkg>` |
| Go | `go.mod` | `go build ./...` | `go test ./...` | `go run ./cmd/<bin>` |
| Cargo | `Cargo.toml` | `cargo build` | `cargo test` | `cargo run` |
| .NET | `*.csproj` / `*.sln` | `dotnet build` | `dotnet test` | `dotnet run` |
| Make | `Makefile` | `make` / `make build` | `make test` | `make run` |
| Task runners | `Taskfile.yml`, `justfile` | task/just targets | task/just targets | task/just targets |

> The README often lists commands too, but it can be stale. When the README and the manifest disagree,
> trust the manifest/CI; flag the contradiction.

---

## Identifying the CI pipeline

Look for these directories/files at the repository root to determine the CI provider, then read the
pipeline definition for the canonical build/test/deploy steps:

| Provider | Where the pipeline is defined |
|---|---|
| GitHub Actions | `.github/workflows/*.yml` |
| GitLab CI | `.gitlab-ci.yml` |
| CircleCI | `.circleci/config.yml` |
| Azure Pipelines | `azure-pipelines.yml` |
| Bitbucket Pipelines | `bitbucket-pipelines.yml` |
| Travis CI | `.travis.yml` |
| Drone | `.drone.yml` |
| Generic / self-hosted | a root pipeline file (often a declarative script) plus `ci/` or `scripts/` helpers |

### Reading a CI pipeline

Whatever the provider, the structure is the same — extract these:

1. **Triggers** — on push / pull request / tag / schedule / manual.
2. **Jobs / stages** — typically build → test → (package) → (deploy). Note the order and dependencies.
3. **Build & test commands** — the actual shell commands a job runs. These are authoritative — they are
   exactly what the project runs in CI.
4. **Artifacts** — what each stage produces (compiled binary, container image, package).
5. **Deploy targets & environments** — which environments exist (e.g. dev / staging / production) and
   how a deploy is triggered (auto on merge, manual approval, tag).
6. **Helper scripts** — pipelines frequently call scripts under `ci/`, `scripts/`, or `bin/`. Read
   those for the real logic (the pipeline file may just orchestrate them).

---

## Multi-stage / split pipelines

Some repositories split CI into multiple files (e.g. a build pipeline and a separate release/deploy
pipeline). When you see more than one pipeline file:

- The **build** pipeline usually handles compile, test, static analysis, versioning, and artifact
  publishing (e.g. pushing a container image to a registry).
- The **release/deploy** pipeline usually handles promotion to environments and may carry
  environment-specific configuration (parameter keys, account/target identifiers) inline.

Read both to get the full build-to-deploy picture; configuration for an environment may live in the
deploy pipeline rather than in the application's config files.

---

## Infrastructure-as-code in the pipeline

If the pipeline provisions or updates infrastructure, it will reference an infrastructure-as-code
layer. Common forms:

- Declarative templates under `infra/`, `deploy/`, `iac/`, or `ci/` (e.g. cloud resource templates).
- Programmatic infrastructure definitions (infrastructure expressed as code in the same language as the
  app or a dedicated subfolder).

A single infrastructure construct/template can create many resources — read the template/construct (or
its generated plan/output) to understand what is actually provisioned, rather than counting top-level
declarations.
