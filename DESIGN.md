# Project Design: Event-Sourced Double-Entry Ledger

## 1. What the system is

A double-entry payments ledger where the Kafka event log is the source of truth.

Commands arrive (transfer, deposit, withdrawal, reversal). They are validated against current account state, and if valid they produce immutable posting events. Postgres holds only **projections** built by consuming that log. Any projection can be dropped and fully rebuilt by replaying Kafka from the beginning.

Double-entry means every transaction produces balanced debit and credit postings. The sum of all postings across the system must always be zero.

### Core principle

> The database is a cache. The log is the truth.

If I can delete the Postgres volume and rebuild every balance exactly, the system is correct. If I cannot, something is wrong and I need to find out what.

## 2. Non-goals

Writing these down so I do not scope-creep into never finishing.

- **Not** a real payments product. No PCI compliance, no bank integrations, no KYC.
- **Not** a frontend project. A minimal UI or plain API is fine. No React app, no design work.
- **Not** multi-currency at first. Single currency, minor units (integer cents), no FX.
- **Not** a microservices sprawl. Two or three services maximum. More services means more YAML and unwanted complexity.
- **Not** aiming for 100% test coverage. Aiming for tests that catch the specific concurrency and replay bugs I care about.
- **Not** trying to be novel. This is a well-understood problem, deliberately, so I can focus on execution and operations rather than domain invention.

## 3. Architecture

```
                    ┌──────────────────┐
   HTTP commands →  │  Command Service │
                    │  (Spring Boot)   │
                    └────────┬─────────┘
                             │ validates against
                             │ current balance projection
                             │ produces posting events
                             ▼
                    ┌──────────────────┐
                    │      Kafka       │  ← source of truth
                    │  (compacted +    │
                    │   retained log)  │
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
     ┌────────────┐  ┌────────────┐  ┌────────────┐
     │ Balance    │  │ Statement  │  │ Audit      │
     │ Projector  │  │ Projector  │  │ Projector  │
     └─────┬──────┘  └─────┬──────┘  └─────┬──────┘
           │               │               │
           └───────────────┼───────────────┘
                           ▼
                   ┌──────────────┐
                   │  PostgreSQL  │  ← rebuildable cache
                   └──────┬───────┘
                          │
                    ┌─────┴──────────┐
                    │  Query Service │
                    │  (+ LLM NL→    │
                    │   structured   │
                    │   query)       │
                    └────────────────┘
```

**Services:**

1. **Command Service** — accepts commands, validates, emits events. Owns write-side invariants.
2. **Projector(s)** — consume the event log, maintain read models. Can start as one service with multiple consumers.
3. **Query Service** — serves read models, hosts the LLM natural-language query feature.

Start with these merged if that gets me to a working slice faster. Split them when there is a reason to, and record the reason as an ADR.

## 4. Functional requirements

### 4.1 Accounts
- Create an account with a type (asset, liability, revenue, expense, equity)
- Accounts have a currency and a balance in integer minor units
- No account may be deleted, only closed

### 4.2 Commands
- `Transfer` — move an amount from one account to another
- `Deposit` / `Withdrawal` — external money movement against a designated external account
- `Reversal` — reverses a prior transaction by ID, producing compensating postings. Never mutates or deletes the original.

### 4.3 Time handling (bi-temporal)

Every posting carries two timestamps, and conflating them is a classic fintech bug:

- **Effective time** (also called value date or business time): when the transaction actually occurred in the real world
- **Recorded time** (also called system time or transaction time): when this system learned about it

These diverge constantly in practice: a backdated correction, a settlement file arriving three days late, a reversal applied to a transaction from last quarter. A ledger that only stores one timestamp cannot answer "what did we believe the balance was as of last Tuesday," which is a question regulators and auditors genuinely ask.

**Minimum requirement:** store both, and make the balance query accept an optional as-of parameter for each.

**Optional stretch:** full bi-temporal projections supporting "balance as of effective date X, as known at recorded time Y." This is real scope. Decide via ADR whether to build it or explicitly defer it, and document the reasoning either way.

