/*
 * Purchase and store documents - store and purchase requisitions, purchase orders, MRRs and
 * returns, material issues, direct receives, transfer requests/issues/receives, stock adjustments
 * and fabric transfers.
 *
 * One script for every screen, driven by window.SUPPLY (SupplyDocumentController.page):
 *   - the list is App.DocumentScreen;
 *   - the viewer shows the document, where each line stands downstream, what it was posted at, and
 *     the actions its state allows (post, submit, approve, cancel, short-close, raise the next step);
 *   - the editor either lists a parent's open lines with what each still allows, or takes items
 *     (or fabric lots) added directly. The server decides: every quantity is checked against its
 *     parent line and every issue against what the store holds.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const CFG = window.SUPPLY;
    const STEP = CFG.step;
    const { api, esc, fmt, toast, fail, icon } = App;
    const $ = (sel, root) => (root || document).querySelector(sel);
    const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));
    const nf = new Intl.NumberFormat(undefined, { maximumFractionDigits: 4 });
    const mf = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const num = v => v == null || v === '' ? '—' : nf.format(Number(v));
    const money = v => v == null || v === '' ? '—' : mf.format(Number(v));
    const filled = v => v != null && String(v).trim() !== '';
    const qtyUnit = (v, uom) => `${num(v)}${uom ? ` <span class="text-xs text-gray-500">${esc(uom)}</span>` : ''}`;
    const OPEN = ['APPROVED', 'PROCESSING', 'PARTIAL'];
    /** Steps that take stock out of a store: the editor shows what the store holds. */
    const TAKES_OUT = ['MI', 'TI', 'PRT', 'SA'];
    const FABRIC_FIELDS = [['construction', 'Construction'], ['composition', 'Composition'], ['weaveType', 'Weave'],
        ['finishType', 'Finish'], ['finishWidth', 'Width (in)'], ['gsm', 'GSM']];

    const screen = new App.DocumentScreen({
        kind: CFG.label, api: CFG.api, table: 'supplyTable', revise: false,
        canEdit: CFG.canCreate || CFG.canAmend, onEdit: doc => editor.open(doc), onView: view
    });
    // After the screen, which has read ?open= already; the page's address then becomes the list's.
    // Unsaved means lines chosen in the editor - the viewer has nothing to lose.
    const page = new App.PageEditor({
        list: $('[data-list-view]'),
        isDirty: () => page.current === editorDialog && (editor.picked.size > 0 || editor.direct.length > 0),
        onReopen: params => {
            const id = Number(params.get('open'));
            if (id) return void screen.open(id);
            if (params.has('new') && CFG.canCreate) return void editor.open(null, Number(params.get('parent')) || null);
            return false;
        }
    });

    // =========================================================================================
    // Viewer
    // =========================================================================================

    const viewer = document.getElementById('supplyViewer');

    function view(doc, history) {
        $('[data-view-title]', viewer).innerHTML = `<span class="doc-no">${esc(doc.documentNo || 'Unnumbered')}</span> ${App.statusBadge(doc.status)}`;
        const facts = [
            ['Date', fmt.date(doc.documentDate)],
            [doc.parent ? doc.parent.label : null, doc.parent && `<a class="card-link" href="/${esc(doc.parent.slug)}?open=${esc(doc.parent.id)}">${esc(doc.parent.documentNo)}</a>`, true],
            ['Supplier', doc.partyName],
            ['Purchase type', doc.purchaseTypeLabel],
            ['Requisition type', doc.requisitionTypeLabel],
            ['Department', doc.department],
            [CFG.transfer ? 'From store' : 'Store', doc.warehouseName],
            ['To store', doc.toWarehouseName],
            [STEP === 'PO' ? 'Delivery by' : 'Required by', doc.requiredDate && fmt.date(doc.requiredDate)],
            ['Lead time', filled(doc.leadTimeDays) ? `${doc.leadTimeDays} days` : null],
            ['Invoice no', doc.invoiceNo],
            [{ MRR: 'Challan no', SA: 'Count sheet no' }[STEP] || 'Reference', doc.referenceNo],
            ['Vehicle', doc.vehicleNo],
            ['Total quantity', num(doc.totalQuantity)],
            [CFG.priced && STEP !== 'SA' ? 'Amount' : null, CFG.priced && `${money(doc.subtotalAmount)} ${esc(doc.currency || '')}`, true],
            [doc.currency && doc.currency !== 'BDT' ? 'Rate to BDT' : null, num(doc.exchangeRate)],
            [doc.postedValue != null ? 'Stock value (BDT)' : null, money(doc.postedValue)]
        ].filter(([label, value]) => label && value != null && value !== '')
            .map(([label, value, html]) => `<div><dt class="text-xs text-gray-500">${esc(label)}</dt>
                <dd class="mt-0.5 font-medium text-gray-900 dark:text-white">${html ? value : esc(value)}</dd></div>`).join('');

        const children = (doc.children || []).length ? `<section class="form-section">
                <div class="form-section-head"><h3 class="form-section-title">Raised against it</h3>
                    <span class="text-xs text-gray-500">${doc.children.length} document(s)</span></div>
                <div class="flex flex-wrap gap-2">${doc.children.map(c => `<a class="chip" href="${c.slug ? `/${esc(c.slug)}?open=${esc(c.id)}` : '#'}">
                    <span class="text-gray-500">${esc(c.label)}</span> <span class="font-mono">${esc(c.documentNo)}</span> ${App.statusBadge(c.status)}</a>`).join('')}</div>
            </section>` : '';

        $('[data-view-body]', viewer).innerHTML = `
            <section class="form-section">${App.statusSteps(doc.status)}</section>
            <section class="form-section"><dl class="grid grid-cols-2 gap-4 text-sm sm:grid-cols-3 lg:grid-cols-5">${facts}</dl>
                ${doc.remarks ? `<p class="mt-4 whitespace-pre-line text-sm text-gray-600 dark:text-gray-300"><span class="text-gray-500">Remarks:</span> ${esc(doc.remarks)}</p>` : ''}
            </section>
            <section class="form-section">
                <div class="form-section-head"><h3 class="form-section-title">Lines</h3>
                    <span class="text-xs text-gray-500">${(doc.lines || []).length} line(s)</span></div>
                ${lineTable(doc)}
            </section>
            ${children}
            <section class="form-section">
                <div class="form-section-head"><h3 class="form-section-title">History</h3></div>
                ${screen.historyHtml(history)}
            </section>`;

        const own = [];
        if (doc.postable) own.push(`<button type="button" class="btn-primary" data-supply-action="post">${icon('check')}Post</button>`);
        if (doc.closable) own.push(`<button type="button" class="btn-ghost" data-supply-action="close">${icon('lock')}Close ${esc(CFG.label.toLowerCase())}</button>`);
        if (doc.cancellable) own.push(`<button type="button" class="btn-danger-ghost" data-supply-action="cancel">${icon('x-circle')}Cancel</button>`);
        if (doc.deletable) own.push(`<button type="button" class="btn-danger-ghost" data-supply-action="delete">${icon('trash')}Delete</button>`);
        own.push(`<button type="button" class="btn-ghost" data-supply-action="print">${icon('document')}Print</button>`);
        const next = OPEN.includes(doc.status) ? (CFG.next || [])
            .map(n => `<a class="btn-secondary" href="/${esc(n.slug)}?new=1&parent=${esc(doc.id)}">${icon(n.icon)}${esc(n.label)}</a>`) : [];
        $('[data-view-foot]', viewer).innerHTML = `<button type="button" class="btn-ghost" data-page-back>Close</button>
            <div class="flex flex-wrap justify-end gap-2">${next.join('')}${own.join('')}${screen.actionButtons(doc)}</div>`;
        page.show(viewer, `?open=${doc.id}`);
    }

    /** What a line is: item (or fabric), brand and model, specification, fabric construction. */
    function itemCell(l) {
        const title = l.itemName
            ? `<span class="font-medium">${esc(l.itemName)}</span> <span class="font-mono text-xs text-gray-500">${esc(l.itemCode || '')}</span>`
            : `<span class="font-mono">${esc(l.construction || '')}</span> <span class="font-medium">${esc(l.colorName || '')}</span>`;
        const meta = [l.brandName, l.modelName, l.originCountry && `Origin ${l.originCountry}`, l.lotLabel].filter(filled);
        const fabric = FABRIC_FIELDS.filter(([k]) => filled(l[k]) && !(l.itemName == null && k === 'construction'))
            .map(([k, label]) => `${label} ${esc(k === 'finishWidth' || k === 'gsm' ? num(l[k]) : l[k])}`);
        return `${title}
            ${meta.length ? `<div class="text-xs text-gray-500">${meta.map(esc).join(' · ')}</div>` : ''}
            ${fabric.length ? `<div class="text-xs text-gray-500">${fabric.join(' · ')}</div>` : ''}
            ${filled(l.specification) ? `<div class="text-xs text-gray-600 dark:text-gray-300">${esc(l.specification)}</div>` : ''}
            ${filled(l.conditionNote) ? `<div class="text-xs text-amber-700">Condition: ${esc(l.conditionNote)}</div>` : ''}`;
    }

    function lineTable(doc) {
        const lines = doc.lines || [];
        if (!lines.length) return '<p class="text-sm text-gray-500">No lines.</p>';
        const figureLabels = [...new Set(lines.flatMap(l => (l.figures || []).map(f => f.label)))];
        const showFrom = lines.some(l => l.sourceLabel);
        const showRate = CFG.priced;
        const showPosted = lines.some(l => l.postedValue != null);
        const showRolls = lines.some(l => l.rolls != null);
        const showDirection = STEP === 'SA';
        const showLanded = STEP === 'MRR' && lines.some(l => Number(l.customsDuty) + Number(l.supplementaryDuty) + Number(l.allocatedCost) > 0);
        const shortClose = doc.shortClosable;
        const closedCol = shortClose || lines.some(l => l.shortClosed);
        return `<div class="table-wrap"><table class="table-grid">
            <thead><tr><th class="w-8">#</th><th>Item</th>${showFrom ? '<th>From</th>' : ''}${showDirection ? '<th>Direction</th>' : ''}
                ${showRolls ? '<th class="text-right">Rolls</th>' : ''}<th class="text-right">Quantity</th>
                ${showRate ? `<th class="text-right">${STEP === 'SA' ? 'Unit cost' : 'Rate'}</th>` : ''}
                ${showRate && STEP !== 'SA' ? '<th class="text-right">Amount</th>' : ''}
                ${showLanded ? '<th class="text-right">Duties</th><th class="text-right">Allocated costs</th>' : ''}
                ${showPosted ? '<th class="text-right">Posted at</th><th class="text-right">Stock value</th>' : ''}
                ${figureLabels.map(f => `<th class="text-right">${esc(f)}</th>`).join('')}${closedCol ? '<th></th>' : ''}</tr></thead>
            <tbody>${lines.map(l => {
                const figs = Object.fromEntries((l.figures || []).map(f => [f.label, f.value]));
                const closed = l.shortClosed
                    ? `<span class="badge-amber" title="${esc(l.shortCloseReason)}">Short-closed ${num(l.shortClosedQuantity)}</span>`
                    : shortClose ? `<button type="button" class="btn-ghost btn-sm" data-short-close="${esc(l.id)}" data-line-name="${esc(l.itemName || l.colorName || '')}">Short-close</button>` : '';
                return `<tr>
                    <td class="tabular-nums text-gray-500">${esc(l.lineNo)}</td>
                    <td class="min-w-[14rem] whitespace-normal">${itemCell(l)}${filled(l.remarks) ? `<div class="text-xs text-gray-500">${esc(l.remarks)}</div>` : ''}</td>
                    ${showFrom ? `<td class="text-xs">${l.sourceDocumentId ? `<a class="card-link" href="/${esc(doc.parent ? doc.parent.slug : '')}?open=${esc(l.sourceDocumentId)}">${esc(l.sourceLabel)}</a>` : '—'}</td>` : ''}
                    ${showDirection ? `<td>${l.stockDirection === 'IN' ? '<span class="badge-green">Add</span>' : '<span class="badge-red">Take away</span>'}</td>` : ''}
                    ${showRolls ? `<td class="text-right tabular-nums">${num(l.rolls)}</td>` : ''}
                    <td class="text-right tabular-nums font-medium">${qtyUnit(l.quantity, l.uom)}</td>
                    ${showRate ? `<td class="text-right tabular-nums">${STEP === 'SA' && l.stockDirection !== 'IN' ? '—' : money(l.rate)}</td>` : ''}
                    ${showRate && STEP !== 'SA' ? `<td class="text-right tabular-nums">${money(l.lineAmount)}</td>` : ''}
                    ${showLanded ? `<td class="text-right tabular-nums">${money(Number(l.customsDuty) + Number(l.supplementaryDuty))}</td><td class="text-right tabular-nums">${money(l.allocatedCost)}</td>` : ''}
                    ${showPosted ? `<td class="text-right tabular-nums">${money(l.postedUnitCost)}</td><td class="text-right tabular-nums">${money(l.postedValue)}</td>` : ''}
                    ${figureLabels.map(f => `<td class="text-right tabular-nums">${num(figs[f])}</td>`).join('')}
                    ${closedCol ? `<td class="text-right">${closed}</td>` : ''}
                </tr>`;
            }).join('')}</tbody>
        </table></div>`;
    }

    viewer.addEventListener('click', async event => {
        if (event.target.closest('[data-page-back]')) return page.close();
        const docAction = event.target.closest('[data-doc-action]')?.dataset.docAction;
        if (docAction) return screen.act(docAction);
        const doc = screen.current;
        if (!doc) return;
        const shortClose = event.target.closest('[data-short-close]');
        if (shortClose) {
            const values = await App.form({
                title: `Short-close ${shortClose.dataset.lineName || 'this line'}?`,
                message: 'Its remaining balance is given up: nothing more can be raised against it, and what it held upstream is freed. Nothing is deleted.',
                fields: [{ name: 'reason', label: 'Reason', type: 'textarea', required: true, maxlength: 300 }],
                confirmText: 'Short-close', danger: true });
            if (!values) return;
            return run(`${CFG.api}/lines/${shortClose.dataset.shortClose}/short-close`, { reason: values.reason }, 'Line short-closed.');
        }
        const action = event.target.closest('[data-supply-action]')?.dataset.supplyAction;
        if (!action) return;
        if (action === 'print') return printDocument(doc);
        if (action === 'post') {
            if (!await App.confirm({ title: `Post ${doc.documentNo}?`,
                message: 'Its stock moves are written to the ledger. It cannot be edited afterwards - only cancelled, which reverses them.',
                confirmText: 'Post' })) return;
            return run(`${CFG.api}/${doc.id}/post`, null, 'Posted to stock.');
        }
        if (action === 'delete') {
            if (!await App.confirm({ title: `Delete ${doc.documentNo}?`, message: 'The draft is removed and what it drew is given back.',
                confirmText: 'Delete', danger: true })) return;
            try {
                await api(`${CFG.api}/${doc.id}`, { method: 'DELETE' });
                toast('Deleted.', 'success');
                page.close(true);
                screen.grid.reload();
            } catch (error) { fail(error); }
            return;
        }
        const dialogs = {
            cancel: ['Cancel', doc.status === 'DRAFT' || doc.status === 'REJECTED'
                ? 'The document is cancelled and what it drew is given back.'
                : CFG.posting || STEP === 'SA' ? 'Its stock moves are reversed with exact reversing entries. Refused if what it brought in has gone on.'
                              : 'Refused if anything has been raised against it - short-close its lines instead.', 'reason', true],
            close: ['Close', 'A completed document is closed once it is settled.', 'remarks', false]
        }[action];
        const values = await App.form({ title: `${dialogs[0]} ${doc.documentNo}?`, message: dialogs[1],
            fields: [{ name: dialogs[2], label: dialogs[3] ? 'Reason' : 'Remarks (optional)', type: 'textarea', required: dialogs[3], maxlength: 300 }],
            confirmText: dialogs[0], danger: action === 'cancel' });
        if (!values) return;
        run(`${CFG.api}/${doc.id}/${action}`, values, { cancel: 'Cancelled.', close: 'Closed.' }[action]);
    });

    async function run(url, query, message) {
        try {
            const doc = await api(url, { method: 'POST', query: query || undefined });
            toast(message, 'success');
            screen.grid.reload();
            screen.open(doc.id);
        } catch (error) { fail(error); }
    }

    /** A plain printable copy - the purchase order sent to the supplier, the MRR, the gate pass. */
    function printDocument(doc) {
        const priced = CFG.priced && STEP !== 'SA';
        const rows = (doc.lines || []).map(l => `<tr><td>${esc(l.lineNo)}</td>
            <td>${esc(l.itemName || [l.construction, l.colorName].filter(Boolean).join(' '))}<br><small>${esc([l.itemCode, l.brandName, l.modelName, l.lotLabel, l.specification].filter(filled).join(' · '))}</small></td>
            <td class="n">${num(l.quantity)} ${esc(l.uom || '')}</td>${priced ? `<td class="n">${money(l.rate)}</td><td class="n">${money(l.lineAmount)}</td>` : ''}</tr>`).join('');
        const facts = [['Date', fmt.date(doc.documentDate)], ['Against', doc.parent && doc.parent.documentNo], ['Supplier', doc.partyName],
            ['Purchase type', doc.purchaseTypeLabel], ['Department', doc.department], [CFG.transfer ? 'From store' : 'Store', doc.warehouseName],
            ['To store', doc.toWarehouseName], ['Required by', doc.requiredDate && fmt.date(doc.requiredDate)], ['Invoice no', doc.invoiceNo],
            ['Reference / challan', doc.referenceNo], ['Vehicle', doc.vehicleNo], ['Currency', priced ? doc.currency : null]]
            .filter(([, v]) => filled(v)).map(([k, v]) => `<div><dt>${esc(k)}</dt><dd>${esc(v)}</dd></div>`).join('');
        const w = window.open('', '_blank');
        if (!w) return toast('Allow pop-ups to print.', 'warn');
        w.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${esc(doc.documentNo)}</title>
            <style>body{font:13px system-ui,sans-serif;margin:32px;color:#111}h1{font-size:20px;margin:0}table{width:100%;border-collapse:collapse;margin-top:16px}
            th,td{border:1px solid #999;padding:6px;text-align:left;vertical-align:top}.n{text-align:right}dl{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:16px}
            dt{color:#666;font-size:11px}dd{margin:0;font-weight:600}.sign{display:flex;justify-content:space-between;margin-top:64px}.sign div{border-top:1px solid #333;width:28%;padding-top:4px;text-align:center}</style>
            </head><body><h1>${esc(CFG.label)} - ${esc(doc.documentNo)}</h1><dl>${facts}</dl>
            <table><thead><tr><th>#</th><th>Item</th><th class="n">Quantity</th>${priced ? '<th class="n">Rate</th><th class="n">Amount</th>' : ''}</tr></thead><tbody>${rows}</tbody>
            <tfoot><tr><th colspan="2">Total</th><th class="n">${num(doc.totalQuantity)}</th>${priced ? `<th></th><th class="n">${money(doc.subtotalAmount)}</th>` : ''}</tr></tfoot></table>
            ${doc.remarks ? `<p>${esc(doc.remarks)}</p>` : ''}
            <div class="sign"><div>Prepared by</div><div>Store</div><div>${STEP === 'PO' ? 'Authorised by' : 'Received by'}</div></div>
            <script>window.onload=()=>window.print()<\/script></body></html>`);
        w.document.close();
    }

    // =========================================================================================
    // Editor
    // =========================================================================================

    const editorDialog = document.getElementById('supplyEditor');
    const form = $('[data-editor-form]', editorDialog);
    const headerEl = $('[data-editor-header]', editorDialog);
    const linesEl = $('[data-editor-lines]', editorDialog);
    const addWrap = $('[data-add-wrap]', editorDialog);
    const addSelect = $('[data-add-line]', editorDialog);

    const editor = {
        doc: null,
        parentId: null,
        parent: null,          // the parent's header, from open-lines
        rows: new Map(),       // parent line id -> open line row
        picked: new Map(),     // parent line id -> { quantity, rate, originCountry, conditionNote, rolls, remarks }
        direct: [],            // directly added lines
        stores: [],

        get mode() { return this.parentId ? 'parent' : 'direct'; },

        async open(doc, presetParent) {
            this.doc = doc || null;
            this.parentId = null;
            this.parent = null;
            this.rows = new Map();
            this.picked = new Map();
            this.direct = [];
            $('[data-editor-title]', editorDialog).textContent = doc ? `Edit ${doc.documentNo}` : `New ${CFG.label.toLowerCase()}`;
            await this.renderHeader(doc);
            page.show(editorDialog, doc ? `?open=${doc.id}` : `?new=1${presetParent ? `&parent=${presetParent}` : ''}`);
            if (doc) {
                for (const l of doc.lines || []) {
                    if (l.sourceId != null) {
                        this.picked.set(l.sourceId, { quantity: l.quantity, rate: l.rate, originCountry: l.originCountry,
                            conditionNote: l.conditionNote, rolls: l.rolls, remarks: l.remarks, customsDuty: l.customsDuty,
                            supplementaryDuty: l.supplementaryDuty });
                    } else {
                        this.direct.push(directFromLine(l));
                    }
                }
                if (doc.parent) await this.loadParent(doc.parent.id, true);
            } else if (presetParent && CFG.parentLabel) {
                const select = $('[name="parentId"]', headerEl);
                if (select) App.RemoteSelect.of(select).setValue(presetParent);
                await this.loadParent(presetParent, false);
            }
            this.renderLines();
        },

        async renderHeader(doc) {
            const d = doc || {};
            const f = [];
            const storeSelect = (name, label, required, value) => `<div><label class="label" for="se_${name}">${esc(label)}${required ? ' <span class="req">*</span>' : ''}</label>
                <select id="se_${name}" class="field" name="${name}"><option value="">${required ? 'Choose…' : 'None'}</option>
                ${this.stores.map(s => `<option value="${esc(s.id)}"${s.id === value ? ' selected' : ''}>${esc(s.name)}</option>`).join('')}</select></div>`;
            if (!this.stores.length) {
                try { this.stores = await api('/api/supply/stores'); } catch (error) { fail(error); }
            }
            if (CFG.parentLabel) {
                f.push(`<div class="sm:col-span-2"><label class="label" for="seParent">${esc(CFG.parentLabel)}${CFG.requiresParent ? ' <span class="req">*</span>' : ''}</label>
                    <select id="seParent" class="field" name="parentId" ${doc ? 'disabled' : ''} ${CFG.requiresParent ? '' : 'data-allow-clear'}></select>
                    ${CFG.requiresParent ? '' : `<p class="hint">Or leave it empty and add items directly.</p>`}</div>`);
            }
            if (STEP === 'SR') {
                f.push(`<div><label class="label" for="seReqType">Requisition type</label><select id="seReqType" class="field" name="requisitionType">
                    ${CFG.requisitionTypes.map(t => `<option value="${esc(t.value)}"${(d.requisitionType || 'DEPARTMENTAL') === t.value ? ' selected' : ''}>${esc(t.label)}</option>`).join('')}</select></div>`);
                f.push(`<div><label class="label" for="seDept">Department</label><input id="seDept" class="field" name="department" maxlength="100" value="${esc(d.department || '')}"></div>`);
            }
            if (STEP === 'PO') {
                f.push(`<div class="sm:col-span-2"><label class="label" for="seSupplier">Supplier <span class="req">*</span></label>
                    <select id="seSupplier" class="field" name="supplierId" data-remote="/api/parties/lookup?role=SUPPLIER" data-placeholder="Choose the supplier…"></select></div>`);
                f.push(`<div><label class="label" for="sePoType">Purchase type</label><select id="sePoType" class="field" name="purchaseType">
                    ${CFG.purchaseTypes.map(t => `<option value="${esc(t.value)}"${(d.purchaseType || 'DIRECT') === t.value ? ' selected' : ''}>${esc(t.label)}</option>`).join('')}</select></div>`);
                f.push(`<div class="grid grid-cols-2 gap-2"><div><label class="label" for="seCurrency">Currency</label>
                    <input id="seCurrency" class="field uppercase" name="currencyCode" maxlength="3" value="${esc(d.currency || 'BDT')}"></div>
                    <div><label class="label" for="seFx">Rate to BDT</label><input id="seFx" type="number" min="0" step="0.000001" class="field text-right" name="exchangeRate" value="${esc(d.exchangeRate ?? 1)}"></div></div>`);
            }
            // Stores
            if (CFG.transfer) {
                const fromParent = STEP === 'TRC' || STEP === 'FTR';
                if (fromParent) {
                    f.push(`<div><p class="label">From → to</p><p class="field bg-gray-50 dark:bg-gray-800" data-store-display>${esc(d.warehouseName ? `${d.warehouseName} → ${d.toWarehouseName}` : 'As on the transfer issue')}</p></div>`);
                } else {
                    f.push(storeSelect('warehouseId', 'From store', true, d.warehouseId ?? (doc ? null : CFG.defaultStoreId)));
                    f.push(storeSelect('toWarehouseId', 'To store', true, d.toWarehouseId));
                }
            } else if (STEP === 'PRT') {
                f.push(`<div><p class="label">Store</p><p class="field bg-gray-50 dark:bg-gray-800" data-store-display>${esc(d.warehouseName || 'Where the MRR received it')}</p></div>`);
            } else if (CFG.needsStore || STEP === 'SPR' || STEP === 'PO') {
                f.push(storeSelect('warehouseId', STEP === 'PO' || STEP === 'SPR' ? 'Deliver to store' : 'Store', CFG.needsStore,
                    d.warehouseId ?? (doc || !CFG.needsStore ? null : CFG.defaultStoreId)));
            }
            f.push(`<div><label class="label" for="seDate">Date</label><input id="seDate" type="date" class="field" name="documentDate" value="${esc(d.documentDate || new Date().toISOString().slice(0, 10))}"></div>`);
            if (['SR', 'SPR', 'PO', 'ST'].includes(STEP)) {
                f.push(`<div><label class="label" for="seRequired">${STEP === 'PO' ? 'Delivery by' : 'Required by'}</label><input id="seRequired" type="date" class="field" name="requiredDate" value="${esc(d.requiredDate || '')}"></div>`);
                f.push(`<div><label class="label" for="seLead">Lead time (days)</label><input id="seLead" type="number" min="0" step="1" class="field text-right" name="leadTimeDays" value="${esc(d.leadTimeDays ?? '')}"></div>`);
            }
            if (STEP === 'MRR') f.push(`<div><label class="label" for="seInvoice">Invoice no</label><input id="seInvoice" class="field" name="invoiceNo" maxlength="60" value="${esc(d.invoiceNo || '')}"></div>`);
            const refLabel = { MRR: 'Challan no', PRT: 'Return challan no', SA: 'Count sheet no', MI: 'Issue slip no', TI: 'Gate pass no', FTI: 'Gate pass no' }[STEP] || 'Reference';
            f.push(`<div><label class="label" for="seRef">${esc(refLabel)}</label><input id="seRef" class="field" name="referenceNo" maxlength="100" value="${esc(d.referenceNo || '')}"></div>`);
            if (['MRR', 'PRT', 'TI', 'FTI'].includes(STEP)) {
                f.push(`<div><label class="label" for="seVehicle">Vehicle no</label><input id="seVehicle" class="field" name="vehicleNo" maxlength="40" value="${esc(d.vehicleNo || '')}"></div>`);
            }
            f.push(`<div class="sm:col-span-2 lg:col-span-4"><label class="label" for="seRemarks">Remarks</label><input id="seRemarks" class="field" name="remarks" maxlength="1000" value="${esc(d.remarks || '')}"></div>`);
            headerEl.innerHTML = f.join('');

            const parent = $('[name="parentId"]', headerEl);
            if (parent) {
                new App.RemoteSelect(parent, { url: `${CFG.api}/parents`, placeholder: `Choose the ${CFG.parentLabel.toLowerCase()}…`,
                    allowClear: !CFG.requiresParent });
                if (doc && doc.parent) App.RemoteSelect.of(parent).setValue(doc.parent.id, doc.parent.documentNo);
                parent.addEventListener('change', async () => {
                    if (parent.value) {
                        if (this.direct.length && !await App.confirm({ title: 'Replace the lines?',
                            message: `The items added directly are removed; the lines come from the ${CFG.parentLabel.toLowerCase()}.`, confirmText: 'Replace' })) {
                            App.RemoteSelect.of(parent).setValue(null);
                            return;
                        }
                        this.direct = [];
                        this.picked = new Map();
                        await this.loadParent(Number(parent.value), false);
                    } else {
                        this.parentId = null;
                        this.parent = null;
                        this.rows = new Map();
                        this.picked = new Map();
                    }
                    this.renderLines();
                });
            }
            App.remoteSelects(headerEl);
            if (d.partyId && $('[name="supplierId"]', headerEl)) App.RemoteSelect.of($('[name="supplierId"]', headerEl)).setValue(d.partyId, d.partyName);
            $$('select[name="warehouseId"], select[name="toWarehouseId"]', headerEl).forEach(s => s.addEventListener('change', () => this.renderLines()));
            const currency = $('[name="currencyCode"]', headerEl);
            if (currency) currency.addEventListener('input', () => {
                const fx = $('[name="exchangeRate"]', headerEl);
                if (currency.value.toUpperCase() === 'BDT') fx.value = 1;
            });
        },

        async loadParent(id, keepPicks) {
            try {
                const data = await api(`${CFG.api}/open-lines`, { query: { parentId: id, exclude: this.doc ? this.doc.id : undefined } });
                this.parentId = id;
                this.parent = data.parent;
                this.rows = new Map((data.lines || []).map(r => [r.sourceId, r]));
                if (!keepPicks) this.picked = new Map();
                // A store document raised against a transfer or an order starts in that document's store.
                const store = $('select[name="warehouseId"]', headerEl);
                if (store && !store.value && data.parent.warehouseId) store.value = data.parent.warehouseId;
                const to = $('select[name="toWarehouseId"]', headerEl);
                if (to && !to.value && data.parent.toWarehouseId) to.value = data.parent.toWarehouseId;
                const display = $('[data-store-display]', headerEl);
                if (display && data.parent.warehouseName) {
                    display.textContent = data.parent.toWarehouseName ? `${data.parent.warehouseName} → ${data.parent.toWarehouseName}` : data.parent.warehouseName;
                }
                if (STEP === 'TI' || STEP === 'FTI') {
                    [store, to].forEach(s => { if (s) s.disabled = true; });
                }
                if (!this.rows.size) toast(`Nothing is left open on ${data.parent.documentNo} for a ${CFG.label.toLowerCase()}.`, 'warn');
            } catch (error) { fail(error); }
        },

        renderLines() {
            const sub = $('[data-lines-sub]', editorDialog);
            const direct = this.mode === 'direct';
            $('[data-select-all-wrap]', editorDialog).hidden = direct || !this.rows.size;
            addWrap.hidden = !direct || !CFG.direct;
            if (direct) {
                sub.textContent = CFG.requiresParent ? `Choose the ${CFG.parentLabel.toLowerCase()}; its open lines are listed with what each still allows.`
                    : CFG.fabricLots ? 'Add the fabric lots to move from the store; only what is free (not held for a delivery order) can go.'
                    : TAKES_OUT.includes(STEP) ? 'Add the items; the store\'s stock of each is shown, and nothing more than it holds can go out.'
                    : 'Add the items.';
                prepareAddSelect();
                linesEl.innerHTML = this.direct.length ? this.directTable() : '<p class="text-sm text-gray-500">Nothing added yet.</p>';
            } else {
                sub.textContent = `The open lines of ${this.parent ? this.parent.documentNo : 'the document'}, with what each still allows.`;
                linesEl.innerHTML = this.rows.size ? this.parentTable() : '<p class="text-sm text-gray-500">Nothing open to raise against.</p>';
            }
            this.total();
        },

        parentTable() {
            const rows = [...this.rows.values()];
            const showStock = rows.some(r => r.stock != null);
            const extraHead = { PO: '<th class="num">Rate</th>', MRR: '<th>Origin</th><th>Condition</th>', FTR: '<th class="num">Rolls</th>' }[STEP] || '';
            const dutyHead = importReceipt() ? '<th class="num">Customs duty (BDT)</th><th class="num">Suppl. duty (BDT)</th>' : '';
            return `<div class="table-wrap"><table class="line-colours line-colours-edit">
                <thead><tr><th class="w-8"></th><th>Item</th><th class="num">${esc(this.parent ? 'On ' + this.parent.documentNo : 'Ordered')}</th>
                    <th class="num">Taken</th><th class="num">Open</th>${showStock ? '<th class="num">In store</th>' : ''}
                    ${CFG.priced && STEP !== 'PO' ? '<th class="num">Rate</th>' : ''}<th class="num">Quantity</th>${extraHead}${dutyHead}</tr></thead>
                <tbody>${rows.map(r => {
                    const p = this.picked.get(r.sourceId);
                    const on = !!p;
                    const open = Number(r.available);
                    const input = (f, type, attrs, value, cls) => `<input type="${type}" class="field field-sm ${cls || (type === 'number' ? 'w-24 text-right' : 'w-32')}" data-f="${f}" ${attrs || ''}
                        value="${esc(value ?? '')}"${on ? '' : ' disabled'}>`;
                    const extra = {
                        PO: fromImportPi() ? `<td class="num">${money(r.rate)}</td>` : `<td class="num">${input('rate', 'number', 'min="0" step="0.0001"', p ? p.rate : '')}</td>`,
                        MRR: `<td>${input('originCountry', 'text', 'maxlength="60"', p ? p.originCountry : r.originCountry)}</td><td>${input('conditionNote', 'text', 'maxlength="300"', p ? p.conditionNote : '', 'w-40')}</td>`,
                        FTR: `<td class="num">${input('rolls', 'number', 'min="0" step="1"', p ? p.rolls : r.rolls)}</td>`
                    }[STEP] || '';
                    return `<tr data-source="${esc(r.sourceId)}" class="${on ? '' : 'opacity-70'}">
                        <td><input type="checkbox" class="checkbox" data-pick${on ? ' checked' : ''}${!on && open <= 0 ? ' disabled' : ''} aria-label="Include"></td>
                        <td class="min-w-[14rem] whitespace-normal">${itemCell(r)}</td>
                        <td class="num">${qtyUnit(r.cap, r.uom)}</td>
                        <td class="num">${num(r.taken)}</td>
                        <td class="num font-medium ${open <= 0 ? 'text-gray-400' : ''}">${num(r.available)}</td>
                        ${showStock ? `<td class="num ${Number(r.stock) <= 0 ? 'text-red-600' : ''}">${num(r.stock)}</td>` : ''}
                        ${CFG.priced && STEP !== 'PO' ? `<td class="num">${money(r.rate)}</td>` : ''}
                        <td class="num"><input type="number" min="0" step="0.0001" class="field field-sm w-28 text-right" data-f="quantity"
                            value="${esc(p ? p.quantity : '')}"${on ? '' : ' disabled'} ${open > 0 ? `max="${esc(r.available)}"` : ''}></td>
                        ${extra}
                        ${importReceipt() ? `<td class="num">${input('customsDuty', 'number', 'min="0" step="0.01"', p ? p.customsDuty : '')}</td>
                            <td class="num">${input('supplementaryDuty', 'number', 'min="0" step="0.01"', p ? p.supplementaryDuty : '')}</td>` : ''}
                    </tr>`;
                }).join('')}</tbody></table></div>`;
        },

        directTable() {
            const showStock = this.direct.some(e => e.stock != null || e.free != null);
            const priced = CFG.priced;
            return `<div class="table-wrap"><table class="line-colours line-colours-edit">
                <thead><tr><th>${CFG.fabricLots ? 'Fabric lot' : 'Item'}</th>${showStock ? `<th class="num">${CFG.fabricLots ? 'Free' : 'In store'}</th>` : ''}
                    ${STEP === 'SA' ? '<th>Direction</th>' : ''}<th class="num">Quantity</th>
                    ${priced ? `<th class="num">${STEP === 'SA' || STEP === 'MR' ? 'Unit cost' : 'Rate'}</th>` : ''}
                    ${CFG.fabricLots ? '<th class="num">Rolls</th>' : '<th>Specification</th>'}<th class="w-px"></th></tr></thead>
                <tbody>${this.direct.map((e, i) => {
                    const fabricRow = !CFG.fabricLots && e.itemType === 'FABRICS' && ['SPR', 'PO', 'MR'].includes(STEP)
                        ? `<tr data-index="${i}" class="bg-gray-50/60 dark:bg-gray-800/40"><td colspan="${6 + (showStock ? 1 : 0)}">
                            <div class="flex flex-wrap items-center gap-2 text-xs"><span class="text-gray-500">Fabric</span>
                            ${FABRIC_FIELDS.map(([k, label]) => `<input class="field field-sm ${k === 'gsm' || k === 'finishWidth' ? 'w-20 text-right' : 'w-36'}"
                                ${k === 'gsm' || k === 'finishWidth' ? 'type="number" min="0" step="0.01"' : 'maxlength="120"'} placeholder="${esc(label)}"
                                aria-label="${esc(label)}" data-fabric="${k}" value="${esc(e.fabric[k] ?? '')}">`).join('')}</div></td></tr>` : '';
                    return `<tr data-index="${i}">
                        <td class="min-w-[14rem] whitespace-normal"><span class="font-medium">${esc(e.label)}</span>
                            ${e.sub ? `<div class="text-xs text-gray-500">${esc(e.sub)}</div>` : ''}</td>
                        ${showStock ? `<td class="num ${Number(e.stock ?? e.free) <= 0 ? 'text-red-600' : ''}">${qtyUnit(e.stock ?? e.free, e.uom)}</td>` : ''}
                        ${STEP === 'SA' ? `<td><select class="field field-sm w-32" data-f="stockDirection">
                            <option value="IN"${e.stockDirection === 'IN' ? ' selected' : ''}>Add</option>
                            <option value="OUT"${e.stockDirection !== 'IN' ? ' selected' : ''}>Take away</option></select></td>` : ''}
                        <td class="num"><input type="number" min="0" step="0.0001" class="field field-sm w-28 text-right" data-f="quantity" value="${esc(e.quantity ?? '')}"></td>
                        ${priced ? `<td class="num"><input type="number" min="0" step="0.0001" class="field field-sm w-28 text-right" data-f="rate"
                            value="${esc(e.rate ?? '')}" ${STEP === 'SA' && e.stockDirection !== 'IN' ? 'disabled' : ''}
                            placeholder="${STEP === 'SA' || STEP === 'MR' ? 'Average' : ''}"></td>` : ''}
                        ${CFG.fabricLots ? `<td class="num"><input type="number" min="0" step="1" class="field field-sm w-20 text-right" data-f="rolls" value="${esc(e.rolls ?? '')}"></td>`
                            : `<td><input class="field field-sm w-48" maxlength="500" data-f="specification" value="${esc(e.specification ?? '')}"></td>`}
                        <td><button type="button" class="btn-icon btn-sm" data-remove="${i}" aria-label="Remove">${icon('trash')}</button></td>
                    </tr>${fabricRow}`;
                }).join('')}</tbody></table></div>`;
        },

        total() {
            let qty = 0, amount = 0;
            const lines = this.mode === 'direct' ? this.direct : [...this.picked.entries()].filter(([id]) => this.rows.has(id))
                .map(([id, p]) => Object.assign({ rate: STEP === 'PO' ? p.rate : this.rows.get(id).rate }, p));
            for (const l of lines) {
                qty += Number(l.quantity) || 0;
                amount += (Number(l.quantity) || 0) * (Number(l.rate) || 0);
            }
            $('[data-editor-total]', editorDialog).textContent = nf.format(qty);
            const amountEl = $('[data-editor-amount]', editorDialog);
            if (amountEl) amountEl.textContent = mf.format(amount);
        },

        payload() {
            const v = name => $(`[name="${name}"]`, headerEl)?.value || null;
            const n = x => x === '' || x == null ? null : Number(x);
            const lines = this.mode === 'direct'
                ? this.direct.map(e => ({ itemId: e.itemId || null, lotId: e.lotId || null, quantity: n(e.quantity), rate: n(e.rate),
                    rolls: n(e.rolls), specification: e.specification || null, stockDirection: STEP === 'SA' ? (e.stockDirection || 'OUT') : null,
                    remarks: e.remarks || null,
                    fabric: e.itemType === 'FABRICS' && Object.values(e.fabric || {}).some(filled) ? {
                        construction: e.fabric.construction || null, composition: e.fabric.composition || null,
                        weaveType: e.fabric.weaveType || null, finishType: e.fabric.finishType || null,
                        finishWidth: n(e.fabric.finishWidth), gsm: n(e.fabric.gsm) } : null }))
                : [...this.picked.entries()].filter(([id]) => this.rows.has(id)).map(([id, p]) => ({
                    sourceId: Number(id), quantity: n(p.quantity), rate: STEP === 'PO' ? n(p.rate) : null, rolls: n(p.rolls),
                    customsDuty: n(p.customsDuty), supplementaryDuty: n(p.supplementaryDuty),
                    originCountry: p.originCountry || null, conditionNote: p.conditionNote || null, remarks: p.remarks || null }));
            return {
                id: this.doc ? this.doc.id : null,
                documentDate: v('documentDate'), requiredDate: v('requiredDate'),
                warehouseId: n(v('warehouseId')), toWarehouseId: n(v('toWarehouseId')),
                supplierId: n(v('supplierId')), purchaseType: v('purchaseType'), requisitionType: v('requisitionType'),
                department: v('department'), currencyCode: v('currencyCode'), exchangeRate: n(v('exchangeRate')),
                leadTimeDays: n(v('leadTimeDays')), referenceNo: v('referenceNo'), invoiceNo: v('invoiceNo'),
                vehicleNo: v('vehicleNo'), remarks: v('remarks'), lines
            };
        }
    };

    /** A purchase order raised from a supplier's import PI: priced by the PI, not typed. */
    const fromImportPi = () => editor.parent && editor.parent.documentType === 'IMPORT_PROFORMA_INVOICE';
    /** An MRR against an import order carries the duties paid at the port. */
    const importReceipt = () => STEP === 'MRR' && editor.parent && editor.parent.purchaseType === 'IMPORT';

    /** A saved direct line, back in the editor. */
    function directFromLine(l) {
        return {
            itemId: l.itemId, lotId: CFG.fabricLots ? l.lotId : null, itemType: l.itemType, uom: l.uom,
            label: CFG.fabricLots ? [l.construction, l.colorName, l.lotLabel].filter(filled).join(' · ') : `${l.itemCode} · ${l.itemName}`,
            sub: [l.brandName, l.modelName].filter(filled).join(' · '),
            quantity: l.quantity, rate: l.rate, rolls: l.rolls, specification: l.specification, stockDirection: l.stockDirection,
            remarks: l.remarks,
            fabric: Object.fromEntries(FABRIC_FIELDS.map(([k]) => [k, l[k]]))
        };
    }

    /** The "add a line" picker: items (with the store's stock), or the fabric lots the store holds. */
    let addRemote = null;
    function prepareAddSelect() {
        if (!CFG.direct) return;
        if (!addRemote) {
            addRemote = new App.RemoteSelect(addSelect, {
                url: CFG.fabricLots ? '/api/supply/fabric-lots' : '/api/supply/items',
                placeholder: CFG.fabricLots ? 'Add a fabric lot…' : 'Add an item…',
                params: () => {
                    const store = Number($('select[name="warehouseId"]', headerEl)?.value) || undefined;
                    if (CFG.fabricLots) return { warehouseId: store };
                    return { warehouseId: store, stockOnly: ['MI', 'TI'].includes(STEP), stockItems: !['SPR', 'PO'].includes(STEP) };
                }
            });
            addSelect.addEventListener('change', () => {
                const o = addRemote.selected;
                if (!o) return;
                addRemote.setValue(null);
                const key = CFG.fabricLots ? `L:${o.id}` : `I:${o.id}`;
                if (editor.direct.some(e => (CFG.fabricLots ? `L:${e.lotId}` : `I:${e.itemId}`) === key)) {
                    return toast(`${o.text} is already on the list.`, 'warn');
                }
                editor.direct.push(CFG.fabricLots
                    ? { lotId: o.id, label: o.text, sub: o.sub, uom: o.uom, free: o.free, quantity: o.free, rolls: o.rolls, fabric: {} }
                    : { itemId: o.id, label: o.text, sub: o.sub, uom: o.uom, itemType: o.itemType, stock: o.stock,
                        rate: ['PO', 'MR'].includes(STEP) && o.costPrice ? o.costPrice : '', stockDirection: STEP === 'SA' ? 'OUT' : null,
                        quantity: '', fabric: {} });
                editor.renderLines();
            });
        }
    }

    linesEl.addEventListener('change', event => {
        const tr = event.target.closest('tr');
        if (!tr) return;
        if (tr.dataset.source) {
            const id = Number(tr.dataset.source);
            if (event.target.matches('[data-pick]')) {
                if (event.target.checked) {
                    const r = editor.rows.get(id);
                    const defaultQty = Math.max(0, Math.min(Number(r.available) || 0,
                        TAKES_OUT.includes(STEP) && r.stock != null ? Number(r.stock) : Infinity));
                    editor.picked.set(id, { quantity: defaultQty > 0 ? Math.round(defaultQty * 10000) / 10000 : '',
                        rate: STEP === 'PO' && !fromImportPi() ? '' : r.rate, originCountry: r.originCountry, rolls: r.rolls });
                } else {
                    editor.picked.delete(id);
                }
                editor.renderLines();
                return;
            }
            const f = event.target.dataset.f;
            if (f && editor.picked.has(id)) { editor.picked.get(id)[f] = event.target.value; editor.total(); }
            return;
        }
        if (tr.dataset.index != null) {
            const e = editor.direct[Number(tr.dataset.index)];
            if (!e) return;
            if (event.target.dataset.fabric) { e.fabric[event.target.dataset.fabric] = event.target.value; return; }
            const f = event.target.dataset.f;
            if (!f) return;
            e[f] = event.target.value;
            if (f === 'stockDirection') editor.renderLines(); else editor.total();
        }
    });
    linesEl.addEventListener('input', event => {
        const tr = event.target.closest('tr');
        const f = event.target.dataset.f;
        if (!tr || (f !== 'quantity' && f !== 'rate')) return;
        if (tr.dataset.source && editor.picked.has(Number(tr.dataset.source))) editor.picked.get(Number(tr.dataset.source))[f] = event.target.value;
        else if (tr.dataset.index != null && editor.direct[Number(tr.dataset.index)]) editor.direct[Number(tr.dataset.index)][f] = event.target.value;
        editor.total();
    });
    linesEl.addEventListener('click', event => {
        const b = event.target.closest('[data-remove]');
        if (!b) return;
        editor.direct.splice(Number(b.dataset.remove), 1);
        editor.renderLines();
    });
    $('[data-select-all]', editorDialog).addEventListener('change', event => {
        for (const [id, r] of editor.rows) {
            if (event.target.checked && !editor.picked.has(id) && Number(r.available) > 0) {
                const qty = Math.min(Number(r.available), TAKES_OUT.includes(STEP) && r.stock != null ? Number(r.stock) : Infinity);
                editor.picked.set(id, { quantity: qty > 0 ? qty : '', rate: STEP === 'PO' && !fromImportPi() ? '' : r.rate, originCountry: r.originCountry, rolls: r.rolls });
            } else if (!event.target.checked) {
                editor.picked.delete(id);
            }
        }
        editor.renderLines();
    });

    form.addEventListener('submit', async event => {
        event.preventDefault();
        const body = editor.payload();
        if (!body.lines.length) return toast(editor.mode === 'direct' ? 'Add at least one line.' : 'Tick at least one line.', 'warn');
        for (const name of ['warehouseId', 'toWarehouseId']) {
            const el = $(`select[name="${name}"]`, headerEl);
            if (el && !el.disabled && $(`label[for="${el.id}"] .req`, headerEl) && !el.value) {
                el.focus();
                return toast('Choose the store.', 'warn');
            }
        }
        const button = $('[data-editor-save]', editorDialog);
        button.disabled = true;
        try {
            const saved = await api(CFG.api, { method: 'POST', body });
            toast(`${saved.documentNo} saved as a draft.`, 'success');
            editor.picked = new Map();              // saved: nothing left to lose on the way to the viewer
            editor.direct = [];
            screen.grid.reload();
            screen.open(saved.id);
        } catch (error) {
            fail(error);
        } finally {
            button.disabled = false;
        }
    });
    $$('[data-editor-close]', editorDialog).forEach(b => b.addEventListener('click', () => page.close()));

    $$('[data-supply-new]').forEach(b => b.addEventListener('click', () => editor.open(null)));

    // Raised from a parent document: /mrr?new=1&parent=12
    const params = page.initial;
    if (params.get('new') && CFG.canCreate) editor.open(null, Number(params.get('parent')) || null);
});
