# ASG FabricERP

Weaving and dyeing ERP — **asgdynamic's business logic on SpindleERP's structure**, with the
structural weaknesses found in SpindleERP corrected.

Spring Boot 3 · Java 21 · PostgreSQL · Thymeleaf · Tailwind · Maven · Flyway.

> Package/artifact is `com.asg.fabricerp` / `fabricerp`, mirroring SpindleERP's naming.
> Rename if you'd rather keep `asgdynamic`.

## The shape of it

One generic document table discriminated by `DocumentType` — SpindleERP's central idea —
replacing asgdynamic's 42 near-identical controller/table pairs. Three levels deep, not
two: `BusinessDocument` → `BusinessDocumentLineGroup` (one fabric specification) →
`BusinessDocumentColorLine` (one colour, with its own quantity, price and reference
fields). See **"Colour breakdown" — a real correction**, just below, for why it isn't flat.

```
common/              AuditableEntity, BaseOrgEntity, BaseOrgLineEntity,
                     OrgContext (injected), OrgContextListener
global/documents/    BusinessDocument, BusinessDocumentLineGroup, BusinessDocumentColorLine,
                     FabricSpec, DocumentType, BusinessDocumentStatus,
                     BusinessDocumentRepository, DocumentNumberService,
                     ParentLineDrawService, DocumentRevisionService
costing/             CostingService — external fabric costing, server-side only
fabric/              setup · booking · productionorder · requestforpi · weavingworkorder ·
                     processingworkorder · greigereceive · deliveryorder · fabricsdelivery
approval/            maker → checker → approver
utility/datatable/   grid support
resources/db/migration/  Flyway; V1 creates the document model, V6 corrects its shape
```

## "Colour breakdown" — a real correction, not a preference

An earlier version of this model collapsed asgdynamic's two-level `dtlSet`/`dtlLine`
structure into one flat row per colour, embedding the fabric spec directly on it. The
reasoning at the time: *"the middle level only existed to avoid repeating fabric attributes
per colour, and an embeddable does that without a second table."*

A real production Booking API response settled that this was wrong. One line group —
construction `20X20/69X56`, `70% Viscose 30% Linen` — carried **six** colours, each with a
genuinely different `lab_dip_reference` (`25-08A-2279 OPT-C` for White,
`25-08A-2221 OPT-F` for Black) and a different `fabrics_style`. Flattening that would mean
re-entering the whole fabric specification six times for one order, and there was nowhere
to put the per-colour reference fields at all.

Fixed by reinstating the two levels properly: `BusinessDocumentLineGroup` carries the
fabric spec once; `BusinessDocumentColorLine` carries colour, quantity, price,
`labDipReference`, `strikeOffReference`, `colorReference`, `loomReference`, `fabricsStyle`.
`FabricSpec` itself grew — yarn counts/ratios, EPI/PPI, shrinkages, GSM before/after wash,
wash type/instruction, end use, DISPO reference — all confirmed present on the same real
payload and missing before. Verified by reproducing that exact payload as real rows (see
**Verified against a real PostgreSQL database**, below): the same construction, the same
two lab-dip references, the same totals.

The same crawl had also produced two smaller, separately-caught errors, both now fixed:
Booking's document-number prefix was guessed as `BKG`; the real codes (`BKAF000017`,
`BKAF000059`) show it is `BK`. And `ParentLineDrawService` originally drew against a whole
fabric-spec group; the real payload's `so_line_dtl_id` — what a downstream document
actually references — is the **colour line's** id, not the group's, so the drawable unit
moved down a level along with everything else.

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

- **`FabricSpec`** (embedded on every `BusinessDocumentLineGroup`) — construction, declared
  construction, weave type and style, fabric type, finish, composition, yarn counts/ratios,
  EPI/PPI, shrinkages, GSM (calculated + before/after wash), widths, light source, selvedge,
  wash type/instruction, end use, DISPO reference, costing code. Colour, quantity, price and
  the per-colour reference fields (`labDipReference`, `strikeOffReference`, `colorReference`,
  `loomReference`, `fabricsStyle`) live one level down on `BusinessDocumentColorLine` — see
  **"Colour breakdown"**, above, for why that split is real and not decorative.
