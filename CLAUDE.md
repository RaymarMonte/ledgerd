# CLAUDE.md — engineering conventions for `ledgerd`

`docs/DESIGN.md` is the spec. Read the relevant section before changing behaviour; do not work
from recall.

## Invariants

These are enforced hard. A change that violates one is wrong even if the tests pass.

- Every transaction emits at least two postings summing to zero.
- The system-wide sum of all postings is zero.
- No account goes below its configured floor (zero for most; the external account is
  unbounded).
- The same idempotency key produces exactly one transaction.
- The log is append-only. Reversals emit compensating postings; they never mutate or delete
  the original.
- Money is integer minor units. Never a float, never a double.
- Every posting carries both **effective time** (when it happened in the world) and
  **recorded time** (when the system learned of it). Conflating the two is a defect.
- Postgres is a cache. If a change makes a projection unrebuildable from the log, that is a
  design bug regardless of test results.

## Conventions

- **Reproduce, journal, then fix.** A failure gets a reliable reproduction and a `JOURNAL.md`
  entry before a fix lands. A bug that cannot be reproduced on demand is not understood.
- Projections are rebuilt from the log, never patched in place.
- Commit history is not squashed. The iteration is part of the record.

## Scope constraints

- A thin vertical slice comes before any breadth.
- Build order is gated: phases 0–2 are prerequisites for everything after them, and the LLM
  query feature (phase 5) does not start until phase 4 is complete. It is timeboxed to two
  weeks.
- Two to three services maximum. Single currency. No frontend.
- A phase running past twice its estimate gets its scope cut, not its deadline extended.
