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
inventory/item/      item master + categories, UoM, HS codes, brands, models, yarn masters
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

**Item master** (`inventory/item`, V13) — SpindleERP's `inventory.item` package ported onto
this project's conventions: one `InventoryItem` for every item type (the legacy system split it
into seven screens over one table), the three-level category tree with its positional codes
(`CAF110000` → `CAF111100` → `CAF111111`, the same shape as the legacy roots), units of measure,
HS codes, brands, models, yarn types/counts/plies and fiber blends. Two screen grants:
`ITEM` and `ITEM_SETUP`. The seven simple lists share one controller and one spec-driven
template (`inventory/master`), the way the fabric lists do. Master codes come from
`DocumentNumberService.nextCode` (`ITMAF000001`, `YTAF0001`) - SpindleERP's `MAX()+1` hands
two concurrent callers the same code. Corrections, each enforced in the database too:

- yarn attributes live on the item, not in a 1:1 `yarn_items` table; `ck_inv_item_yarn_spec`
  makes "YARN if and only if type + count + ply + blend are all set" a database fact, and
  `ux_inv_item_yarn_identity` stops two active yarns sharing that combination
- one base unit per UoM category (`ux_inv_uom_base_per_category`); SpindleERP allowed two,
  which makes every conversion factor in that category ambiguous
- a category moves only if it has no children, only within its level, and is re-coded; SpindleERP
  could turn a GROUP with children into an ITEM with children
- item-level categories must name their item type; SpindleERP's form never sent one, so the
  type filter on the category picker could never match
- a blend that yarn items use cannot be re-composed; a fiber that blends use stays a fiber
- items, brands, models and yarn masters carry approval with the same four-eyes rule as documents

**Parties** (`party`, V15) — asfl-erp's party module, org-scoped: one `Party` per company with a
`PartyRole` row per capacity (CUSTOMER qualified MARKETING/COMMERCIAL, SUPPLIER, AGENT, BANK,
EMPLOYEE, BRAND, BUYING_HOUSE, GARMENT_FACTORY), plus addresses, contacts and bank accounts held
at a bank that is itself a party. `/api/parties/directory?role=CUSTOMER` is the picker.
`DocumentType.requiredPartyRole()` says who a document may name - sales and production a
customer, purchase a supplier - and it is checked on every save.

**Party maintenance** (`/setup/parties`, screen `PARTY`, V16) — built for a directory in the tens
of thousands: paging, sorting, search and the role-chip counts run in PostgreSQL; a page is one
query plus three batched lookups (roles, primary contact, primary address), never one per row.
Search covers code, names, TIN/BIN and the legacy customer/supplier codes on the roles, backed by
`pg_trgm` indexes when the database permits the extension (V16 skips them with a notice if not).
Filters live in the URL (`?role=BANK&q=brac`, `?id=` opens a party); **Export CSV** streams the
filtered directory. The editor covers the whole aggregate - roles (revoked, not erased, so
"supplier since when" survives), addresses, contacts, bank accounts, other registrations - with
unsaved-change and concurrent-edit protection, and refuses to delete a party a document names.

**Document associations are objects.** `BusinessDocument` maps `businessUnit`, `warehouse`,
`party`, `parentDocument`, `marketingTeam` and `revisionOf` as `@ManyToOne` entities, line groups
map `item` and `uom`, colour lines map `sourceColorLine` - each backed by a real foreign key
(V15). Requests name them as `{"party": {"id": 42}}`; `DocumentReferences` swaps each for the
organization's own row (or refuses) before a save touches anything, and the business unit,
marketing team and revision root are read-only in JSON - the server stamps them.
`organizationId` stays a column: scoping is a predicate, not a navigation.

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
- **V1–V13 on an empty database, with `ddl-auto=validate`** — every item entity matches its
  hand-written DDL. Through the running app's API: a category tree (`CAF111100`,
  `CAF111111`), two fibers, a 60/40 blend named `60% Cotton 40% Viscose`, and a yarn named
  `30/1 CD 60% Cotton 40% Viscose` (`ITMAF000003`); a duplicate yarn, a 90% blend, a fiber
  filed under a yarn category, re-composing the in-use blend and deleting the fiber it uses
  all refused with a readable message; a new yarn type numbered `YTAF0003`, after the two seeded
