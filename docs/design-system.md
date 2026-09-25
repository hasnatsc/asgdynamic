# FABRICS LTD. ERP — Design System & UX Standards

The rules every screen follows. The **code is the source of truth**; this document explains it
and says when to use what.

| Layer | File | Holds |
|---|---|---|
| Tokens & components | `src/main/tailwind/input.css` → built to `static/css/app.css` | Every reusable class (`.btn-*`, `.field`, `.card`, `.table-grid`, `.badge`, `.steps`…) |
| Behaviour | `static/js/app.js` → `window.App` | `api`, `Grid`, `DocumentScreen`, `form`/`confirm` dialogs, `toast`, `tabs`, `fmt`, `status`, `icon`, command palette |
| Brand & icons | `static/images/` | `logo.png` (full colour), `logo-reverse.png` (on red), `logo-mark.png` (favicon, rail, mobile), `icons.svg` sprite |
| Server fragments | `templates/fragments/ui.html` | `status(s)` badge, `workflow(s)` rail — each with an `App.*` twin |
| Shell | `templates/layout/main.html` + `web/Navigation.java` | Sidebar (icons, sections, rail), header, breadcrumb, operating context |
| Dashboard | `templates/home.html` + `web/ManufacturingFlow.java` | KPI tiles, order-to-delivery flow, modules |

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
| Brand | `brand-500` = logo red `#ED1C24` | Logo, active-nav marker, KPI icons, large brand surfaces (sign-in panel, welcome band) |
| Primary action | `brand-600` `#D7141B` (hover `700`) | Primary button, current workflow step. One step darker than the logo so white 14 px text passes AA (5.2:1; the logo red is 4.4:1) |
| Links / active text | `brand-700` | Card links, document numbers, active nav label |
| Neutral | `gray-*` (custom near-neutral ramp in `tailwind.config.js`; `slate` is retired - its blue fights the red) | Text (`900` headings, `700` body, `500` secondary, `400` placeholder), borders `200`, canvas `50` |
| Success | `emerald` | Approved, completed, active, "Can sign in" |
| Warning / awaiting | `amber` | Submitted (awaiting someone), pending, soft limits |
| Danger | `red` (destructive buttons `red-700`) | Rejected, locked, errors. The brand is red too, so danger **always** carries an icon or a word |
| In progress / info | `sky` (pale) / `violet` | Partial fulfilment, info alerts / with counterparty; special access (e.g. "All rows") |

Brand values live only in `tailwind.config.js`. Never write a hex colour in a template.

**Type scale** — `.page-title` (22 px/semibold) → `.card-title` (15 px) / `.section-title` (sm/semibold) →
body `text-sm` → `.hint`/metadata `text-xs` → `.kpi-label`/`.legend`/table headers (xs, uppercase,
tracking-wide; table headers are sentence case, not uppercase). One `h1` per page.

**Spacing & shape** — page gutter `px-4 sm:px-6 lg:px-8`, card padding `p-5`, grid gaps
`gap-4`–`gap-6`. Controls are 36 px high (`.btn`, `.field`), table rows 44 px. Radius: `rounded-lg`
controls, `rounded-xl` cards, `rounded-2xl` dialogs, `rounded-full` badges/chips/steps. Elevation:
a 1 px `gray-200` border plus `shadow-card` for surfaces; `shadow-float` only for dialogs, menus,
toasts and the drawer.

**Icons** — one stroke set (`static/images/icons.svg`, Lucide-derived, 1.75 px):
`<svg class="icon"><use href="/images/icons.svg#name"/></svg>` in templates (use `th:href="@{…}"`),
`App.icon('name', 'classes')` in scripts. Screen icons come from `Navigation.iconFor(Screen)`, so
sidebar, dashboard and command palette always agree. Icons are neutral gray, red only when active.

**Dark mode** — class-based (`.dark` on `<html>`, toggled by `App.theme`). Every component in
`input.css` has a `.dark` override in the second `@layer base` block. **Adding a component
means adding its dark override in the same change.**

---

## 3. Application shell & information architecture

- **Sidebar** is generated from `Screen` + the user's `VIEW` authorities — never hand-edit a
  menu. A section appears only when it has a screen the user may open.
- **Section order follows the business flow**, order-to-cash then support functions. Current
  sections are the first ones below; new modules slot in at their place:

  `Master data → Sales → Inventory → Production → Quality → Purchase → Commercial → Finance → HR → Reports → Administration`

  Declaration order of `Screen.Section` is menu order (`SETUP` is labelled *Master data*). Sections
  collapse (remembered per user in `localStorage`); on desktop the sidebar also collapses to an
  icon rail. The active screen gets `brand-50` fill, `brand-700` text and a 3 px red edge marker.

- **Labels** are nouns, sentence case, and match the page `h1` (`Bulk production order`, not
  `BPO Screen`). Sub-pages of one concept become a `group` item (see Fabric setup, Security).
