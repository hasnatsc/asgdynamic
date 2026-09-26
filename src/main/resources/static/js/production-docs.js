/*
 * Production chain documents - production order, weaving and dyeing work orders, greige receive
 * and issue, finished receive, delivery schedule, delivery order, fabrics delivery.
 *
 * One script for the nine screens, driven by window.CHAIN (ChainDocumentController.page):
 *   - the list is App.DocumentScreen;
 *   - the viewer shows the document, where each line stands downstream, and the actions its state
 *     allows (post, submit, approve, cancel, short-close, close batch, raise the next step);
 *   - the editor lists the parent's open lines with what each still allows. The server decides:
 *     every quantity is checked against its parent line's own stream and refused over the cap.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const CFG = window.CHAIN;
    const STEP = CFG.step;
    const { api, esc, fmt, toast, fail, icon } = App;
    const $ = (sel, root) => (root || document).querySelector(sel);
    const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));
    const nf = new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 });
    const num = v => v == null || v === '' ? '—' : nf.format(Number(v));
    const qtyUnit = (v, uom) => `${num(v)}${uom ? ` <span class="text-xs text-gray-500">${esc(uom)}</span>` : ''}`;
    const OPEN = ['APPROVED', 'PROCESSING', 'PARTIAL'];
    const MONEY = ['BPO', 'RPI', 'DO', 'FD'];

    // Item and fabric specification of a line (ChainViews.fabricOf) - blank values are left out.
    const SPEC = [
        ['declaredConstruction', 'PI construction'], ['epiPpi', 'EPI × PPI'], ['warpCount', 'Warp count'], ['weftCount', 'Weft count'],
        ['warpYarnName', 'Warp yarn'], ['weftYarnName', 'Weft yarn'], ['weave', 'Weave'], ['composition', 'Composition'],
        ['finishType', 'Finish'], ['finishWidth', 'Finish width'], ['cuttableWidth', 'Cuttable width'], ['gsmText', 'GSM'],
        ['shrinkage', 'Shrinkage (warp × weft)'], ['washType', 'Wash'], ['lightSource', 'Light source'], ['selvedge', 'Selvedge'],
        ['endUse', 'End use'], ['costingCode', 'Costing no'], ['qualityReference', 'Quality ref.'], ['styleReference', 'Style ref.'],
        ['itemDescription', 'Description', true]
    ];
    const filled = v => v != null && String(v).trim() !== '';
    const itemLabel = g => {
        const parts = [g.itemCode, g.itemName].filter(filled);
        return parts.length === 2 && parts[0] === parts[1] ? parts[0] : parts.join(' · ');
    };
    function specValues(g) {
        const wash = [filled(g.gsmBeforeWash) && `before wash ${num(g.gsmBeforeWash)}`, filled(g.gsmAfterWash) && `after wash ${num(g.gsmAfterWash)}`].filter(Boolean);
        return Object.assign({}, g, {
            epiPpi: filled(g.epi) || filled(g.ppi) ? `${num(g.epi)} × ${num(g.ppi)}` : null,
            weave: [g.weaveType, g.weaveStyle].filter(filled).join(' · ') || null,
            gsmText: filled(g.gsm) ? `${num(g.gsm)}${wash.length ? ` (${wash.join(', ')})` : ''}` : (wash.join(', ') || null),
            finishWidth: filled(g.finishWidth) ? num(g.finishWidth) : null,
            cuttableWidth: filled(g.cuttableWidth) ? num(g.cuttableWidth) : null
        });
    }
    /** The spec panel of a line card; `skip` leaves out what the card already shows elsewhere. */
    function specFacts(g, skip, extraClass) {
        const v = specValues(g);
        const facts = SPEC.filter(([key]) => !(skip || []).includes(key) && filled(v[key]));
        if (!facts.length) return '';
        return `<dl class="spec-facts border-t border-gray-100 px-4 py-3 dark:border-gray-800 ${extraClass || ''}">${facts.map(([key, label, wide]) =>
            `<div${wide ? ' class="is-wide"' : ''}><dt>${esc(label)}</dt><dd>${esc(v[key])}</dd></div>`).join('')}</dl>`;
    }
    /** A colour's references under its name: colour ref, lab dip, strike-off, loom, style, specification. */
    function colourDetail(l, construction) {
        const refs = [
            filled(l.colorReference) && ['Colour ref.', l.colorReference],
            filled(l.labDip) && ['Lab dip', l.labDip],
            filled(l.strikeOffReference) && ['Strike-off', l.strikeOffReference],
            filled(l.loomReference) && ['Loom', l.loomReference],
            filled(l.fabricsStyle) && l.fabricsStyle !== construction && ['Style', l.fabricsStyle]
        ].filter(Boolean);
        return (refs.length ? `<div class="mt-0.5 flex flex-wrap gap-x-3 text-xs text-gray-500 dark:text-gray-400">${refs.map(([k, v]) =>
                `<span>${esc(k)} <span class="text-gray-700 dark:text-gray-300">${esc(v)}</span></span>`).join('')}</div>` : '')
            + (filled(l.colorSpecification) ? `<div class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">${esc(l.colorSpecification)}</div>` : '');
    }
    /** The colour name with its code. */
    const colourName = l => `<span class="font-medium">${esc(l.colorName || '—')}</span>${filled(l.colorCode)
        ? ` <span class="ml-1 rounded bg-gray-100 px-1.5 py-0.5 font-mono text-[11px] text-gray-600 dark:bg-gray-800 dark:text-gray-300">${esc(l.colorCode)}</span>` : ''}`;
    /** For a line that takes a whole fabric line (greige woven per construction): the colours it covers. */
    function coveredColours(list) {
        if (!list || !list.length) return '';
        return `<ul class="mt-1.5 space-y-1">${list.map(c => `<li class="flex flex-wrap items-baseline gap-x-2 text-xs">
            <span>${colourName(c)}</span><span class="tabular-nums text-gray-500">${num(c.quantity)}</span>
            ${colourDetail(c) ? `<div class="basis-full">${colourDetail(c)}</div>` : ''}</li>`).join('')}</ul>`;
    }

    // What can be raised from an open document of this step: [slug, label, icon, condition(doc)].
    const NEXT = {
        BPO: [['weaving-wo', 'Weaving WO', 'loom'], ['processing-wo', 'Dyeing WO', 'dyeing', d => (d.lineGroups || []).some(g => g.route && g.route.needsProcessing)],
              ['requestforpi', 'Delivery schedule', 'calendar']],
        WWO: [['greige-receive', 'Greige receive', 'receive']],
        PWO: [['greige-issue', 'Greige issue', 'transfer', d => !d.batchClosed], ['finished-receive', 'Finished receive', 'packing', d => !d.batchClosed]],
        RPI: [['delivery-order', 'Delivery order', 'clipboard-check']],
        DO: [['fabrics-delivery', 'Fabrics delivery', 'truck']]
    };

    const screen = new App.DocumentScreen({
        kind: CFG.label, api: CFG.api, table: 'chainTable', revise: CFG.revisable,
        canEdit: CFG.canCreate || CFG.canAmend, onEdit: doc => editor.open(doc), onView: view
    });

    // =========================================================================================
    // Viewer
    // =========================================================================================

    const viewer = document.getElementById('chainViewer');

    function view(doc, history) {
        $('[data-view-title]', viewer).innerHTML = `<span class="doc-no">${esc(doc.documentNo || 'Unnumbered')}</span> ${App.statusBadge(doc.status)}`
            + (doc.revisionNo ? ` <span class="badge-gray">Revision ${esc(doc.revisionNo)}</span>` : '')
            + (doc.batchClosed ? ' <span class="badge-violet">Batch closed</span>' : '');
        const facts = [
            ['Date', fmt.date(doc.documentDate)],
            ['Required', doc.requiredDate && fmt.date(doc.requiredDate)],
            [doc.parent ? doc.parent.label : null, doc.parent && `<a class="card-link" href="/${esc(doc.parent.slug)}?open=${esc(doc.parent.id)}">${esc(doc.parent.documentNo)}</a>`, true],
            ['Buyer', doc.partyName],
            ['Marketing team', doc.marketingTeamName],
            ['Store', doc.warehouseName],
            ['Process', doc.processKind],
            ['Vendor', doc.vendorName],
            [{ GR: 'Challan no', FFR: 'Challan no', GI: 'Batch no', RPI: 'PI reference', FD: 'Challan / gate pass' }[STEP] || 'Reference', doc.referenceNo],
            ['Garments', doc.garmentsName],
            ['Delivery address', doc.garmentsAddress],
            ['Vehicle', doc.vehicleNo],
            ['Driver', doc.driverName],
            ['Total quantity', num(doc.totalQuantity)],
            [MONEY.includes(STEP) ? 'Amount' : null, MONEY.includes(STEP) && `${num(doc.subtotalAmount)} ${esc(doc.currency || '')}`, true]
        ].filter(([label, value]) => label && value != null && value !== '')
            .map(([label, value, html]) => `<div><dt class="text-xs text-gray-500">${esc(label)}</dt>
                <dd class="mt-0.5 font-medium text-gray-900 dark:text-white">${html ? value : esc(value)}</dd></div>`).join('');

        const groups = (doc.lineGroups || []).map(g => groupCard(doc, g)).join('');
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
                <div class="form-section-head"><h3 class="form-section-title">Lines</h3></div>
                <div class="space-y-3">${groups || '<p class="text-sm text-gray-500">No lines.</p>'}</div>
            </section>
            ${children}
            <section class="form-section">
                <div class="form-section-head"><h3 class="form-section-title">History</h3></div>
                ${screen.historyHtml(history)}
            </section>`;

        const own = [];
        if (doc.postable) own.push(`<button type="button" class="btn-primary" data-chain-action="post">${icon('check')}Post</button>`);
        if (doc.batchClosable) own.push(`<button type="button" class="btn-ghost" data-chain-action="close-batch">${icon('lock')}Close batch</button>`);
        if (doc.closable) own.push(`<button type="button" class="btn-ghost" data-chain-action="close">${icon('lock')}Close ${esc(CFG.label.toLowerCase())}</button>`);
        if (doc.cancellable) own.push(`<button type="button" class="btn-danger-ghost" data-chain-action="cancel">${icon('x-circle')}Cancel</button>`);
        if (doc.deletable) own.push(`<button type="button" class="btn-danger-ghost" data-chain-action="delete">${icon('trash')}Delete</button>`);
        if (STEP === 'FD' && doc.status === 'APPROVED') own.push(`<button type="button" class="btn-ghost" data-chain-action="print">${icon('document')}Challan</button>`);
        if (STEP === 'DO' && OPEN.includes(doc.status)) own.push(`<button type="button" class="btn-ghost" data-chain-action="print">${icon('document')}Print</button>`);
        const next = OPEN.includes(doc.status) ? (NEXT[STEP] || []).filter(([, , , when]) => !when || when(doc))
            .map(([slug, label, ic]) => `<a class="btn-secondary" href="/${slug}?new=1&parent=${doc.id}">${icon(ic)}${esc(label)}</a>`) : [];
        $('[data-view-foot]', viewer).innerHTML = `<button type="button" class="btn-ghost" data-close>Close</button>
            <div class="flex flex-wrap justify-end gap-2">${next.join('')}${own.join('')}${screen.actionButtons(doc)}</div>`;
        if (!viewer.open) viewer.showModal();
    }

    function groupCard(doc, g) {
        const r = g.route;
        const meta = [g.fabricType, r && r.label, r && (r.greigeKey === 'CONSTRUCTION' ? 'Woven per fabric line' : 'Woven per colour'),
            r && (r.deliverStage === 'FINISHED' ? 'Delivered finished' : 'Delivered as greige'),
            r && r.yarnPrep && r.yarnPrep !== 'None' && `Needs ${r.yarnPrep.toLowerCase()}`,
            g.dispoReference && `DISPO ${g.dispoReference}`].filter(Boolean);
        const strip = [
            ['Quantity', qtyUnit(g.groupQuantity, g.uom)],
            STEP === 'BPO' && r ? ['Greige allowance', `${num(r.greigeAllowancePct)} %`] : null,
            STEP === 'BPO' && g.greigeQuantity != null ? ['Greige to weave', qtyUnit(g.greigeQuantity, g.uom)] : null,
            g.wovenOrdered != null ? ['On weaving WOs', qtyUnit(g.wovenOrdered, g.uom)] : null,
        ].filter(Boolean);
        const lines = g.colorLines || [];
        const figureLabels = [...new Set(lines.flatMap(l => (l.figures || []).map(f => f.label)))];
        const showLot = lines.some(l => l.lotLabel || l.dyeLot || l.shade || l.grade);
        const showRolls = lines.some(l => l.rolls != null);
        const showDate = lines.some(l => l.deliveryDate);
        const shortClose = doc.shortClosable;
        return `<article class="line-card">
            <header class="line-head">
                <span class="line-no">${esc(g.groupNo)}</span>
                <div class="min-w-0 flex-1">
                    <div class="flex min-w-0 flex-wrap items-baseline gap-x-2">
                        <h4 class="line-title font-mono">${esc(g.construction || 'No construction')}</h4>
                        ${itemLabel(g) ? `<span class="line-sub" title="Item">${esc(itemLabel(g))}</span>` : ''}
                    </div>
                    ${meta.length ? `<p class="line-meta">${meta.map(m => `<span>${esc(m)}</span>`).join('')}</p>` : ''}
                </div>
            </header>
            <dl class="line-strip">${strip.map(([k, v]) => `<div><dt>${esc(k)}</dt><dd>${v}</dd></div>`).join('')}</dl>
            ${specFacts(g)}
            <div class="table-wrap"><table class="line-colours">
                <thead><tr><th>Colour</th><th>From</th>${showLot ? '<th>Lot</th>' : ''}${showDate ? '<th>Delivery</th>' : ''}
                    ${showRolls ? '<th class="num">Rolls</th>' : ''}<th class="num">Quantity</th>
                    ${figureLabels.map(f => `<th class="num">${esc(f)}</th>`).join('')}${shortClose || lines.some(l => l.shortClosed) ? '<th></th>' : ''}</tr></thead>
                <tbody>${lines.map(l => {
                    const figs = Object.fromEntries((l.figures || []).map(f => [f.label, f.value]));
                    const lot = [l.lotLabel, !l.lotLabel && l.dyeLot && `Lot ${l.dyeLot}`, !l.lotLabel && l.shade && `Shade ${l.shade}`,
                                 !l.lotLabel && l.grade && `Grade ${l.grade}`].filter(Boolean).join(' · ');
                    const closed = l.shortClosed
                        ? `<span class="badge-amber" title="${esc(l.shortCloseReason)}">Short-closed ${num(l.shortClosedQuantity)}</span>`
                        : shortClose ? `<button type="button" class="btn-ghost btn-sm" data-short-close="${esc(l.id)}" data-line-name="${esc(l.colorName || '')}">Short-close</button>` : '';
                    return `<tr>
                        <td class="min-w-[12rem]">${colourName(l)}${colourDetail(l, g.construction)}${coveredColours(l.coversColours)}</td>
                        <td class="text-xs text-gray-600">${esc(l.sourceLabel || '—')}</td>
                        ${showLot ? `<td class="text-xs">${esc(lot || '—')}</td>` : ''}
                        ${showDate ? `<td>${esc(fmt.date(l.deliveryDate))}</td>` : ''}
                        ${showRolls ? `<td class="num">${num(l.rolls)}</td>` : ''}
                        <td class="num font-medium">${num(l.quantity)}</td>
                        ${figureLabels.map(f => `<td class="num">${num(figs[f])}</td>`).join('')}
                        ${shortClose || lines.some(x => x.shortClosed) ? `<td class="text-right">${closed}</td>` : ''}
                    </tr>`;
                }).join('')}</tbody>
            </table></div>
        </article>`;
    }

    viewer.addEventListener('click', async event => {
        const docAction = event.target.closest('[data-doc-action]')?.dataset.docAction;
        if (docAction) {
            if (docAction === 'edit') viewer.close();
            return screen.act(docAction);
        }
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
        const action = event.target.closest('[data-chain-action]')?.dataset.chainAction;
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
                viewer.close();
                screen.grid.reload();
            } catch (error) { fail(error); }
            return;
        }
        const dialogs = {
            cancel: ['Cancel', doc.status === 'DRAFT' || doc.status === 'REJECTED'
                ? 'The document is cancelled and what it drew is given back.'
                : CFG.posting ? 'Its stock moves are reversed with exact reversing entries. Refused if the stock has already gone on.'
                              : 'Refused if anything has been raised against it - short-close its lines instead.', 'reason', true],
            close: ['Close', 'A completed document is closed once commercial settlement is done.', 'remarks', false],
            'close-batch': ['Close batch', 'Every open line is closed at what was received; greige issued but not received as finished is booked as process loss.', 'remarks', false]
        }[action];
        const values = await App.form({ title: `${dialogs[0]} ${doc.documentNo}?`, message: dialogs[1],
            fields: [{ name: dialogs[2], label: dialogs[3] ? 'Reason' : 'Remarks (optional)', type: 'textarea', required: dialogs[3], maxlength: 300 }],
            confirmText: dialogs[0], danger: action === 'cancel' });
        if (!values) return;
        run(`${CFG.api}/${doc.id}/${action}`, values, { cancel: 'Cancelled.', close: 'Closed.', 'close-batch': 'Batch closed.' }[action]);
    });

    async function run(url, query, message) {
        try {
            const doc = await api(url, { method: 'POST', query: query || undefined });
            toast(message, 'success');
            screen.grid.reload();
            screen.open(doc.id);
        } catch (error) { fail(error); }
    }

    /** A plain printable copy - the challan the buyer's factory signs, or the delivery order. */
    function printDocument(doc) {
        const rows = (doc.lineGroups || []).flatMap(g => (g.colorLines || []).map(l => `<tr>
            <td>${esc(g.construction || '')}<br><small>${esc(g.fabricType || '')}</small></td><td>${esc(l.colorName || '')}</td>
            <td>${esc(l.lotLabel || '')}</td><td class="n">${l.rolls == null ? '' : esc(l.rolls)}</td>
            <td class="n">${num(l.quantity)} ${esc(g.uom || '')}</td></tr>`)).join('');
        const w = window.open('', '_blank');
        if (!w) return toast('Allow pop-ups to print.', 'warn');
        w.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${esc(doc.documentNo)}</title>
            <style>body{font:13px system-ui,sans-serif;margin:32px;color:#111}h1{font-size:20px;margin:0}table{width:100%;border-collapse:collapse;margin-top:16px}
            th,td{border:1px solid #999;padding:6px;text-align:left;vertical-align:top}.n{text-align:right}dl{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:16px}
            dt{color:#666;font-size:11px}dd{margin:0;font-weight:600}.sign{display:flex;justify-content:space-between;margin-top:64px}.sign div{border-top:1px solid #333;width:28%;padding-top:4px;text-align:center}</style>
            </head><body><h1>${esc(STEP === 'FD' ? 'Delivery challan' : CFG.label)} - ${esc(doc.documentNo)}</h1>
            <dl><div><dt>Date</dt><dd>${esc(fmt.date(doc.documentDate))}</dd></div><div><dt>Buyer</dt><dd>${esc(doc.partyName || '')}</dd></div>
            <div><dt>Against</dt><dd>${esc(doc.parent ? doc.parent.documentNo : '')}</dd></div><div><dt>Garments</dt><dd>${esc(doc.garmentsName || '')}</dd></div>
            <div><dt>Delivery address</dt><dd>${esc(doc.garmentsAddress || '')}</dd></div><div><dt>Store</dt><dd>${esc(doc.warehouseName || '')}</dd></div>
            <div><dt>Vehicle</dt><dd>${esc(doc.vehicleNo || '')}</dd></div><div><dt>Driver</dt><dd>${esc(doc.driverName || '')}</dd></div>
            <div><dt>Gate pass</dt><dd>${esc(doc.referenceNo || '')}</dd></div></dl>
            <table><thead><tr><th>Fabric</th><th>Colour</th><th>Lot</th><th class="n">Rolls</th><th class="n">Quantity</th></tr></thead><tbody>${rows}</tbody>
            <tfoot><tr><th colspan="4">Total</th><th class="n">${num(doc.totalQuantity)}</th></tr></tfoot></table>
            <div class="sign"><div>Store</div><div>Security</div><div>Received by</div></div>
            <script>window.onload=()=>window.print()<\/script></body></html>`);
        w.document.close();
    }

    // =========================================================================================
    // Editor
    // =========================================================================================

    const editorDialog = document.getElementById('chainEditor');
    const form = $('[data-editor-form]', editorDialog);
    const headerEl = $('[data-editor-header]', editorDialog);
    const linesEl = $('[data-editor-lines]', editorDialog);

    const REF_LABEL = { GR: 'Challan no', FFR: 'Challan no', GI: 'Batch no', RPI: 'PI reference', DO: 'Reference', FD: 'Challan / gate pass no' };
    const REQUIRED_LABEL = { BPO: 'Required by', WWO: 'Target date', PWO: 'Target date', RPI: 'Deliver from', DO: 'Delivery date' };
    const STORE_STAGE = { GR: 'GREIGE', FFR: 'FINISHED', GI: '', DO: '' };

    const editor = {
        doc: null,
        parents: new Map(),     // parent document id -> documentNo
        rows: new Map(),        // "KIND:id" -> open line row
        picked: new Map(),      // "KIND:id" -> { quantity, lotId, rolls, dyeLot, shade, grade, deliveryDate, remarks, revisedFromLineId }
        allowances: new Map(),  // BPO: booking group id -> %
        stores: [],

        async open(doc, presetParent) {
            this.doc = doc || null;
            this.parents = new Map();
            this.rows = new Map();
            this.picked = new Map();
            this.allowances = new Map();
            $('[data-editor-title]', editorDialog).textContent = doc
                ? `Edit ${doc.documentNo}` : `New ${CFG.label.toLowerCase()}`;
            await this.renderHeader(doc);
            linesEl.innerHTML = '<p class="text-sm text-gray-500">Nothing chosen yet.</p>';
            if (viewer.open) viewer.close();
            editorDialog.showModal();
            if (doc) {
                for (const g of doc.lineGroups || []) {
                    for (const l of g.colorLines || []) {
                        if (l.sourceId == null) continue;
                        this.picked.set(`${l.sourceKind}:${l.sourceId}`, { quantity: l.quantity, lotId: l.lotId, rolls: l.rolls,
                            dyeLot: l.dyeLot, shade: l.shade, grade: l.grade, deliveryDate: l.deliveryDate, remarks: l.remarks,
                            revisedFromLineId: l.revisedFromLineId });
                    }
                }
                const parentIds = new Set((doc.lineGroups || []).flatMap(g => (g.colorLines || []).map(l => l.sourceDocumentId)).filter(Boolean));
                if (!parentIds.size && doc.parent) parentIds.add(doc.parent.id);
                for (const id of parentIds) await this.addParent(id, null, true);
            } else if (presetParent) {
                await this.addParent(presetParent, null, false);
                const select = $('[name="parentId"]', headerEl);
                if (select) App.RemoteSelect.of(select).setValue(presetParent);
            }
            this.renderLines();
        },

        async renderHeader(doc) {
            const d = doc || {};
            const f = [];
            const parentMulti = STEP === 'RPI';
            f.push(`<div class="sm:col-span-2"><label class="label" for="ceParent">${esc(CFG.parentLabel)} <span class="req">*</span></label>
                <select id="ceParent" class="field" name="parentId" data-placeholder="Choose the ${esc(CFG.parentLabel.toLowerCase())}…"
                    ${doc && !parentMulti ? 'disabled' : ''}></select>
                ${parentMulti ? '<p class="hint">A schedule may take lines from several production orders of the same buyer - choose each in turn.</p>' : ''}</div>`);
            if (STEP === 'PWO') {
                f.push(`<div><label class="label" for="ceKind">Process <span class="req">*</span></label>
                    <select id="ceKind" class="field" name="processKind">${CFG.processKinds.map(k =>
                        `<option value="${esc(k.value)}"${(d.processKindCode || 'DYE') === k.value ? ' selected' : ''}>${esc(k.label)}</option>`).join('')}</select></div>`);
            }
            if (STEP in STORE_STAGE) {
                f.push(`<div><label class="label" for="ceStore">Store <span class="req">*</span></label>
                    <select id="ceStore" class="field" name="warehouseId"><option value="">Choose…</option></select></div>`);
            }
            f.push(`<div><label class="label" for="ceDate">Date</label><input id="ceDate" type="date" class="field" name="documentDate" value="${esc(d.documentDate || new Date().toISOString().slice(0, 10))}"></div>`);
            if (REQUIRED_LABEL[STEP]) {
                f.push(`<div><label class="label" for="ceRequired">${esc(REQUIRED_LABEL[STEP])}</label><input id="ceRequired" type="date" class="field" name="requiredDate" value="${esc(d.requiredDate || '')}"></div>`);
            }
            if (STEP === 'WWO' || STEP === 'PWO') {
                f.push(`<div class="sm:col-span-2"><label class="label" for="ceVendor">Vendor <span class="text-gray-400">(if subcontracted)</span></label>
                    <select id="ceVendor" class="field" name="vendorId" data-remote="/api/parties/lookup?role=SUPPLIER" data-allow-clear data-placeholder="In-house"></select></div>`);
            }
            if (STEP === 'RPI' || STEP === 'DO') {
                f.push(`<div class="sm:col-span-2"><label class="label" for="ceGarments">Garments</label>
                    <select id="ceGarments" class="field" name="garmentsId" data-remote="/api/parties/lookup?role=GARMENT_FACTORY" data-allow-clear data-placeholder="As on the order"></select></div>`);
            }
            if (STEP === 'RPI' || STEP === 'DO' || STEP === 'FD') {
                f.push(`<div class="sm:col-span-2"><label class="label" for="ceAddress">Delivery address</label><input id="ceAddress" class="field" name="garmentsAddress" maxlength="500" value="${esc(d.garmentsAddress || '')}"></div>`);
            }
            if (STEP === 'FD') {
                f.push(`<div><label class="label" for="ceVehicle">Vehicle no</label><input id="ceVehicle" class="field" name="vehicleNo" maxlength="40" value="${esc(d.vehicleNo || '')}"></div>`);
                f.push(`<div><label class="label" for="ceDriver">Driver</label><input id="ceDriver" class="field" name="driverName" maxlength="100" value="${esc(d.driverName || '')}"></div>`);
            }
            if (REF_LABEL[STEP]) {
                f.push(`<div><label class="label" for="ceRef">${esc(REF_LABEL[STEP])}</label><input id="ceRef" class="field" name="referenceNo" maxlength="100" value="${esc(d.referenceNo || '')}"></div>`);
            }
            f.push(`<div class="sm:col-span-2 lg:col-span-4"><label class="label" for="ceRemarks">Remarks</label><input id="ceRemarks" class="field" name="remarks" maxlength="1000" value="${esc(d.remarks || '')}"></div>`);
            headerEl.innerHTML = f.join('');

            const parent = $('[name="parentId"]', headerEl);
            new App.RemoteSelect(parent, { url: `${CFG.api}/parents`, placeholder: `Choose the ${CFG.parentLabel.toLowerCase()}…` });
            if (doc && doc.parent) App.RemoteSelect.of(parent).setValue(doc.parent.id, doc.parent.documentNo);
            parent.addEventListener('change', () => {
                if (!parent.value) return;
                this.addParent(Number(parent.value), App.RemoteSelect.of(parent).selected?.text, false).then(() => this.renderLines());
            });
            App.remoteSelects(headerEl);
            if (d.vendorId) App.RemoteSelect.of($('[name="vendorId"]', headerEl)).setValue(d.vendorId, d.vendorName);
            if (d.garmentsId && $('[name="garmentsId"]', headerEl)) App.RemoteSelect.of($('[name="garmentsId"]', headerEl)).setValue(d.garmentsId, d.garmentsName);

            const kind = $('[name="processKind"]', headerEl);
            if (kind) kind.addEventListener('change', async () => {
                // A rework draws its own stream, so what is open changes with the kind.
                const ids = [...this.parents.keys()];
                this.rows = new Map();
                for (const id of ids) await this.addParent(id, this.parents.get(id), true);
                this.renderLines();
            });
            const store = $('[name="warehouseId"]', headerEl);
            if (store) {
                const stage = STORE_STAGE[STEP];
                try {
                    this.stores = await api('/api/production/stores', { query: { stage } });
                } catch (error) { fail(error); this.stores = []; }
                store.innerHTML = '<option value="">Choose…</option>' + this.stores.map(s => `<option value="${esc(s.id)}"${s.id === d.warehouseId ? ' selected' : ''}>`
                    + `${esc(s.name)}${STEP === 'DO' ? ` (${[s.holdsGreige && 'greige', s.holdsFinished && 'finished'].filter(Boolean).join(' + ') || 'no fabric role'})` : ''}</option>`).join('');
                // A new document starts in the user's own store when it can take it, else the only one there is.
                if (!d.warehouseId) {
                    const own = this.stores.find(s => s.id === CFG.defaultStoreId);
                    if (own) store.value = own.id;
                    else if (this.stores.length === 1) store.value = this.stores[0].id;
                }
                store.addEventListener('change', () => this.renderLines());
            }
        },

        async addParent(id, label, keepPicks) {
            if (!keepPicks && STEP !== 'RPI') { this.parents.clear(); this.rows.clear(); if (!this.doc) this.picked.clear(); }
            const kind = $('[name="processKind"]', headerEl)?.value;
            try {
                const rows = await api(`${CFG.api}/open-lines`, { query: { parentId: id, exclude: this.doc ? this.doc.id : undefined, kind: STEP === 'PWO' ? kind : undefined } });
                this.parents.set(id, label || (rows[0] && rows[0].documentNo) || `#${id}`);
                rows.forEach(r => this.rows.set(`${r.sourceKind}:${r.sourceId}`, r));
                if (!rows.length) toast(`Nothing is left open on ${label || 'that document'} for a ${CFG.label.toLowerCase()}.`, 'warn');
            } catch (error) { fail(error); }
        },

        renderLines() {
            const rows = [...this.rows.values()];
            $('[data-select-all-wrap]', editorDialog).hidden = !rows.length;
            if (!rows.length) {
                linesEl.innerHTML = `<p class="text-sm text-gray-500">${this.parents.size ? 'Nothing open to raise against.' : 'Nothing chosen yet.'}</p>`;
                this.total();
                return;
            }
            const store = Number($('[name="warehouseId"]', headerEl)?.value) || null;
            const byGroup = new Map();
            rows.forEach(r => {
                const key = `${r.documentId}:${r.groupId}`;
                if (!byGroup.has(key)) byGroup.set(key, []);
                byGroup.get(key).push(r);
            });
            const extraHead = {
                BPO: '', WWO: '', PWO: '', GR: '<th class="num">Rolls</th>',
                GI: '<th>Lot</th><th class="num">Rolls</th>', FFR: '<th>Dye lot</th><th>Shade</th><th>Grade</th><th class="num">Rolls</th>',
                RPI: '<th>Delivery date</th>', DO: '<th>Lot</th>', FD: '<th>Lot</th><th class="num">Rolls</th>'
            }[STEP];
            const figureHead = { FFR: '<th class="num">Issued</th>', GI: '<th class="num">Planned greige</th>', FD: '<th class="num">Reserved</th>' }[STEP] || '';
            linesEl.innerHTML = [...byGroup.values()].map(group => {
                const g = group[0];
                const allowance = STEP === 'BPO' ? `<label class="ml-auto flex items-center gap-2 text-xs text-gray-600">Greige allowance
                    <input type="number" min="0" max="100" step="0.01" class="field field-sm w-24 text-right" data-allowance="${esc(g.groupId)}"
                        value="${esc(this.allowances.get(g.groupId) ?? g.greigeAllowancePct ?? '')}" placeholder="${g.routeCode ? '' : 'No route'}"> %</label>` : '';
                return `<article class="line-card mb-3">
                    <header class="line-head flex-wrap">
                        <div class="min-w-0 flex-1">
                            <h4 class="line-title"><span class="font-mono">${esc(g.construction || 'No construction')}</span>
                                <span class="line-sub">${esc(g.documentNo)} · line ${esc(g.groupNo)}${itemLabel(g) ? ` · ${esc(itemLabel(g))}` : ''}</span></h4>
                            <p class="line-meta">${[g.fabricType, g.route || g.routeLabel, g.yarnPrep && g.yarnPrep !== 'None' && `Needs ${g.yarnPrep.toLowerCase()}`,
                                STEP === 'BPO' && !g.routeCode && 'No process route - add one before submitting']
                                .filter(Boolean).map(m => `<span>${esc(m)}</span>`).join('')}</p>
                        </div>${allowance}
                    </header>
                    ${specFacts(g, ['itemDescription', 'costingCode', 'qualityReference', 'styleReference', 'endUse', 'selvedge', 'lightSource', 'washType'], 'spec-facts-compact')}
                    <div class="table-wrap"><table class="line-colours line-colours-edit">
                        <thead><tr><th class="w-8"></th><th>Colour</th><th class="num">Ordered</th><th class="num">Allowed</th><th class="num">Taken</th><th class="num">Open</th>
                            ${figureHead}<th class="num">Quantity</th>${extraHead}</tr></thead>
                        <tbody>${group.map(r => this.rowHtml(r, store)).join('')}</tbody>
                    </table></div>
                </article>`;
            }).join('');
            this.total();
        },

        rowHtml(r, store) {
            const key = `${r.sourceKind}:${r.sourceId}`;
            const p = this.picked.get(key);
            const on = !!p;
            const qty = p ? p.quantity : '';
            const lotSelect = lots => {
                const options = (lots || []).filter(l => !store || l.warehouseId === store);
                return `<select class="field field-sm min-w-[12rem]" data-f="lotId"${on ? '' : ' disabled'}>
                    <option value="">${options.length ? 'Choose lot…' : 'No stock'}</option>${options.map(l =>
                        `<option value="${esc(l.lotId)}"${p && Number(p.lotId) === l.lotId ? ' selected' : ''}>${esc(lotLabel(l))} · ${esc(l.warehouseName)}: ${num(l.free)} free</option>`).join('')}</select>`;
            };
            const input = (f, type, attrs, value) => `<input type="${type}" class="field field-sm ${type === 'number' ? 'w-24 text-right' : 'w-28'}" data-f="${f}" ${attrs || ''}
                value="${esc(value ?? '')}"${on ? '' : ' disabled'}>`;
            const extra = {
                GR: `<td class="num">${input('rolls', 'number', 'min="0" step="1"', p && p.rolls)}</td>`,
                GI: `<td>${lotSelect(r.lots)}</td><td class="num">${input('rolls', 'number', 'min="0" step="1"', p && p.rolls)}</td>`,
                FFR: `<td>${input('dyeLot', 'text', 'maxlength="40"', p ? p.dyeLot : r.dyeLot)}</td><td>${input('shade', 'text', 'maxlength="40"', p ? p.shade : r.shade)}</td>
                      <td><select class="field field-sm w-16" data-f="grade"${on ? '' : ' disabled'}><option${!p || p.grade !== 'B' ? ' selected' : ''}>A</option><option${p && p.grade === 'B' ? ' selected' : ''}>B</option></select></td>
                      <td class="num">${input('rolls', 'number', 'min="0" step="1"', p && p.rolls)}</td>`,
                RPI: `<td>${input('deliveryDate', 'date', '', p && p.deliveryDate)}</td>`,
                DO: `<td>${lotSelect(r.lots)}${r.deliveryDate ? `<div class="mt-1 text-xs text-gray-500">Scheduled ${esc(fmt.date(r.deliveryDate))}</div>` : ''}</td>`,
                FD: `<td class="text-xs">${esc((r.lots || []).map(lotLabel).join(', ') || '—')}</td><td class="num">${input('rolls', 'number', 'min="0" step="1"', p && p.rolls)}</td>`
            }[STEP] || '';
            const figure = { FFR: `<td class="num">${num(r.issued)}</td>`, GI: `<td class="num">${num(r.plannedGreige)}</td>`, FD: `<td class="num">${num(r.reserved)}</td>` }[STEP] || '';
            const name = r.sourceKind === 'GROUP'
                ? `Greige - all colours <span class="text-xs text-gray-500">(${esc(r.colours)} colours)</span>${coveredColours(r.coversColours)}`
                : `${colourName(r)}${colourDetail(r, r.construction)}`;
            const open = Number(r.available);
            return `<tr data-key="${esc(key)}" class="${on ? '' : 'opacity-70'}">
                <td><input type="checkbox" class="checkbox" data-pick${on ? ' checked' : ''}${!on && open <= 0 ? ' disabled' : ''} aria-label="Include"></td>
                <td class="min-w-[12rem]">${name}</td>
                <td class="num">${qtyUnit(r.quantity, r.uom)}</td>
                <td class="num">${num(r.cap)}</td>
                <td class="num">${num(r.taken)}</td>
                <td class="num font-medium ${open <= 0 ? 'text-gray-400' : ''}">${num(r.available)}</td>
                ${figure}
                <td class="num"><input type="number" min="0" step="0.0001" class="field field-sm w-28 text-right" data-f="quantity"
                    value="${esc(qty)}"${on ? '' : ' disabled'} ${open > 0 ? `max="${esc(r.available)}"` : ''}></td>
                ${extra}
            </tr>`;
        },

        total() {
            const total = [...this.picked.values()].reduce((t, p) => t + (Number(p.quantity) || 0), 0);
            $('[data-editor-total]', editorDialog).textContent = nf.format(total);
        },

        payload() {
            const v = name => $(`[name="${name}"]`, headerEl)?.value || null;
            const lines = [...this.picked.entries()].filter(([key]) => this.rows.has(key)).map(([key, p]) => {
                const [kind, id] = key.split(':');
                return { sourceKind: kind, sourceId: Number(id), quantity: p.quantity === '' ? null : Number(p.quantity),
                    lotId: p.lotId ? Number(p.lotId) : null, rolls: p.rolls === '' || p.rolls == null ? null : Number(p.rolls),
                    dyeLot: p.dyeLot || null, shade: p.shade || null, grade: p.grade || null,
                    deliveryDate: p.deliveryDate || null, remarks: p.remarks || null, revisedFromLineId: p.revisedFromLineId || null };
            });
            return {
                id: this.doc ? this.doc.id : null,
                documentDate: v('documentDate'), requiredDate: v('requiredDate'),
                warehouseId: v('warehouseId') ? Number(v('warehouseId')) : null,
                vendorId: v('vendorId') ? Number(v('vendorId')) : null,
                processKind: v('processKind'), referenceNo: v('referenceNo'),
                garmentsId: v('garmentsId') ? Number(v('garmentsId')) : null,
                garmentsAddress: $('[name="garmentsAddress"]', headerEl) ? $('[name="garmentsAddress"]', headerEl).value : null,
                vehicleNo: v('vehicleNo'), driverName: v('driverName'), remarks: v('remarks'),
                lines,
                groups: [...this.allowances.entries()].map(([id, pct]) => ({ sourceGroupId: Number(id), greigeAllowancePct: pct === '' ? null : Number(pct) }))
            };
        }
    };

    function lotLabel(l) {
        if (l.stage === 'GREIGE') return 'Greige';
        return [l.dyeLot && `Lot ${l.dyeLot}`, l.shade && `Shade ${l.shade}`, `Grade ${l.grade}`].filter(Boolean).join(' · ');
    }

    linesEl.addEventListener('change', event => {
        const allowance = event.target.closest('[data-allowance]');
        if (allowance) { editor.allowances.set(Number(allowance.dataset.allowance), allowance.value); return; }
        const tr = event.target.closest('tr[data-key]');
        if (!tr) return;
        const key = tr.dataset.key;
        if (event.target.matches('[data-pick]')) {
            if (event.target.checked) {
                const r = editor.rows.get(key);
                editor.picked.set(key, pickDefaults(r));
            } else {
                editor.picked.delete(key);
            }
            editor.renderLines();
            return;
        }
        const field = event.target.dataset.f;
        if (field && editor.picked.has(key)) {
            editor.picked.get(key)[field] = event.target.value;
            if (field === 'quantity') editor.total();
        }
    });
    linesEl.addEventListener('input', event => {
        const tr = event.target.closest('tr[data-key]');
        if (tr && event.target.dataset.f === 'quantity' && editor.picked.has(tr.dataset.key)) {
            editor.picked.get(tr.dataset.key).quantity = event.target.value;
            editor.total();
        }
    });
    $('[data-select-all]', editorDialog).addEventListener('change', event => {
        for (const [key, r] of editor.rows) {
            if (event.target.checked && !editor.picked.has(key) && Number(r.available) > 0) {
                editor.picked.set(key, pickDefaults(r));
            } else if (!event.target.checked) {
                editor.picked.delete(key);
            }
        }
        editor.renderLines();
    });

    /**
     * A ticked line starts at its planned amount still to go (not the tolerance ceiling), and - when
     * the store holds exactly one lot that fits - that lot, limited to what is free in it.
     */
    function pickDefaults(r) {
        // A delivery takes what its delivery order holds; only an issue or a delivery order picks free stock.
        const lotId = STEP === 'GI' || STEP === 'DO' ? onlyLot(r) : null;
        let qty = Number(r.suggested ?? r.available) || 0;
        const lot = lotId && (r.lots || []).find(l => l.lotId === lotId);
        if (lot) qty = Math.min(qty, Number(lot.free) || 0);
        if (STEP === 'FD' && r.reserved != null) qty = Math.min(qty, Number(r.reserved) || 0);
        return { quantity: qty > 0 ? Math.round(qty * 10000) / 10000 : '', deliveryDate: $('[name="requiredDate"]', headerEl)?.value || null,
            dyeLot: r.dyeLot || null, shade: r.shade || null, grade: 'A', lotId };
    }

    /** Pre-picks the lot when the store holds exactly one that fits. */
    function onlyLot(r) {
        const store = Number($('[name="warehouseId"]', headerEl)?.value) || null;
        const lots = (r.lots || []).filter(l => !store || l.warehouseId === store);
        return lots.length === 1 ? lots[0].lotId : null;
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();
        const body = editor.payload();
        if (!body.lines.length) return toast('Tick at least one line.', 'warn');
        if ($('[name="warehouseId"]', headerEl) && !body.warehouseId) {
            $('[name="warehouseId"]', headerEl).focus();
            return toast('Choose the store.', 'warn');
        }
        const button = $('[data-editor-save]', editorDialog);
        button.disabled = true;
        try {
            const saved = await api(CFG.api, { method: 'POST', body });
            toast(`${saved.documentNo} saved as a draft.`, 'success');
            editorDialog.close();
            screen.grid.reload();
            screen.open(saved.id);
        } catch (error) {
            fail(error);
        } finally {
            button.disabled = false;
        }
    });
    $$('[data-editor-close]', editorDialog).forEach(b => b.addEventListener('click', async () => {
        if (editor.picked.size && !await App.confirm({ title: 'Close without saving?', message: 'The lines you picked will be lost.',
            confirmText: 'Close', danger: true })) return;
        editorDialog.close();
    }));
    editorDialog.addEventListener('cancel', event => { event.preventDefault(); $('[data-editor-close]', editorDialog).click(); });

    $$('[data-chain-new]').forEach(b => b.addEventListener('click', () => editor.open(null)));

    // Raised from a board or a parent document: /weaving-wo?new=1&parent=12
    const params = new URLSearchParams(location.search);
    if (params.get('new') && CFG.canCreate) editor.open(null, Number(params.get('parent')) || null);
});