- Written straight into the tables, bypassing Java: a yarn without its four attributes, a fiber
  carrying one, a second identical yarn, a case-variant duplicate item name, a second base unit,
  a 0% blend component and a root with a parent are each rejected by the database

Compiled and tested with IntelliJ's bundled Maven (`plugins/maven/lib/maven3/bin/mvn`) on
JDK 21: all 145 tests pass, 42 of them for the item master (category codes and tree rules,
blend composition, yarn naming and identity, UoM base units, four-eyes approval, and the item
screens rendered through the real security config and layout).

## Before it runs

1. **Rotate the costing API credential** (compromised — it was in client JS), then set
   `COSTING_USER` / `COSTING_PASSWORD`.
2. Create the database and set `FABRICERP_DB_URL` / `FABRICERP_DB_USER` / `FABRICERP_DB_PASSWORD`.
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

**Administration screens** (all behind `SCREEN_SECURITY_ADMIN_*`, under *Administration → Security*):

- **Overview** (`/setup/security`) — who cannot work right now (locked; restricted with no scope),
  pending administrator-set passwords, the last 24 hours of failed logins and refusals, recent
  events. Every number is organization-scoped and comes from an aggregate query, not a scan.
- **Users** (`/setup/users`) — searchable, status-filtered grid; a tabbed editor for profile,
  roles, effective-dated data scope and sign-in state (lock/unlock, temporary-password reset with
  a generator). The business-unit code is derived from the unit, never typed beside its id.
  Fields nobody may change on their own account are shown disabled rather than refused on save.
- **Roles** (`/setup/roles`) — screen × verb matrix grouped by menu section, with row/column
  toggles and the "any verb implies VIEW" rule mirrored client-side; user counts per role, a
  filter separating granting roles from V11's empty shells, and duplicate-a-role.
- **Access log** (`/setup/access-log`) — the ADM-11 log, filterable by user, event and date. The
  log has no organization column, so reads narrow through the user it names; failed logins for
  usernames that exist nowhere are therefore not shown to any tenant (they stay in the table).

The sidebar is derived from the signed-in user's `VIEW` authorities (`web/Navigation`), so the
menu cannot list a screen the user would be refused. `/api/**` answers `401`/`403`/`400`/`409`
with a JSON `message` (`common/ApiExceptionHandler`); pages get `templates/error.html`. Shared
browser behaviour — grid, dialogs, toasts, CSRF-aware fetch — lives in `static/js/app.js`.

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
- **Front-end JS for the fabric screens** — the line-table behaviour those templates declare via
  `data-action` / `data-lookup` attributes. `static/js/app.js` now provides the grid, dialogs and
  fetch helpers the security screens use; the fabric templates are still standalone pages outside
  `layout/main.html` and have not been moved onto it.
- **Switching operating unit/store mid-session** — the header shows it read-only; see
  `FabricUser`'s javadoc.
- **Party data.** The legacy capture has no customer records - enter them on **Setup → Parties**
  or load them. It does have 49 bank names (`buyerBank`), without codes.
- **Validate the V15 foreign keys** once legacy rows are clean. They are `NOT VALID`: enforced on
  every write from V15 on, but not yet checked against rows written before
  (`ALTER TABLE gbl_business_documents VALIDATE CONSTRAINT fk_gbd_party;` and the other five).
- **Fibers and blends.** V13 seeds the 19 legacy units, 13 HS codes, the brand and the yarn
  type/count/ply lists; V14 the full legacy category tree (6 roots, 32 groups, 8 item-level
  categories, codes exactly as issued - including `CAF111201` and the `SFB…` ones). The legacy
  tree has no fiber category, so the four fibers (Viscose, Tencel, Cotton, Linen) and their
  three blends still need an item-level FIBER category created first, then entry through the
  screens. V14's item types on the item-level categories (Dyes/Chemicals → CHEMICALS, Yarn →
  YARN, the three fabrics → FABRICS, the two MRO ones → MRO) are inferred from their names.
- **Unit conversions to confirm:** V13 takes `Ton` as the metric tonne and `Gallon` as the US
  gallon; the legacy list carried names only.
- Purchase, store, commercial and accounts modules.

Per-screen field and event inventories for all of it are in
[`business-logic-capture/`](business-logic-capture/) — `per-screen-capture.md` is the
acceptance checklist for each screen.
