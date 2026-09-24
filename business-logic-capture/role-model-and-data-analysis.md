# Role Model, Data Layer & Master Data — Deep Analysis

Derived offline from the 137 captured screens. Companion files: `role_model.json`, `master_data_catalog.json`.

## 1. The grid architecture (important for migration)

Every list in the application is a **server-side DataTable**. The page ships an empty `<tbody>`; rows are
fetched by AJAX. Critically, the data source is the **same `/{controller}/index` URL** that serves the HTML:

```js
requestmapTable.DataTable({
    processing: true, serverSide: true, iDisplayLength: 25,
    sAjaxSource: actionIndex,              // = "/requestmap/index"
    fnServerParams: function (aoData) {
        aoData.push({name: "conditionParams", value: "requestmapTable"});
    }
});
```

So `/{c}/index` is **content-negotiated**: plain GET returns the page, GET with DataTables params
(`sEcho`/`iDisplayStart`/`iDisplayLength` + `conditionParams=<tableId>`) returns JSON rows.
Each screen names its table via `conditionParams`, which the server uses to pick the query.

**Migration implication:** do not replicate this overloading. Split it into a clean pair —
`GET /{entity}` returning the Thymeleaf page, and `GET /api/{entity}` returning a `Page<T>` for the grid
(Spring Data `Pageable` maps naturally onto DataTables' start/length/sort/search).

## 2. Complete role model — 71 roles

The full role list was recovered from the `requestmap` and `userRole` screens. Highlights:

### Segregation of duties (maker → checker → approver)

The commercial document types each carry a three-stage role triad — this is the single most important
control pattern in the system and must be preserved:

| Document | Maker | Checker | Approver |
|---|---|---|---|
| Proforma Invoice | `ROLE_PI_MAKER` | `ROLE_PI_CHECKER` | `ROLE_PI_APPROVAL` |
| Letter of Credit | `ROLE_LC_MAKER` | `ROLE_LC_CHECKER` | `ROLE_LC_APPROVAL` |
| Commercial Invoice | `ROLE_CI_MAKER` | `ROLE_CI_CHECKER` | `ROLE_CI_APPROVAL` |

### Role families

| Family | Count | Roles |
|---|---|---|
| COMMERCIAL | 15 | `ROLE_COMMERCIAL`, `ROLE_COMMERCIAL_CI_ISSUE`, `ROLE_COMMERCIAL_CI_REPORT`, `ROLE_COMMERCIAL_CI_REVISION`, `ROLE_COMMERCIAL_DOCUMENT_NAMES`, `ROLE_COMMERCIAL_LC_COST_HEAD`, `ROLE_COMMERCIAL_LC_RECEIVING`, `ROLE_COMMERCIAL_LC_REPORT`, `ROLE_COMMERCIAL_LC_REVISION`, `ROLE_COMMERCIAL_LC_TERMS_&_CONDITIONS`, `ROLE_COMMERCIAL_PI_ISSUE`, `ROLE_COMMERCIAL_PI_REPORT`, `ROLE_COMMERCIAL_PI_REVISION`, `ROLE_COMMERCIAL_PI_TERMS_&_CONDITIONS`, `ROLE_COMMERCIAL_SETUP` |
| PRODUCTION | 6 | `ROLE_PRODUCTION`, `ROLE_PRODUCTION_DELIVERY_SCHEDULE`, `ROLE_PRODUCTION_ORDER`, `ROLE_PRODUCTION_ORDER_REPORT_LIST`, `ROLE_PRODUCTION_ORDER_REQUIREMENT`, `ROLE_PRODUCTION_ORDER_REVISION` |
| CI | 4 | `ROLE_CI_APPROVAL`, `ROLE_CI_CHECKER`, `ROLE_CI_ISSUE_VIEW`, `ROLE_CI_MAKER` |
| MARKETING | 4 | `ROLE_MARKETING_ADMIN`, `ROLE_MARKETING_EXECUTIVE`, `ROLE_MARKETING_MANAGER`, `ROLE_MARKETING_TEAMLEAD` |
| DELIVERY | 3 | `ROLE_DELIVERY_ORDER`, `ROLE_DELIVERY_ORDER_REPORT_LIST`, `ROLE_DELIVERY_ORDER_REQUEST` |
| IMPORT | 3 | `ROLE_IMPORT_MAKER`, `ROLE_IMPORT_MANAGER`, `ROLE_IMPORT_SETUP` |
| LC | 3 | `ROLE_LC_APPROVAL`, `ROLE_LC_CHECKER`, `ROLE_LC_MAKER` |
| PI | 3 | `ROLE_PI_APPROVAL`, `ROLE_PI_CHECKER`, `ROLE_PI_MAKER` |
| ADMIN | 2 | `ROLE_ADMIN`, `ROLE_ROLE_ADMIN_IMPORT_COMMERCIAL` |
| BOOKING | 2 | `ROLE_BOOKING_REPORT`, `ROLE_BOOKING_REVISION` |
| FABRICS | 2 | `ROLE_FABRICS_DELIVERY`, `ROLE_FABRICS_DELIVERY_REPORT_LIST` |
| PLANNING | 2 | `ROLE_PLANNING`, `ROLE_PLANNING_HEAD` |
| SALES | 2 | `ROLE_SALES`, `ROLE_SALES_&_MARKETING_SETUP` |

Single-role families: `ROLE_ACCOUNTS`, `ROLE_ADMINISTRATOR`, `ROLE_APPROVAL`, `ROLE_BANK_SETUP`, `ROLE_EMPLOYEE`, `ROLE_FINISHED_GOODS_TRANSACTION`, `ROLE_GREIGE_FABRICS_TRANSACTION`, `ROLE_HRM`, `ROLE_INVENTORY`, `ROLE_OPEN_API`, `ROLE_PROCESSING_STORE`, `ROLE_PURCHASE`, `ROLE_REPORT`, `ROLE_RND`, `ROLE_SERVICE`, `ROLE_SETUP`, `ROLE_SOLID_DYED_BOOKING`, `ROLE_SUPER_ADMIN`, `ROLE_WEAVING_STORE`, `ROLE_YARN_DYED_BOOKING`

### Notable roles

- `ROLE_SUPER_ADMIN`, `ROLE_ADMINISTRATOR`, `ROLE_ADMIN` — **three** overlapping admin roles; consolidate in the rebuild.
- `ROLE_OPEN_API` — implies a machine/API access path worth locating in the source.
- `ROLE_MARKETING_ADMIN` / `_MANAGER` / `_TEAMLEAD` / `_EXECUTIVE` — a four-level marketing hierarchy,
  a natural fit for Spring Security `RoleHierarchy`.
- `ROLE_PROCESSING_STORE`, `ROLE_WEAVING_STORE` — roles scoped to a **store**, confirming that store
  context and authorization are entangled. Model store-scoping explicitly rather than as a role.
- `ROLE_SOLID_DYED_BOOKING` / `ROLE_YARN_DYED_BOOKING` — authorization varies by **product type**.

## 3. Master data catalog — 112 reference lists

Actual values were recovered from the dropdowns rendered on working screens. These seed the new system
and define which lookups become enums vs. tables.

### Domain enums (small, stable → Java enum or lookup table)

| List | Values | Example |
|---|---|---|
| `weaveType` | 13 | 2/1 S Twill; 2/1 Z Twill; 2/2 Matt; 2/2 S Twill |
| `so_dtlSet_weaveStyles` | 14 | Broken Twill; Cavalry Twill; HBT; Long Float |
| `so_dtlSet_fabricsFinishType` | 12 | Aero Finish; Both Side Peach; Brush; Carbon peach Finish |
| `fabricType` | 18 | Greige Indigo Denim; Greige Indigo Denim Spandex; Greige Solid Dyed; Greige Solid Dyed Lungi |
| `so_dtlSet_fabricsType` | 14 | Greige Solid Dyed; Greige Solid Dyed (LUNGI); Greige Solid Dyed Spandex; Greige Yarn Dyed |
| `so_dtlSet_lightSource` | 7 | CWF; D65; Filament; TL83 |
| `selvedgeEnum` | 10 | 10 mm + 10 mm; 12 mm + 12 mm; 15 mm + 15 mm; 20 mm + 20 mm |
| `tenure` | 13 | 120 Days; 120 Days; 150 Days; 150 Days |
| `dashboardEnum` | 19 | Accounts Dashboard; Administrator Dashboard; Booking Dashboard; Booking Management Dashboard |

### Reference tables (larger, maintained data)

| Entity | Values | Screens | Notes |
|---|---|---|---|
| `bank` | 49 | 3 | Banks — also as `beneficiaryBank` / `buyerBank`, i.e. one table, three roles |
| `accHead` | 338 | 1 | Chart of accounts heads, format `Name (Parent Group)` |
| `operationUnit` | 19 | 6 | Units of measure, coded `U100-Kilometer`; same list as `purchaseUnit`/`salesUnit` |
| `countries` | 17 | 3 | Country, `Name-ISO2` |
| `hsCode` | 13 | 5 | HS tariff codes |
| `genericName` | 13 | 1 | Chemical generic names |
| `designation` | 11 | 1 | HR designations, coded `DAF00000n-Title` |
| `marketingGroup` | 12 | 2 | Marketing groups |
| `documentUrlsSet` | 19 | 1 | Document types registered in the Approval engine |

### Transactional data visible in pickers

These are live documents, useful for understanding code formats and for test fixtures:

- `scheduleList` (74) — `BPOAF{seq}-SCAF{seq}-{Customer}`; links BPO → delivery schedule → customer.
- `piList` (46) — `IMPPIAF{seq}-{ref}-{Supplier}` import proforma invoices.
- `expLcReceive` (32) — `LCAF{seq}-{Customer} {date}` export LCs.
- `poList` (10) — purchase orders.

**Document code convention:** `{TYPE}{UNIT}{sequence}` where `AF` = the ASFL business unit
(e.g. `BPOAF000001`, `LCAF000002`, `DAF000002`). The business-unit code is embedded in every document
number — preserve this in the new numbering service.

### Per-screen field prefixing

Detail-set dropdowns are prefixed with the owning entity (`so_dtlSet_weaveType`, `po_dtlSet_countries`,
`grm_dtlSet_countries`). The **same** reference list is re-rendered per screen under a different field
name — in the rebuild, bind these to one shared lookup and one Thymeleaf fragment.
