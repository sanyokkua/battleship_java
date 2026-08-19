# Production-Ready, IDE-Independent Code-Quality Toolchain for AI-Assisted Java Projects

## 1. Executive Summary

An AI coding agent can be given a fully terminal-driven, deterministic Java quality gate today. The verified core stack is: **Spotless** (formatting), **javac -Xlint + -Werror selectively** (compiler diagnostics), **Error Prone (+ NullAway)** (compile-time bug/nullness detection), **SpotBugs + Find Security Bugs** (bytecode bug/security detection), **PMD or Checkstyle** (style/smell rules — pick one, not both), **OWASP Dependency-Check and/or OSV-Scanner** (vulnerable dependencies), **Maven Enforcer / Gradle dependency-analysis** (dependency hygiene), **CycloneDX** (SBOM), and **ArchUnit** (architecture, as tests). All are free/OSS, actively maintained as of mid-2026, and run from Maven, Gradle, or the CLI. IDE-only features (IntelliJ inspections, SonarLint) are replaced by these, with the important exception that **JavaFX thread-confinement and FXML wiring have no reliable CLI checker and remain runtime/IDE-only** — a documented gap.

The single most important design decision: **wrap every tool behind a small, fixed command contract** (`verify-fast`, `verify`, `format`, `format-check`, etc.) implemented as Maven profiles / Gradle tasks and exposed through a thin script or `Makefile`/`justfile`, and document it in `AGENTS.md`. The agent never learns plugin syntax; it learns ~10 stable commands.

**TL;DR (three bullets answering the core question):**
- **Yes, a complete IDE-independent gate is achievable**: Spotless + javac lint + Error Prone/NullAway + SpotBugs/FindSecBugs + one of Checkstyle/PMD + OSV-Scanner/OWASP DC + Enforcer/dependency-analysis + CycloneDX + ArchUnit, all free/OSS, all with deterministic exit codes and machine-readable (SARIF/XML/JSON) output.
- **Wrap tools behind a fixed command contract** (`format`, `format-check`, `verify-fast`, `verify`, …) via Maven profiles / Gradle tasks + a `justfile`/`Makefile`, documented in `AGENTS.md`; give the agent a two-command finish rule: iterate with `verify-fast`, finish with `verify`, and compare against a frozen baseline so only *new* findings block.
- **Two real gaps remain**: JavaFX thread-confinement and FXML controller/`fx:id` wiring have **no** reliable CLI/static checker (runtime TestFX/Monocle tests only), and Spring bean-wiring/AOT/native compatibility can only be verified by **compiling and starting the application context**.

## 2. What IDE Inspections Provide That CLI Tooling Must Replace

IntelliJ IDEA and Eclipse inspections bundle several capability classes that an AI agent loses when working headless:

- **Real-time nullability analysis** (IntelliJ `@Nullable`/`@NotNull` dataflow). CLI replacement: **NullAway** (compile-time, JSpecify-aware) or **Checker Framework** (full type system).
- **Unused code / unreachable code / unused declaration highlighting.** CLI replacement: partial — javac (unused locals via `-Xlint`), PMD (`UnusedPrivateField`, `UnusedLocalVariable`, `UnusedPrivateMethod`), SpotBugs (`UPM`, `URF`, dead stores `DLS`). Unused *public* API and cross-module usage is not reliably detectable.
- **Deprecated API usage.** CLI replacement: `javac -Xlint:deprecation,removal`, `jdeprscan`, OpenRewrite.
- **Code smells / quick-fixes.** CLI replacement: PMD, Checkstyle, Error Prone (with `--patch`), SonarQube.
- **Spring/JavaFX-specific inspections** (bean wiring, FXML controller field validation, config-key completion). CLI replacement: mostly **none** — these require compiling/starting the application context (Spring) or runtime FXMLLoader (JavaFX). ArchUnit and Spring's own config-metadata processor partially cover the Spring side.
- **Structural search & architecture rules.** CLI replacement: **ArchUnit** (as JUnit tests).

The key epistemic point: IDE inspections are advisory and interactive; CLI tools must produce **deterministic exit codes** and **machine-readable output** (SARIF/XML/JSON) so an agent can decide pass/fail without a human.

## 3. Java Tooling Landscape (verified status, mid-2026)