### 4.4 Invariants
- Every transaction produces at least two postings that sum to zero
- The sum of all postings in the system is always zero
- No account may go below its configured floor (zero for most, unbounded for the external account)
- The same command submitted twice with the same idempotency key produces exactly one transaction
- Events are append-only. Nothing in the log is ever updated or deleted.

### 4.5 Projections
- **Balance** — current balance per account
- **Statement** — ordered posting history per account, with running balance
- **Audit** — who did what when, including rejected commands and why

### 4.6 Rebuild
- A CLI or admin endpoint that truncates a projection and replays the log to rebuild it
- Must produce identical results to the incrementally-built version. Write a test that asserts this.

## 5. Non-functional requirements

| Requirement | Target |
|---|---|
| Idempotency | Duplicate command or redelivered event never double-posts |
| Ordering | Per-account ordering guaranteed; global ordering explicitly not |
| Durability | Acks=all, no data loss on broker failure |
| Throughput | Publish a real measured number, then improve it |
| Latency | Publish p50 / p95 / p99 for command acceptance |
| Observability | Consumer lag, throughput, error rate on a Grafana dashboard |
| Schema evolution | Add a field without breaking existing consumers |
| Graceful shutdown | Pod termination commits offsets and finishes in-flight work |

## 6. Feature requirements by area

### 6.1 Kafka
- Topics: `commands`, `postings`, `dead-letter`
- Partitioning key: account ID (decide and document why, this is an ADR)
- Log compaction and retention configured deliberately, not left on defaults
- Schema registry with Avro or Protobuf, with a compatibility mode chosen and justified
- Consumer groups with explicit offset commit strategy
- Dead letter topic with a documented replay procedure

### 6.2 Kubernetes
- All services deployed via manifests or Helm
- Liveness and readiness probes that are actually meaningful, not just `GET /health` returning 200
- `preStop` hooks and `terminationGracePeriodSeconds` tuned so consumers shut down cleanly
- Resource requests and limits set, and observed under load
- Horizontal scaling of consumers, including the experiment of scaling past the partition count to see idle pods

### 6.3 Terraform
- Everything provisioned in code: cluster, managed Kafka or self-hosted, Postgres, networking
- Remote state backend configured
- Separate `dev` and `prod`-shaped workspaces or environments
- `terraform destroy` and `apply` must fully work. This is the disaster recovery drill.

### 6.4 LLM feature: natural language to ledger query

The feature must be **load-bearing and constrained**, not a chatbot.

#### Why an LLM belongs in a high-integrity ledger at all

An LLM is a non-deterministic component. CQRS gives me a principled place to put one. It lives strictly on the **read side**, operating against projections, behind a validator. It never touches the command path, never produces an event, and cannot affect the log. If it is wrong, a query is wrong. It can never corrupt the ledger.

That separation is the point. The write side is deterministic, validated, and auditable. The speculative component is quarantined on the query side where being wrong is recoverable.

**Hard rules:**
- Phase 5 does not begin until Phases 0 through 4 are complete
- Timebox: 2 weeks. If the eval harness is not producing a number by then, ship what exists, publish the partial result honestly, and move on.
- No scope bleed into earlier phases.

**Requirements:**
- Input: plain English, for example "show me every reversal on account 4471 last quarter over 50,000"
- Output: a **validated structured query object**, never raw SQL from the model
- The model may only reference a whitelisted schema of fields and operators
- Every generated query passes a validator before execution. Validation failure is a handled path, not a crash.
- Framework: Spring AI (natural fit since already on Spring Boot) or LangChain4j
- **Eval harness required:** minimum 50 labeled question/expected-query pairs, with a runnable accuracy score committed to the repo and reported in the README

### 6.5 Observability
- Prometheus metrics from all services
- Grafana dashboard: consumer lag, command throughput, rejection rate, LLM latency and cost
- Structured JSON logging with correlation IDs across services
- Distributed tracing if time allows (nice to have, not required)

## 7. Phased build plan

**Rule: build the thinnest possible vertical slice before adding any breadth.** A working narrow system beats a half-built broad one. The most likely failure mode for this project is stalling at 40% with wide, unfinished surface area.