- **`DocumentType`** — Booking → BPO → Request-for-PI → Delivery Order for sales;
  Rout Card → Weaving WO → Processing WO → Greige Receive → Greige Issue → Finished Fabrics
  Receive for production. Routing, not recipe: no `PRODUCTION_RECIPE`, no `WASTE_RECEIVE`.
- **No RFQ / Comparative Statement** — fabric inputs are bought on contract, unlike cotton.
- **`isRevisable()`** — fabric sales documents are revised, never edited in place.
- **`CostingService`** — the external fabric-costing API, server-side. Replaces
  `onclick_so_dtlSet_fabricsCost` / `gsmCalculated` / `leadTime`.

## What is built

**Core** — `BusinessDocument` → `BusinessDocumentLineGroup` → `BusinessDocumentColorLine` +
`FabricSpec`, `DocumentType` (33 types), the status machine, org-scoped repository,
numbering service, `DocumentRevisionService` and `ParentLineDrawService` (shared — see
below).

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
BPO colour line draws against a *specific* Booking colour line's outstanding quantity
(`BusinessDocumentColorLine.sourceColorLineId`, added after the crawl showed asgdynamic
tracking exactly this as `transaction_qty_so`). `fulfil()`/`release()`, written for
Booking's own ceiling, are reused unchanged to enforce it — proof the guard generalizes
rather than being Booking-specific. Editing a draft BPO releases its old reservation
before applying the new one, rather than stacking; deleting one releases it entirely.

**Request For PI** (3rd) and **Weaving Work Order** (4th) — both draw against a BPO, the
same way BPO draws against a Booking. Confirmed against the real crawl: `deliveryOrder`'s
`transactionQtySchedule` field shows Delivery Order draws against Request-for-PI, not BPO
directly, and `textileWoByPro`'s `proPopulate`/`proReceive` functions show Weaving WO,
Processing WO and Greige Receive **each draw independently against the BPO**, not through
each other. Verified end to end against a real database: a BPO line drawn on by both RPI
(300) and WWO (250) simultaneously shows 550/700 fulfilled, and the DB `CHECK` still
rejects exceeding it.

Weaving Work Order is also the **first type with no revision path** — its legacy screen's
captured functions carry no revision handler, unlike every type built before it. A real
branch in the pattern, not an oversight: documented on `WeavingWorkOrderService` rather
than silently omitted.

**Processing Work Order** (5th), **Greige Receive** (6th), **Delivery Order** (7th, draws
against Request-for-PI) and **Fabrics Delivery** (8th, draws against Delivery Order) —
extending both chains one/two more hops each on the same `ParentLineDrawService`. None are
revisable or costed, matching what their legacy screens' captured functions actually show.

Verified end to end against a real database: the full **5-hop sales chain** — Booking → BPO
→ Request-for-PI → Delivery Order → Fabrics Delivery — inserted and queried, each hop's
ledger correct (BPO line 600/800 fulfilled, RPI line 400/600, DO line 400/400), and the
`CHECK` constraint still rejects over-drawing the Delivery Order line at that depth.

**A correction caught before it was built on:** an earlier pass assumed Greige Issue draws
against Greige Receive and Finished Fabrics Receive draws against Greige Issue — sequential,
like the sales chain. Checking `textileIssue`'s actual captured fields before building it
showed a `woCode` field and "WO QTY"/"Issue QTY" grid columns, not a receipt reference —
meaning it likely draws against the **Weaving Work Order's** committed quantity instead.
Genuinely ambiguous from field names alone, so those two types are not built yet rather than
encoding a guess with false confidence — see **Still to build**.