| Tool | Latest (mid-2026) | License | JDK to run | Operates on | Classification |
|---|---|---|---|---|---|
| Spotless (Maven/Gradle) | Gradle plugin 7.x; core `spotless-lib` 3.x | Apache-2.0 | 11+ (Gradle plugin 7 needs 11) | source text | Verified fact |
| google-java-format | 1.x, last release Mar 3, 2026 (Maven Central) | Apache-2.0 | **min Java 21 to run** (per README/release notes) | source | Verified fact |
| palantir-java-format | current, Apache-2.0 | Apache-2.0 | uses Java impl when JDK ≥ 21 | source | Verified fact |
| Checkstyle | 13.7.0 | LGPL-2.1 | JDK 21–23 confirmed buildable | source AST | Verified fact |
| PMD | 7.19.0 (Nov 28, 2025); 7.21 adds Java 26 | BSD/Apache-2.0/LGPL | Java 8 runtime OK, builds need 21+ | source AST | Verified fact |
| SpotBugs | 4.9.x / 4.10.1 | LGPL-2.1 | JRE 11+ | bytecode | Verified fact |
| Find Security Bugs | 1.14.0 (Apr 2025) | LGPL | via SpotBugs | bytecode | Verified fact |
| Error Prone | 2.43.0 requires JDK 21 (2.42 last for 17) | Apache-2.0 | JDK 21+ | compiler AST | Verified fact |
| NullAway | 0.13.x (min JDK 17, EP 2.36+) | MIT | JDK 17+ | compiler AST | Verified fact |
| Checker Framework | current | GPL-2.0-w-classpath-exception (LGPL parts) | 8+ | compiler AST/type | Vendor/maintainer claim |
| SonarQube Community Build | 2025.x/2026.x monthly (calendar ver.) | LGPL-3.0 core; analyzers SSALv1 | server; JDK 17+ | source | Verified fact |
| OWASP Dependency-Check | 12.x | Apache-2.0 | 11+ | manifests/JARs | Verified fact |
| OSV-Scanner | current | Apache-2.0 | Go binary | lockfiles/SBOM | Verified fact |
| CycloneDX Maven plugin | 2.9.2 | Apache-2.0 | 8+ | build graph | Verified fact |
| CycloneDX Gradle plugin | 3.3.0 | Apache-2.0 | 8+ | build graph | Verified fact |
| Maven Enforcer | 3.6.3 | Apache-2.0 | — | build model | Verified fact |
| Revapi Maven plugin | current | Apache-2.0 | — | bytecode | Verified fact |
| japicmp | current | Apache-2.0 | — | bytecode | Verified fact |
| ArchUnit | 1.4.2 (Apr 18, 2026) | Apache-2.0/BSD | 8+ | bytecode (tests) | Verified fact |
| OpenRewrite (rewrite-maven/gradle) | 8.66.x (Nov 2025) | Apache-2.0 (Community recipes) | 8+ | LST | Verified fact |
| jdeps / jdeprscan | JDK-bundled | GPL-2.0-CPE | matches JDK | bytecode | Verified fact |
| autonomousapps dependency-analysis (Gradle) | 2.x | Apache-2.0 | — | bytecode/source | Verified fact |

## 4. Tool Comparison Matrix (capability → tool)

| Capability | Best CLI tool(s) | Fast enough for every run? | Machine-readable output |
|---|---|---|---|
| Format / format-check | Spotless | Yes (incremental) | exit code + diff |
| Compiler warnings | javac `-Xlint:all` | Yes | plain text |
| Unchecked/raw types, deprecation | javac `-Xlint:unchecked,deprecation,removal` | Yes | plain text |
| Likely bugs (dataflow, at compile) | Error Prone | Yes (part of compile) | compiler diagnostics |
| Likely bugs (bytecode) | SpotBugs | No (CI/pre-push) | SARIF/XML |
| Security (taint) | Find Security Bugs, SonarQube | No (CI) | SARIF/XML |
| Nullness | NullAway (fast), Checker Framework (slow) | NullAway: yes; CF: no | compiler diagnostics |
| Style/smells | Checkstyle (style), PMD (smells) | Yes | XML/SARIF |
| Dead stores / unused private | SpotBugs, PMD | SpotBugs no / PMD yes | XML |
| Deprecated JDK APIs (binaries) | jdeprscan | Yes | plain text |
| Automated deprecated-API migration | OpenRewrite | No | diffs |
| Unused/undeclared deps | Maven `dependency:analyze`, autonomousapps (Gradle) | Yes | text/console |
| Dependency convergence/bans | Maven Enforcer; Gradle constraints | Yes | text |
| Vulnerable deps | OWASP Dependency-Check, OSV-Scanner | DC: no (DB); OSV: yes | JSON/SARIF |
| SBOM | CycloneDX | Yes | CycloneDX JSON/XML |
| API/binary compat | Revapi, japicmp | No | XML/HTML |
| Architecture rules | ArchUnit | Yes (as tests) | JUnit results |

## 5. Maven Recommendations

**Formatting:** `spotless-maven-plugin` with `<java><palantirJavaFormat/></java>` (or `<googleJavaFormat>`), plus `<pom>`, `<markdown>`, `<formats>` for other file types. Bind `check` to the `verify` phase; expose `spotless:apply` as `format`. Pin the plugin version and the formatter version (formatter upgrades cause repo-wide diffs — see §2.2 rationale below and §19).

