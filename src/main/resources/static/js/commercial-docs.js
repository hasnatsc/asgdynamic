/*
 * Commercial documents - export PI, LC and CI; import PI and LC.
 *
 * One script for the five screens, driven by window.COMMERCIAL (CommercialDocumentController.page):
 *   - the list is App.DocumentScreen;
 *   - the viewer shows the document, its commercial facts, where each line stands downstream, what
 *     has been recorded against it (UD, UP, BTB LCs, contracts, required documents, costs, a CI's
 *     realization, an import PI's milestones), its papers to print, and what may be done now;
 *   - the editor takes the parent's open lines (schedule, PI or LC lines - or, for a regular CI, the
 *     delivery challans of its LC) and the commercial facts. The server decides: every quantity is
 *     checked against its parent line, and every bank account against its owner.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const CFG = window.COMMERCIAL;
    const STEP = CFG.step;
    const { api, esc, fmt, toast, fail, icon } = App;
    const $ = (sel, root) => (root || document).querySelector(sel);
    const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));
    const nf = new Intl.NumberFormat(undefined, { maximumFractionDigits: 3 });
    const mf = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const num = v => v == null || v === '' ? '—' : nf.format(Number(v));
    const money = v => v == null || v === '' ? '—' : mf.format(Number(v));
    const filled = v => v != null && String(v).trim() !== '';
    const date = v => v ? fmt.date(v) : null;
    const yesNo = v => v ? 'Yes' : 'No';
    const OPEN = ['APPROVED', 'PROCESSING', 'PARTIAL'];
    const isImport = STEP === 'IPI' || STEP === 'ILC';

    /** What each document records after it is raised - as CommercialRecordsService allows. */
    const RECORDS = {
        EPI: ['COST'], ELC: ['UD', 'UP', 'BTB_LC', 'SALES_CONTRACT', 'REQUIRED_DOC', 'COST'], ECI: ['COST'],
        IPI: ['COST'], ILC: ['REQUIRED_DOC', 'COST']
    };
    const KIND_LABEL = { UD: 'Utilization declarations (UD)', UP: 'Utilization permissions (UP)', BTB_LC: 'Back-to-back raw material LCs',
        SALES_CONTRACT: 'Sales contracts', REQUIRED_DOC: 'Required documents', COST: 'Costs' };
    const COST_KIND = { EPI: 'PI', IPI: 'PI', ELC: 'LC', ILC: 'LC', ECI: 'CI' };

    const screen = new App.DocumentScreen({
        kind: CFG.label, api: CFG.api, table: 'commercialTable', revise: CFG.revisable,
        canEdit: CFG.canCreate || CFG.canAmend, onEdit: doc => editor.open(doc), onView: view
    });

    // =========================================================================================
    // Viewer
    // =========================================================================================

    const viewer = document.getElementById('commercialViewer');

    function facts(doc) {
        const d = doc.details || {};
        const partyLabel = STEP === 'EPI' ? 'Applicant' : isImport ? 'Supplier' : 'Buyer';
        const rows = [
            ['Date', date(doc.documentDate)],
            [doc.parent ? doc.parent.label : null, doc.parent && `<a class="card-link" href="/${esc(doc.parent.slug)}?open=${esc(doc.parent.id)}">${esc(doc.parent.documentNo)}</a>`, true],
            [partyLabel, doc.partyName], ['Brand', doc.brandName], ['Garments', doc.garmentsName], ['Team', doc.marketingTeamName],
            ['Value', `${money(doc.subtotalAmount)} ${esc(doc.currency || '')}`, true],
            [doc.currency && doc.currency !== 'BDT' ? 'Rate to BDT' : null, num(doc.exchangeRate)],
            ['Supplier PI no', isImport && STEP === 'IPI' ? doc.referenceNo : null],
            ['LC no', d.lcNo], ['CI kind', d.ciKindLabel], ['Master LC', d.masterLcNo && `${d.masterLcNo}${d.masterLcDate ? ' · ' + date(d.masterLcDate) : ''}`],
            ['Doc type', d.importDocType], ['LC type', d.lcTypeLabel],
            ['LC issued', date(d.issueDate)],
            [STEP === 'EPI' ? 'Offer validity' : STEP === 'IPI' ? 'Validity' : 'Expiry', date(d.validityDate)],
            ['Last shipment', date(d.shipmentDate)],
            ['Tenure', d.tenureLabel], ['Payment', d.paymentTermsLabel], ['INCO terms', d.deliveryTermsLabel],
            [{ EPI: 'Advising bank', ELC: 'Beneficiary bank', ECI: 'Beneficiary bank', ILC: 'Local bank' }[STEP] || 'Bank', d.bankName],
            ['Our account', d.bankAccount],
            [STEP === 'ILC' ? "Supplier's bank" : "Buyer's bank", d.counterBankName], ["Buyer's account", d.counterBankAccount],
            ['Foreign bank', [d.foreignBankName, d.foreignBankSwift && `SWIFT ${d.foreignBankSwift}`, d.foreignBankBin && `BIN ${d.foreignBankBin}`,
                d.foreignBankRouting && `Routing ${d.foreignBankRouting}`].filter(Boolean).join(' · ')],
            ['Beneficiary account', d.beneficiaryAccountNo],
            ['HS code', d.hsCode], ['Bond licence', d.applicantBondLicence],
            ['Net / gross weight', filled(d.netWeight) ? `${num(d.netWeight)} / ${num(d.grossWeight)} kg` : null],
            ['Calculated weight', filled(d.calcNetWeight) && Number(d.calcNetWeight) > 0 ? `${num(d.calcNetWeight)} / ${num(d.calcGrossWeight)} kg` : null],
            ['Partial shipment', STEP === 'ELC' || STEP === 'ILC' ? yesNo(d.partialShipment) : null],
            ['BTMA certificate', STEP === 'ELC' ? yesNo(d.btmaCertificate) : null],
            ['Acknowledged', date(d.acknowledgedOn)],
            ['Port', d.port], ['C&F agent', d.cnfAgent], ['IP no', d.ipNo], ['SRO benefited', STEP === 'ILC' ? yesNo(d.sroBenefited) : null],
            ['BTMA', d.btmaNo && `${d.btmaNo}${d.btmaDate ? ' · ' + date(d.btmaDate) : ''}`],
            ['Bill of entry', d.billOfEntryNo && `${d.billOfEntryNo}${d.billOfEntryDate ? ' · ' + date(d.billOfEntryDate) : ''}`],
            ['Backed by export LC', d.backedByDocumentNo && `<a class="card-link" href="/export-lc?open=${esc(d.backedByDocumentId)}">${esc(d.backedByDocumentNo)}</a>`, true],
            ['Local agent', d.localAgentName], ['IBC no', d.ibcNo],
            ['Cash incentive', filled(d.incentiveAmount) ? `${money(d.incentiveAmount)}${d.incentiveAppliedOn ? ' · applied ' + date(d.incentiveAppliedOn) : ''}${d.incentiveConfirmedOn ? ' · confirmed ' + date(d.incentiveConfirmedOn) : ''}` : null],
            [STEP === 'ELC' ? 'Invoiced / realized' : null, STEP === 'ELC' ? `${money(doc.invoicedValue)} / ${money(doc.realizedValue)}` : null]
        ].filter(([label, value]) => label && value != null && value !== '');
        return `<dl class="grid grid-cols-2 gap-4 text-sm sm:grid-cols-3 lg:grid-cols-5">${rows.map(([label, value, html]) =>
            `<div><dt class="text-xs text-gray-500">${esc(label)}</dt><dd class="mt-0.5 font-medium text-gray-900 dark:text-white">${html ? value : esc(value)}</dd></div>`).join('')}</dl>
            ${d.amountInWords ? `<p class="mt-3 text-xs font-medium tracking-wide text-gray-600 dark:text-gray-300">${esc(d.amountInWords)}</p>` : ''}
            ${doc.remarks ? `<p class="mt-3 whitespace-pre-line text-sm text-gray-600 dark:text-gray-300"><span class="text-gray-500">Remarks:</span> ${esc(doc.remarks)}</p>` : ''}`;
    }

    function what(l) {
        if (l.itemName) return `<span class="font-medium">${esc(l.itemName)}</span> <span class="font-mono text-xs text-gray-500">${esc(l.itemCode || '')}</span>
            ${filled(l.specification) ? `<div class="text-xs text-gray-500">${esc(l.specification)}</div>` : ''}`;
        const meta = [l.composition, l.weave, filled(l.finishWidth) && `${num(l.finishWidth)}″`, filled(l.gsm) && `${num(l.gsm)} GSM`, l.fabricType].filter(Boolean);
        return `<span class="font-mono text-xs">${esc(l.construction || '')}</span> <span class="font-medium">${esc(l.colorName || '')}</span>
            ${meta.length ? `<div class="text-xs text-gray-500">${esc(meta.join(' · '))}</div>` : ''}`;
    }

    function linesTable(doc) {
        const lines = doc.lines || [];
        if (!lines.length) return '<p class="text-sm text-gray-500">No lines.</p>';
        const figs = [...new Set(lines.flatMap(l => (l.figures || []).map(f => f.label)))];
        const challan = lines.some(l => l.challanNo);
        return `<div class="table-wrap"><table class="table-grid">
            <thead><tr><th>Fabric / item</th><th>From</th>${challan ? '<th>Challan</th>' : ''}<th class="text-right">Quantity</th>
                <th class="text-right">Rate</th><th class="text-right">Value</th>${figs.map(f => `<th class="text-right">${esc(f)}</th>`).join('')}</tr></thead>
            <tbody>${lines.map(l => {
                const byLabel = Object.fromEntries((l.figures || []).map(f => [f.label, f.value]));
                return `<tr><td class="min-w-[14rem] whitespace-normal">${what(l)}</td>
                    <td class="text-xs">${l.sourceDocumentId ? `<a class="card-link" href="/${esc(doc.parent ? doc.parent.slug : '')}?open=${esc(l.sourceDocumentId)}">${esc(l.sourceLabel)}</a>` : '—'}</td>
                    ${challan ? `<td class="text-xs">${l.challanNo ? `<a class="card-link" href="/fabrics-delivery?open=${esc(l.deliveryLineId)}">${esc(l.challanNo)}</a> · ${esc(date(l.challanDate) || '')}` : '—'}</td>` : ''}
                    <td class="text-right tabular-nums font-medium">${num(l.quantity)} <span class="text-xs text-gray-500">${esc(l.uom || '')}</span></td>
                    <td class="text-right tabular-nums">${money(l.rate)}</td><td class="text-right tabular-nums">${money(l.lineAmount)}</td>
                    ${figs.map(f => `<td class="text-right tabular-nums">${num(byLabel[f])}</td>`).join('')}</tr>`;
            }).join('')}</tbody>
            <tfoot><tr><td colspan="${challan ? 3 : 2}">Total</td><td class="text-right tabular-nums">${num(doc.totalQuantity)}</td><td></td>
                <td class="text-right tabular-nums">${money(doc.subtotalAmount)}</td>${figs.map(() => '<td></td>').join('')}</tr></tfoot>
        </table></div>`;
    }

    function recordsHtml(doc) {
        const events = doc.events || [];
        return (RECORDS[STEP] || []).map(kind => {
            const list = events.filter(e => e.kind === kind);
            const total = (doc.eventTotals || {})[kind];
            const cols = {
                UD: [['refNo', 'UD no'], ['date', 'Received'], ['amount', 'Value']],
                UP: [['refNo', 'UP no'], ['date', 'Issued'], ['amount', 'Value']],
                BTB_LC: [['refNo', 'BTB LC no'], ['codeLabel', 'Material'], ['remarks', 'Supplier'], ['date', 'Opened'], ['amount', 'Amount']],
                SALES_CONTRACT: [['refNo', 'Contract no'], ['date', 'Date']],
                REQUIRED_DOC: [['documentName', 'Document']],
                COST: [['costHead', 'Cost head'], ['date', 'Date'], ['amount', 'Amount (BDT)'], ['remarks', 'Remarks']]
            }[kind];
            return `<section class="form-section">
                <div class="form-section-head"><h3 class="form-section-title">${esc(KIND_LABEL[kind])}</h3>
                    <div class="flex items-center gap-3">${total != null ? `<span class="text-xs text-gray-500">Total ${money(total)}</span>` : ''}
                    ${doc.canRecord ? `<button type="button" class="btn-ghost btn-sm" data-add-record="${kind}">${icon('plus')}Add</button>` : ''}</div></div>
                ${list.length ? `<div class="table-wrap"><table class="table-grid text-sm"><thead><tr>${cols.map(([, l]) => `<th>${esc(l)}</th>`).join('')}<th class="w-px"></th></tr></thead>
                    <tbody>${list.map(e => `<tr>${cols.map(([k]) => `<td class="${k === 'amount' ? 'text-right tabular-nums' : ''}">${esc(
                        k === 'amount' ? money(e[k]) : k === 'date' ? (date(e[k]) || '—') : (e[k] ?? '—'))}</td>`).join('')}
                        <td>${doc.canRecord ? `<button type="button" class="btn-icon btn-sm" data-remove-record="${esc(e.id)}" aria-label="Remove">${icon('trash')}</button>` : ''}</td></tr>`).join('')}</tbody></table></div>`
                    : '<p class="text-sm text-gray-500">None recorded.</p>'}
            </section>`;
        }).join('');
    }

    function realizationHtml(doc) {
        const r = doc.realization;
        if (!r) return '';
        const latest = [...r.steps].reverse().find(s => s.recorded);
        return `<section class="form-section">
            <div class="form-section-head"><div><h3 class="form-section-title">Realization</h3>
                <p class="form-section-sub">${r.maturityDue ? `Matures ${esc(date(r.maturityDue))}` : 'Matures once accepted, by the LC\'s tenure'}</p></div>
                ${latest && doc.canRecord ? `<button type="button" class="btn-ghost btn-sm" data-undo-realization>${icon('refresh')}Take back ${esc(latest.label.toLowerCase())}</button>` : ''}</div>
            <ol class="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">${r.steps.map(s => `<li class="rounded-lg border px-3 py-2 text-sm ${s.recorded
                ? 'border-emerald-200 bg-emerald-50/60 dark:border-emerald-900 dark:bg-emerald-950/30' : 'border-gray-200 dark:border-gray-700'}">
                <div class="flex items-center justify-between gap-2"><span class="font-medium">${esc(s.label)}</span>
                    ${s.recorded ? icon('check-circle', 'text-emerald-600') : s.optional ? '<span class="text-xs text-gray-400">optional</span>' : ''}</div>
                ${s.recorded ? `<div class="text-xs text-gray-600 dark:text-gray-300">${esc(date(s.date))}${s.amount != null ? ` · ${money(s.amount)} ${esc(doc.currency)}` : ''}${s.refNo ? ` · ${esc(s.refNo)}` : ''}</div>`
                    : s.allowed && doc.canRecord ? `<button type="button" class="btn-ghost btn-sm mt-1" data-realize="${esc(s.code)}" data-has-amount="${s.hasAmount}">${icon('plus')}Record</button>` : ''}
            </li>`).join('')}</ol>
        </section>`;
    }

    function milestonesHtml(doc) {
        if (!doc.milestones) return '';
        return `<section class="form-section">
            <div class="form-section-head"><div><h3 class="form-section-title">Checklist</h3>
                <p class="form-section-sub">Management approval is the approval itself; these are ticked off by the commercial desk.</p></div></div>
            <ul class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">${doc.milestones.map(m => `<li class="flex items-center justify-between gap-2 rounded-lg border px-3 py-2 text-sm
                ${m.done ? 'border-emerald-200 bg-emerald-50/60 dark:border-emerald-900 dark:bg-emerald-950/30' : 'border-gray-200 dark:border-gray-700'}">
                <div><span class="font-medium">${esc(m.label)}</span>${m.done ? `<div class="text-xs text-gray-500">${esc(date(m.date))} · ${esc(m.by || '')}</div>` : ''}</div>
                ${doc.canRecord ? (m.done ? `<button type="button" class="btn-icon btn-sm" data-undo-milestone="${esc(m.eventId)}" aria-label="Take back">${icon('x')}</button>`
                    : `<button type="button" class="btn-ghost btn-sm" data-milestone="${esc(m.code)}">${icon('check')}Done</button>`) : ''}</li>`).join('')}</ul>
        </section>`;
    }

    function backToBackHtml(doc) {
        if (!doc.backToBack || !doc.backToBack.length) return '';
        return `<section class="form-section"><div class="form-section-head"><h3 class="form-section-title">Import LCs opened back-to-back on it</h3></div>
            <div class="flex flex-wrap gap-2">${doc.backToBack.map(b => `<a class="chip" href="/import-lc?open=${esc(b.id)}"><span class="font-mono">${esc(b.lcNo || b.documentNo)}</span>
                <span class="text-gray-500">${esc(b.supplier || '')} · ${esc(b.currency)} ${money(b.amount)}</span> ${App.statusBadge(b.status)}</a>`).join('')}</div></section>`;
    }

    function view(doc, history) {
        $('[data-view-title]', viewer).innerHTML = `<span class="doc-no">${esc(doc.documentNo || 'Unnumbered')}</span> ${App.statusBadge(doc.status)}`
            + (doc.revisionNo ? ` <span class="badge-gray">Amendment ${esc(doc.revisionNo)}</span>` : '');
        const terms = (doc.keyTerms || []).map(t => `<li class="flex gap-2"><span class="w-36 shrink-0 text-gray-500">${esc(t.title)}</span><span>${esc(t.text)}</span></li>`).join('')
            + (doc.terms || []).map(t => `<li class="flex gap-2"><span class="w-36 shrink-0 text-gray-400">·</span><span>${esc(t)}</span></li>`).join('');
        const children = (doc.children || []).length ? `<section class="form-section"><div class="form-section-head"><h3 class="form-section-title">Raised against it</h3></div>
            <div class="flex flex-wrap gap-2">${doc.children.map(c => `<a class="chip" href="${c.slug ? `/${esc(c.slug)}?open=${esc(c.id)}` : '#'}">
                <span class="text-gray-500">${esc(c.label)}</span> <span class="font-mono">${esc(c.documentNo)}</span> ${App.statusBadge(c.status)}</a>`).join('')}</div></section>` : '';
        $('[data-view-body]', viewer).innerHTML = `
            <section class="form-section">${App.statusSteps(doc.status)}</section>
            <section class="form-section">${facts(doc)}</section>
            <section class="form-section"><div class="form-section-head"><h3 class="form-section-title">Lines</h3></div>${linesTable(doc)}</section>
            ${realizationHtml(doc)}${milestonesHtml(doc)}${recordsHtml(doc)}${backToBackHtml(doc)}
            ${terms ? `<section class="form-section"><div class="form-section-head"><h3 class="form-section-title">Terms &amp; conditions</h3></div><ul class="space-y-1.5 text-sm">${terms}</ul></section>` : ''}
            ${children}
            <section class="form-section"><div class="form-section-head"><h3 class="form-section-title">History</h3></div>${screen.historyHtml(history)}</section>`;

        const own = [];
        if (doc.canRecord && doc.status !== 'DRAFT' && { ELC: 1, ILC: 1, ECI: 1 }[STEP]) {
            own.push(`<button type="button" class="btn-ghost" data-com-action="facts">${icon('edit')}${{ ELC: 'Acknowledgement & incentive', ILC: 'Bill of entry', ECI: 'IBC no' }[STEP]}</button>`);
        }
        if (doc.closable) own.push(`<button type="button" class="btn-ghost" data-com-action="close">${icon('lock')}Close ${esc(CFG.label)}</button>`);
        if (doc.cancellable) own.push(`<button type="button" class="btn-danger-ghost" data-com-action="cancel">${icon('x-circle')}Cancel</button>`);
        if (doc.deletable) own.push(`<button type="button" class="btn-danger-ghost" data-com-action="delete">${icon('trash')}Delete</button>`);
        const prints = (CFG.prints || []).length > 1
            ? `<select class="field w-auto" data-print aria-label="Print"><option value="">Print…</option>${CFG.prints.map(p => `<option value="${esc(p.code)}">${esc(p.label)}</option>`).join('')}</select>`
            : (CFG.prints || []).map(p => `<button type="button" class="btn-ghost" data-print-one="${esc(p.code)}">${icon('document')}${esc(p.label)}</button>`).join('');
        const next = doc.open ? (CFG.next || []).map(n => `<a class="btn-secondary" href="/${esc(n.slug)}?new=1&parent=${esc(doc.id)}">${icon(n.icon)}${esc(n.label)}</a>`) : [];
        $('[data-view-foot]', viewer).innerHTML = `<button type="button" class="btn-ghost" data-close>Close</button>
            <div class="flex flex-wrap items-center justify-end gap-2">${prints}${next.join('')}${own.join('')}${screen.actionButtons(doc)}</div>`;
        if (!viewer.open) viewer.showModal();
    }

    async function act(url, opts, message) {
        try {
            const doc = await api(url, opts);
            toast(message, 'success');
            screen.grid.reload();
            screen.open(doc && doc.id ? doc.id : screen.current.id);
        } catch (error) { fail(error); }
    }

    viewer.addEventListener('change', event => {
        const p = event.target.closest('[data-print]');
        if (p && p.value) { window.open(`/${CFG.slug}/${screen.current.id}/print?doc=${encodeURIComponent(p.value)}`, '_blank'); p.value = ''; }
    });

    viewer.addEventListener('click', async event => {
        const docAction = event.target.closest('[data-doc-action]')?.dataset.docAction;
        if (docAction) {
            if (docAction === 'edit') viewer.close();
            return screen.act(docAction);
        }
        const doc = screen.current;
        if (!doc) return;
        const base = `${CFG.api}/${doc.id}`;
        const one = event.target.closest('[data-print-one]');
        if (one) return window.open(`/${CFG.slug}/${doc.id}/print?doc=${encodeURIComponent(one.dataset.printOne)}`, '_blank');

        const add = event.target.closest('[data-add-record]');
        if (add) return addRecord(doc, add.dataset.addRecord);
        const remove = event.target.closest('[data-remove-record]');
        if (remove) {
            if (!await App.confirm({ title: 'Remove this record?', confirmText: 'Remove', danger: true })) return;
            return act(`${base}/records/${remove.dataset.removeRecord}`, { method: 'DELETE' }, 'Removed.');
        }
        const realize = event.target.closest('[data-realize]');
        if (realize) {
            const step = (doc.realization.steps || []).find(s => s.code === realize.dataset.realize);
            const fields = [{ name: 'date', label: 'Date', type: 'date', required: true, value: new Date().toISOString().slice(0, 10) }];
            if (step.hasAmount) fields.push({ name: 'amount', label: `Amount (${doc.currency})`, type: 'number', required: true,
                value: step.code === 'FINAL_PAYMENT' ? doc.subtotalAmount : '' });
            fields.push({ name: 'refNo', label: 'Reference', maxlength: 100 }, { name: 'remarks', label: 'Remarks', type: 'textarea', maxlength: 500 });
            const values = await App.form({ title: `${step.label} - ${doc.documentNo}`, fields, confirmText: 'Record',
                message: step.code === 'FINAL_PAYMENT' ? 'The CI is realized and the receipt is posted to accounts.' : '' });
            if (!values) return;
            return act(`/api/export-ci/${doc.id}/realization`, { method: 'POST', query: Object.assign({ step: step.code }, values) }, `${step.label} recorded.`);
        }
        if (event.target.closest('[data-undo-realization]')) {
            const values = await App.form({ title: 'Take back the latest step?', message: 'A final payment taken back reverses its receipt in accounts.',
                fields: [{ name: 'reason', label: 'Reason', type: 'textarea', required: true, maxlength: 300 }], confirmText: 'Take back', danger: true });
            if (!values) return;
            return act(`/api/export-ci/${doc.id}/realization/undo`, { method: 'POST', query: values }, 'Taken back.');
        }
        const milestone = event.target.closest('[data-milestone]');
        if (milestone) {
            const values = await App.form({ title: (doc.milestones.find(m => m.code === milestone.dataset.milestone) || {}).label,
                fields: [{ name: 'date', label: 'Date', type: 'date', required: true, value: new Date().toISOString().slice(0, 10) },
                         { name: 'remarks', label: 'Remarks', type: 'textarea', maxlength: 500 }], confirmText: 'Done' });
            if (!values) return;
            return act(`/api/import-pi/${doc.id}/milestones`, { method: 'POST', query: Object.assign({ milestone: milestone.dataset.milestone }, values) }, 'Recorded.');
        }
        const undoMilestone = event.target.closest('[data-undo-milestone]');
        if (undoMilestone) return act(`/api/import-pi/${doc.id}/milestones/${undoMilestone.dataset.undoMilestone}`, { method: 'DELETE' }, 'Taken back.');

        const action = event.target.closest('[data-com-action]')?.dataset.comAction;
        if (!action) return;
        if (action === 'facts') return editFacts(doc);
        if (action === 'delete') {
            if (!await App.confirm({ title: `Delete ${doc.documentNo}?`, message: 'The draft is removed and what it drew is given back.', confirmText: 'Delete', danger: true })) return;
            try {
                await api(base, { method: 'DELETE' });
                toast('Deleted.', 'success');
                viewer.close();
                screen.grid.reload();
            } catch (error) { fail(error); }
            return;
        }
        const values = await App.form({ title: `${action === 'cancel' ? 'Cancel' : 'Close'} ${doc.documentNo}?`,
            message: action === 'cancel' ? 'Refused if anything is raised against it; an approved CI\'s invoice is reversed in accounts.' : 'A completed document is closed once settled.',
            fields: [{ name: action === 'cancel' ? 'reason' : 'remarks', label: action === 'cancel' ? 'Reason' : 'Remarks (optional)', type: 'textarea',
                required: action === 'cancel', maxlength: 300 }], confirmText: action === 'cancel' ? 'Cancel it' : 'Close', danger: action === 'cancel' });
        if (!values) return;
        act(`${base}/${action}`, { method: 'POST', query: values }, action === 'cancel' ? 'Cancelled.' : 'Closed.');
    });

    async function addRecord(doc, kind) {
        let fields;
        const today = new Date().toISOString().slice(0, 10);
        try {
            if (kind === 'COST') {
                const heads = await api('/api/commercial/cost-heads', { query: { kind: COST_KIND[STEP] } });
                if (!heads.length) return toast('Add a cost head under Commercial setup first.', 'warn');
                fields = [{ name: 'costHeadId', label: 'Cost head', type: 'select', required: true, options: heads.map(h => ({ value: String(h.id), label: h.name })) },
                    { name: 'amount', label: 'Amount (BDT)', type: 'number', required: true }, { name: 'date', label: 'Date', type: 'date', required: true, value: today },
                    { name: 'remarks', label: 'Remarks', maxlength: 500 }];
            } else if (kind === 'REQUIRED_DOC') {
                const names = await api('/api/commercial/document-names', { query: { kind: 'LC' } });
                fields = [{ name: 'documentNameId', label: 'Document', type: 'select', required: true, options: names.map(n => ({ value: String(n.id), label: n.name })) }];
            } else if (kind === 'BTB_LC') {
                fields = [{ name: 'refNo', label: 'BTB LC no', required: true, maxlength: 100 },
                    { name: 'code', label: 'Material', type: 'select', required: true, options: CFG.materialTypes.map(m => ({ value: m, label: m })) },
                    { name: 'remarks', label: 'Supplier', maxlength: 500 }, { name: 'date', label: 'Opened on', type: 'date', required: true, value: today },
                    { name: 'amount', label: 'Amount', type: 'number', required: true }];
            } else if (kind === 'SALES_CONTRACT') {
                fields = [{ name: 'refNo', label: 'Sales contract no', required: true, maxlength: 100 }, { name: 'date', label: 'Date', type: 'date', required: true, value: today }];
            } else {
                fields = [{ name: 'refNo', label: `${kind} no`, required: true, maxlength: 100 },
                    { name: 'date', label: kind === 'UD' ? 'Received on' : 'Issued on', type: 'date', required: true, value: today },
                    { name: 'amount', label: 'Value', type: 'number', required: true }];
            }
        } catch (error) { return fail(error); }
        const values = await App.form({ title: `Add - ${KIND_LABEL[kind]}`, fields, confirmText: 'Add' });
        if (!values) return;
        const body = Object.assign({ kind }, values);
        ['amount', 'costHeadId', 'documentNameId'].forEach(k => { if (body[k] === '' || body[k] == null) delete body[k]; else if (k !== 'amount') body[k] = Number(body[k]); });
        act(`${CFG.api}/${doc.id}/records`, { method: 'POST', body }, 'Recorded.');
    }

    async function editFacts(doc) {
        const d = doc.details || {};
        const fields = {
            ELC: [{ name: 'acknowledgedOn', label: 'Acknowledged on', type: 'date', value: d.acknowledgedOn || '' },
                { name: 'incentiveAmount', label: 'Cash incentive amount', type: 'number', value: d.incentiveAmount ?? '' },
                { name: 'incentiveAppliedOn', label: 'Incentive applied on', type: 'date', value: d.incentiveAppliedOn || '' },
                { name: 'incentiveConfirmedOn', label: 'Incentive confirmed on', type: 'date', value: d.incentiveConfirmedOn || '' }],
            ILC: [{ name: 'billOfEntryNo', label: 'Bill of entry no', maxlength: 60, value: d.billOfEntryNo || '' },
                { name: 'billOfEntryDate', label: 'Bill of entry date', type: 'date', value: d.billOfEntryDate || '' }],
            ECI: [{ name: 'ibcNo', label: 'IBC no', maxlength: 60, value: d.ibcNo || '' }]
        }[STEP];
        const values = await App.form({ title: `${doc.documentNo}`, fields, confirmText: 'Save' });
        if (!values) return;
        Object.keys(values).forEach(k => { if (values[k] === '') values[k] = null; });
        act(`${CFG.api}/${doc.id}/facts`, { method: 'POST', body: values }, 'Saved.');
    }

    // =========================================================================================
    // Editor
    // =========================================================================================

    const editorDialog = document.getElementById('commercialEditor');
    const form = $('[data-editor-form]', editorDialog);
    const headerEl = $('[data-editor-header]', editorDialog);
    const linesEl = $('[data-editor-lines]', editorDialog);
    const termsEl = $('[data-editor-terms]', editorDialog);

    const LABELS = {
        validityDate: { EPI: 'Offer validity', IPI: 'Validity', ELC: 'Expiry date', ILC: 'Expiry date' },
        bankId: { EPI: 'Advising bank', ELC: 'Beneficiary bank', ILC: 'Local bank' },
        counterBankId: { ELC: "Buyer's bank", ILC: "Supplier's bank" }
    };
    const HEADER = {
        EPI: ['validityDate', 'tenure', 'paymentTerms', 'deliveryTerms', 'bankId', 'bankAccountId', 'hsCodeId', 'applicantBondLicence', 'netWeight', 'grossWeight'],
        ELC: ['lcNo', 'issueDate', 'validityDate', 'shipmentDate', 'masterLcNo', 'masterLcDate', 'tenure', 'paymentTerms', 'deliveryTerms',
              'bankId', 'bankAccountId', 'counterBankId', 'counterBankAccountId', 'foreignBankName', 'foreignBankSwift', 'foreignBankBin',
              'foreignBankRouting', 'partialShipment', 'btmaCertificate'],
        ECI: ['ciKind', 'netWeight', 'grossWeight'],
        IPI: ['validityDate', 'localAgentId'],
        ILC: ['importDocType', 'lcType', 'lcNo', 'issueDate', 'validityDate', 'shipmentDate', 'tenure', 'paymentTerms', 'deliveryTerms', 'bankId',
              'bankAccountId', 'counterBankId', 'foreignBankName', 'foreignBankSwift', 'beneficiaryAccountNo', 'port', 'cnfAgent', 'ipNo',
              'sroBenefited', 'btmaNo', 'btmaDate', 'partialShipment', 'backedByDocumentId']
    };
    const select = (name, label, options, value, blank) => `<div><label class="label" for="ce_${name}">${esc(label)}</label>
        <select id="ce_${name}" class="field" name="${name}">${blank ? `<option value="">${esc(blank)}</option>` : ''}${options.map(o =>
            `<option value="${esc(o.value)}"${String(value ?? '') === String(o.value) ? ' selected' : ''}>${esc(o.label)}</option>`).join('')}</select></div>`;
    const input = (name, label, type, value, extra) => `<div><label class="label" for="ce_${name}">${esc(label)}</label>
        <input id="ce_${name}" class="field" type="${type || 'text'}" name="${name}" value="${esc(value ?? '')}" ${extra || ''}></div>`;
    const check = (name, label, value) => `<label class="flex items-center gap-2 self-end pb-2 text-sm"><input type="checkbox" class="checkbox" name="${name}"${value ? ' checked' : ''}> ${esc(label)}</label>`;
    const remote = (name, label, url, placeholder, span) => `<div class="${span || ''}"><label class="label" for="ce_${name}">${esc(label)}</label>
        <select id="ce_${name}" class="field" name="${name}" data-remote="${esc(url)}" data-allow-clear data-placeholder="${esc(placeholder || 'Choose…')}"></select></div>`;

    function detailField(name, d) {
        const v = d[name];
        switch (name) {
            case 'validityDate': return input(name, LABELS.validityDate[STEP], 'date', v);
            case 'shipmentDate': return input(name, 'Last shipment date', 'date', v);
            case 'issueDate': return input(name, 'LC issued on', 'date', v);
            case 'lcNo': return input(name, STEP === 'ELC' ? 'LC no (BTB LC)' : 'LC / TT no', 'text', v, 'maxlength="80"');
            case 'masterLcNo': return input(name, 'Master LC no', 'text', v, 'maxlength="80"');
            case 'masterLcDate': return input(name, 'Master LC date', 'date', v);
            case 'tenure': return select(name, 'Tenure', CFG.tenures, v, 'Choose…');
            case 'paymentTerms': return select(name, 'Payment', CFG.paymentTerms, v, 'Choose…');
            case 'deliveryTerms': return select(name, 'INCO terms', CFG.incoTerms, v, 'Choose…');
            case 'bankId': return remote(name, LABELS.bankId[STEP], '/api/parties/lookup?role=BANK', 'Choose the bank…');
            case 'bankAccountId': return `<div><label class="label" for="ce_bankAccountId">Our account</label><select id="ce_bankAccountId" class="field" name="bankAccountId"><option value="">Choose the bank first</option></select></div>`;
            case 'counterBankId': return remote(name, LABELS.counterBankId[STEP], '/api/parties/lookup?role=BANK', 'Choose the bank…');
            case 'counterBankAccountId': return `<div><label class="label" for="ce_counterBankAccountId">Buyer's account</label><select id="ce_counterBankAccountId" class="field" name="counterBankAccountId"><option value="">—</option></select></div>`;
            case 'foreignBankName': return input(name, 'Foreign bank', 'text', v, 'maxlength="200"');
            case 'foreignBankSwift': return input(name, 'SWIFT code', 'text', v, 'maxlength="20"');
            case 'foreignBankBin': return input(name, 'Foreign bank BIN', 'text', v, 'maxlength="40"');
            case 'foreignBankRouting': return input(name, 'Routing no', 'text', v, 'maxlength="40"');
            case 'beneficiaryAccountNo': return input(name, 'Beneficiary account no', 'text', v, 'maxlength="60"');
            case 'hsCodeId': return `<div><label class="label" for="ce_hsCodeId">HS code</label><select id="ce_hsCodeId" class="field" name="hsCodeId"><option value="">Choose…</option></select></div>`;
            case 'applicantBondLicence': return input(name, 'Applicant bond licence', 'text', v, 'maxlength="80"');
            case 'netWeight': return input(name, 'Net weight (kg)', 'number', v, `min="0" step="0.001" placeholder="${esc(d.calcNetWeight ? 'Calculated ' + num(d.calcNetWeight) : 'Calculated')}"`);
            case 'grossWeight': return input(name, 'Gross weight (kg)', 'number', v, `min="0" step="0.001" placeholder="${esc(d.calcGrossWeight ? 'Calculated ' + num(d.calcGrossWeight) : 'Calculated')}"`);
            case 'partialShipment': return check(name, 'Partial shipment allowed', v ?? true);
            case 'btmaCertificate': return check(name, 'BTMA certificate', v);
            case 'sroBenefited': return check(name, 'SRO benefited', v);
            case 'ciKind': return select(name, 'CI kind', CFG.ciKinds, v || 'REGULAR');
            case 'importDocType': return select(name, 'Paid by', CFG.importDocTypes, v || 'LC');
            case 'lcType': return select(name, 'LC type', CFG.lcTypes, v, 'Choose…');
            case 'port': return `<div><label class="label" for="ce_port">Port</label><input id="ce_port" class="field" name="port" list="ce_ports" maxlength="30" value="${esc(v || '')}">
                <datalist id="ce_ports">${CFG.ports.map(p => `<option value="${esc(p)}">`).join('')}</datalist></div>`;
            case 'cnfAgent': return input(name, 'C&F agent', 'text', v, 'maxlength="150"');
            case 'ipNo': return input(name, 'IP no', 'text', v, 'maxlength="60"');
            case 'btmaNo': return input(name, 'BTMA no', 'text', v, 'maxlength="60"');
            case 'btmaDate': return input(name, 'BTMA date', 'date', v);
            case 'localAgentId': return remote(name, 'Local agent', '/api/parties/lookup?role=AGENT', 'None');
            case 'backedByDocumentId': return remote(name, 'Back-to-back against export LC', '/api/commercial/export-lcs', 'None', 'sm:col-span-2');
            default: return '';
        }
    }

    const editor = {
        doc: null, parents: new Map(), rows: new Map(), challans: new Map(), picked: new Map(), direct: [],

        mode() {
            if (this.parents.size) return STEP === 'ECI' && this.ciKind() === 'REGULAR' ? 'challans' : 'parent';
            return CFG.direct ? 'direct' : 'parent';
        },
        ciKind() { return $('[name="ciKind"]', headerEl)?.value || 'REGULAR'; },

        async open(doc, presetParent) {
            this.doc = doc || null;
            this.parents = new Map(); this.rows = new Map(); this.challans = new Map(); this.picked = new Map(); this.direct = [];
            $('[data-editor-title]', editorDialog).textContent = doc ? `Edit ${doc.documentNo}` : `New ${CFG.label}`;
            termsEl.value = doc ? (doc.terms || []).join('\n') : '';
            if (viewer.open) viewer.close();
            this.renderHeader(doc);
            editorDialog.showModal();
            if (doc) {
                for (const l of doc.lines || []) {
                    if (l.deliveryLineId) this.picked.set(`D:${l.deliveryLineId}`, { sourceId: l.sourceId, deliveryLineId: l.deliveryLineId, quantity: l.quantity });
                    else if (l.sourceId) this.picked.set(`S:${l.sourceId}`, { sourceId: l.sourceId, quantity: l.quantity, rate: l.rate });
                    else this.direct.push({ itemId: l.itemId, label: `${l.itemCode} · ${l.itemName}`, uom: l.uom, quantity: l.quantity, rate: l.rate, specification: l.specification });
                }
                const ids = new Set((doc.lines || []).map(l => l.sourceDocumentId).filter(Boolean));
                for (const id of ids) await this.addParent(id, true);
            } else if (presetParent) {
                await this.addParent(presetParent, false);
                const sel = $('[name="parentId"]', headerEl);
                if (sel) App.RemoteSelect.of(sel).setValue(presetParent);
            }
            this.renderLines();
        },

        renderHeader(doc) {
            const d = Object.assign({}, doc ? doc.details : {});
            const f = [];
            f.push(`<div class="sm:col-span-2"><label class="label" for="ce_parent">${esc(CFG.parentLabel)}${CFG.direct ? ' <span class="text-gray-400">(or add items)</span>' : ' <span class="req">*</span>'}</label>
                <select id="ce_parent" class="field" name="parentId" data-placeholder="Choose the ${esc(CFG.parentLabel.toLowerCase())}…"></select>
                ${CFG.manyParents ? `<p class="hint">Several may be added - of one ${isImport ? 'supplier' : 'buyer'}, in one currency.</p>` : ''}
                <div class="mt-1 flex flex-wrap gap-1" data-parent-chips></div></div>`);
            if (STEP === 'EPI') f.push(remote('partyId', 'Applicant', '/api/parties/lookup', 'As on the schedule', 'sm:col-span-2'));
            if (STEP === 'IPI') {
                f.push(remote('partyId', 'Supplier *', '/api/parties/lookup?role=SUPPLIER', 'Choose the supplier…', 'sm:col-span-2'));
                f.push(input('referenceNo', "Supplier's PI no", 'text', doc && doc.referenceNo, 'maxlength="100"'));
                f.push(input('currencyCode', 'Currency', 'text', doc ? doc.currency : 'USD', 'maxlength="3"'));
            }
            f.push(input('documentDate', STEP === 'ELC' ? 'Received on' : 'Date', 'date', doc ? doc.documentDate : new Date().toISOString().slice(0, 10)));
            f.push(input('exchangeRate', 'Rate to BDT', 'number', doc ? doc.exchangeRate : '', 'min="0" step="0.000001" placeholder="As on the parent"'));
            for (const name of HEADER[STEP]) {
                if (name === 'ciKind' && doc) continue;
                f.push(detailField(name, d));
            }
            f.push(`<div class="sm:col-span-2 lg:col-span-4"><label class="label" for="ce_remarks">Remarks</label><input id="ce_remarks" class="field" name="remarks" maxlength="1000" value="${esc(doc ? doc.remarks || '' : '')}"></div>`);
            headerEl.innerHTML = f.join('');

            const parent = $('[name="parentId"]', headerEl);
            new App.RemoteSelect(parent, { url: `${CFG.api}/parents`, placeholder: `Choose the ${CFG.parentLabel.toLowerCase()}…`, allowClear: true });
            parent.addEventListener('change', async () => {
                if (!parent.value) return;
                if (!CFG.manyParents) { this.parents.clear(); this.rows.clear(); this.challans.clear(); if (!this.doc) this.picked.clear(); this.direct = []; }
                await this.addParent(Number(parent.value), false);
                App.RemoteSelect.of(parent).setValue(null);
                this.renderLines();
            });
            App.remoteSelects(headerEl);
            const setRemote = (name, id, text) => { const el = $(`[name="${name}"]`, headerEl); if (el && id) App.RemoteSelect.of(el).setValue(id, text); };
            if (doc) {
                setRemote('partyId', doc.partyId, doc.partyName);
                setRemote('bankId', d.bankId, d.bankName);
                setRemote('counterBankId', d.counterBankId, d.counterBankName);
                setRemote('localAgentId', d.localAgentId, d.localAgentName);
                setRemote('backedByDocumentId', d.backedByDocumentId, d.backedByDocumentNo);
            }
            // Accounts follow the bank chosen: ours, and the buyer's.
            const fillAccounts = async (bankName, accountName, owner, selected) => {
                const bank = $(`[name="${bankName}"]`, headerEl), account = $(`[name="${accountName}"]`, headerEl);
                if (!bank || !account) return;
                const ownerId = typeof owner === 'function' ? owner() : owner;
                if (!ownerId) { account.innerHTML = '<option value="">—</option>'; return; }
                try {
                    const list = await api('/api/commercial/accounts', { query: { owner: ownerId, bankId: bank.value || undefined } });
                    account.innerHTML = `<option value="">${list.length ? 'Choose…' : 'No account at this bank'}</option>` + list.map(a =>
                        `<option value="${esc(a.id)}"${String(selected ?? '') === String(a.id) ? ' selected' : ''}>${esc(`${a.accountNumber} · ${a.bankName}${a.branchName ? ', ' + a.branchName : ''}${a.currency ? ' · ' + a.currency : ''}`)}</option>`).join('');
                } catch (error) { fail(error); }
            };
            const ownerOfDoc = () => this.partyId();
            fillAccounts('bankId', 'bankAccountId', 'self', d.bankAccountId);
            fillAccounts('counterBankId', 'counterBankAccountId', ownerOfDoc, d.counterBankAccountId);
            $('[name="bankId"]', headerEl)?.addEventListener('change', () => fillAccounts('bankId', 'bankAccountId', 'self'));
            $('[name="counterBankId"]', headerEl)?.addEventListener('change', () => fillAccounts('counterBankId', 'counterBankAccountId', ownerOfDoc));
            const hs = $('[name="hsCodeId"]', headerEl);
            if (hs) api('/api/lookup/inventory/hs-codes').then(list => {
                hs.innerHTML = '<option value="">Choose…</option>' + list.map(o => `<option value="${esc(o.id)}"${String(d.hsCodeId) === String(o.id) ? ' selected' : ''}>${esc(o.text || o.code)}</option>`).join('');
            }).catch(fail);
            $('[name="ciKind"]', headerEl)?.addEventListener('change', () => { this.picked.clear(); this.renderLines(); });
        },

        partyId() {
            const p = $('[name="partyId"]', headerEl);
            if (p && p.value) return Number(p.value);
            const first = [...this.parents.values()][0];
            return first ? first.partyId : this.doc ? this.doc.partyId : null;
        },

        async addParent(id, keep) {
            try {
                const data = await api(`${CFG.api}/open-lines`, { query: { parentId: id, exclude: this.doc ? this.doc.id : undefined } });
                this.parents.set(id, data.parent);
                (data.lines || []).forEach(r => this.rows.set(r.sourceId, r));
                (data.challans || []).forEach(c => this.challans.set(c.deliveryLineId, c));
                if (!keep && !(data.lines || []).some(r => Number(r.available) > 0)) toast(`Nothing is left open on ${data.parent.documentNo}.`, 'warn');
                const cb = $('[name="counterBankId"]', headerEl);
                if (cb) cb.dispatchEvent(new Event('change'));
            } catch (error) { fail(error); }
        },

        renderLines() {
            const mode = this.mode();
            $('[data-parent-chips]', headerEl).innerHTML = [...this.parents.values()].map(p =>
                `<span class="chip"><span class="font-mono">${esc(p.documentNo)}</span> <span class="text-gray-500">${esc(p.partyName || '')}</span></span>`).join('');
            $('[data-select-all-wrap]', editorDialog).hidden = mode !== 'parent' || !this.rows.size;
            const addWrap = $('[data-add-wrap]', editorDialog);
            addWrap.hidden = mode !== 'direct';
            if (mode === 'direct') prepareAdd();
            $('[data-lines-sub]', editorDialog).textContent = {
                challans: 'The posted delivery challans of the LC\'s PIs, with what is left to invoice of each.',
                parent: this.parents.size ? 'The open lines, with what each still allows.' : `Choose the ${CFG.parentLabel.toLowerCase()}; its open lines are listed.`,
                direct: 'Add items directly, or choose a purchase requisition above.'
            }[mode];
            if (mode === 'challans') linesEl.innerHTML = this.challans.size ? this.challanTable() : '<p class="text-sm text-gray-500">No delivered, uninvoiced challans under this LC. An advance CI bills ahead of delivery.</p>';
            else if (mode === 'parent') linesEl.innerHTML = this.rows.size ? this.parentTable() : '<p class="text-sm text-gray-500">Nothing chosen yet.</p>';
            else linesEl.innerHTML = this.direct.length ? this.directTable() : '<p class="text-sm text-gray-500">Nothing added yet.</p>';
            this.total();
        },

        parentTable() {
            const priceInput = STEP === 'IPI';
            return `<div class="table-wrap"><table class="line-colours line-colours-edit"><thead><tr><th class="w-8"></th><th>Fabric / item</th><th>On</th>
                <th class="num">Quantity</th><th class="num">Taken</th><th class="num">Open</th><th class="num">Rate</th><th class="num">This ${esc(CFG.label)}</th></tr></thead>
                <tbody>${[...this.rows.values()].map(r => {
                    const p = this.picked.get(`S:${r.sourceId}`);
                    const on = !!p, open = Number(r.available);
                    return `<tr data-key="S:${esc(r.sourceId)}" class="${on ? '' : 'opacity-70'}">
                        <td><input type="checkbox" class="checkbox" data-pick${on ? ' checked' : ''}${!on && open <= 0 ? ' disabled' : ''} aria-label="Include"></td>
                        <td class="min-w-[14rem] whitespace-normal">${what(r)}</td><td class="font-mono text-xs">${esc(r.documentNo)}</td>
                        <td class="num">${num(r.cap)} <span class="text-xs text-gray-500">${esc(r.uom || '')}</span></td><td class="num">${num(r.taken)}</td>
                        <td class="num font-medium ${open <= 0 ? 'text-gray-400' : ''}">${num(r.available)}</td>
                        <td class="num">${priceInput ? `<input type="number" min="0" step="0.0001" class="field field-sm w-24 text-right" data-f="rate" value="${esc(p ? p.rate ?? '' : '')}"${on ? '' : ' disabled'}>` : money(r.rate)}</td>
                        <td class="num"><input type="number" min="0" step="0.0001" class="field field-sm w-28 text-right" data-f="quantity" value="${esc(p ? p.quantity : '')}"${on ? '' : ' disabled'}></td></tr>`;
                }).join('')}</tbody></table></div>`;
        },

        challanTable() {
            return `<div class="table-wrap"><table class="line-colours line-colours-edit"><thead><tr><th class="w-8"></th><th>Challan</th><th>Fabric</th>
                <th class="num">Delivered</th><th class="num">Invoiced</th><th class="num">Open</th><th class="num">Rate</th><th class="num">Invoice</th></tr></thead>
                <tbody>${[...this.challans.values()].map(c => {
                    const p = this.picked.get(`D:${c.deliveryLineId}`);
                    const on = !!p, open = Number(c.available);
                    const rate = (this.rows.get(c.lcLineId) || {}).rate;
                    return `<tr data-key="D:${esc(c.deliveryLineId)}" class="${on ? '' : 'opacity-70'}">
                        <td><input type="checkbox" class="checkbox" data-pick${on ? ' checked' : ''}${!on && open <= 0 ? ' disabled' : ''} aria-label="Include"></td>
                        <td class="text-xs"><span class="font-mono">${esc(c.challanNo)}</span><div class="text-gray-500">${esc(date(c.challanDate) || '')}</div></td>
                        <td class="min-w-[14rem] whitespace-normal">${what(c)}</td>
                        <td class="num">${num(c.quantity)} <span class="text-xs text-gray-500">${esc(c.uom || '')}</span></td><td class="num">${num(c.invoiced)}</td>
                        <td class="num font-medium ${open <= 0 ? 'text-gray-400' : ''}">${num(c.available)}</td><td class="num">${money(rate)}</td>
                        <td class="num"><input type="number" min="0" step="0.0001" class="field field-sm w-28 text-right" data-f="quantity" value="${esc(p ? p.quantity : '')}"${on ? '' : ' disabled'}></td></tr>`;
                }).join('')}</tbody></table></div>`;
        },

        directTable() {
            return `<div class="table-wrap"><table class="line-colours line-colours-edit"><thead><tr><th>Item</th><th class="num">Quantity</th><th class="num">Unit price</th><th>Specification</th><th class="w-px"></th></tr></thead>
                <tbody>${this.direct.map((e, i) => `<tr data-index="${i}"><td class="min-w-[14rem]"><span class="font-medium">${esc(e.label)}</span> <span class="text-xs text-gray-500">${esc(e.uom || '')}</span></td>
                    <td class="num"><input type="number" min="0" step="0.0001" class="field field-sm w-28 text-right" data-f="quantity" value="${esc(e.quantity ?? '')}"></td>
                    <td class="num"><input type="number" min="0" step="0.0001" class="field field-sm w-28 text-right" data-f="rate" value="${esc(e.rate ?? '')}"></td>
                    <td><input class="field field-sm w-48" maxlength="500" data-f="specification" value="${esc(e.specification ?? '')}"></td>
                    <td><button type="button" class="btn-icon btn-sm" data-remove="${i}" aria-label="Remove">${icon('trash')}</button></td></tr>`).join('')}</tbody></table></div>`;
        },

        total() {
            let qty = 0, amount = 0;
            const mode = this.mode();
            if (mode === 'direct') this.direct.forEach(e => { qty += Number(e.quantity) || 0; amount += (Number(e.quantity) || 0) * (Number(e.rate) || 0); });
            else for (const [key, p] of this.picked) {
                const q = Number(p.quantity) || 0;
                const rate = key.startsWith('D:') ? (this.rows.get((this.challans.get(p.deliveryLineId) || {}).lcLineId) || {}).rate
                    : STEP === 'IPI' ? p.rate : (this.rows.get(p.sourceId) || {}).rate;
                qty += q; amount += q * (Number(rate) || 0);
            }
            $('[data-editor-total]', editorDialog).textContent = nf.format(qty);
            $('[data-editor-amount]', editorDialog).textContent = mf.format(amount);
        },

        payload() {
            const v = n => { const el = $(`[name="${n}"]`, headerEl); return el ? (el.type === 'checkbox' ? el.checked : (el.value || null)) : null; };
            const n = x => x === '' || x == null ? null : Number(x);
            const details = {};
            for (const name of HEADER[STEP]) details[name] = v(name);
            ['bankId', 'bankAccountId', 'counterBankId', 'counterBankAccountId', 'hsCodeId', 'netWeight', 'grossWeight', 'localAgentId', 'backedByDocumentId']
                .forEach(k => { if (k in details) details[k] = n(details[k]); });
            if (this.doc && STEP === 'ECI') details.ciKind = (this.doc.details || {}).ciKind;
            const mode = this.mode();
            const lines = mode === 'direct'
                ? this.direct.map(e => ({ itemId: e.itemId, quantity: n(e.quantity), rate: n(e.rate), specification: e.specification || null }))
                : [...this.picked.values()].filter(p => mode === 'challans' ? p.deliveryLineId && this.challans.has(p.deliveryLineId) : this.rows.has(p.sourceId))
                    .map(p => ({ sourceId: p.sourceId, deliveryLineId: p.deliveryLineId || null, quantity: n(p.quantity), rate: STEP === 'IPI' ? n(p.rate) : null }));
            const terms = termsEl.value.split('\n').map(t => t.trim()).filter(Boolean);
            return {
                id: this.doc ? this.doc.id : null, documentDate: v('documentDate'), partyId: n(v('partyId')),
                currencyCode: v('currencyCode'), exchangeRate: n(v('exchangeRate')), referenceNo: v('referenceNo'), remarks: v('remarks'),
                details, lines, terms: this.doc || terms.length ? terms : null
            };
        }
    };

    let addRemote = null;
    function prepareAdd() {
        if (addRemote) return;
        const sel = $('[data-add-line]', editorDialog);
        addRemote = new App.RemoteSelect(sel, { url: '/api/supply/items', placeholder: 'Add an item…', params: () => ({ stockItems: false }) });
        sel.addEventListener('change', () => {
            const o = addRemote.selected;
            if (!o) return;
            addRemote.setValue(null);
            if (editor.direct.some(e => e.itemId === o.id)) return toast(`${o.text} is already on the list.`, 'warn');
            editor.direct.push({ itemId: o.id, label: o.text, uom: o.uom, quantity: '', rate: o.costPrice || '' });
            editor.renderLines();
        });
    }

    linesEl.addEventListener('change', event => {
        const tr = event.target.closest('tr');
        if (!tr) return;
        if (tr.dataset.index != null) {
            const e = editor.direct[Number(tr.dataset.index)];
            if (e && event.target.dataset.f) { e[event.target.dataset.f] = event.target.value; editor.total(); }
            return;
        }
        const key = tr.dataset.key;
        if (!key) return;
        if (event.target.matches('[data-pick]')) {
            if (event.target.checked) {
                if (key.startsWith('D:')) {
                    const c = editor.challans.get(Number(key.slice(2)));
                    editor.picked.set(key, { sourceId: c.lcLineId, deliveryLineId: c.deliveryLineId, quantity: Number(c.available) || '' });
                } else {
                    const r = editor.rows.get(Number(key.slice(2)));
                    editor.picked.set(key, { sourceId: r.sourceId, quantity: Number(r.available) || '', rate: STEP === 'IPI' ? '' : r.rate });
                }
            } else editor.picked.delete(key);
            return editor.renderLines();
        }
        const p = editor.picked.get(key);
        if (p && event.target.dataset.f) { p[event.target.dataset.f] = event.target.value; editor.total(); }
    });
    linesEl.addEventListener('input', event => {
        const tr = event.target.closest('tr');
        const f = event.target.dataset.f;
        if (!tr || !f) return;
        const target = tr.dataset.index != null ? editor.direct[Number(tr.dataset.index)] : editor.picked.get(tr.dataset.key);
        if (target) { target[f] = event.target.value; editor.total(); }
    });
    linesEl.addEventListener('click', event => {
        const b = event.target.closest('[data-remove]');
        if (!b) return;
        editor.direct.splice(Number(b.dataset.remove), 1);
        editor.renderLines();
    });
    $('[data-select-all]', editorDialog).addEventListener('change', event => {
        for (const r of editor.rows.values()) {
            const key = `S:${r.sourceId}`;
            if (event.target.checked && !editor.picked.has(key) && Number(r.available) > 0) {
                editor.picked.set(key, { sourceId: r.sourceId, quantity: Number(r.available), rate: STEP === 'IPI' ? '' : r.rate });
            } else if (!event.target.checked) editor.picked.delete(key);
        }
        editor.renderLines();
    });

    form.addEventListener('submit', async event => {
        event.preventDefault();
        const body = editor.payload();
        if (!body.lines.length) return toast('Choose at least one line.', 'warn');
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
        if ((editor.picked.size || editor.direct.length) && !await App.confirm({ title: 'Close without saving?',
            message: 'The lines you chose will be lost.', confirmText: 'Close', danger: true })) return;
        editorDialog.close();
    }));
    editorDialog.addEventListener('cancel', event => { event.preventDefault(); $('[data-editor-close]', editorDialog).click(); });
    $$('[data-com-new]').forEach(b => b.addEventListener('click', () => editor.open(null)));

    // Raised from a parent: /export-lc?new=1&parent=12
    const params = new URLSearchParams(location.search);
    if (params.get('new') && CFG.canCreate) editor.open(null, Number(params.get('parent')) || null);
});
