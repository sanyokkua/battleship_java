# Project Structures — Type Decision Tree and Annotated Layouts

Use this reference to classify a repository and know where each kind of artifact lives. The decision
tree keys off manifest files (the most reliable signal); annotated layouts show the conventional
directory shape per stack. Always verify by reading file contents — names and nesting vary.

---

## Quick decision tree (by manifest at the repository root)

```
Which manifest(s) are present?
├── package.json                 → Node.js / JavaScript / TypeScript project
│     ├── "workspaces" field      → monorepo (npm/yarn/pnpm workspaces)
│     ├── tsconfig.json present    → TypeScript
│     └── framework dep            → React / Next.js / Express / NestJS / etc. (read dependencies)
├── pyproject.toml | setup.py | requirements.txt → Python project
│     └── framework dep            → Django / Flask / FastAPI / etc.
├── pom.xml | build.gradle(.kts)  → Java / Kotlin / JVM project
│     ├── <modules> in pom.xml     → multi-module Maven build
│     ├── settings.gradle          → multi-project Gradle build
│     └── spring-boot dependency   → Spring Boot service
├── go.mod                        → Go project (module path = first line)
├── Cargo.toml                    → Rust project ("members" → workspace)
├── *.csproj | *.sln              → .NET project / solution
├── Gemfile                       → Ruby project
├── composer.json                 → PHP project
└── several of the above in subfolders, or multiple .git dirs → multi-repo / polyglot monorepo
```

> Some projects mix infrastructure-as-code (a separate manifest under an `infra/` or `deploy/`
> subfolder) with application code. Classify the application first, then note the infrastructure layer
> separately.

---

## Node.js / TypeScript

```
package.json            → identity (name), dependencies, scripts (build/test/start)
package-lock.json       → npm lockfile (or yarn.lock / pnpm-lock.yaml)
tsconfig.json           → TypeScript config (presence ⇒ TypeScript)
src/                    → application source
  index.ts | main.ts    → common entry point
dist/ | build/          → compiled output (generated — ignore)
test/ | __tests__/      → tests
node_modules/           → installed dependencies (generated — ignore)
```

- Entry point: `package.json` → `main` / `bin` / `scripts.start`; or `src/index.*`.
- Commands: `package.json` → `scripts` (`build`, `test`, `start`, `dev`, `lint`).
- Library vs app: a library has no start/serve script and is published (has `main`/`exports`); an app
  has a start/serve script.

## Python

```
pyproject.toml          → modern build/dependency config (or setup.py / setup.cfg)
requirements.txt        → pinned dependencies (often alongside pyproject)
src/<package>/ | <package>/ → source package
  __main__.py | app.py | main.py → entry point
tests/                  → tests (pytest / unittest)
.venv/ | venv/          → virtual environment (generated — ignore)
```

- Entry point: `pyproject.toml` `[project.scripts]`, a `__main__.py`, or a framework app object
  (`app = FastAPI()` / `application` for WSGI).
- Commands: `pyproject.toml` (`pytest`, `tox.ini`, `Makefile`), or README.

## Java / Kotlin (Maven or Gradle)

```
pom.xml | build.gradle(.kts)   → build config, dependencies, version
settings.gradle | <modules>    → multi-module/multi-project indicator
src/main/java/ | src/main/kotlin/ → application source
  **/*Application.{java,kt}     → Spring Boot entry point (@SpringBootApplication)
src/main/resources/            → config (application.yml/properties), static resources
src/test/                      → tests
target/ | build/               → compiled output (generated — ignore)
```

- Entry point: the class with `public static void main` (Spring Boot: `@SpringBootApplication`).
- Commands: Maven (`mvn clean package`, `mvn test`) or Gradle (`./gradlew build`, `./gradlew test`).
- Multi-module: the root `pom.xml`/`settings.gradle` lists modules; real code is in submodule folders.

## Go

```
go.mod                  → module path + dependencies (Go version on the `go` line)
go.sum                  → dependency checksums
cmd/<binary>/main.go    → entry point(s) — one folder per binary (idiomatic)
internal/               → private packages
pkg/                    → exported packages
*_test.go               → tests (co-located with code)
```

- Entry point: `main.go` under `cmd/` (or repo root for single-binary projects).
- Commands: `go build ./...`, `go test ./...`, `go run ./cmd/<binary>`.

## Rust

```
Cargo.toml              → package/workspace config, dependencies
Cargo.lock              → locked dependency versions
src/main.rs             → binary entry point
src/lib.rs              → library entry point
tests/                  → integration tests
target/                 → build output (generated — ignore)
```

- Workspace: `[workspace] members = [...]` lists member crates.
- Commands: `cargo build`, `cargo test`, `cargo run`.

## .NET

```
*.sln                   → solution (groups projects)
*.csproj                → project file (target framework, package refs)
Program.cs              → entry point (Main / top-level statements)
appsettings.json        → configuration
bin/ obj/               → build output (generated — ignore)
```

- Commands: `dotnet build`, `dotnet test`, `dotnet run`.

## Container / deployable service (any language)

```
Dockerfile              → image definition (base image, build steps, entrypoint)
docker-compose.yml      → local multi-service orchestration
.dockerignore           → build-context exclusions
entrypoint.sh           → container startup script (may be at root or in a docker/ subfolder)
infra/ | deploy/ | iac/ → infrastructure-as-code (templates, modules, manifests)
```

- The `Dockerfile` `ENTRYPOINT`/`CMD` reveals how the service actually starts in production.
- Infrastructure-as-code lives in a dedicated folder; treat it as a separate layer from app code.

## Monorepo / multi-repo

```
apps/ | services/ | packages/ | libs/ → top-level groupings, each with its own manifest
turbo.json | nx.json | lerna.json | pnpm-workspace.yaml → JS monorepo tooling
```

- Each subfolder with its own manifest is an independent deployable/package — list them, do not blend.
- Multiple `.git` directories ⇒ a folder of separate repositories; navigate each independently.

---

## Supplementary files to check (any project type)

| What | Where |
|---|---|
| Documentation | `README.md`, `docs/`, `mkdocs.yml`, `*.md` at root |
| API definitions | `openapi.yaml`/`swagger.*`, GraphQL `*.graphql`, Postman/Bruno collections |
| Architecture diagrams | `docs/`, `images/`, `*.drawio`, `*.mmd` |
| Code quality / linting | `.eslintrc*`, `eslint.config.*`, `ruff.toml`, `.editorconfig`, static-analysis config |
| Dependency automation | `renovate.json`, `.github/dependabot.yml` |
| Security scanning | IaC scanner config, suppression files |
| Language version pin | `.nvmrc`, `.python-version`, `.tool-versions`, `.java-version` |