### Phase 0: Thin vertical slice
One command type (transfer), one event, one projection (balance), one query endpoint. Kafka and Postgres in Docker Compose locally. No Kubernetes, no Terraform, no LLM.
**Done when:** I can POST a transfer and GET a correct balance.

### Phase 1: Correctness
Double-entry invariants, idempotency keys, reversal, the rebuild-from-log path, and the test asserting replay equals incremental.
**Done when:** replaying the log reproduces balances exactly.

### Phase 2: Kafka for real
Schema registry, partitioning decision, compaction and retention config, dead letter topic, consumer group tuning. Break ordering deliberately and fix it. **Wire Toxiproxy into Docker Compose here**, in front of both Kafka and Postgres, so network fault injection is available for the rest of the build rather than bolted on at the end.
**Done when:** the system survives a broker restart, a poison message, and 500ms of injected broker latency without data loss.

### Phase 3: Kubernetes
Deploy everything to a cluster. Probes, graceful shutdown, resource limits, horizontal scaling of consumers. Kill pods on purpose.
**Done when:** killing any pod mid-load loses and duplicates nothing.

### Phase 4: Terraform
Provision the whole environment in code. Destroy it. Rebuild it. Replay. Verify.
**Done when:** full teardown and rebuild produces identical balances.

### Phase 5: LLM query feature
**Gated: does not start until Phase 4 is complete. Timeboxed to 2 weeks.**
Spring AI or LangChain4j, structured output, schema whitelist, validator, eval harness with 50+ questions and a published score. Read side only, never touching the command path.
**Done when:** I can state a measured accuracy number and describe the failure categories. If the timebox expires first, publish the partial result honestly and move to Phase 6.

### Phase 6: Observability and load
Prometheus, Grafana dashboard, load test, publish numbers, tune one thing, publish improved numbers.
**Done when:** the README contains a before-and-after benchmark.

### Phase 7: Write it up
README polish, ADRs backfilled and cleaned (all ADRs should be in /docs/adr if not yet already), "what went wrong" section written from the journal, diagram finalized, demo GIF.
**Done when:** the README can stand on its own without the walkthrough

Phases 5 and 6 can swap order. Phases 0 through 2 are non-negotiable prerequisites for everything else.

## 8. Open decisions to make early

Each of these should become an ADR. Do the writeup in docs/adr. Format: context, options considered, decision, consequences. They should be short but properly show tradeoff reasoning for the decision made.

1. **Money representation: integer minor units vs BigDecimal vs floating point.** The decision is integer minor units. Write the full reasoning anyway: IEEE 754 cannot exactly represent most decimal fractions, so repeated addition accumulates error, and in a double-entry system that error means the ledger stops summing to zero. Cover why BigDecimal is safe but carries performance and serialization cost, and why integers in the smallest currency unit are the standard choice. Include a failing test demonstrating float drift, which makes the ADR concrete rather than theoretical.
2. **Validation against projection during concurrent commands.** If the Command Service validates against the balance projection, a bad state may occur when concurrent commands are processed. Due to the nature of the projection update being asynchronous, concurrent commands are susceptible to stale reads. This can then lead to the commands wrongfully passing the account floor and idempotency-key checks and getting persisted in the event log. What's worse is any reconstruction of the projection from the event logs will still include this bad state, and will pass the reconstruction test mentioned in section 4.6. Should the system prevent this or try to compensate? If preventing, how?
3. **Bi-temporal modeling: effective time vs recorded time.** How both are stored, whether full as-of-both-axes querying is built or deferred, and what the deferral costs. See section 4.3.
4. Kafka partition key: account ID vs transaction ID vs composite
5. Schema format: Avro vs Protobuf vs JSON Schema, and which compatibility mode
6. Managed Kafka (MSK, Confluent Cloud, Redpanda Cloud) vs self-hosted in-cluster, weighed against cost
7. Single service with multiple consumers vs separate deployable projectors
8. Snapshotting strategy, since replaying from the beginning forever does not scale
9. Spring AI vs LangChain4j
10. Cloud provider
11. Placement of the LLM component strictly on the read side, and why non-deterministic components are quarantined from the write path (see section 6.4) 