**Compiler:** `maven-compiler-plugin` with `<compilerArgs><arg>-Xlint:all</arg></compilerArgs>`. Add `-Werror` for libraries and greenfield apps; for Spring Boot apps use a curated lint subset (drop `-processing`, `-serial`) because annotation processors and generated code produce noise. Note the JDK behavior: `-Xlint:removal` is on by default; `-Xlint:deprecation` is not (per Oracle "Notifications and Warnings" docs for JDK 21/25). A concrete `-Werror` pitfall verified in the wild: annotation-processor rounds can emit `warning: No processor claimed any of these annotations`, which turns a clean build red under `-Werror` (immutables issue #672) — one reason libraries adopt `-Werror` but Spring/Lombok-heavy apps should not blanket-enable it. Generated sources should live under `target/generated-sources` and be excluded from Spotless and lint.

**Bug detection:** Error Prone via `annotationProcessorPaths` on the compiler plugin (no separate build step); SpotBugs via `spotbugs-maven-plugin` (`4.9.x`) bound to `verify` or run on demand; add `findsecbugs-plugin` as a SpotBugs plugin dependency.

**Style:** `maven-checkstyle-plugin` OR `maven-pmd-plugin` — one, not both.

**Dependencies/security:** `maven-enforcer-plugin` 3.6.3 (`dependencyConvergence`, `banDuplicatePomDependencyVersions`, `requireUpperBoundDeps`, `bannedDependencies`); `dependency:analyze` for unused/undeclared; `dependency-check-maven` and/or OSV-Scanner CLI; `cyclonedx-maven-plugin` `makeAggregateBom`; optionally `versions-maven-plugin` for outdated checks and `revapi-maven-plugin` for API compat.

**Architecture:** ArchUnit as JUnit 5 tests under `src/test/java`.

## 6. Gradle Recommendations

**Formatting:** `com.diffplug.spotless` 7.x (full configuration-cache support as of the 7.0 release). Same formatter choice as Maven; Gradle Kotlin DSL fully supported. Also format `*.gradle.kts` via the `kotlinGradle` block.

**Compiler:** `tasks.withType<JavaCompile> { options.compilerArgs.addAll(listOf("-Xlint:all")) }`; add `-Werror` selectively. Note Gradle treats compiler warnings per-task, so exclude generated-source tasks explicitly.

**Bug detection:** `net.ltgt.errorprone` plugin wires Error Prone into `JavaCompile`; `net.ltgt.nullaway` adds NullAway. SpotBugs via `com.github.spotbugs` (`6.x`). Note the Gradle-runtime constraint: because Error Prone 2.43.0 requires JDK 21 to *run*, your Gradle daemon/build JVM must be JDK 21+ (Gradle issue #35768). You can still target older bytecode with `--release`.

**Style:** built-in `checkstyle` plugin or `pmd` plugin (one).

**Dependencies/security:** `com.autonomousapps.dependency-analysis` (2.x) — the de-facto Gradle equivalent of `mvn dependency:analyze` (Gradle has no native equivalent); Gradle native **dependency locking** and **constraints** for convergence; `org.owasp.dependencycheck` and/or OSV-Scanner; `org.cyclonedx.bom` 3.3.0; `me.champeau.gradle.japicmp` or the Revapi Gradle plugin for compat.

**Architecture:** ArchUnit as tests.

## 7. JavaFX-Specific Recommendations

JavaFX desktop projects can use the *general* stack above, but **the JavaFX-specific inspections that IntelliJ provides have essentially no CLI equivalent** (subagent-verified):

- **Thread-confinement (off-FX-thread scene-graph mutation):** No SpotBugs, PMD, Error Prone, or SonarQube rule detects this. Error Prone's thread-safety checks (`GuardedBy`, `ThreadSafe`) are lock/annotation-based, not JavaFX-Application-Thread aware; SpotBugs' only JavaFX awareness is *defensive* (it ignores `@FXML` fields in `UR_UNIT_READ` to avoid false positives). The violation surfaces only at runtime as `IllegalStateException: Not on FX application thread` (thrown by `com.sun.javafx.tk.Toolkit.checkFxUserThread`) or via IntelliJ. **Mitigation:** enforce a convention via a custom ArchUnit rule (e.g., methods touching UI must be in designated packages) and rely on runtime tests using TestFX; document this as a manual-review item.
- **FXML controller/`fx:id` validation:** No reliable mainstream CLI tool. FXMLLoader throws `javafx.fxml.LoadException` at runtime; IntelliJ/Scene Builder validate interactively. The experimental `bsels/javafx-maven-plugin` `fxml-source` goal generates Java from FXML for compile-time safety, but it is a tiny single-author project (~19 commits) requiring Java 25 and a modular project — not production-grade. Other "fx-validation" projects are runtime *form-input* validators, not FXML wiring validators. **Mitigation:** a smoke test that loads each FXML via `FXMLLoader` in a headless (Monocle) test is the only reliable check.
- **Packaging validation:** Use JDK-bundled `jdeps` (module/dependency analysis), `jlink`, and `jpackage`; the official `org.openjfx:javafx-maven-plugin` (0.0.8, last released October 5, 2021 — dormant but functional, goals `run`/`jlink` only) and `org.openjfx.javafxplugin` Gradle plugin (0.1.0) manage the module path but perform **no** semantic validation. **Badass JLink** Gradle plugin (`org.beryx.jlink` 4.0.1, actively maintained; maintenance taken over by Axel Howind) builds runtime images/installers. `jpackage` cannot cross-build installers — CI must run on each target OS.
- **CSS/resource validation:** No dedicated JavaFX CSS linter; treat as runtime.
- **PMD JavaFX ruleset:** none exists. PMD ships `bestpractices`, `codestyle`, `design`, `errorprone`, `multithreading`, `performance`, `security` categories — no `javafx.xml`. (PMD's *own* GUI happens to be built with JavaFX; unrelated.)

Bottom line for the JavaFX architect: put the general stack in place, add TestFX + headless FXML smoke tests, and accept that thread-safety and FXML wiring are **runtime-verified, not statically verified**.

## 8. Spring Boot-Specific Recommendations

- **Nullness:** Spring Framework 7 / Spring Boot 4 (GA November 20, 2025) switched the whole Spring codebase to **JSpecify** annotations, deprecating the `org.springframework.lang` annotations (per Sébastien Deleuze, Spring blog "Null-safe applications with Spring Boot 4," November 12, 2025). NullAway JSpecify mode is the recommended CLI enforcement. Standard NullAway enforcement runs on JDK 17+; **full JSpecify mode** (checking generics, arrays, varargs) requires a newer compilation toolchain — the Spring team recommends Java 25, and most JDK 21.0.8+ distributions (except Oracle JDK) support the required `-XDaddTypeAnnotationsToSymbol=true` flag; JavaCodeGeeks (June 2026) states full JSpecify mode needs "at least JDK 22 as the compilation toolchain." In all cases you can keep a Java 17 bytecode baseline with `--release 17` on a newer toolchain.
- **Deprecated Spring APIs / config properties:** **OpenRewrite** community recipes (`org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_5`, `...boot4.UpgradeSpringBoot_4_0`) migrate deprecated APIs and config keys and are free (Community Edition; some composite recipes are Moderne Source Available). The Boot 4.0 recipe chains Spring Framework 7, Security 7, Batch 6, Hibernate 7.1, Testcontainers 2, and property migration. Run as `mvn rewrite:run` / `gradle rewriteRun` in a dedicated migration task, never in the default gate (auto-fixes are not guaranteed behavior-preserving).
- **Config metadata:** `spring-boot-configuration-processor` generates metadata; the `spring-boot-properties-migrator` module flags renamed/removed properties at startup.
- **Architecture:** ArchUnit for layering (controllers → services → repositories), naming, and no-field-injection rules. SonarQube Server 2025 Release 2+ adds a Design & Architecture feature (commercial editions).
- **Checks that require a running context (cannot be static):** bean wiring/`@Autowired` resolution, conditional configuration, actuator endpoints, AOT/native-image compatibility. Use `@SpringBootTest` context-load tests and `spring-boot-starter-test`; for native, compatibility is validated by the GraalVM `native-maven-plugin`/`native-gradle-plugin` build plus Spring AOT processing — these are build/runtime checks, not lint.

## 9. Dependency and Security Analysis

**Dependency hygiene (not security):**
- *Declared-but-unused / used-but-undeclared:* Maven `dependency:analyze`; Gradle autonomousapps `buildHealth`/`projectHealth`. Both operate on bytecode and share known limits: source-retention annotations (`@Retention(SOURCE)`, e.g. some generated annotations) and cross-module class hierarchies are invisible, requiring `permitUnusedDeclared`-style allowances.
- *Convergence / duplicates / version conflicts:* Maven Enforcer `dependencyConvergence` (3.6.3) — note it fails easily on SLF4J/Guava, so scope excludes are normal; Gradle uses constraints + `dependencyInsight`.
- *Locking / reproducibility:* Maven has no native lockfile (use dependency management or community lock plugins); Gradle has first-class **dependency locking**.

**Vulnerability scanning:**
- **OWASP Dependency-Check** (Apache-2.0, free/unlimited): CPE/NVD-based transitive matching for Maven/Gradle/npm/Python/Ruby/Go/NuGet; strong compliance-oriented HTML + CycloneDX SBOM output; **downloads and caches the NVD database** (needs an NVD API key and periodic updates — slow first run, so CI/scheduled, not every local run); higher false-positive rate due to CPE matching; suppression via XML file; configurable `failBuildOnCVSS`.
- **OSV-Scanner** (Google/OpenSSF, Apache-2.0): queries the OSV database (aggregates NVD + GitHub Advisory + ecosystem advisories); ecosystem-specific matching → **fewer false positives**; very fast; great for lockfiles/SBOM; online by default.
- **GitHub Dependency Review / Dependabot:** GitHub-native, free, opens fix PRs; no CI-blocking gate by itself.

*Do two scanners add value?* Modestly. OWASP DC (CPE/NVD) and OSV (OSV DB) have **different data sources and different false-positive/negative profiles**, so running both catches more, at the cost of noise. For most projects, **OSV-Scanner in CI/pre-push + Dependabot for fix PRs** is the best signal/noise; add OWASP DC in strict configs for compliance SBOMs. **Known-vulnerability scanning ≠ malicious-package detection** — none of these reliably detect a freshly published malicious package; that needs a behavioral/reputation scanner and is out of scope for these OSS scanners.

**SBOM:** CycloneDX Maven (`makeAggregateBom`) / Gradle (`cyclonedxBom`) — CycloneDX is more mature for JVM than SPDX tooling; validate the output before shipping.

**API/binary compatibility:** **Revapi** (tracks API "surface", reports dep type leakage) and **japicmp** (source+binary, semantic-versioning hints) — both bytecode-based. The research-grade source-based **Roseau** (Latappy, Degueule, Falleri, Robbes & Ochoa, "Roseau: Fast, Accurate, Source-based API Breaking Change Analysis in Java," 41st IEEE ICSME 2025, arXiv:2507.17369) reports higher accuracy across 60 popular Maven Central libraries — "Roseau achieves higher accuracy (F1 = 0.99) than JApiCmp (F1 = 0.86) and Revapi (F1 = 0.91)" — but is not yet a production build plugin. Use japicmp or Revapi in libraries only.

## 10. Dead-Code Detection and Its Limitations

| Category | Reliable CLI detection? | Tool |
|---|---|---|
| Unused imports | Yes | Spotless (removeUnusedImports), Checkstyle, PMD |
| Unused local variables | Yes | javac `-Xlint`, PMD `UnusedLocalVariable` |
| Unused private fields/methods | Yes | PMD, SpotBugs (`URF`, `UPM`) |
| Dead stores | Yes | SpotBugs `DLS` (note: suppressed for Java 21 type switches) |
| Unreachable code | Yes (compiler error in Java for truly unreachable) | javac |
| Unused *public* API | **No — unreliable** | none safely |
| Unused classes (whole) | Partial, unreliable | — |

**Fundamental limits:** reflection, Spring DI/component scanning, JavaFX `@FXML` field/method binding, `ServiceLoader`, JNI, and serialization all create usages invisible to static analysis. SpotBugs explicitly ignores `@FXML` fields and fields initialized in `@PostConstruct`/`@BeforeEach` to avoid false positives. **No tool may safely delete public or framework-referenced code** — an agent must treat "unused public/framework" findings as advisory only, never auto-delete.

## 11. Recommended Minimal, Production, and Strict Stacks

### Configuration A — Minimal baseline (small/early-stage)
- **Exact tools:** Spotless (palantir/google format + POM/misc), javac `-Xlint:all` (with `-Werror` if greenfield), Maven Enforcer (Maven) / dependency locking + constraints (Gradle), **OSV-Scanner** (one vulnerability scanner), CycloneDX (optional).
- **Why each:** Spotless = deterministic formatting; javac lint = free compiler truth; Enforcer/locking = dependency sanity; OSV-Scanner = fast vuln floor with low FPs.
- **What it replaces:** IDE formatting + basic inspection warnings.
- **Intentionally does not cover:** deep bug detection, nullness, architecture, API compat.
- **Maven/Gradle integration:** Spotless plugin + compiler args + Enforcer/locking + OSV CLI step.
- **Local/CI command structure:** `format`, `format-check`, `verify` (= format-check + compile + enforce); CI runs `verify` + OSV.
- **Cost:** seconds locally. **FP risk:** minimal (Enforcer convergence noise). **Maintenance:** near-zero (pin versions).

### Configuration B — Production default (most Java/JavaFX/Spring Boot projects)
- **Exact tools:** A **plus** Error Prone (compile-time), SpotBugs + Find Security Bugs (CI/pre-push), **one** of Checkstyle/PMD, ArchUnit (tests), CycloneDX SBOM, Dependabot/Renovate for fix PRs.
- **Why each:** Error Prone = cheap compile-time bug catch; SpotBugs/FSB = bytecode bugs + security taint; Checkstyle *or* PMD = style/smells without duplication; ArchUnit = enforce layering; CycloneDX = supply-chain inventory.
- **What it replaces:** the bulk of IntelliJ inspections (bugs, smells, deprecations, structure).
- **Intentionally does not cover:** enforced nullness, cross-version API compat, centralized dashboards.
- **Integration:** Error Prone via `annotationProcessorPaths`/`net.ltgt.errorprone`; SpotBugs plugin; Checkstyle/PMD plugin; ArchUnit test dependency.
- **Command structure:** `verify-fast` (format-check + compile + Error Prone + changed-file Checkstyle) local; `verify` (+ SpotBugs + tests + ArchUnit + OSV + SBOM) in CI/pre-push.
- **Cost:** local fast path stays seconds; SpotBugs/DC run pre-push/CI. **FP risk:** SpotBugs/FSB and PMD need a curated suppression baseline. **Maintenance:** moderate (baseline triage, version bumps).

### Configuration C — Strict / high-assurance (security-sensitive, large, long-lived)
- **Exact tools:** B **plus** NullAway (JSpecify mode) as `ERROR`, optionally Checker Framework for critical modules, SonarQube Community Build (self-hosted; LGPL-3.0 core + SSALv1 analyzers), OWASP Dependency-Check for compliance SBOM, Revapi/japicmp for libraries, full CycloneDX SBOM in release builds, scheduled security jobs, OpenRewrite for periodic deprecation migration.
- **Why each:** NullAway/CF = enforce nullness; SonarQube = centralized quality/security tracking; OWASP DC = NVD compliance evidence + SBOM; Revapi/japicmp = API stability guarantees; scheduled jobs = catch newly disclosed CVEs.
- **What it replaces:** remaining IDE dataflow/nullness inspections; adds guarantees IDEs never provided (SBOM, API compat, org-wide dashboards).
- **Intentionally does not cover:** JavaFX thread/FXML static checks (impossible), runtime-only Spring context validation.
- **Integration:** NullAway via Error Prone; SonarScanner in CI; DC scheduled profile; Revapi/japicmp on library modules; OpenRewrite as on-demand task.
- **Command structure:** `verify` local includes NullAway; CI adds Sonar + Revapi; nightly adds DC + Checker Framework.
- **Cost:** significantly slower (SonarQube server, Checker Framework, NVD DB); reserve slow tools for CI/scheduled. **FP risk:** highest (Checker Framework, CPE-based DC); requires ongoing triage. **Maintenance:** substantial (Sonar server ops, CF annotations, suppression governance).

## 12. Proposed Repository Command Contract

Implement these as **Maven profiles/phases** and **Gradle tasks**, then wrap in a `Makefile` or `justfile` so the agent calls identical verbs everywhere:

| Command | Meaning | Maven | Gradle |
|---|---|---|---|
| `format` | rewrite changed files | `mvn spotless:apply` | `./gradlew spotlessApply` |
| `format-check` | verify, no writes | `mvn spotless:check` | `./gradlew spotlessCheck` |
| `compile` | compile with lint | `mvn -q compile` | `./gradlew classes` |
| `lint` | style + compiler lint | `mvn checkstyle:check` (or pmd) | `./gradlew checkstyleMain` |
| `inspect` | deep static analysis | `mvn spotbugs:check` | `./gradlew spotbugsMain` |
| `dead-code` | unused/dead reporting | `mvn pmd:check` | `./gradlew pmdMain` |
| `dependency-check` | unused/convergence | `mvn enforcer:enforce dependency:analyze` | `./gradlew buildHealth` |
| `security-check` | vulns | `osv-scanner ...` / `mvn dependency-check:check` | `osv-scanner ...` |
| `verify-fast` | fast local gate | `mvn -Pfast verify` | `./gradlew verifyFast` |
| `verify` | full gate | `mvn verify` | `./gradlew check` |

`verify-fast` = format-check + compile + Error Prone + (changed-file) Checkstyle. `verify` = everything including SpotBugs, tests, ArchUnit, security. Both **non-interactive, deterministic exit codes**. Recommendation: implement the verbs in a `justfile` (or `Makefile`) because it is language-agnostic, self-documenting (`just --list`), and lets the same commands work identically for developers, the agent, hooks, and CI — the agent memorizes verbs, never plugin syntax.

## 13. Pre-commit, Pre-push, and CI Design

- **Recommend Lefthook** (single Go binary, parallel execution, one `lefthook.yml`, language-agnostic) over the Python `pre-commit` framework for a Java repo with no existing npm/Python toolchain; `pre-commit` is fine if you want its large community-hook catalog and full environment isolation. Avoid Husky (npm-only) for non-JS repos.
- **pre-commit hook:** `format-check` (or auto-`format` staged files) + fast Checkstyle on changed files only. Must stay **sub-second to a few seconds** or developers/agents will `--no-verify` (hooks are trivially bypassable with `git commit --no-verify`).
- **pre-push hook:** `verify-fast` + SpotBugs on changed modules.
- **PR CI:** full `verify` (all static analysis + tests + ArchUnit + SBOM + OSV-Scanner). This is the **authoritative gate** — hooks are convenience, CI is enforcement.
- **main-branch CI:** full `verify` + publish SBOM + API-compat check (libraries).
- **Scheduled (nightly/weekly):** OWASP Dependency-Check with fresh NVD DB, SonarQube full scan, Checker Framework (strict).
- **Caching/incrementality:** Gradle build cache + configuration cache; Maven `-o`/reactor; changed-file execution in hooks (Spotless `ratchetFrom`, glob-scoped Checkstyle); full-repository verification in CI. **Never let changed-file scoping weaken the CI gate — CI always runs full-repo.**

## 14. AI-Agent Operating Instructions (for AGENTS.md / CLAUDE.md)

Document this workflow verbatim so the agent follows it deterministically:

1. **Before editing:** run `verify-fast` once to capture the **baseline** (record counts of findings per tool). Legacy findings exist; the agent's job is to not *add* new ones.
2. **During implementation:** run `format` on touched files, then `verify-fast` iteratively.
3. **Before finishing:** run the one documented command — **`verify`** — and ensure exit code 0, or that any non-zero is attributable solely to pre-existing baseline findings.
4. **Detecting newly introduced findings:** compare current tool output against the recorded baseline (per-file/per-rule). SpotBugs, PMD, Checkstyle, and SonarQube all support baseline/diff modes; Spotless `ratchetFrom` limits formatting to changed refs. **ArchUnit `FreezingArchRule`** freezes legacy violations and fails only on new ones. **Never** add a permanent baseline as a substitute for fixing — baselines are a migration aid with a documented shrink-to-zero plan.
5. **Do not suppress a rule to make CI pass.** Suppressions require a documented reason (comment + tracking issue); global disabling of a warning category is prohibited without a written rationale.
6. **Never assume auto-fix is behavior-preserving** — after `format`/OpenRewrite/Error Prone `--patch`, re-run tests.
7. **Report unresolved warnings clearly** in the final summary: list new findings, cite the rule ID, and state whether each was fixed, suppressed-with-reason, or left as pre-existing.

## 15. Example Maven Configuration

```xml
<properties>
  <maven.compiler.release>21</maven.compiler.release>
  <spotless.version>2.44.0</spotless.version>
  <errorprone.version>2.43.0</errorprone.version>
  <nullaway.version>0.13.0</nullaway.version>
  <spotbugs.version>4.9.8.2</spotbugs.version>
  <findsecbugs.version>1.14.0</findsecbugs.version>
</properties>

<build>
  <plugins>
    <!-- Formatting -->
    <plugin>
      <groupId>com.diffplug.spotless</groupId>
      <artifactId>spotless-maven-plugin</artifactId>
      <version>${spotless.version}</version>
      <configuration>
        <java>
          <palantirJavaFormat/>
          <removeUnusedImports/>
          <importOrder/>
        </java>
        <pom><sortPom/></pom>
      </configuration>
      <executions>
        <execution><goals><goal>check</goal></goals></execution>
      </executions>
    </plugin>

    <!-- Compiler + Error Prone + NullAway -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <compilerArgs>
          <arg>-Xlint:all</arg>
          <arg>-XDcompilePolicy=simple</arg>
          <arg>--should-stop=ifError=FLOW</arg>
          <arg>-XDaddTypeAnnotationsToSymbol=true</arg>
          <arg>-Xplugin:ErrorProne -Xep:NullAway:ERROR -XepOpt:NullAway:AnnotatedPackages=com.example</arg>
        </compilerArgs>
        <annotationProcessorPaths>
          <path><groupId>com.google.errorprone</groupId>
                <artifactId>error_prone_core</artifactId>
                <version>${errorprone.version}</version></path>
          <path><groupId>com.uber.nullaway</groupId>
                <artifactId>nullaway</artifactId>
                <version>${nullaway.version}</version></path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>

    <!-- SpotBugs + Find Security Bugs -->
    <plugin>
      <groupId>com.github.spotbugs</groupId>
      <artifactId>spotbugs-maven-plugin</artifactId>
      <version>${spotbugs.version}</version>
      <configuration>
        <effort>Max</effort>
        <plugins>
          <plugin><groupId>com.h3xstream.findsecbugs</groupId>
                  <artifactId>findsecbugs-plugin</artifactId>
                  <version>${findsecbugs.version}</version></plugin>
        </plugins>
        <sarifOutput>true</sarifOutput>
      </configuration>
    </plugin>

    <!-- Enforcer -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-enforcer-plugin</artifactId>
      <version>3.6.3</version>
      <executions><execution><id>enforce</id>
        <goals><goal>enforce</goal></goals>
        <configuration><rules>
          <dependencyConvergence/>
          <banDuplicatePomDependencyVersions/>
          <requireUpperBoundDeps/>
        </rules></configuration>
      </execution></executions>
    </plugin>

    <!-- SBOM -->
    <plugin>
      <groupId>org.cyclonedx</groupId>
      <artifactId>cyclonedx-maven-plugin</artifactId>
      <version>2.9.2</version>
      <executions><execution><goals><goal>makeAggregateBom</goal></goals></execution></executions>
    </plugin>
  </plugins>
</build>
```

Vulnerability scanning is run via the OSV-Scanner CLI in CI and `dependency-check-maven` in a scheduled profile (kept out of the default build because of NVD download time). The `-XDaddTypeAnnotationsToSymbol=true` flag is required for Error Prone/NullAway JSpecify checking to see type annotations across compilation boundaries on JDK 21+.

## 16. Example Gradle Configuration (Kotlin DSL)

```kotlin
plugins {
    java
    id("com.diffplug.spotless") version "7.0.2"
    id("net.ltgt.errorprone") version "4.1.0"
    id("net.ltgt.nullaway") version "2.2.0"
    id("com.github.spotbugs") version "6.4.8"
    checkstyle
    id("org.cyclonedx.bom") version "3.3.0"
    id("com.autonomousapps.dependency-analysis") version "2.7.0"
    id("org.owasp.dependencycheck") version "12.1.0"
}

dependencies {
    errorprone("com.google.errorprone:error_prone_core:2.43.0")
    errorprone("com.uber.nullaway:nullaway:0.13.0")
    compileOnly("org.jspecify:jspecify:1.0.0")
    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0")
}

spotless {
    java {
        palantirJavaFormat()
        removeUnusedImports()
        importOrder()
    }
    kotlinGradle { ktlint() }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all"))
    options.errorprone {
        nullaway {
            error()
            annotatedPackages.add("com.example")
        }
        // disable NullAway on test compilation
        if (name.lowercase().contains("test")) disable("NullAway")
    }
}

spotbugs {
    effort.set(com.github.spotbugs.snom.Effort.MAX)
}

dependencyLocking { lockAllConfigurations() }
```

Run `./gradlew spotlessApply` for `format`, `./gradlew check` for `verify`, and `./gradlew buildHealth` for dependency hygiene. OSV-Scanner runs as a separate CLI step in CI. Ensure the Gradle build JVM is JDK 21+ because Error Prone 2.43.0 requires it to run.

## 17. Example Lefthook Configuration

```yaml
# lefthook.yml
pre-commit:
  parallel: true
  jobs:
    - name: format
      run: mvn -q spotless:apply
      stage_fixed: true
    - name: checkstyle-changed
      glob: "*.java"
      run: mvn -q checkstyle:check

pre-push:
  parallel: false
  jobs:
    - name: verify-fast
      run: mvn -q -Pfast verify
    - name: spotbugs
      run: mvn -q spotbugs:check
```

For Gradle, replace `mvn` verbs with the corresponding `./gradlew` tasks. Keep pre-commit fast (format + changed-file Checkstyle only); move SpotBugs and full verify to pre-push/CI so hooks are never bypassed for being slow.

## 18. Adoption and Migration Plan

1. **Week 1 — deterministic floor:** Add Spotless (choose palantir or google format, pin plugin *and* formatter versions), run `format` once as a single "reformat repo" commit, add `.git-blame-ignore-revs`. Turn on `-Xlint:all`. Wire the command contract (`Makefile`/`justfile` + Maven profiles / Gradle tasks) and `AGENTS.md`.
2. **Week 2 — dependency & vuln floor:** Add Enforcer / dependency locking, OSV-Scanner in CI, Dependabot/Renovate. Add CycloneDX.
3. **Weeks 3–4 — bug detection:** Add Error Prone (start with default checks, `WARN` then `ERROR`), then SpotBugs + FindSecBugs with an initial suppression baseline and a shrink plan. Add ArchUnit with `FreezingArchRule` for legacy.
4. **Month 2 — style + architecture:** Add Checkstyle **or** PMD (one). Formalize ArchUnit layering rules.
5. **Strict track (as needed):** Introduce NullAway in JSpecify mode module-by-module (`@NullMarked`), stand up SonarQube Community Build, add Revapi/japicmp for libraries, schedule OWASP DC + OpenRewrite deprecation sweeps.

Each step is independently revertible and adds one deterministic command. **Benchmarks that should change the plan:** if `verify-fast` exceeds ~10–15 s locally, move a tool to pre-push/CI; if the SpotBugs/PMD suppression baseline is not shrinking month-over-month, stop adding rules and burn down debt first; if two vuln scanners produce >~20% duplicate noise with no new true positives over a quarter, drop to one.

## 19. Tools That Should Not Be Adopted (and why)

- **Both Checkstyle and PMD together by default** — large rule overlap for smells; pick one (Checkstyle for layout/style enforcement, PMD for smell/dead-code) unless you have a specific need.
- **FindBugs (original)** — abandoned; superseded by SpotBugs. Do not use.
- **Nebula Gradle Lint `unused-dependency`** — Groovy-DSL-only, less maintained; prefer autonomousapps for Gradle.
- **Relying on SonarLint IDE / IntelliJ inspections in the gate** — IDE-only, violates the core constraint.
- **Checker Framework everywhere** — high false-positive/annotation burden; reserve for critical modules in strict config only.
- **OpenRewrite in the default gate** — it's a migration tool; auto-fixes are not guaranteed behavior-preserving. Run on demand.
- **Roseau** — promising accuracy (F1=0.99) but research-grade, not a production build plugin yet.
- **Multiple overlapping vulnerability scanners by default** — mostly duplicate results; add a second only for compliance/coverage in strict config.
- **Global warning suppression / permanent baselines as a fix** — prohibited by the operating rules in §14.
- **google-java-format for lambda-heavy Java 21+ code** — its narrower style handles modern lambda/stream code less gracefully (community-reported, e.g. GeoNetwork/GeoServer switched to Palantir); **palantir-java-format** (120-col, lambda-friendly, Apache-2.0) is generally the better default for Spring Boot 21+ codebases. Note both now require Java 21 to *run* the formatter.
- **`-Werror` blanket-enabled on Spring/Lombok/annotation-processor-heavy apps** — processor rounds and generated code produce warnings that turn clean builds red (verified: immutables issue #672, Lombok+Error Prone on JDK 25). Use it on libraries/greenfield, not on framework-heavy apps.

## 20. Open Questions and Evidence Gaps

- **JavaFX static analysis** remains the biggest gap: no CLI detector for thread-confinement or FXML wiring (subagent-verified against SpotBugs/PMD/Error Prone/Sonar as of mid-2026). Runtime tests (TestFX/Monocle) are the only reliable check.
- **Error Prone + Lombok + JDK 25** — a reported `AbstractMethodError` in Lombok's JavaDoc handling with Error Prone on JDK 25 (Lombok issue #3940, Sept 14, 2025; partial fix in Lombok v1.18.42, Sept 18, 2025; issue was reopened). Use Lombok ≥ 1.18.42 and verify Error Prone compatibility before enabling on JDK 25.
- **Maven native dependency locking** — no first-class lockfile; community plugins vary. Gradle is ahead here.
- **SonarQube analyzer licensing** — core is LGPL-3.0 but bundled analyzers moved to the Sonar Source-Available License v1 (SSALv1 — source-available, not OSI-open) starting November 29, 2024; confirm this is acceptable for your use. (SonarQube Cloud free tier caps at 50k LOC.)
- **OpenRewrite composite recipe licensing** — community recipes are Apache-2.0, but some composite/enterprise recipes are Moderne Source Available; verify per-recipe.
- **Two-scanner marginal value** — the exact additional CVE coverage of OWASP DC + OSV together vs. either alone is not quantified in a rigorous public benchmark; treat "run both" as a coverage/compliance choice, not a proven necessity.
- **Full-JSpecify-mode minimum JDK** — sources differ (Spring recommends JDK 25 / JDK 21.0.8+ with a flag; JavaCodeGeeks cites JDK 22+ as the toolchain minimum). Validate on your exact JDK before enforcing NullAway JSpecify mode as `ERROR`.

## 21. Source List

- Oracle: "Notifications and Warnings" (JDK 21 and JDK 25 core docs); "Enhanced Deprecation"; JDK 25 Release Notes; Consolidated JDK 21 Release Notes (licensing/LTS); jpackage/jdeprscan/jdeps tool specifications (docs.oracle.com, dev.java).
- Spotless: diffplug/spotless GitHub (plugin-maven/README, Gradle Plugin 7.0/7.0.2 release notes).
- google-java-format and palantir-java-format GitHub repos; Maven Central artifact pages; jqno.nl formatter comparison; JabRef/GeoServer (GSIP 228)/GeoNetwork decision records.
- Checkstyle 13.7.0 (checkstyle.sourceforge.io); PMD 7.16–7.21 release notes (pmd.github.io) and Wikipedia PMD entry (7.19.0, Nov 28 2025).
- SpotBugs (spotbugs.github.io, GitHub CHANGELOG/releases, JDK 21 issues #2567/#3693); Find Security Bugs (find-sec-bugs.github.io, 1.14.0).
- Error Prone installation docs and issue #4867 (JDK 21 minimum; 2.42 last for JDK 17); Gradle issue #35768; NullAway CHANGELOG/README/JSpecify wiki (uber/NullAway); immutables issue #672 (`-Werror` + processors).
- Spring blog: "Null-safe applications with Spring Boot 4" (Sébastien Deleuze, 2025-11-12); "Null Safety in Spring applications with JSpecify and NullAway" (2025-03-10). Java Code Geeks JSpecify article (Nov 2025 / Jun 2026).
- SonarSource: "A better (free) SonarQube experience"; license page (SSALv1 from 2024-11-29); open-source editions; Community Build docs.
- OWASP Dependency-Check (mend.io overview, docs); OSV-Scanner and SCA comparisons (Aikido, AppSec Santa, tomodahinata, decryptiondigest, 2025–2026).
- Maven Enforcer built-in rules incl. dependencyConvergence (maven.apache.org/enforcer, 3.6.3); CycloneDX Maven (2.9.2) and Gradle (3.3.0) plugin GitHub repos; Quarkus CycloneDX caveats.
- Revapi (revapi.org, GitHub) and japicmp (siom79.github.io); Roseau ICSME 2025 paper (arXiv:2507.17369, conf.researchr.org).
- ArchUnit (archunit.org, v1.4.2 Apr 18 2026; codecentric, DZone, Medium guides); OpenRewrite docs (8.66.1 changelog, Spring Boot 3.4/3.5/4.0 recipes; Moderne blog).
- autonomousapps dependency-analysis-gradle-plugin GitHub (2.x); gradle-dependency-analyze GitHub (bytecode limits).
- Lefthook (lefthook.dev, evilmartians benchmark wiki); pre-commit vs Lefthook comparisons (0xdc.me, andymadge.com, npm-compare).
- OpenJFX docs (openjfx.io); JPackageScriptFX; Badass JLink plugin (plugins.gradle.org, 4.0.1); bsels/javafx-maven-plugin (fxml-source goal); Lombok issue #3940 and v1.18.42 changelog.