- **Header** carries: menu toggle (< lg), breadcrumb (from the same nav model via
  `Navigation.trail`), command palette (`Search screens…`, Ctrl+K), operating context (unit + store),
  theme toggle, user menu. Anything added here must be global and real - no notification bell
  until there are notifications.
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

### B. Document screen (transactional) — *reference: `fabric/booking.html`*

For anything with a `DocumentType`: bookings, BPOs, work orders, receipts, requisitions, POs,
PIs, LCs, invoices.

One page per document type: the **register** (archetype A) wired by `App.DocumentScreen`, a
**review drawer** that opens on row click, and a **create editor** below the list that stays
`hidden` until *New …* is pressed.

```html
<div class="card overflow-hidden" data-doc-screen>
  <div class="toolbar">[data-filter=search] [data-filter=status] [data-filter=from|to] [data-filter-reset]</div>
  <table id="bookingTable" class="table-grid table-stack">
    <th data-col="documentNo" data-format="doc" data-sort="documentNo">…   <!-- doc|date|num|status|actions -->
  </table>
  <div class="pager" data-pager></div>
</div>
<form id="bookingForm" class="card" hidden>… .form-section … .form-actions</form>
<script>new App.DocumentScreen({ kind: 'Booking', api: '/api/booking', table: 'bookingTable',
                                 form: 'bookingForm', revise: true })</script>
```

The **drawer** (`<dialog class="drawer">`) shows the workflow rail, header facts, spec cards with
their colour lines, the approval history (`/api/documents/{id}/history`) and the actions the status
allows. It reads the type's `GET {api}/{id}` payload, so a new document type needs no drawer code.

The **editor**:

```
┌ card header    title · workflow rail (fragments/ui :: workflow)
├ header fields  party · dates · currency · references            (grid 1 → 4 cols)
├ line groups    fabric spec (entered once) + .table-edit colour lines, "Add colour"
├ totals         <dl class="totals">                               (server-computed)
└ form actions   [Save draft] [Cancel]  ……  totals                (.form-actions, sticky)
```

Rules:
- The workflow rail sits in every document form's header:
  `<th:block th:replace="~{fragments/ui :: workflow(*{status})}"></th:block>`.
- Sections are `.form-section` blocks (title via `.form-section-title`); spec groups are
  `.row-card`s with a `.table-lines`/`.table-edit` colour grid.
- **Actions follow `BusinessDocumentStatus.allowedNext()`**: Save only when `isEditable()`,
  Submit on `DRAFT`, Approve/Reject on `SUBMITTED`, Raise revision when `isCommitted()` and the
  type `isRevisable()`. Everything else hidden, not disabled.
- Submit / Approve / Reject / Raise revision live in the review drawer, not the editor.
- Footer order = dialog-footer order: secondary on the left, **primary last (right)**.
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

