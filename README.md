# ASG FabricERP

Weaving and dyeing ERP — **asgdynamic's business logic on SpindleERP's structure**, with the
structural weaknesses found in SpindleERP corrected.

Spring Boot 3 · Java 21 · PostgreSQL · Thymeleaf · Tailwind · Maven · Flyway.

> Package/artifact is `com.asg.fabricerp` / `fabricerp`, mirroring SpindleERP's naming.
> Rename if you'd rather keep `asgdynamic`.

## The shape of it

One generic document table discriminated by `DocumentType` — SpindleERP's central idea —
replacing asgdynamic's 42 near-identical controller/table pairs.

```
common/              AuditableEntity, BaseOrgEntity, BaseOrgLineEntity,
                     OrgContext (injected), OrgContextListener
global/documents/    BusinessDocument, BusinessDocumentLine, FabricSpec,
                     DocumentType, BusinessDocumentStatus,
                     BusinessDocumentRepository, DocumentNumberService
costing/             CostingService — external fabric costing, server-side only
fabric/              setup · booking · productionorder · planning · transaction
approval/            maker → checker → approver
utility/datatable/   grid support
resources/db/migration/  Flyway; V1 creates the document model
```

## What was kept from SpindleERP

- **One `BusinessDocument` + `DocumentType` enum** for every transactional document
- `BaseOrgEntity` / `BaseOrgLineEntity` split for headers vs child rows
- Multi-tenant scoping, soft delete, optimistic locking
- The approval engine's maker/checker/approver shape
- Commercial document set **including back-to-back LC** (`EBLC`/`IBLC`), which asgdynamic
  never had and a fabric exporter needs

## What was changed, and why

| SpindleERP | Here |
|---|---|
| `ContextProvider` — static fields, 6 repositories, called from `@PrePersist` | `OrgContext` **injected** into a Spring-managed entity listener. Entities are unit-testable; no repository query mid-flush. |
| Audit columns declared in **3** base classes; one silently wrote NULLs for want of `@EnableJpaAuditing` | Declared **once** in `AuditableEntity`, stamped by exactly one mechanism |
| `@Version` on headers but **not** on lines | On everything — lines carry the quantity and money |
| `ddl-auto=update`, Flyway off, V-migrations hand-applied | **Flyway owns the schema**; `ddl-auto=validate`. Hand-written DDL. |
| **194 of 327 FKs unindexed** | Every FK indexed at creation — verified: 0 unindexed |
| Org modelled as `@ManyToOne Organization` (join on every query) | `organization_id` column — scoping is a predicate, not a navigation |
| Org-scoping and soft-delete are a convention each query must remember | Baked into `BusinessDocumentRepository`; no unscoped finder to call by accident |
| Ceilings added later (`YarnReceiveCeilingTest`, `DeliveryChallanBalanceGuardTest`) | `fulfil()` guard **plus** a DB `CHECK` — enforced in both places from day one |
| ~40 tests on the AI layer, 3 on the transactional core | Tests start at the core: totals, fulfilment ceiling, status machine |

Naming drift was also fixed: SpindleERP carries `yarn_*` alongside `yrn_*`, `sls_*` beside
`sal_*`, and unprefixed `journal_entry_*` next to 13 `acc_*` tables. One prefix per module here.

## Where asgdynamic's logic lives

- **`FabricSpec`** (embedded on every line) — construction, declared construction, weave type
  and style, fabric type, finish, composition, GSM, finish/cuttable width, light source,
  selvedge, colour, costing code.
  asgdynamic modelled these as a middle `dtlSet` level and repeated the columns per screen
  (`so_dtlSet_weaveType`, `po_dtlSet_weaveType`, …). They are what the line *is*, so they are
  embedded — one less join, one less table per document type.
- **`DocumentType`** — Booking → BPO → Request-for-PI → Delivery Order for sales;
  Rout Card → Weaving WO → Processing WO → Greige Receive → Greige Issue → Finished Fabrics
  Receive for production. Routing, not recipe: no `PRODUCTION_RECIPE`, no `WASTE_RECEIVE`.
- **No RFQ / Comparative Statement** — fabric inputs are bought on contract, unlike cotton.
- **`isRevisable()`** — fabric sales documents are revised, never edited in place.
- **`CostingService`** — the external fabric-costing API, server-side. Replaces
  `onclick_so_dtlSet_fabricsCost` / `gsmCalculated` / `leadTime`.

## What is built

**Core** — `BusinessDocument` + `BusinessDocumentLine` + `FabricSpec`, `DocumentType` (33
types), the status machine, org-scoped repository, numbering service,
`DocumentRevisionService` (shared — see below).

**Security** — `FabricUser` → `FabricUserPrincipal` → `SecurityOrgContext`, deny-by-default
HTTP config, form login, remember-me. Every fabric service now has a real `OrgContext` to
resolve. Details under **Security**, further down.

