# Missing Screens — Investigation Report

Follow-up crawl of the 20 screens that failed the first pass. **Neither group is a crawler limitation** —
both are defects in the running application.

## Method

Grails returns different codes that discriminate the cause:

| Response | Meaning |
|---|---|
| `403` | URL is **not in the Spring Security requestmap** — deny-by-default. Confirmed by probing invented names (`selvegde`, `routCardIndex`) which all return 403. |
| `404` | URL **is permitted** by the requestmap, but **no controller/action is deployed**. |
| `500` | Controller exists and executes, then **throws**. |

Each controller was tested at `/{c}/index`, at the bare root `/{c}`, with parameter variations
(`max`, `offset`, `id`, `sort`, `businessUnit`, `storeId`), via POST, and across all standard actions.

## Group A — 14 dead menu links (404: permitted but not implemented)

These return 404 **even at the bare controller root**, so the controller does not exist in the deployed build.
They are live menu entries that lead nowhere for every user.

| Controller | Menu entry |
|---|---|
| `ciClosing` | CI Closing(Import) — Commercial/Import |
| `ciIssuing` | CI Issuing(Import) — Commercial/Import |
| `ciPayment` | CI Payment(Import) — Commercial/Import |
| `ciRealization` | CI Realization — Commercial/Export |
| `ciReceiving` | CI Receiving — Commercial/Export |
| `importPi` | Proforma Invoice(Import) — Commercial/Import |
| `lCTermsCondition` | LC Terms & Condition — Commercial/Setup |
| `lcClosing` | LC Closing & Post Processing — Commercial/Export |
| `lcIssuing` | LC Issuing(Import) — Commercial/Import |
| `pITermsCondition` | PI Terms & Condition — Commercial/Setup |
| `routCard` | Rout Card — Planning/Transaction |
| `selvage` | Selvage — R&D/Setup |
| `sprReport` | SPR Report — Inventory/Stock Report |
| `srReport` | SR Report — Inventory/Stock Report |

Note the pattern: 10 of 14 are **Commercial LC/CI** screens. Working siblings already cover much of that
ground (`expLcReceive`, `expCiReceive`, `impLcIssue`, `importPiReceive`, `expLcReport`…), which suggests the
menu still points at an older or planned naming scheme that was superseded.

## Group B — 6 broken screens (500: exists but throws)

`index` is the only action registered for these (all other actions return 403/unmapped), and it throws on
every request — with no params, with params, via GET and POST. Not recoverable from the client.

| Controller | Menu entry |
|---|---|
| `globalArea` | Area — System Setup/Location |
| `globalCity` | City — System Setup/Location |
| `globalCountries` | Country — System Setup/Location |
| `globalCurrency` | Currency — System Setup/Global Setup |
| `globalRegion` | Region — System Setup/Location |
| `fabricsTransfer` | Transfer Request — Inventory/Fabrics Transfer |

### Recovered data models

Although the maintenance screens are broken, the entities they manage were recovered from the dropdowns
they populate on working screens (`recovered_refdata.json`):

- **Country** — ids sequential from 705; label format `Name-ISO2` (e.g. `Afghanistan-AF`, `Bangladesh-BD`).
  Consumed by `fiberItem`, `inventoryItem`, `itElectronicsFa`, `goodsReceiptNote`.
- **Currency** — code-keyed, not numeric: `AUD`, `BDT`, `EUR`, `USD`. Consumed by 10 screens including
  `booking`, `currencyConversion`, `importPiReceive`.
- **Region / Area** — appear in **no other screen anywhere in the application**. Strong evidence these are
  vestigial: the screens are broken and nothing consumes the data, so no one has noticed.
- **City** — no genuine consumer found (the one apparent match was a false positive on a CI-type select).

- **fabricsTransfer (Transfer Request)** — fully inferable from its working analogues. `inventoryTransfer`
  is the identical pattern for general items, and `fabricsTransferIssue` / `fabricsTransferReceive` both
  consume its output via `transferPopulate` + `transferCode`:

  | Aspect | Recovered definition |
  |---|---|
  | Prefix | `transfer` |
  | Endpoints | `/fabricsTransfer/index`, `/write`, `/edit`, `/remove` |
  | Header fields | `code`, `fromStore` (session store), `toStore` (select), `transactionDate`, `leadTime`, `remarks` |
  | Detail line | Item, Construction, Composition, Weave Type, Finished Width, Colour Code/Name, `transactionQty` |
  | Verbs | `transferOpen`, `transferClose`, `transferConfirm`, `transferApprove`, `transferChangeStatus`, `transferPopulate`, `transferReport`, `transferShow`, `transferReset` |
  | Grid | SL, Code, From Store, To Store, Date, Lead Time (days), Remarks, Action |

## Session context

This account has exactly **one** business unit (`ASFL Business Unit`, id 13196) and **one** store
(`Processing Store`, id 13197), so no alternate context could be tried. Switching is a POST to `/` with
`{id, unitSwitch|storeSwitch}` which **forces a logout** to re-apply — worth redesigning in the migration.

## Recommendations

1. **Remove or fix the 14 dead menu entries** — users can reach them and hit a 404. Decide per entry whether
   the function is genuinely missing or already served by a working sibling.
2. **Get the server log / stack trace for the 6 × 500** — the fix is almost certainly small and shared
   (5 of the 6 are Location/Global reference screens failing identically).
3. **Do not port Region/Area blindly.** Confirm they are used by the business before rebuilding them.
4. In the new system these become straightforward CRUD screens on the shared scaffold; the Country and
   Currency data above is enough to seed them.