**`DocumentRevisionService`** — extracted out of `BookingService` the moment `BpoService`
needed the same revision logic, rather than copy-pasting a second time. Every revisable
type now calls one implementation.

**`ParentLineDrawService`** — the loadParent/draw/release mechanics lived inline in
`BpoService` until a second consumer (`RequestForPiService`) needed the identical logic;
extracted the same way `DocumentRevisionService` was. What stayed **out** of it: each type's
own header-copy-on-edit fields and whether it refreshes costing (neither RPI nor WWO does —
confirmed by the absence of any `fabricsCost`/`gsmCalculated` function in their captured
screens, unlike Booking and BPO) — forcing those into one generic shape would have papered
over real differences rather than removing actual duplication.

**Approval engine** — `ApprovalService` + `ApprovalController` (`/api/documents/{id}/submit
|approve|reject`, `/history`), generic over every document type, not one per fabric service.
`BookingService`/`BpoService` lost their duplicated one-line `submit()` to it. Enforces:

- the maker role for submit, the approver role for approve/reject — both resolved per
  `DocumentType` (`makerRole()`/`approverRole()`), not hardcoded per controller
- **four-eyes**: the document's own creator cannot approve it, even holding the role.
  asgdynamic's client-side `*ChangeStatus()` handlers had no such check
- a full audit trail in `apr_document_history` — who, when, what transition, remarks —
  which the legacy status-change handlers left no server-side record of at all

Two dev accounts (`admin`/`approver`) are seeded, not one, specifically so four-eyes is
exercisable locally without weakening it — a single account could submit but could never
legally approve its own document.

**Grid** — `DataTableRequest`/`Response` with a `SortWhitelist`, so a client-supplied sort
column can never reach the query planner unchecked. SpindleERP's `BaseDataTableService`
concatenates its `ORDER BY` and `WHERE` over raw `JdbcTemplate`.

## Verified against a real PostgreSQL database

- V1–V6 apply cleanly in order, **0 unindexed foreign keys** throughout — including after
  V6 dropped and rebuilt the document-line tables into two levels
- **The real BKAF000028 payload reproduced as actual rows**: one `gbl_business_document_
  line_groups` row (construction `30X30+40D/156X88`) with two `gbl_business_document_
  color_lines` children (PUMICE STONE and BLACK), each carrying its own `lab_dip_reference`
  and `fabrics_style`; summed `line_amount` matched the real document total (67,680.00)
  exactly
- The fulfilment `CHECK` verified at colour-line depth: drawing 10,000 of PUMICE STONE's
  15,040 leaves exactly 5,040 outstanding; the 5,041st unit is rejected; BLACK's own
  ceiling is untouched by White's draw
- 98 attribute rows seeded — `2/1 S Twill`, `Broken Twill`, `Aero Finish`, `CWF`,
  `10 mm + 10 mm` … with no duplicate codes generated
- `uk_fab_attr_org_type_code` rejects a duplicate code; the same code under a different
  attribute type is allowed
- Numbering yields `BPOAF000001`, `000002`, `000003` — the legacy format (with Booking's
  own prefix corrected to `BK`, confirmed by the same real payload)
- `ck_gbd_revision_lineage` rejects a revision with no parent
- `sec_fabric_users` username uniqueness enforced; deleting a user cascades its authority
  rows (0 remained after delete)
- `fk_gbdcl_source_color_line` (BPO colour → Booking colour) blocks deleting a colour line
  that has been drawn against, and allows the delete once the dependent line is gone first
- A full submit → approve round trip written to `apr_document_history` reads back newest
  first with the right actor on each row; deleting the parent document cascades its history
- A real 4-document chain (Booking → BPO → Request-for-PI → Weaving WO) inserted and
  queried end to end: the BPO line correctly shows 550/700 fulfilled with RPI and WWO
  drawing against it independently, and the fulfilment `CHECK` still rejects exceeding it
  at that depth