**Setup** — all seven fabric reference lists behind **one** entity, service, controller and
template, routed by slug (`/setup/fabric/weave-type`). Adding a list means adding one
`AttributeType` constant. Seeded with the 98 real values recovered from the live asgdynamic
screens.

**Booking** (1st document type) — service (save, submit, revise, delete), controller
(page + grid + detail + actions), Thymeleaf screen with the fabric line table.

**BPO** (2nd document type) — raised against a Booking. This is the one that mattered: a
BPO line draws against a *specific* Booking line's outstanding quantity
(`BusinessDocumentLine.sourceLineId`, added after the crawl showed asgdynamic tracking
exactly this as `transaction_qty_so`). `fulfil()`/`release()`, written for Booking's own
ceiling, are reused unchanged to enforce it — proof the guard generalizes rather than
being Booking-specific. Editing a draft BPO releases its old reservation before applying
the new one, rather than stacking; deleting one releases it entirely.

**`DocumentRevisionService`** — extracted out of `BookingService` the moment `BpoService`
needed the same revision logic, rather than copy-pasting a second time. Both services now
call one implementation.

**Grid** — `DataTableRequest`/`Response` with a `SortWhitelist`, so a client-supplied sort
column can never reach the query planner unchecked. SpindleERP's `BaseDataTableService`
concatenates its `ORDER BY` and `WHERE` over raw `JdbcTemplate`.

## Verified against a real PostgreSQL database

- V1–V4 apply cleanly in order, **0 unindexed foreign keys** throughout
- 98 attribute rows seeded — `2/1 S Twill`, `Broken Twill`, `Aero Finish`, `CWF`,
  `10 mm + 10 mm` … with no duplicate codes generated
- `uk_fab_attr_org_type_code` rejects a duplicate code; the same code under a different
  attribute type is allowed
- Numbering yields `BPOAF000001`, `000002`, `000003` — the legacy format
- `ck_gbd_revision_lineage` rejects a revision with no parent
- `ck_gbdl_fulfilment` rejects over-fulfilment; valid rows accepted
- `sec_fabric_users` username uniqueness enforced; deleting a user cascades its authority
  rows (0 remained after delete)
- `fk_gbdl_source_line` (BPO line → Booking line) blocks deleting a line that has been
  drawn against, and allows the delete once the dependent line is gone first

Java sources parse cleanly. **They have not been compiled and the tests have not run** —
there is no Maven CLI on this machine, so dependencies were never resolved. Four test
classes are written but unexecuted: document rules, Booking revision semantics, BPO
ceiling/release behaviour, security context resolution.

## Before it runs

1. **Rotate the costing API credential** (compromised — it was in client JS), then set
   `COSTING_USER` / `COSTING_PASSWORD`.
2. Create the database and set `DB_URL` / `DB_USER` / `DB_PASSWORD`.
3. Implement `OrgContext` against your session/security setup.
4. Seed `gbl_document_sequence` from the legacy high-water mark per `(org, TYPE+UNIT)`, or
   numbers already in circulation will be reissued.
5. `mvn -N wrapper:wrapper`, or open in IntelliJ (bundles Maven).

```bash
npm install && npm run css:build
mvn spring-boot:run
mvn -Dfrontend.skip=true test
```

## Security

`FabricUser` (username, password hash, default business unit/warehouse, a flat set of
`ROLE_*` authority strings) → `FabricUserPrincipal` (carries the scope into
`Authentication`) → `SecurityOrgContext` (a stateless singleton `OrgContext` that reads
`SecurityContextHolder` fresh on every call — nothing cached, so nothing goes stale).

Deny-by-default at the HTTP layer (`anyRequest().authenticated()`); every route's actual
role requirement is `@PreAuthorize` on the controller method, not a separate URL-pattern
table. SpindleERP's `DynamicAuthorizationManager` has exactly such a table, and its own
comment records why an unmatched route currently **grants** access rather than denying —
a live fail-open path kept for migration safety. There is no equivalent gap here: a
controller method with no `@PreAuthorize` is unreachable, not ungoverned.

**First login:** nothing is seeded by default. Set `app.seed-dev-user=true` and the
`FABRIC_ADMIN_PASSWORD` environment variable, start the app once, then turn the property
back off. `DevUserSeeder` refuses to run without that env var rather than generating or
printing a password — same rule as every other secret in this project.

## Still to build

- **Approval engine** — maker → checker → approver, with the four-eyes rule.
- **The remaining fabric documents** — BPO, Request-for-PI, Rout Card, Weaving/Processing
  WO, Greige Receive/Issue, Finished Fabrics Receive. Each is a service + controller +
  template following `fabric/booking`; the document model needs no change.
- **Front-end JS** — the grid and line-table behaviour the templates declare via
  `data-action` / `data-lookup` attributes.
- **Party, item and UoM masters** — currently referenced by id only.
- Purchase, store, commercial and accounts modules.

Per-screen field and event inventories for all of it are in
[`business-logic-capture/`](business-logic-capture/) — `per-screen-capture.md` is the
acceptance checklist for each screen.
