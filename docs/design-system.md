# ASG FabricERP — Design System & UX Standards

The rules every screen follows. The **code is the source of truth**; this document explains it
and says when to use what.

| Layer | File | Holds |
|---|---|---|
| Tokens & components | `src/main/tailwind/input.css` → built to `static/css/app.css` | Every reusable class (`.btn-*`, `.field`, `.card`, `.table-grid`, `.badge`, `.steps`…) |
| Behaviour | `static/js/app.js` → `window.App` | `api`, `Grid`, `form`/`confirm` dialogs, `toast`, `tabs`, `fmt`, `status`, command palette |
| Server fragments | `templates/fragments/ui.html` | `status(s)` badge, `workflow(s)` rail — each with an `App.*` twin |
| Shell | `templates/layout/main.html` + `web/Navigation.java` | Sidebar, header, operating context |

Build CSS after touching `input.css` or adding classes in a template: `npm run css:build`
(`css:dev` watches).

---

## 1. Principles

1. **Reuse before you invent.** If a class exists, use it. A new pattern goes into `input.css`
   with a comment, and into this file, before a second screen uses it. No one-off
   `text-xl font-semibold` headers, no page-local colour choices.
2. **The server is the gate; the UI tells the truth.** Hiding a button is a courtesy, not
   security — every route keeps its `@PreAuthorize`. The reverse also holds: never show UI that
   has nothing behind it (a notification dot with no notifications, a filter that doesn't
   filter, an Approve button on a draft).
3. **Computed values are shown, never typed.** GSM, line amounts, totals, document numbers come
   from the server. Render them `readonly` (styled automatically) or as text.
4. **Dense, but legible.** ERP users live in these screens all day. `text-sm` body, `text-xs`
   metadata, tabular numbers, compact `.table-edit` for line entry — but never below 12 px, and
   touch targets stay ≥ 32 px (44 px on shop-floor screens).
5. **Keyboard first.** `Ctrl+K` opens the command palette, `/` focuses the page search, Enter
   submits dialogs, Esc closes them, sortable headers are focusable. Don't break these.
6. **One status vocabulary.** A document status looks the same on every screen, in every grid,
   in light and dark mode (§5).
7. **Accessible by default.** Real `<label for>`, native `<dialog>`, `aria-sort`, `aria-current`,
   `role="alert"` on errors, focus rings on everything interactive (§11).

---

## 2. Foundations

**Colour roles** — use the role, not a hue.

| Role | Tailwind | Used for |
|---|---|---|
| Brand / primary action | `brand-600` (hover `700`) | Primary button, active nav, current workflow step, links |
| Neutral | `slate-*` | Text (`900` headings, `700` body, `500` secondary, `400` placeholder), borders `200`, surfaces `50` |
| Success | `emerald` | Approved, completed, active, "Can sign in" |
| Warning / awaiting | `amber` | Submitted (awaiting someone), pending, soft limits |
| Danger | `red` | Rejected, locked, errors, destructive buttons |
| In progress | `brand` (pale) / `violet` | Partial fulfilment / with counterparty; special access (e.g. "All rows") |

`brand` is still a placeholder ramp in `tailwind.config.js` — swap in ASG's values there only.

**Type scale** — `.page-title` (xl/semibold) → `.card-title`/`.section-title` (sm/semibold) →
body `text-sm` → `.hint`/metadata `text-xs` → `.kpi-label`/`.legend`/table headers (xs, uppercase,
tracking-wide). One `h1` per page.

**Spacing & shape** — page gutter `px-4 sm:px-6 py-6`, card padding `p-4` (lists) / `p-5`
(forms), grid gaps `gap-3`–`gap-4`, sections `space-y-5`. Radius: `rounded-md` controls,
`rounded-lg` cards, `rounded-xl` dialogs, `rounded-full` badges/chips/steps. Elevation:
`shadow-sm ring-1` for cards; `shadow-2xl` only for dialogs.

**Dark mode** — class-based (`.dark` on `<html>`, toggled by `App.theme`). Every component in
`input.css` has a `.dark` override in the second `@layer base` block. **Adding a component
means adding its dark override in the same change.**

---

## 3. Application shell & information architecture

- **Sidebar** is generated from `Screen` + the user's `VIEW` authorities — never hand-edit a
  menu. A section appears only when it has a screen the user may open.
- **Section order follows the business flow**, order-to-cash then support functions. Current
  sections are the first ones below; new modules slot in at their place:

  `Sales → Production → Quality → Inventory → Purchase → Commercial → Finance → HR → Reports → Setup → Administration`

- **Labels** are nouns, sentence case, and match the page `h1` (`Bulk production order`, not
  `BPO Screen`). Sub-pages of one concept become a `group` item (see Fabric setup, Security).