- A welcome band (greeting by the user's clock, date and unit on the brand-red panel).
- A KPI row (`.kpi` with `.kpi-icon`, max 5 across on xl). **Every KPI is a link** to the list it
  counts, shown only with `sec:authorize` on that screen's VIEW authority. Show the actionable
  sub-number (`3 drafts`, `2 locked`).
- The **order-to-delivery flow** (`.flow`, from `ManufacturingFlow`): planning → material
  requirement → material issue → production → QC → packing → finished goods → delivery. A stage
  links only built screens the user may open; unbuilt stages say *Planned*.
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
| Partial | sky (pale) | Being fulfilled |
| Processing | violet (pale) | With a counterparty |
| Completed | green (solid) | Fully fulfilled |
| Closed | gray (solid) | Archived; nothing further |
| Cancelled | grey, struck through | Void |

Grids and the drawer add a leading dot (`.badge-dot`). Non-document states reuse the `badge-*`
helpers with the same meaning: Active = `badge-green`,
Inactive = `badge-gray`, Pending = `badge-amber`, Locked/Error = `badge-red`. **Colour is never
the only signal** — badges always carry text.

---

## 6. Component catalogue

| Need | Use | Notes |
|---|---|---|
| Buttons | `.btn-primary` `.btn-ghost` `.btn-subtle` `.btn-danger` `.btn-danger-ghost` `.btn-icon`, `+ .btn-sm`/`.btn-lg` | One primary per region. Icon buttons need `aria-label` + `title`. |
| Inputs | `.field`, `.label` (+ `<span class="req">*</span>`), `.hint`, `.error-text`, `.checkbox`, `.input-icon` | Label above, hint below. `readonly` = computed. |
| Grouping | `.card` `.card-header` `.card-title` `.card-sub` `.card-link`, `.form-section` (+`-head`/`-title`/`-sub`), `.form-grid`, `.fieldset` + `.legend`, `.section-title`, `.row-card` (+`-head`) | |
| Tables | `.table-grid`, `.table-lines`/`.table-edit` (inline entry), `.table-stack` (mobile cards; `App.Grid` fills `data-label`), `.num`, `.doc-no` | Wrap in `.table-wrap`. |
| List chrome | `.toolbar`, `.pager`, `.chip`/`.chip-count` | Pager is rendered by `App.Grid`. |
| Status | `.badge` + `data-status`, `.badge-*`, `.badge-brand`, `.badge-dot` | See §5. |
| Workflow | `.steps` > `.step` (`is-done`/`is-current`/`is-failed`) > `.step-dot` | Via `fragments/ui :: workflow`; JS twin in the drawer. |
| Totals | `<dl class="totals">` | Server values only. |
| Document actions | `.form-actions` (section-built forms) / `.action-bar` (last child of a `.card p-5` form) | Sticky at the viewport bottom. |
| Messages | `.alert-{error,success,warn,info}` inline; `App.toast(msg, type)` transient | Errors: server's sentence, via `App.fail`. |
| Dialogs | `<dialog class="modal modal-{lg,xl}">` or `<dialog class="drawer">` + `.modal-head/title/body/foot`; `App.form`, `App.confirm` | `data-sticky` on editors so a stray click can't lose work. Drawer = review alongside the list. |
| Tabs | `.tabs` / `.tab[role=tab]` + `[data-panel]`, `App.tabs(root)` | Count badges in tab labels. |
| Empty | `.empty` + `.empty-icon` `.empty-title` `.empty-text` (`.empty-state` alias) | Say why it's empty and what to do next. `App.Grid` renders it (`emptyText`, `emptyIcon`). |
| Loading | `.skeleton` (Grid does this) | No spinners over whole pages. |
| KPI | `.kpi` `.kpi-icon` `.kpi-label` `.kpi-value` `.kpi-meta` | Always a link. |
| Menus | `details[data-menu]` + `.menu` `.menu-item` (`-danger`) | Closes on outside click. |
| View switch | `.segmented` | Inside toolbars, instead of tabs. |
| Flow | `.flow` `.flow-stage` (`is-live`) | Dashboard process strip. |

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

**Every table looks the same because the look lives in one place.** A template writes only
`class="table-grid"`; `input.css` supplies compact 44 px rows, 14 px `gray-800` text, uppercase
headers and zebra rows. Never add per-page table classes or colours.

- **View first.** A row click and the **View** button open the record read-only; only **Edit**
  (shown with AMEND) opens it for changes. Record dialogs call
  `App.viewMode(dialog, mode === 'view', { canEdit: CAN.amend })` at the end of `openEditor()`:
  fields lock (and read as values), save/delete/add/remove hide, Cancel reads Close, and an Edit
  button switches the same dialog to edit mode. Screens without a dialog editor use
  `App.viewRecord({ title, fields, canEdit, onEdit })`. Deep links open in view mode (`?view=`).
- **Row buttons** come only from `App.rowActions(...)` with `App.recordButtons(id, canAmend)` (View,
  plus Edit with AMEND) and `App.rowButton(label, icon, attrs)`: bordered, icon + word, always
  visible. The header cell reads `<th class="w-px">Actions</th>`. Any table whose rows carry
  `.row-actions` pins that column to the right edge automatically, so Edit never scrolls away.
- **One line per row**: secondary facts go inline in gray (`name · SKU 123`), not on a second
  line; badge groups use `.badge-list` (no wrap). A wide table scrolls sideways instead.
- Line-entry grids (`.table-lines`, `.table-edit`) stay plain (no zebra, no hover).

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
| ≥ 1024 (desktop) | Fixed sidebar (collapsible to a 72 px icon rail); full grids; content capped at 1600 px |

## 11. Accessibility checklist

- [ ] Every input has a `<label for>` (or `aria-label` in toolbars).
- [ ] Icon-only buttons have `aria-label`; decorative SVGs `aria-hidden="true"`.
- [ ] Status never conveyed by colour alone.
- [ ] Focus visible on every control (`focus-visible:ring-*` is built into components).
- [ ] Errors use `role="alert"`; toasts live in an `aria-live` region (done by `App.toast`).
- [ ] Contrast ≥ 4.5:1 for text in both themes — don't put `gray-400` on body copy, and don't put
      white body text on `brand-500` (use `brand-600`).
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
- [ ] No `[[` inside a template's script (write `[ [`): Thymeleaf reads it as an inline expression
      and the whole page fails to render.
- [ ] Rows open in view mode; Edit is the only way into the editable form.

## 15. Known gaps (as of 2026-09-25)

- The eight `fabric/*` document screens have working lists, filters and the review drawer
  (submit / approve / reject / revise). The **create editor is not wired**: lookups
  (`data-lookup`) are empty, line add/remove buttons do nothing, and Save shows a notice instead
  of posting. Wiring it (JSON `POST {api}`) is the next step.
- `DashboardService` returns 0 for bookings and production orders until those counts exist.
- Copy mixes "Color" and "Colour", and Title Case column headers remain on fabric line grids.
