# Timeline — Phase Framework

A reusable template for working through Phases 0–8 from `docs/ARCHITECTURE.md` §9. Copy the block under **"Template — copy this per phase"** into a new file per phase (e.g. `docs/phases/phase-1-domain.md`) when you start it, and fill it in as you go. The point isn't ceremony — it's catching two specific failure modes: starting a phase without rereading the relevant architecture decisions, and losing track of what actually happened (vs. what was planned) once you're three phases further in.

The **quick-reference table** at the bottom has the seed content for all nine phases pulled from the blueprint, so you don't have to flip back to `ARCHITECTURE.md` to start filling a template out.

---

## Template — copy this per phase

```markdown
# Phase N — <name>

## Objective
<one sentence — what does this phase deliver, in plain language>

## Prerequisites
- [ ] Phase N-1 is done (all its checkboxes below are checked)
- [ ] Reread ARCHITECTURE.md §<relevant sections> before starting

## In scope
<bulleted, specific — copy from the roadmap table's "Deliverable" column and expand>

## Explicitly out of scope for this phase
<what you will be tempted to build here but shouldn't — copy from ARCHITECTURE.md
§9 "Explicitly NOT in V1" plus anything phase-specific, e.g. Phase 6 = "no WYSIWYG,
no drag-and-drop reordering yet, block types beyond Text/Link/Image">

## Locked decisions relevant to this phase
<pull the 1-3 rows from ARCHITECTURE.md §0 that actually constrain this phase —
e.g. Phase 2 → decision #8 (SQLite from V1, no JSON library)>

## Task checklist
- [ ]
- [ ]
- [ ]

## Files created / modified
<fill in as you go — makes the retro below easier to write, and doubles as a
mini changelog if you're not committing every small step to git>

## Tests written
- [ ] Unit tests for: <what>
- [ ] Integration/contract tests for: <what, if applicable this phase>
- [ ] Manual smoke check: <what you clicked through by hand>

## Definition of done
<copy the "Done when" cell from the roadmap table — don't soften it. If it says
"contract tests green against both implementations," that means both, not one.>

## Retro (fill in when the phase is closed)
- Estimated: <from ARCHITECTURE.md §11's time table> — Actual: <___>
- What took longer than expected, and why:
- Any deviation from ARCHITECTURE.md (and whether the blueprint should be updated to match):
- Anything punted to a later phase that wasn't originally planned that way:
```

---

## Quick reference: all phases

Seed content for the template above, taken from `ARCHITECTURE.md` §9 and §11. Fill in the specifics as you actually work — this table is a starting point, not a substitute for the filled-out phase doc.

| Phase | Objective | Key doc sections to reread | Done when | Est. hours |
|---|---|---|---|---|
| 0 — Skeleton | Project boots, styled, tests run | §1 (stack), §11 Risk 5 (no `module-info.java`) | `mvn javafx:run` shows a styled empty window; `mvn test` passes | 1–3 |
| 1 — Domain | Full domain model, zero UI/DB | §4 (data model), §7.4–7.5 | Full unit-test coverage per §8; zero framework imports in `domain/` | 3–5 |
| 2 — Storage + service | Both `IdeaRepository` impls, service layer | §4.6–4.7 (schema), §7.1–7.3 (transactional save), §8 (contract test) | Contract tests green against both implementations; real SQLite round-trip verified | 4–7 |
| 3 — Read-only list | List UI against real seeded data | §6.2 (layout/hierarchy), §2 (view/controller split) | The list looks finished, even though nothing is editable | 3–6 |
| 4 — CRUD | Full create/edit/delete, single text-block descriptions | §6.3 (editor layout), §7.1–7.3 (data flow) | Full CRUD works end to end, including a restart confirming persistence | 3–5 |
| 5 — Organization | Tag chips, search, sort | §6.4, §7.4 (query/predicate rules) | All filter/sort rules from §7.4 work in the UI | 2–4 |
| 6 — Rich content | Block editor: text → link → image, in that order | §11 Risk 1 (scope discipline!), §6.5, §7.5 | Each block type works before starting the next | 4–7 |
| 7 — Polish | Keyboard-only usable | — | Usable without a mouse | 2–4 |
| 8 — Packaging | Installer, no JDK required on target machine | §11 Risk 5 (classpath-mode jpackage) | Installer runs on a clean machine; packaged app persists data across runs | 3–5 |

**Running total if every phase lands at its estimate midpoint: ~30 hours.** Track actuals in each phase's retro section — if two phases in a row run over, that's the signal to reread §11 Risk 1 and check whether scope is creeping before phase 3.
