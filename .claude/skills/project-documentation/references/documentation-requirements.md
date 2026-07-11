# Documentation Requirements for AI-Based Service Research

## Core Principle

> Documentation for AI is not about completeness — it is about **precision of
> relationships** and **consistency of language** across service boundaries. A single
> inconsistent name breaks the traversal chain.

AI agent needs to **traverse unknown service landscape**, understand **boundaries**,
trace **data/logic flows**, and answer **business-level questions** without human
guidance. Documentation must be both **machine-traversable** and **semantically rich**.

---

## Section Requirements

### 1. Service Identity Card
*AI needs a canonical anchor point to recognize and reference the service.*

- **Unique service name/ID** — consistent identifier used across ALL documentation in ALL services
- **Single-sentence purpose** — what business problem this service solves
- **Domain/bounded context** — which business area it belongs to (e.g., Payments, Identity, Notifications)
- **Keywords & synonyms** — alternative terms that business or other teams use for this service
- **Owner/team** — organizational context
- **Technology stack** — language, framework, runtime

Without stable canonical names, AI cannot correlate references across repositories. When
service A docs say "calls UserSvc" and service B is named "user-management-service", the
agent loses the connection.

### 2. Entry Points (Inputs)

For each entry point:
- **Type** — REST, gRPC, GraphQL, SQS/SNS, RabbitMQ/Kafka, cron, CLI, event stream
- **Identifier** — endpoint path, queue name, topic name, schedule expression
- **Contract/Schema** — request/message payload structure (link or inline)
- **Trigger semantics** — what business event/intent this entry point represents
- **Authentication/authorization** — who/what can call it

AI needs to know not just *that* an endpoint exists, but *what business action it
initiates* — this is what links it to user stories.

### 3. Exit Points (Outputs)

For each output:
- **Type** — DB write, queue publish, external API call, file write, cache update
- **Target identifier** — queue name, topic, DB table/collection, external service name
- **Schema** — what data is pushed
- **Semantics** — what this output *means* in business terms
- **Conditions** — when this output happens (always / on success / on specific state)

Outputs are where business logic *produces results*. AI must trace where data goes
after this service finishes.

### 4. Business Logic

- **Primary flows** — step-by-step description of the main processing paths (happy path)
- **Branching conditions** — what determines different paths
- **Key algorithms** — transformations, calculations, validations, enrichment logic
- **State transitions** — if service manages state (e.g., Order: PENDING → CONFIRMED → SHIPPED)
- **Edge cases & error flows** — what happens on failure, retry logic, compensating actions
- **Business rules** — explicit constraints (e.g., "cannot create order if user is suspended")

This is the core content for user story research. AI needs enough detail to answer
"where is the logic for X" without reading actual code.

### 5. External Dependencies During Processing

For each dependency:
- **Service/resource name** — canonical name (must match that service's identity card)
- **Type** — synchronous call / DB read / cache lookup / async message
- **Purpose** — *why* it's called
- **Criticality** — blocking/non-blocking, required/optional
- **Failure behavior** — what happens if dependency is unavailable

"Calls UserService" is insufficient. AI needs to understand *why* and *what data is
used* to trace cross-service logic chains for a given feature.

### 6. Data Contracts & Domain Models

- **Key entities** — domain objects this service owns or processes (with schema)
- **Ubiquitous language** — specific meaning of terms *within this service's context*
- **Input/output schema definitions** — not just types, but field-level descriptions
- **Data ownership** — which data this service is the source of truth for

The same word "User" may mean different things in different services. AI must understand
semantic equivalence across service boundaries.

### 7. Integration & Dependency Map

Structured format:
```
Upstream (who calls this service): [ServiceA, ServiceB, cron-scheduler]
Downstream (who this service calls): [ServiceC, ServiceD]
Shared infrastructure: [orders-db, payment-events-topic, redis-cache]
Events consumed: [UserRegistered, PaymentCompleted]
Events published: [OrderCreated, OrderFailed]
```

This is the **navigation layer** for AI. A structured map allows the agent to
efficiently decide which services to enter next during research.

### 8. Searchability Anchors

- **Feature flags** referenced by the service
- **Functional area tags** — e.g., `#billing`, `#user-onboarding`, `#fraud-detection`
- **User-facing feature names** — business names that appear in user stories or PRDs
- **Code location hints** — key class/module names where logic lives

AI research often starts from a vague business term. These anchors are the index that
maps business language → service → specific logic location.

---

## Format Requirements

| Requirement | Rationale |
|---|---|
| **Structured header (YAML frontmatter)** | Machine-parseable metadata for fast scanning |
| **Consistent section naming** across all services | AI builds pattern recognition across repos |
| **Explicit cross-references** using canonical names | Reliable graph traversal |
| **Self-contained** — no "see the code" deferrals for core logic | AI needs answers without code parsing |
| **Versioned** — when logic changed | Prevents outdated research results |
| **Co-located with code** (e.g., `/docs` in repo) | Agent can find it without separate system |
