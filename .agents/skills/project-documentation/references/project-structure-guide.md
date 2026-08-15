# Project Structure & Navigation Guide (for Documentation)

Use this reference while documenting a repository: classify the project type, then know where each
artifact you must document (entry points, configuration, infrastructure, pipeline) lives. It is
technology-agnostic — verify by reading files; names and nesting vary.

---

## Quick decision tree (by manifest at the repository root)

```
Which manifest(s) are present?
├── package.json                 → Node.js / JavaScript / TypeScript
│     ├── "workspaces"            → monorepo
│     └── start/serve script      → application (else likely a library)
├── pyproject.toml | requirements.txt → Python
├── pom.xml | build.gradle(.kts)  → JVM (Java/Kotlin); <modules>/settings.gradle ⇒ multi-module
├── go.mod                        → Go
├── Cargo.toml                    → Rust ("members" ⇒ workspace)
├── *.csproj | *.sln              → .NET
├── Gemfile / composer.json       → Ruby / PHP
└── multiple manifests / .git dirs → polyglot monorepo / multi-repo
```

Plus a deployable layer when present: `Dockerfile`, `docker-compose.yml`, and an infrastructure-as-code
folder (`infra/`, `deploy/`, `iac/`, or `ci/`).

---

## Where to find what you must document

| You need to document… | Look in… |
|---|---|
| **Identity** (name, version) | the manifest (`name`/`version`), `README.md`, CI/deploy config |
| **Entry points** | route/controller files, message listeners, scheduled-job declarations, CLI definitions, webhook handlers; for JVM the `main`/app-bootstrap class; for serverless the function handlers |
| **Exit points** | HTTP client calls, message publishers, database writes (repositories/ORM/queries), cache writes, file/external-system outputs |
| **Business logic** | service/handler/use-case layers; trace from each entry point inward; tests often encode edge cases |
| **External dependencies** | client/SDK wiring, dependency declarations in the manifest, config referencing external hosts/queues |
| **Data contracts** | entity/DTO/model classes, shared schema files (e.g. Avro/Protobuf/JSON Schema), database migrations |
| **Configuration** | config files (`application.*`, `appsettings.json`, `config/*`), env-var usage, parameter/secret store references |
| **Build & run** | the manifest scripts/targets, `Makefile`/task runner, `Dockerfile`, CI pipeline |
| **Infrastructure** | the IaC folder/templates or programmatic infrastructure definitions |
| **Repository metadata** | git history (default branch, recent contributors, last activity) — `scripts/git-metadata.sh` |

---

## Annotated layouts (common shapes)

### Node.js / TypeScript
```
package.json   → name, scripts (build/test/start), dependencies
src/           → source; index.ts/main.ts entry point
test/          → tests
dist/|build/   → generated output (ignore)
```

### Python
```
pyproject.toml | requirements.txt → deps/build
src/<pkg>/ | <pkg>/  → package; __main__.py/app.py/main.py entry point
tests/         → tests
```

### JVM (Maven/Gradle)
```
pom.xml | build.gradle(.kts)  → build/deps/version; multi-module via <modules>/settings.gradle
src/main/{java,kotlin}/       → source; *Application class is the bootstrap
src/main/resources/           → config (application.yml/properties)
src/test/                     → tests
```

### Container / serverless service
```
Dockerfile        → image + ENTRYPOINT/CMD (how it starts in prod)
infra/|deploy/|iac/ → infrastructure-as-code (one construct may create many resources)
ci/|scripts/      → deploy helper scripts (read these; the pipeline often just calls them)
```

---

## Configuration chain (document keys, never values)

```
Source of truth (env-specific config file OR parameter/secret store entry)
   ↓
Deploy/infra layer maps it to an environment variable
   ↓
Runtime environment variable (KEY=value in the process environment)
   ↓
Application binding (framework binds env var/key → typed setting)
   ↓
Code reads the setting (consumption point)
```

Reliability ranking when sources disagree:

1. Environment-specific config committed to the repo (e.g. `*-prod` config / `params/prod.*`) —
   authoritative for that environment.
2. The deploy/infrastructure definition (container env block, deploy manifest, CI deploy stage) —
   authoritative for what actually runs.
3. In-code defaults — fallbacks only.
4. One-time setup/bootstrap scripts and README tables — often **stale**; cross-check before trusting.

Document each value as `KEY → application property` (and the store path for secrets). Never include the
secret value itself.

---

## Other files worth noting in docs

| Purpose | Files |
|---|---|
| Existing docs | `docs/`, `README.md`, `mkdocs.yml`, `images/` |
| API collections / specs | `openapi.*`, `*.graphql`, Postman/Bruno collections |
| Diagrams | `*.drawio`, `*.mmd`, `docs/` images |
| Code quality | linter/static-analysis config, `renovate.json`/dependabot |
| Security | IaC scanner config, suppression files |
