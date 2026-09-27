/*
 * Item stock (balances, the item ledger, the monthly stock report) and Inventory periods. Each page
 * marks its root with data-stock-page; everything here is read-only except closing and reopening
 * a month, which the server guards.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const root = document.querySelector('[data-stock-page]');
    if (!root) return;
    const { api, esc, fmt, toast, fail, icon } = App;
    const $ = sel => root.querySelector(sel);
    const nf = new Intl.NumberFormat(undefined, { maximumFractionDigits: 3 });
    const mf = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const num = v => v == null || v === '' ? '—' : nf.format(Number(v));
    const money = v => v == null || v === '' ? '—' : mf.format(Number(v));
    const n = v => Number(v) || 0;
    const TYPES = { RECEIPT: 'MRR receipt', PURCHASE_RETURN: 'Purchase return', ISSUE: 'Issue', DIRECT_RECEIVE: 'Direct receive',
        TRANSFER_OUT: 'Transfer out', TRANSFER_IN: 'Transfer in', ADJUST_IN: 'Adjustment (added)', ADJUST_OUT: 'Adjustment (taken)',
        REVERSAL: 'Reversal' };

    // ================================================================================ item stock
    if (root.dataset.stockPage === 'items') {
        App.tabs(root.querySelector('[data-tabs-root]'), tab => { if (tab === 'monthly') monthly(); });
        const tbody = $('[data-rows] tbody');
        const pager = $('[data-pager]');
        const size = 50;
        let page = 0, total = 0, draw = 0, lastRows = [];

        const params = () => ({ q: $('[data-q]').value, warehouseId: $('[data-store]').value || undefined,
            itemType: $('[data-type]').value || undefined, lowOnly: $('[data-low]').checked, withZero: $('[data-zero]').checked });

        async function load() {
            const mine = ++draw;
            tbody.style.opacity = '.55';
            try {
                const data = await api('/api/stock/items', { query: Object.assign({ page, size }, params()) });
                if (mine !== draw) return;
                total = n(data.total);
                lastRows = data.rows || [];
                const t = data.totals || {};
                $('[data-kpis]').innerHTML = [
                    ['Stock value (BDT)', money(t.value), (data.byStore || []).slice(0, 2).map(s => `${s.store} ${money(s.value)}`).join(' · ')],
                    ['Items in stock', num(t.items)], ['Store lines', num(t.lines)],
                    ['At or below reorder level', num(t.low), 'needs buying', n(t.low) ? 'text-red-700' : '']
                ].map(([label, value, meta, tone]) => `<div class="kpi"><span class="kpi-label">${esc(label)}</span>
                    <span class="kpi-value ${tone || ''}">${esc(value)}</span>${meta ? `<span class="kpi-meta">${esc(meta)}</span>` : ''}</div>`).join('');
                tbody.innerHTML = lastRows.length ? lastRows.map(r => `<tr>
                    <td>${esc(r.store)}</td>
                    <td><span class="font-medium">${esc(r.itemName)}</span> <span class="font-mono text-xs text-gray-500">${esc(r.itemCode)}</span>
                        ${r.category ? `<div class="text-xs text-gray-500">${esc(r.category)}</div>` : ''}</td>
                    <td class="text-xs">${esc(r.itemType)}</td>
                    <td class="text-right tabular-nums font-medium">${num(r.quantity)} <span class="text-xs text-gray-500">${esc(r.uom || '')}</span>
                        ${r.low ? ' <span class="badge-red">Low</span>' : ''}</td>
                    <td class="text-right tabular-nums">${money(r.averageCost)}</td>
                    <td class="text-right tabular-nums">${money(r.value)}</td>
                    <td class="text-right tabular-nums">${num(r.reorderLevel)}</td>
                    <td class="text-xs">${r.lastMoved ? fmt.timeTag(r.lastMoved) : '—'}</td>
                    <td>${App.rowActions(App.rowButton('Ledger', 'history', `data-item="${esc(r.itemId)}" data-wh="${esc(r.warehouseId)}"
                        data-label="${esc(`${r.itemCode} · ${r.itemName} · ${r.store}`)}"`))}</td></tr>`).join('')
                    : `<tr><td colspan="9"><div class="empty"><span class="empty-icon">${icon('inventory', 'icon-lg')}</span>
                        <p class="empty-text">No item stock matches these filters. Stock arrives with an MRR, a direct receive or a transfer.</p></div></td></tr>`;
                const from = total ? page * size + 1 : 0, to = Math.min((page + 1) * size, total);
                pager.innerHTML = `<span><b class="font-medium">${from}–${to}</b> of <b class="font-medium">${total}</b></span>
                    <div class="flex items-center gap-1">
                        <button type="button" class="btn-icon btn-sm border border-gray-200 dark:border-gray-700" data-page="-1" aria-label="Previous page" ${page === 0 ? 'disabled' : ''}>${icon('chevron-left')}</button>
                        <button type="button" class="btn-icon btn-sm border border-gray-200 dark:border-gray-700" data-page="1" aria-label="Next page" ${to >= total ? 'disabled' : ''}>${icon('chevron-right')}</button></div>`;
            } catch (error) {
                if (mine === draw) tbody.innerHTML = `<tr><td colspan="9"><div class="empty"><p class="empty-title">Could not load stock</p><p class="empty-text">${esc(error.message)}</p></div></td></tr>`;
            } finally {
                tbody.style.opacity = '';
            }
        }
        const reload = () => { page = 0; load(); };
        $('[data-q]').addEventListener('input', App.debounce(reload, 250));
        ['[data-store]', '[data-type]', '[data-low]', '[data-zero]'].forEach(s => $(s).addEventListener('change', reload));
        pager.addEventListener('click', e => {
            const b = e.target.closest('[data-page]');
            if (!b || b.disabled) return;
            page = Math.max(0, page + Number(b.dataset.page));
            load();
        });
        $('[data-csv]').addEventListener('click', async () => {
            try {
                const data = await api('/api/stock/items', { query: Object.assign({ page: 0, size: 200 }, params()) });
                App.downloadCsv('item-stock.csv', [['Store', 'Item code', 'Item', 'Type', 'Unit', 'On hand', 'Average cost', 'Value', 'Reorder level']]
                    .concat((data.rows || []).map(r => [r.store, r.itemCode, r.itemName, r.itemType, r.uom, r.quantity, r.averageCost, r.value, r.reorderLevel])));
                if (n(data.total) > 200) toast('The first 200 lines were exported; narrow the filters for the rest.', 'warn');
            } catch (error) { fail(error); }
        });
        load();

        // ------------------------------------------------------------------------- the ledger
        const drawer = root.querySelector('[data-ledger]');
        const yearStart = new Date().getFullYear() + '-01-01';
        let ledgerFor = null;
        async function ledger() {
            const body = drawer.querySelector('[data-ledger-body]');
            body.innerHTML = '<div class="skeleton h-5 w-2/3"></div>';
            try {
                const l = await api(`/api/stock/items/${ledgerFor.item}/ledger`, { query: { warehouseId: ledgerFor.wh,
                    from: drawer.querySelector('[data-ledger-from]').value || undefined, to: drawer.querySelector('[data-ledger-to]').value || undefined } });
                const uom = l.item.uom || '';
                body.innerHTML = `<dl class="mb-4 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                        <div><dt class="text-xs text-gray-500">Brought forward</dt><dd class="font-medium tabular-nums">${num(l.openingQuantity)} ${esc(uom)}<br><span class="text-xs text-gray-500">${money(l.openingValue)}</span></dd></div>
                        <div><dt class="text-xs text-gray-500">In</dt><dd class="font-medium tabular-nums text-emerald-700">${num(l.in)}</dd></div>
                        <div><dt class="text-xs text-gray-500">Out</dt><dd class="font-medium tabular-nums text-red-700">${num(l.out)}</dd></div>
                        <div><dt class="text-xs text-gray-500">Balance</dt><dd class="font-medium tabular-nums">${num(l.closingQuantity)} ${esc(uom)}<br><span class="text-xs text-gray-500">${money(l.closingValue)}</span></dd></div></dl>
                    ${l.moves.length ? `<div class="table-wrap"><table class="table-grid whitespace-nowrap text-sm"><thead><tr><th>Date</th><th>Document</th><th>Move</th>
                        <th class="text-right">In</th><th class="text-right">Out</th><th class="text-right">Cost</th><th class="text-right">Balance</th><th class="text-right">Value</th></tr></thead>
                        <tbody>${l.moves.map(m => `<tr>
                            <td>${esc(fmt.date(m.moveDate))}</td>
                            <td>${m.slug ? `<a class="card-link" href="/${esc(m.slug)}?open=${esc(m.documentId)}">${esc(m.documentNo)}</a>` : esc(m.documentNo)}
                                <div class="text-xs text-gray-500">${esc([m.party, m.store, m.postedBy].filter(Boolean).join(' · '))}</div></td>
                            <td class="text-xs">${esc(TYPES[m.moveType] || m.moveType)}${m.remarks ? `<div class="max-w-[14rem] truncate text-gray-500" title="${esc(m.remarks)}">${esc(m.remarks)}</div>` : ''}</td>
                            <td class="text-right tabular-nums text-emerald-700">${n(m.quantity) > 0 ? num(m.quantity) : ''}</td>
                            <td class="text-right tabular-nums text-red-700">${n(m.quantity) < 0 ? num(-n(m.quantity)) : ''}</td>
                            <td class="text-right tabular-nums">${money(m.unitCost)}</td>
                            <td class="text-right tabular-nums font-medium">${num(m.balance)}</td>
                            <td class="text-right tabular-nums">${money(m.balanceValue)}</td></tr>`).join('')}</tbody></table></div>`
                        : '<p class="text-sm text-gray-500">No moves in these dates.</p>'}`;
            } catch (error) { body.innerHTML = ''; fail(error); }
        }
        root.addEventListener('click', e => {
            const b = e.target.closest('[data-item]');
            if (!b) return;
            ledgerFor = { item: b.dataset.item, wh: b.dataset.wh };
            drawer.querySelector('[data-ledger-sub]').textContent = b.dataset.label;
            drawer.querySelector('[data-ledger-from]').value = yearStart;
            drawer.querySelector('[data-ledger-to]').value = '';
            drawer.showModal();
            ledger();
        });
        drawer.querySelectorAll('input[type=date]').forEach(i => i.addEventListener('change', () => ledgerFor && ledger()));

        // -------------------------------------------------------------------- monthly report
        let monthlyData = null;
        async function monthly() {
            const body = $('[data-monthly] tbody');
            body.style.opacity = '.55';
            try {
                monthlyData = await api('/api/stock/items/monthly', { query: { month: $('[data-month]').value || undefined,
                    warehouseId: $('[data-m-store]').value || undefined, itemType: $('[data-m-type]').value || undefined } });
                const rows = monthlyData.rows || [];
                body.innerHTML = rows.length ? rows.map(r => `<tr>
                    <td>${esc(r.store)}</td>
                    <td><span class="font-medium">${esc(r.itemName)}</span> <span class="font-mono text-xs text-gray-500">${esc(r.itemCode)}</span>
                        <span class="text-xs text-gray-500">${esc(r.uom || '')}</span></td>
                    ${['opening', 'in', 'out', 'closing'].map(k => `<td class="text-right tabular-nums">${num(r[k + 'Quantity'])}</td>
                        <td class="text-right tabular-nums text-gray-600">${money(r[k + 'Value'])}</td>`).join('')}</tr>`).join('')
                    : `<tr><td colspan="10"><div class="empty"><p class="empty-text">Nothing was held or moved in ${esc(monthlyData.label)}.</p></div></td></tr>`;
                const t = monthlyData.totals || {};
                $('[data-monthly] tfoot').innerHTML = rows.length ? `<tr class="font-semibold"><td colspan="2">Total value (BDT)</td>
                    ${['opening', 'in', 'out', 'closing'].map(k => `<td></td><td class="text-right tabular-nums">${money(t[k + 'Value'])}</td>`).join('')}</tr>` : '';
            } catch (error) { fail(error); } finally { body.style.opacity = ''; }
        }
        ['[data-month]', '[data-m-store]', '[data-m-type]'].forEach(s => $(s).addEventListener('change', monthly));
        $('[data-m-csv]').addEventListener('click', () => {
            if (!monthlyData) return;
            App.downloadCsv(`monthly-stock-${monthlyData.month}.csv`, [['Store', 'Item code', 'Item', 'Unit', 'Opening qty', 'Opening value',
                'Received qty', 'Received value', 'Issued qty', 'Issued value', 'Closing qty', 'Closing value']]
                .concat((monthlyData.rows || []).map(r => [r.store, r.itemCode, r.itemName, r.uom, r.openingQuantity, r.openingValue,
                    r.inQuantity, r.inValue, r.outQuantity, r.outValue, r.closingQuantity, r.closingValue])));
        });
    }

    // =================================================================================== periods
    if (root.dataset.stockPage === 'periods') {
        let year = Number(root.dataset.year);
        const canClose = root.dataset.canClose === 'true';
        const canReopen = root.dataset.canReopen === 'true';
        const tbody = $('[data-periods] tbody');
        async function load() {
            $('[data-year-label]').textContent = year;
            try {
                const months = await api('/api/stock/periods', { query: { year } });
                tbody.innerHTML = months.map(m => {
                    const closed = m.status === 'CLOSED';
                    const action = closed
                        ? (canReopen ? `<button type="button" class="btn-ghost btn-sm" data-reopen="${esc(m.month)}" data-label="${esc(m.label)}">${icon('unlock')}Reopen</button>` : '')
                        : (canClose ? `<button type="button" class="btn-ghost btn-sm" data-close-month="${esc(m.month)}" data-label="${esc(m.label)}">${icon('lock')}Close</button>` : '');
                    return `<tr>
                        <td class="font-medium">${esc(m.label)}</td>
                        <td>${closed ? '<span class="badge-gray">Closed</span>' : '<span class="badge-green">Open</span>'}</td>
                        <td class="text-right tabular-nums">${num(m.moves)}</td>
                        <td class="text-right tabular-nums ${n(m.drafts) ? 'text-amber-700' : ''}">${num(m.drafts)}</td>
                        <td class="text-xs">${m.closedBy ? `${esc(m.closedBy)} · ${fmt.timeTag(m.closedAt)}` : '—'}</td>
                        <td class="text-xs">${m.reopenedBy ? `${esc(m.reopenedBy)} · ${fmt.timeTag(m.reopenedAt)}` : '—'}</td>
                        <td class="max-w-[16rem] truncate text-xs text-gray-600" title="${esc(m.remarks || '')}">${esc(m.remarks || '')}</td>
                        <td class="text-right">${action}</td></tr>`;
                }).join('');
            } catch (error) { fail(error); }
        }
        root.addEventListener('click', async e => {
            const step = e.target.closest('[data-year-step]');
            if (step) { year += Number(step.dataset.yearStep); return load(); }
            const close = e.target.closest('[data-close-month]');
            const reopen = e.target.closest('[data-reopen]');
            if (!close && !reopen) return;
            const b = close || reopen;
            const values = await App.form({
                title: `${close ? 'Close' : 'Reopen'} ${b.dataset.label}?`,
                message: close ? 'Nothing can be posted into it afterwards - no receipt, issue, transfer, adjustment or cancellation - until it is reopened.'
                               : 'Postings dated in it are accepted again. The reason is kept on the period.',
                fields: [{ name: close ? 'remarks' : 'reason', label: close ? 'Remarks (optional)' : 'Reason', type: 'textarea', required: !close, maxlength: 400 }],
                confirmText: close ? 'Close month' : 'Reopen', danger: !!reopen });
            if (!values) return;
            try {
                await api(`/api/stock/periods/${close ? b.dataset.closeMonth : b.dataset.reopen}/${close ? 'close' : 'reopen'}`, { method: 'POST', query: values });
                toast(close ? 'Month closed.' : 'Month reopened.', 'success');
                load();
            } catch (error) { fail(error); }
        });
        load();
    }
});
