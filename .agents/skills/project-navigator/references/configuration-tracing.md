# Configuration Tracing Guide — Following a Value End to End

Configuration is the part of a project most often misread. This reference describes the generic
configuration value chain and how to follow a single setting from its source of truth to where the
application consumes it. It is technology-agnostic — apply it to any stack.

---

## Where configuration comes from (check in order)

1. **Environment variables** — the most common runtime input. Defined in deployment manifests,
   container definitions, CI/deploy pipelines, or `.env` files (the latter for local development;
   never commit real secrets).
2. **Config files in the repository** — e.g. `application.yml`/`application.properties` (JVM),
   `appsettings.json` (.NET), `settings.py`/`config.py` (Python), `config/*.json|yaml` (many stacks).
   Often layered per environment (`*-dev`, `*-prod`).
3. **Parameter / secret stores** — externalized configuration and secrets resolved at deploy or
   runtime (cloud parameter stores, secret managers, key vaults). The repository holds the *keys/paths*,
   not the values.
4. **Build-time defaults / constants** — values compiled in or set in code (lowest precedence;
   usually fallbacks).

Precedence is typically: explicit environment variable > environment-specific config file > base config
file > in-code default. Confirm the precedence for the specific framework.

---

## The end-to-end value chain

Trace any single configuration value through these layers:

```
Source of truth
  (parameter/secret store entry, or an environment-specific config file)
        ↓
Deployment / infrastructure layer
  (deploy manifest, container definition, or CI pipeline maps the source → an ENV VAR)
        ↓
Runtime environment variable
  (the process starts with KEY=value in its environment)
        ↓
Application configuration binding
  (framework binds the ENV VAR / config key to a typed setting, e.g.
   @Value / Environment / process.env / os.environ / IConfiguration)
        ↓
Code that reads the setting
  (the value is used in a class/function — the consumption point)
```

To document a value, capture each hop: where it is defined, how it becomes an env var, and where the
code reads it. Record **keys, never secret values**.

---

## How to follow a value in practice

1. **Start from the consumption point or the key.** Grep for the env-var name or config key across the
   repo (e.g. `grep -r "DATABASE_URL"`).
2. **Find where the framework binds it.** Look in the config file / binding annotation that maps the
   external key to the application property.
3. **Find where deployment sets it.** Look in the container definition, deploy manifest, or pipeline
   for where the env var is assigned (often from a parameter-store path).
4. **Find the source of truth.** The parameter-store path or environment-specific config file holds the
   authoritative current value. Initial bootstrap/setup scripts may be stale — prefer the
   committed environment config or deployment definition.

---

## Reliability of configuration sources

| Source | Reliability |
|---|---|
| Environment-specific config committed to the repo (e.g. `params/prod.json`, `application-prod.yml`) | Authoritative for that environment |
| Infrastructure/deploy definition (container env block, deploy manifest, CI deploy stage) | Authoritative for what actually runs |
| In-code defaults / constants | Authoritative only as fallbacks |
| One-time setup / bootstrap / provisioning scripts | Often stale — cross-check before trusting |
| README / wiki configuration tables | Frequently out of date — verify against the above |

> Warning: setup/provisioning scripts are usually written once during initial project creation and not
> kept in sync. Always cross-reference against the committed environment configuration or the deploy
> definition for the current truth.

---

## Documenting configuration safely

- Record the **key/path and the consuming property**, not the value: `DATABASE_URL → spring.datasource.url`.
- For secrets, name the store path/key only (e.g. `secret: db-credentials`) — never the secret itself.
- Note which environments differ and *how* (different host, scaled values, feature toggles), not the
  literal secret values.
