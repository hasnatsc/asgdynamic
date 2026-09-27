/*
 * The commercial register (the legacy PI, LC and CI reports and dashboard) and commercial setup
 * (document names, cost heads). Each page marks its root with data-commercial-screen.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const root = document.querySelector('[data-commercial-screen]');
    if (!root) return;
    const { api, esc, fmt, toast, fail, icon } = App;
    const $ = sel => root.querySelector(sel);
    const nf = new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 });
    const mf = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const num = v => v == null || v === '' ? '—' : nf.format(Number(v));
    const money = v => v == null || v === '' ? '—' : mf.format(Number(v));
    const date = v => v ? fmt.date(v) : '—';
    const link = (slug, id, text) => `<a class="doc-no" href="/${slug}?open=${esc(id)}">${esc(text)}</a>`;

    // =============================================================================== register
    if (root.dataset.commercialScreen === 'register') {
        const MILESTONES = { CED: 'CED', CNF: 'C&F', BOND: 'Bond', PI_CORRECTED: 'PI corr.', LC_DRAFT: 'LC draft', LC_CORRECTED: 'LC corr.' };
        const REGISTERS = {
            'export-lc': {
                head: ['LC', 'LC no', 'Buyer', 'Issued', 'Expiry', 'Days left', 'Value', 'Invoiced', 'Realized', 'UD', 'UP', 'BTB', 'Incentive', 'Status'],
                row: r => [link('export-lc', r.id, r.documentNo) + (r.revisionNo ? ` <span class="badge-gray">A${esc(r.revisionNo)}</span>` : ''),
                    `<span class="font-mono">${esc(r.lcNo || '—')}</span>${r.masterLcNo ? `<div class="text-xs text-gray-500">Master ${esc(r.masterLcNo)}</div>` : ''}`,
                    esc(r.buyer || '—'), esc(date(r.issueDate)), esc(date(r.validityDate)),
                    r.daysToExpiry == null ? '—' : `<span class="${r.daysToExpiry < 0 ? 'text-red-700' : r.daysToExpiry <= 15 ? 'text-amber-700' : ''}">${num(r.daysToExpiry)}</span>`,
                    `${money(r.lcValue)} <span class="text-xs text-gray-500">${esc(r.currency)}</span>`, money(r.invoiced), money(r.realized),
                    money(r.udValue), money(r.upValue), money(r.btbValue), money(r.incentiveAmount), App.statusBadge(r.status)],
                csv: r => [r.documentNo, r.lcNo, r.masterLcNo, r.buyer, r.issueDate, r.validityDate, r.daysToExpiry, r.currency, r.lcValue, r.invoiced,
                    r.realized, r.udValue, r.upValue, r.btbValue, r.incentiveAmount, r.status],
                csvHead: ['LC', 'LC no', 'Master LC', 'Buyer', 'Issued', 'Expiry', 'Days left', 'Currency', 'Value', 'Invoiced', 'Realized', 'UD', 'UP', 'BTB', 'Incentive', 'Status']
            },
            'export-ci': {
                head: ['CI', 'LC', 'Buyer', 'Date', 'Kind', 'Value', 'Realization', 'Matures', 'Realized', 'IBC', 'Status'],
                row: r => [link('export-ci', r.id, r.documentNo), `<span class="font-mono">${esc(r.lcNo || r.lcDocumentNo || '—')}</span>`, esc(r.buyer || '—'),
                    esc(date(r.documentDate)), esc(r.ciKind === 'ADVANCE' ? 'Advance' : 'Regular'), `${money(r.subtotalAmount)} <span class="text-xs text-gray-500">${esc(r.currency)}</span>`,
                    esc((r.realizationStep || 'Not submitted').replaceAll('_', ' ').toLowerCase()),
                    r.maturityDue ? `<span class="${r.overdue ? 'font-medium text-red-700' : ''}">${esc(date(r.maturityDue))}${r.overdue ? ' · overdue' : ''}</span>` : '—',
                    r.realized ? `${money(r.realized)} <div class="text-xs text-gray-500">${esc(date(r.realizedOn))}</div>` : '—', esc(r.ibcNo || '—'), App.statusBadge(r.status)],
                csv: r => [r.documentNo, r.lcNo, r.buyer, r.documentDate, r.ciKind, r.currency, r.subtotalAmount, r.realizationStep, r.maturityDue, r.realized, r.realizedOn, r.ibcNo, r.status],
                csvHead: ['CI', 'LC no', 'Buyer', 'Date', 'Kind', 'Currency', 'Value', 'Realization', 'Matures', 'Realized', 'Realized on', 'IBC', 'Status']
            },
            'export-pi': {
                head: ['PI', 'Date', 'Applicant', 'Brand', 'Schedules', 'Quantity', 'Value', 'Validity', 'LCs', 'Status'],
                row: r => [link('export-pi', r.id, r.documentNo) + (r.revisionNo ? ` <span class="badge-gray">A${esc(r.revisionNo)}</span>` : ''), esc(date(r.documentDate)),
                    esc(r.applicant || '—'), esc(r.brand || '—'), `<span class="text-xs">${esc(r.schedules || '—')}</span>`, num(r.totalQuantity),
                    `${money(r.subtotalAmount)} <span class="text-xs text-gray-500">${esc(r.currency)}</span>`, esc(date(r.validityDate)),
                    `<span class="font-mono text-xs">${esc(r.lcs || '—')}</span>`, App.statusBadge(r.status)],
                csv: r => [r.documentNo, r.documentDate, r.applicant, r.brand, r.schedules, r.totalQuantity, r.currency, r.subtotalAmount, r.validityDate, r.lcs, r.status],
                csvHead: ['PI', 'Date', 'Applicant', 'Brand', 'Schedules', 'Quantity', 'Currency', 'Value', 'Validity', 'LCs', 'Status']
            },
            'import-pi': {
                head: ['PI', 'Supplier PI', 'Date', 'Supplier', 'Local agent', 'Value', 'Checklist', 'LC / PO', 'Status'],
                row: r => [link('import-pi', r.id, r.documentNo), esc(r.supplierPiNo || '—'), esc(date(r.documentDate)), esc(r.supplier || '—'), esc(r.localAgent || '—'),
                    `${money(r.subtotalAmount)} <span class="text-xs text-gray-500">${esc(r.currency)}</span>`,
                    (r.milestones || '').split(',').filter(Boolean).map(m => `<span class="badge-green">${esc(MILESTONES[m] || m)}</span>`).join(' ') || '—',
                    `<span class="font-mono text-xs">${esc(r.raised || '—')}</span>`, App.statusBadge(r.status)],
                csv: r => [r.documentNo, r.supplierPiNo, r.documentDate, r.supplier, r.localAgent, r.currency, r.subtotalAmount, r.milestones, r.raised, r.status],
                csvHead: ['PI', 'Supplier PI', 'Date', 'Supplier', 'Local agent', 'Currency', 'Value', 'Checklist', 'LC / PO', 'Status']
            },
            'import-lc': {
                head: ['LC', 'LC no', 'Supplier', 'Type', 'Issued', 'Expiry', 'Port', 'Value', 'Costs (BDT)', 'Bill of entry', 'Backed by', 'Status'],
                row: r => [link('import-lc', r.id, r.documentNo), `<span class="font-mono">${esc(r.lcNo || '—')}</span>`, esc(r.supplier || '—'),
                    esc([r.importDocType, r.lcType].filter(Boolean).join(' · ') || '—'), esc(date(r.issueDate)), esc(date(r.validityDate)), esc(r.port || '—'),
                    `${money(r.subtotalAmount)} <span class="text-xs text-gray-500">${esc(r.currency)}</span>`, money(r.costs),
                    r.billOfEntryNo ? `${esc(r.billOfEntryNo)}<div class="text-xs text-gray-500">${esc(date(r.billOfEntryDate))}</div>` : '—',
                    esc(r.backedBy || '—'), App.statusBadge(r.status)],
                csv: r => [r.documentNo, r.lcNo, r.supplier, r.importDocType, r.lcType, r.issueDate, r.validityDate, r.port, r.currency, r.subtotalAmount, r.costs,
                    r.billOfEntryNo, r.billOfEntryDate, r.backedBy, r.status],
                csvHead: ['LC', 'LC no', 'Supplier', 'Doc type', 'LC type', 'Issued', 'Expiry', 'Port', 'Currency', 'Value', 'Costs', 'Bill of entry', 'BoE date', 'Backed by', 'Status']
            }
        };
        let current = 'export-lc', rows = [];
        const table = $('[data-register]');
        async function load() {
            const reg = REGISTERS[current];
            table.querySelector('thead').innerHTML = `<tr>${reg.head.map(h => `<th>${esc(h)}</th>`).join('')}</tr>`;
            table.style.opacity = '.55';
            try {
                rows = await api(`/api/commercial/register/${current}`, { query: { q: $('[data-q]').value || undefined,
                    from: $('[data-from]').value || undefined, to: $('[data-to]').value || undefined } });
                table.querySelector('tbody').innerHTML = rows.length ? rows.map(r => `<tr>${reg.row(r).map(c => `<td>${c}</td>`).join('')}</tr>`).join('')
                    : `<tr><td colspan="${reg.head.length}"><div class="empty"><span class="empty-icon">${icon('search', 'icon-lg')}</span><p class="empty-text">Nothing matches.</p></div></td></tr>`;
            } catch (error) { fail(error); } finally { table.style.opacity = ''; }
        }
        App.tabs(root.querySelector('[data-tabs-root]'), tab => { current = tab; load(); });
        $('[data-q]').addEventListener('input', App.debounce(load, 250));
        [$('[data-from]'), $('[data-to]')].forEach(el => el.addEventListener('change', load));
        $('[data-csv]').addEventListener('click', () => {
            const reg = REGISTERS[current];
            App.downloadCsv(`commercial-${current}.csv`, [reg.csvHead].concat(rows.map(reg.csv)));
        });
        api('/api/commercial/register/summary').then(s => {
            $('[data-kpis]').innerHTML = [
                ['PIs awaiting LC', money(s.piOpenValue)], ['LCs open', money(s.lcOpenValue)],
                ['LCs expiring in 15 days', num(s.lcExpiring), '', Number(s.lcExpiring) ? 'text-amber-700' : ''],
                ['CIs to realize', num(s.ciUnrealized)], ['Value to realize', money(s.ciUnrealizedValue)],
                ['Import LCs open (BDT)', money(s.importLcValueBdt)], ['Import PIs in hand', num(s.importPiOpen)]
            ].map(([l, v, meta, tone]) => `<div class="kpi"><span class="kpi-label">${esc(l)}</span><span class="kpi-value ${tone || ''}">${esc(v)}</span>
                ${meta ? `<span class="kpi-meta">${esc(meta)}</span>` : ''}</div>`).join('');
        }).catch(fail);
        load();
    }

    // ================================================================================== setup
    if (root.dataset.commercialScreen === 'setup') {
        const canEdit = root.dataset.canEdit === 'true', canDelete = root.dataset.canDelete === 'true';
        const KINDS = [{ value: 'LC', label: 'LC' }, { value: 'PI', label: 'PI' }, { value: 'CI', label: 'CI' }];
        let data = { documentNames: [], costHeads: [] };
        const actions = (kind, id) => `${canEdit ? App.rowButton('Edit', 'edit', `data-edit="${kind}" data-id="${esc(id)}"`) : ''}
            ${canDelete ? App.rowButton('Delete', 'trash', `data-delete="${kind}" data-id="${esc(id)}"`) : ''}`;
        async function load() {
            try { data = await api('/api/commercial/setup'); } catch (error) { return fail(error); }
            $('[data-names] tbody').innerHTML = data.documentNames.map(n => `<tr><td class="font-mono text-xs">${esc(n.code)}</td><td>${esc(n.name)}</td>
                <td>${esc(n.docKind)}</td><td class="text-right">${num(n.sortOrder)}</td><td>${n.active ? '<span class="badge-green">Yes</span>' : '<span class="badge-gray">No</span>'}</td>
                <td>${App.rowActions(actions('document-names', n.id))}</td></tr>`).join('');
            $('[data-heads] tbody').innerHTML = data.costHeads.map(h => `<tr><td class="font-mono text-xs">${esc(h.code)}</td><td>${esc(h.name)}</td>
                <td>${esc(h.docKind)}</td><td class="font-mono text-xs">${esc(h.accountCode || '—')}</td><td>${h.active ? '<span class="badge-green">Yes</span>' : '<span class="badge-gray">No</span>'}</td>
                <td>${App.rowActions(actions('cost-heads', h.id))}</td></tr>`).join('');
        }
        async function edit(kind, id) {
            const names = kind === 'document-names';
            const item = id ? (names ? data.documentNames : data.costHeads).find(x => String(x.id) === String(id)) : {};
            const fields = [{ name: 'name', label: 'Name', required: true, maxlength: 150, value: item.name || '' },
                { name: 'docKind', label: 'For', type: 'select', options: KINDS, value: item.docKind || 'LC' },
                names ? { name: 'sortOrder', label: 'Order', type: 'number', value: item.sortOrder ?? 0 }
                      : { name: 'accountCode', label: 'Ledger account code (optional)', maxlength: 40, value: item.accountCode || '' },
                { name: 'active', label: 'Active', type: 'select', options: [{ value: 'true', label: 'Yes' }, { value: 'false', label: 'No' }],
                  value: item.active === false ? 'false' : 'true' }];
            const values = await App.form({ title: `${id ? 'Edit' : 'Add'} ${names ? 'document name' : 'cost head'}`, fields, confirmText: 'Save' });
            if (!values) return;
            values.active = values.active === 'true';
            if (names) values.sortOrder = Number(values.sortOrder) || 0;
            try {
                await api(`/api/commercial/setup/${kind}`, { method: 'POST', query: id ? { id } : undefined, body: values });
                toast('Saved.', 'success');
                load();
            } catch (error) { fail(error); }
        }
        root.addEventListener('click', async e => {
            const add = e.target.closest('[data-add]');
            if (add) return edit(add.dataset.add, null);
            const ed = e.target.closest('[data-edit]');
            if (ed) return edit(ed.dataset.edit, ed.dataset.id);
            const del = e.target.closest('[data-delete]');
            if (del) {
                if (!await App.confirm({ title: 'Delete it?', message: 'If a document has used it, it is retired instead, so that document still reads.',
                    confirmText: 'Delete', danger: true })) return;
                try {
                    const r = await api(`/api/commercial/setup/${del.dataset.delete}/${del.dataset.id}`, { method: 'DELETE' });
                    toast(r.outcome === 'retired' ? 'In use - retired instead.' : 'Deleted.', 'success');
                    load();
                } catch (error) { fail(error); }
            }
        });
        load();
    }
});