- **Header** carries: menu toggle (< lg), operating context (unit + store badges), command
  palette, theme toggle, user menu. Anything added here must be global and real.
- **Home** = dashboard (§4.D). It links to screens; it doesn't duplicate them.

---

## 4. Page archetypes

Every screen is one of these. Pick the archetype first, then fill it.

### A. Register (list) page — *reference: `setup/users.html`, `setup/parties.html`*

```html
<div class="page-header">
  <div><h1 class="page-title">…</h1><p class="page-sub">One sentence: what this is for.</p></div>
  <button class="btn-primary">New …</button>          <!-- one primary action, top right -->
</div>
<div class="card overflow-hidden">
  <div class="…chips row">…</div>                      <!-- optional: segment filter with counts -->
  <div class="toolbar">search [data-search] · filters (aria-label) · Apply</div>
  <div class="overflow-x-auto"><table class="table-grid">…</table></div>
  <div id="…Pager" class="pager"></div>
</div>
```

Wired with `new App.Grid({ url, table, columns, search, pager, params, onRowClick })`. Row
click opens the record; the last column holds row actions (`<th class="w-px"><span
class="sr-only">Actions</span></th>`). Export (CSV) is a `.btn-ghost` beside the primary action.

### B. Document editor (transactional) — *reference: `fabric/booking.html`*

For anything with a `DocumentType`: bookings, BPOs, work orders, receipts, requisitions, POs,
PIs, LCs, invoices.

```
┌ workflow rail  Draft › Submitted › Approved › Completed          (fragments/ui :: workflow)
├ header fields  party · dates · currency · references            (grid 1 → 4 cols)
├ line groups    fabric spec (entered once) + .table-edit colour lines, "Add colour"
├ totals         <dl class="totals">                               (server-computed)
└ action bar     [Raise revision]  ……  [Reject] [Approve] [Submit] [Save]   (.action-bar, sticky)
```

Rules:
- The workflow rail sits at the top of every document form: `<ol th:replace="~{fragments/ui ::
  workflow(*{status})}"></ol>`.
- **Actions follow `BusinessDocumentStatus.allowedNext()`**: Save only when `isEditable()`,
  Submit on `DRAFT`, Approve/Reject on `SUBMITTED`, Raise revision when `isCommitted()` and the
  type `isRevisable()`. Everything else hidden, not disabled.
- Action bar order = dialog-footer order: secondary on the left, **primary last (right)**.
- Reject and Cancel always go through `App.form` with a required *reason* field.
- Committed documents are read-only; changes go through **Raise revision**, never in-place edits.
- Drawing from a parent document (BPO ← Booking, DO ← RPI) uses a "From …" picker that loads
  open balances; drawn quantities show the remaining balance next to the input.
- Long forms: break into `<fieldset class="fieldset"><legend class="legend">…` groups; in a
  dialog, use `.tabs` with counts (see Parties).

### C. Master data (setup) — *reference: `setup/parties.html`, `inventory/items.html`*

Register page (A) + editor in a `<dialog class="modal modal-xl" data-sticky>` with `.tabs`
for sub-collections (addresses, contacts, accounts) using `.row-card` repeaters. Codes are
`font-mono uppercase`, fixed once saved (say so in a `.hint`). Deactivate, don't delete, once a
record is referenced; show usage as a badge in the dialog header.

### D. Dashboard — *reference: `home.html`*

- A KPI row (`.kpi`, max 5 across on xl). **Every KPI is a link** to the list pre-filtered to what
  it counts. Show the actionable sub-number (`3 drafts`, `2 locked`).
- Below: **work queues before charts** — "Awaiting my approval", "Overdue deliveries",
  "Work orders due this week" as compact `table-grid`s with 5–10 rows and a "View all" link.
- Charts only where a trend drives a decision (daily greige output vs plan, delivery
  on-time %). Follow the `dataviz` skill; never a pie for more than 4 parts.

### E. Approval inbox

A cross-type register of documents in `SUBMITTED` that the user may approve. Columns: type,
number, party, amount, maker, submitted (`fmt.timeTag`), age. Row click opens the document
editor (B) where the decision is made — the inbox never approves blind. Four-eyes is enforced
server-side (`ApprovalService`); the UI simply doesn't offer Approve on your own documents.

### F. Shop-floor / production entry (greige receive, roll inspection, loom output)

Used on tablets beside the machine. One task per screen, big targets (`py-3 text-base`, ≥ 44 px),
`inputmode="decimal"` on quantities, scan-first (barcode/roll number field autofocused), a
running tally visible at all times, Save → toast → field cleared for the next roll. Works at
768 px portrait without horizontal scrolling.