- A real **5-hop sales chain** (Booking → BPO → RPI → Delivery Order → Fabrics Delivery)
  inserted and queried end to end, every hop's ledger correct, `CHECK` still enforced at
  full depth

Java sources parse cleanly. **They have not been compiled and the tests have not run** —
there is no Maven CLI on this machine, so dependencies were never resolved. Eleven test
classes are written but unexecuted: document rules (now covering multi-colour groups),
revision semantics (now covering per-colour reference fields), ceiling/release behaviour
across all six draw-against-parent types, security context resolution, approval
role/four-eyes checks.

## Before it runs

1. **Rotate the costing API credential** (compromised — it was in client JS), then set
   `COSTING_USER` / `COSTING_PASSWORD`.
2. Create the database and set `DB_URL` / `DB_USER` / `DB_PASSWORD`.
3. Create the first login: `app.seed-dev-user=true` + `FABRIC_ADMIN_PASSWORD`, start once,
   then turn the property back off (see **Security** below).
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

**First login:** nothing is seeded by default. Set `app.seed-dev-user=true` plus
`FABRIC_ADMIN_PASSWORD` (a maker) and `FABRIC_APPROVER_PASSWORD` (an approver — needed to
actually exercise the four-eyes rule, since the maker can never approve its own document),
start the app once, then turn the property back off. `DevUserSeeder` refuses to create an
account without its env var rather than generating or printing a password — same rule as
every other secret in this project.

## Still to build

- **Greige Issue and Finished Fabrics Receive — genuinely unresolved, not just undone.**
  `textileIssue`'s captured fields (`woCode`, grid columns "WO NO"/"WO QTY"/"Issue QTY")
  suggest Greige Issue draws against the **Weaving Work Order**, not against Greige
  Receive as the family's original comment on `DocumentType` assumed before anyone checked
  the fields — likewise Finished Fabrics Receive against the **Processing Work Order**
  (`dyeingReceive` shows the same shape). This is a reasonable reading of the field names,
  not a confirmed fact: verify against the real legacy screen behaviour, or a stakeholder
  who worked with it, before picking a `PARENT_TYPE` and building on it. Both would
  otherwise be a straightforward seventh/eighth instance of the exact same
  `ParentLineDrawService` pattern.
- **Raw Material Issue, Sales Return.** Sales Return's capture is unusually thin — 6 generic
  fields, **zero captured functions** — thinner than every other type built here by a wide
  margin. Treat it as unimplemented-in-the-legacy-system-in-practice rather than a normal
  gap; get real requirements before building it, the same caution as Rout Card below.
- **`ROUT_CARD` has no captured legacy data at all** — one of the 14 dead menu entries
  found 404ing even at the bare controller root (see
  `business-logic-capture/missing-screens-report.md`). Never actually implemented in the
  legacy system. Build it from a real requirements conversation, not by guessing at
  asgdynamic's intent the way every other type here was grounded in its capture.
- Each new type needs one line added to `DocumentType`'s `roleRoot` (see its javadoc)
  before its controller can call `ApprovalService`.
- **The maker/checker/approver triad** for the Commercial family (PI/LC/CI) — today every
  document type uses the single-stage `ROLE_APPROVAL` path; the three-stage version is a
  documented extension point on `DocumentType.approverRole()`, deliberately not built until
  a Commercial document type exists to test it against.
- **A `Role`/`Permission` table**, if the flat `ROLE_*` string set on `FabricUser` ever
  needs to be editable at runtime rather than a fixed set of `@PreAuthorize` strings /
  `DocumentType.roleRoot()` values.
- **Front-end JS** — the grid and line-table behaviour the templates declare via
  `data-action` / `data-lookup` attributes.
- **Party, item and UoM masters** — currently referenced by id only.
- Purchase, store, commercial and accounts modules.

Per-screen field and event inventories for all of it are in
[`business-logic-capture/`](business-logic-capture/) — `per-screen-capture.md` is the
acceptance checklist for each screen.