### G. Report

Toolbar with parameters (period, unit, party, status) → **Run** → `table-grid` with a totals
row (`<tfoot>` using `.num`) → Export CSV / Print. Parameters live in the URL query so a
report can be bookmarked and shared. Print: hide shell with `print:hidden`, keep the table.

---

## 5. Status & colour semantics

`BusinessDocumentStatus` is rendered **only** through `fragments/ui :: status(s)` (server) or
`App.status(s)` (client). Both emit `<span class="badge" data-status="…">`; the colour (keyed on `[data-status]`) lives in
one place in `input.css`.

| Status | Look | Meaning to the user |
|---|---|---|
| Draft | grey | Being prepared by the maker |
| Submitted | amber | Waiting for someone else |
| Rejected | red | Back with the maker — see reason |
| Approved | green (pale) | Committed; downstream may draw on it |
| Partial | blue (pale) | Being fulfilled |
| Processing | violet (pale) | With a counterparty |
| Completed | green (solid) | Fully fulfilled |
| Closed | slate (solid) | Archived; nothing further |
| Cancelled | grey, struck through | Void |

Non-document states reuse the `badge-*` helpers with the same meaning: Active = `badge-green`,
Inactive = `badge-gray`, Pending = `badge-amber`, Locked/Error = `badge-red`. **Colour is never
the only signal** — badges always carry text.

---

## 6. Component catalogue

| Need | Use | Notes |
|---|---|---|
| Buttons | `.btn-primary` `.btn-ghost` `.btn-subtle` `.btn-danger` `.btn-icon`, `+ .btn-sm` | One primary per region. Icon buttons need `aria-label` + `title`. |
| Inputs | `.field`, `.label`, `.hint`, `.checkbox` | Label above, hint below. `readonly` = computed. |
| Grouping | `.card` `.card-header` `.card-title`, `.fieldset` + `.legend`, `.section-title` | |
| Tables | `.table-grid`, `.table-edit` (inline entry), `.table-stack` (mobile cards, simple lists only), `.num` | Wrap in `.overflow-x-auto`. |
| List chrome | `.toolbar`, `.pager`, `.chip`/`.chip-count` | Pager is rendered by `App.Grid`. |
| Status | `.badge` + `data-status`, `.badge-*` | See §5. |
| Workflow | `.steps` `.step` (`is-done`/`is-current`/`is-failed`) `.step-sep` | Via `fragments/ui :: workflow`. |
| Totals | `<dl class="totals">` | Server values only. |
| Document actions | `.action-bar` | Last child of a `.card p-5` form. |
| Messages | `.alert-{error,success,warn,info}` inline; `App.toast(msg, type)` transient | Errors: server's sentence, via `App.fail`. |
| Dialogs | `<dialog class="modal modal-{lg,xl}">` + `.modal-head/body/foot`; `App.form`, `App.confirm` | `data-sticky` on editors so a stray click can't lose work. |
| Tabs | `.tabs` / `.tab[role=tab]` + `[data-panel]`, `App.tabs(root)` | Count badges in tab labels. |
| Empty | `.empty-state` + `.empty-title` | Say why it's empty and what to do next. |
| Loading | `.skeleton` (Grid does this) | No spinners over whole pages. |
| KPI | `.kpi` `.kpi-label` `.kpi-value` | Always a link. |

---

## 7. Forms

- Layout: `grid grid-cols-1 gap-4 sm:grid-cols-{2|4|6}`; span wide fields (`sm:col-span-2`).
  Order fields in the sequence the paper document / user's head uses: party → dates → currency →
  references → remarks.
- Required fields get `required`; validation is native first (`reportValidity`), then the
  server's message via `App.fail`. Never a generic "Error occurred".
- **Units in the label**, not the value: `Quantity (Yds)`, `Price / Mtr`, `Finish width (in)`.
- Numbers: `type="number"` with the domain step (quantity `0.0001`, rate `0.000001`),
  right-aligned, `.num` in read-only cells. Dates: `dd MMM yyyy` server-side, `App.fmt.date`
  client-side, `fmt.timeTag` for "3 min ago" with exact time in the tooltip.
- Lookups: `<select data-lookup="…">` for short lists; searchable picker for parties/items.
- Unsaved changes: editors track `dirty` and confirm before closing (see Users).

## 8. Tables

- Text left, **numbers right with `.num`** (a bare `text-right` on a `<th>` is overridden by
  `.table-grid`), status as a badge, dates via `fmt`. Codes/numbers `font-mono`.
- Sortable columns carry `data-sort="field"`; the Grid sets `aria-sort`.
- Default page size 25; options 10/25/50/100.
- Empty text says what's missing: "No bookings match these filters".
- Line-entry grids (`.table-edit`) scroll sideways on small screens rather than wrapping.

## 9. Workflows & approvals

- Lifecycle is `BusinessDocumentStatus`; transitions live in `allowedNext()` and nowhere else.
- Maker → checker → approver through the generic `/api/documents/{id}/submit|approve|reject`.
- Irreversible actions (approve, cancel, delete, unlock) → `App.confirm({ danger: true })` naming
  the object: "Cancel booking BKAF000017?". Reject/Cancel collect a reason.
- After an action: toast the outcome, reload the grid row, update the workflow rail.

## 10. Responsive

| Width | Behaviour |
|---|---|
| < 640 (phone) | Single-column forms; toolbars wrap; simple lists may use `.table-stack`; document editing is read-mostly |
| 640–1023 (tablet) | Sidebar off-canvas; 2–4 column forms; shop-floor screens (§4.F) designed here |
| ≥ 1024 (desktop) | Fixed sidebar; full grids; content capped at `max-w-screen-2xl` |

## 11. Accessibility checklist

- [ ] Every input has a `<label for>` (or `aria-label` in toolbars).
- [ ] Icon-only buttons have `aria-label`; decorative SVGs `aria-hidden="true"`.
- [ ] Status never conveyed by colour alone.
- [ ] Focus visible on every control (`focus-visible:ring-*` is built into components).
- [ ] Errors use `role="alert"`; toasts live in an `aria-live` region (done by `App.toast`).
- [ ] Contrast ≥ 4.5:1 for text in both themes — don't put `slate-400` on body copy.
- [ ] Dialogs are native `<dialog>` opened with `showModal()` (focus trap + Esc for free).

## 12. Content style

- Sentence case everywhere: titles, buttons, column headers (`Lab dip ref.`, not `Lab Dip Ref.`).
- Buttons are verbs: `New booking`, `Raise revision`, `Add colour`. Primary in a dialog says what
  happens: `Save user`, not `OK`.
- `page-sub` is one sentence of purpose, not instructions.
- Document types use the names in `DocumentType` (`Bulk production order`, `Greige fabrics received`).

## 13. Module blueprints

| Module | Archetypes | Key screens / documents |
|---|---|---|
| **Sales** | A+B, E | Booking → BPO → Request for PI → Delivery order → Fabrics delivery; sales return |
| **Production** | A+B, F, D | Rout card, weaving WO, processing WO, greige receive/issue, finished fabrics receive; loom & dyeing dashboards |
| **Quality** | F, A+B, G | Roll inspection (4-point), lab dip / strike-off approval, shade band; defect & rejection reports |
| **Inventory / Store** | A+B, C, G | Items & yarn masters (C); requisition, issue, receive, transfer, adjustment (B); stock ledger & ageing (G) |
| **Purchase** | A+B, E | SPR / consumption SPR → PO → MRR → purchase return (no RFQ — contract buying) |
| **Commercial** | A+B | Export/import PI, LC, back-to-back LC, commercial invoice, debit/credit note |
| **Finance** | A+B, G, D | Vouchers, party ledgers, receivables vs LC maturity; trial balance & P&L reports |
| **HR** | C, A+B, G | Employees (as parties), attendance, leave; headcount reports |
| **Reports** | G | Cross-module, parameterised, exportable |
| **Setup / Admin** | C, A | Parties, fabric attributes, numbering, users, roles, access log |

## 14. Before you merge a screen

- [ ] Picked an archetype (§4) and matched its reference screen's structure.
- [ ] Only classes from `input.css`/Tailwind utilities for layout — no new colours or ad-hoc components.
- [ ] Statuses via `fragments/ui :: status` / `App.status`.
- [ ] Actions match `allowedNext()`; primary action last; destructive actions confirmed.
- [ ] Numbers `.num`, units in labels, computed fields readonly.
- [ ] Checked at 375 px, 768 px and ≥ 1280 px, in light **and** dark mode.
- [ ] Keyboard pass: Tab through, Enter/Esc in dialogs, `/` and `Ctrl+K` still work.
- [ ] `npm run css:build` run and `app.css` committed.

## 15. Known gaps (as of 2026-09-25)

- The eight `fabric/*` document screens now follow archetype B's markup, but have **no script
  yet**: the grid, filters, `data-action` buttons and lookups are unwired, and the action bar
  still shows every button regardless of status. Wiring them with `App.Grid` + `allowedNext()` is
  the next step.
- The editor currently sits under the list on the same page; target is list → open document
  (own route `/booking/{id}`, or `modal-xl` like Parties). Decide once for all document types.
- `brand` colours are placeholders.
- Copy mixes "Color" and "Colour", and Title Case column headers remain on fabric line grids.
