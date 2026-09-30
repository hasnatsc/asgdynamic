/*
 * Fabrics production report (ProductionReportController): one row per production order with a bar
 * for every stage, KPIs over the filtered set, sort, pages, column choice, the colour-line breakdown
 * of an order, and the printout - the same filter and sort sent to /production/report/export, which
 * fills the Jasper template as PDF or Excel.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const root = document.querySelector('[data-production-report]');
    if (!root) return;
    const { api, esc, fmt, fail, icon } = App;
    const $ = (sel, r) => (r || root).querySelector(sel);
    const $$ = (sel, r) => Array.from((r || root).querySelectorAll(sel));
    const nf = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 });
    const num = v => v == null || v === '' ? '—' : nf.format(Number(v));
    const n = v => Number(v) || 0;

    /** Stage columns: key, title, and the figure's done/of wording for the tooltip. */
    const STAGES = [
        ['lc', 'LC progress', 'on export LCs of the order'],
        ['weaving', 'Weaving', 'greige on weaving work orders of the greige needed'],
        ['processing', 'Processing', 'on dyeing work orders of the part that is dyed'],
        ['greigeReceived', 'Greige received', 'greige received of the greige needed'],
        ['greigeIssued', 'Issue for processing', 'greige issued of the greige the dyed part needs'],
        ['finished', 'Finished goods', 'finished received of the dyed part'],
        ['delivery', 'Delivery progress', 'delivered of the quantity on delivery orders']
    ];
    /** Columns a user may hide; the choice is kept per browser. */
    const OPTIONAL = [['bpoDate', 'BPO date'], ['marketingPerson', 'Marketing person'], ['garments', 'Garments'], ['dispoNo', 'Dispo no'],
        ...STAGES.map(([k, t]) => [k, t]), ['due', 'Due date']];
    const HIDDEN_KEY = 'productionReport.hidden';
    let hidden = new Set(JSON.parse(localStorage.getItem(HIDDEN_KEY) || '[]'));

    const state = { page: 0, size: 10, sort: 'bpoDate', dir: 'desc', total: 0, rows: [] };
    const person = App.RemoteSelect.of($('[data-person]'));
    const garments = App.RemoteSelect.of($('[data-garments]'));

    function filters() {
        return {
            q: $('[data-q]').value.trim() || undefined,
            personId: $('[data-person]').value || undefined,
            garmentsId: $('[data-garments]').value || undefined,
            from: $('[data-from]').value || undefined,
            to: $('[data-to]').value || undefined
        };
    }

    // ------------------------------------------------------------------------------ loading

    let draw = 0;
    async function load() {
        const mine = ++draw;
        const tbody = $('[data-rows] tbody');
        tbody.style.opacity = '.55';
        try {
            const data = await api('/api/production/report', {
                query: Object.assign({ page: state.page, size: state.size, sort: state.sort, dir: state.dir }, filters()) });
            if (mine !== draw) return;
            state.total = n(data.total);
            state.rows = data.rows || [];
            kpis(data.totals || {});
            tbody.innerHTML = state.rows.length ? state.rows.map((r, i) => rowHtml(r, state.page * state.size + i + 1)).join('')
                : `<tr><td colspan="16" class="whitespace-normal"><div class="empty"><span class="empty-icon">${icon('search', 'icon-lg')}</span>
                    <p class="empty-title">No production orders match</p><p class="empty-text">Clear a filter, or widen the date range.</p></div></td></tr>`;
            applyColumns();
            pager();
            sortMarks();
        } catch (error) {
            if (mine !== draw) return;
            tbody.innerHTML = `<tr><td colspan="16"><div class="empty"><p class="empty-title">Could not load the report</p>
                <p class="empty-text">${esc(error.message)}</p></div></td></tr>`;
        } finally {
            if (mine === draw) tbody.style.opacity = '';
        }
    }
    const reload = () => { state.page = 0; load(); };

    // --------------------------------------------------------------------------------- KPIs

    function kpis(t) {
        const unit = t.uom ? ` <span class="pr-kpi-unit">${esc(t.uom)}</span>` : '';
        // Shares of the order quantity: an LC may cover only part of the orders, and "972% of LC" says nothing.
        const base = n(t.quantity);
        const share = v => base > 0 ? `${Math.round(n(v) * 100 / base)}% of order quantity` : '—';
        const thisMonth = n(t.thisMonth), lastMonth = n(t.lastMonth);
        const trend = lastMonth > 0
            ? (() => { const pct = Math.round((thisMonth - lastMonth) * 100 / lastMonth);
                       return `<span class="${pct >= 0 ? 'text-emerald-700' : 'text-red-700'}">${pct >= 0 ? '↑' : '↓'} ${Math.abs(pct)}% vs last month</span>`; })()
            : `${nf.format(thisMonth)} this month`;
        const cards = [
            ['document', 'Total BPO', nf.format(n(t.orders)), trend, 'red'],
            ['credit-card', 'Total LC quantity', num(t.lcQuantity) + unit,
                n(t.lcQuantity) > 0 ? `${share(t.lcQuantity)} (${num(t.quantity)})` : `No export LC yet · orders ${num(t.quantity)}`, 'rose'],
            ['loom', 'In production', num(t.inProduction) + unit, `Greige received · ${share(t.inProduction)}`, 'green'],
            ['package', 'Finished goods', num(t.finished) + unit, share(t.finished), 'amber'],
            ['truck', 'Delivered', num(t.delivered) + unit, share(t.delivered), 'blue'],
            ['clock', 'Pending delivery', num(t.pending) + unit,
                `${share(t.pending)}${n(t.overdue) ? ` · <span class="text-red-700">${nf.format(n(t.overdue))} overdue</span>` : ''}`, 'violet']
        ];
        $('[data-kpis]').innerHTML = cards.map(([ic, label, value, meta, tone]) => `<div class="pr-kpi">
            <span class="pr-kpi-icon" data-tone="${tone}">${icon(ic)}</span>
            <div class="min-w-0"><p class="pr-kpi-label">${esc(label)}</p>
                <p class="pr-kpi-value">${value}</p><p class="pr-kpi-meta">${meta}</p></div></div>`).join('');
    }

    // --------------------------------------------------------------------------------- rows

    const band = pct => pct >= 100 ? 'done' : pct >= 70 ? 'good' : pct >= 40 ? 'mid' : 'low';

    function stageCell(r, [key, title, words]) {
        const pct = r[key + 'Pct'];
        if (pct == null) {
            const why = key === 'delivery' ? 'No delivery order yet' : 'Not dyed: delivered as greige';
            return `<td class="pr-cell" data-col="${key}"><span class="text-gray-400" title="${esc(why)}">—</span></td>`;
        }
        const done = r[key + 'Done'], of = r[key + 'Of'];
        return `<td class="pr-cell" data-col="${key}" title="${esc(`${title}: ${num(done)} ${words} ${num(of)}`)}">
            <div class="pr-progress" data-band="${band(pct)}">
                <span class="pr-track"><span class="pr-fill" style="width:${Math.min(100, Math.max(0, pct))}%"></span></span>
                <b>${esc(pct)}%</b></div>
            <div class="pr-figure">${num(done)} / ${num(of)}</div></td>`;
    }

    const DUE = { COMPLETED: ['check-circle', 'green'], OVERDUE: ['alert', 'red'], DUE_SOON: ['clock', 'amber'],
                  ON_TIME: ['check-circle', 'green'], NO_DUE_DATE: ['calendar', 'gray'] };

    function rowHtml(r, sl) {
        const [dueIcon, tone] = DUE[r.dueState] || DUE.NO_DUE_DATE;
        const title = r.buyer ? `Buyer: ${r.buyer}` : '';
        return `<tr data-id="${esc(r.bpoId)}">
            <td class="text-gray-500 tabular-nums" data-col="sl">${sl}</td>
            <td data-col="bpoNo"><a class="doc-no whitespace-nowrap" href="/bpo?open=${esc(r.bpoId)}" title="${esc(title)}">${esc(r.bpoNo)}</a></td>
            <td class="whitespace-nowrap" data-col="bpoDate">${esc(fmt.date(r.bpoDate))}</td>
            <td data-col="marketingPerson">${esc(r.marketingPerson || '—')}</td>
            <td class="min-w-[9rem]" data-col="garments">${esc(r.garments || '—')}</td>
            <td class="min-w-[6rem]" data-col="dispoNo">${esc(r.dispoNo || '—')}</td>
            ${STAGES.map(s => stageCell(r, s)).join('')}
            <td class="whitespace-nowrap" data-col="due">${esc(r.dueDate ? fmt.date(r.dueDate) : '—')}
                <span class="pr-due" data-tone="${tone}">${icon(dueIcon, 'h-3 w-3')}${esc(r.dueText || '')}</span></td>
            <td class="text-right">
                <div class="flex items-center justify-end gap-1">
                    <a class="btn-icon btn-sm" href="/bpo?open=${esc(r.bpoId)}" title="View the production order" aria-label="View ${esc(r.bpoNo)}">${icon('eye')}</a>
                    <button type="button" class="btn-icon btn-sm" data-breakdown-open="${esc(r.bpoId)}" title="Colour lines, stage by stage"
                            aria-label="Colour lines of ${esc(r.bpoNo)}">${icon('chart-bar')}</button>
                    <details class="relative" data-menu>
                        <summary class="btn-icon btn-sm list-none" aria-label="More for ${esc(r.bpoNo)}" title="More">${icon('more')}</summary>
                        <div class="menu w-56" role="menu">
                            <a class="menu-item" role="menuitem" href="/bpo?open=${esc(r.bpoId)}">${icon('planning')}Open production order</a>
                            <a class="menu-item" role="menuitem" href="/production/board?q=${encodeURIComponent(r.bpoNo)}">${icon('activity')}On the production board</a>
                            <button type="button" class="menu-item" role="menuitem" data-print-one="${esc(r.bpoId)}">${icon('document')}Print this order (PDF)</button>
                        </div>
                    </details>
                </div>
            </td>
        </tr>`;
    }

    // ------------------------------------------------------------------- sort, pages, columns

    function sortMarks() {
        $$('[data-sort]').forEach(b => {
            const on = b.dataset.sort === state.sort;
            b.closest('th').setAttribute('aria-sort', on ? (state.dir === 'asc' ? 'ascending' : 'descending') : 'none');
            b.dataset.dir = on ? state.dir : '';
        });
    }

    root.addEventListener('click', event => {
        const sort = event.target.closest('[data-sort]');
        if (sort) {
            const key = sort.dataset.sort;
            state.dir = state.sort === key && state.dir === 'asc' ? 'desc' : state.sort === key ? 'asc' : (key === 'bpoDate' || key === 'dueDate' ? 'desc' : 'asc');
            state.sort = key;
            return reload();
        }
        const page = event.target.closest('[data-page]');
        if (page && !page.disabled) {
            state.page = Number(page.dataset.page);
            load();
            return;
        }
        const exp = event.target.closest('[data-export]');
        if (exp) {
            exp.closest('details')?.removeAttribute('open');
            return exportReport(exp.dataset.export);
        }
        const one = event.target.closest('[data-print-one]');
        if (one) {
            one.closest('details')?.removeAttribute('open');
            return exportReport('pdf', { bpoId: one.dataset.printOne });
        }
        const open = event.target.closest('[data-breakdown-open]');
        if (open) return breakdown(Number(open.dataset.breakdownOpen));
    });

    function pager() {
        const pages = Math.max(1, Math.ceil(state.total / state.size));
        const from = state.total ? state.page * state.size + 1 : 0, to = Math.min((state.page + 1) * state.size, state.total);
        $('[data-showing]').textContent = `Showing ${nf.format(from)} to ${nf.format(to)} of ${nf.format(state.total)} entries`;
        const cur = state.page;
        const list = [...new Set([0, cur - 1, cur, cur + 1, pages - 1].filter(p => p >= 0 && p < pages))].sort((a, b) => a - b);
        const buttons = [];
        buttons.push(`<button type="button" class="pr-page" data-page="${cur - 1}" aria-label="Previous page" ${cur === 0 ? 'disabled' : ''}>${icon('chevron-left')}</button>`);
        list.forEach((p, i) => {
            if (i && p - list[i - 1] > 1) buttons.push('<span class="px-1 text-gray-400">…</span>');
            buttons.push(`<button type="button" class="pr-page" data-page="${p}" ${p === cur ? 'aria-current="page"' : ''}>${p + 1}</button>`);
        });
        buttons.push(`<button type="button" class="pr-page" data-page="${cur + 1}" aria-label="Next page" ${cur >= pages - 1 ? 'disabled' : ''}>${icon('chevron-right')}</button>`);
        $('[data-pages]').innerHTML = buttons.join('');
    }

    function columnsMenu() {
        $('[data-columns]').innerHTML = `<p class="px-2 pb-1 pt-1 text-xs font-medium text-gray-500">Show columns</p>` + OPTIONAL.map(([key, label]) =>
            `<label class="flex cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-gray-50 dark:hover:bg-gray-800">
                <input type="checkbox" class="checkbox" data-toggle-col="${key}" ${hidden.has(key) ? '' : 'checked'}>${esc(label)}</label>`).join('');
    }

    function applyColumns() {
        $$('[data-col]', $('[data-rows]')).forEach(cell => { cell.hidden = hidden.has(cell.dataset.col); });
        // The group header spans only the stages still shown.
        const group = $('[data-group="production"]');
        const shown = STAGES.slice(0, 6).filter(([k]) => !hidden.has(k)).length;
        group.hidden = shown === 0;
        group.colSpan = Math.max(shown, 1);
    }

    $('[data-columns]').addEventListener('change', event => {
        const box = event.target.closest('[data-toggle-col]');
        if (!box) return;
        box.checked ? hidden.delete(box.dataset.toggleCol) : hidden.add(box.dataset.toggleCol);
        localStorage.setItem(HIDDEN_KEY, JSON.stringify([...hidden]));
        applyColumns();
    });

    // ---------------------------------------------------------------------------- filters

    $('[data-q]').addEventListener('input', App.debounce(reload, 250));
    ['[data-person]', '[data-garments]', '[data-from]', '[data-to]'].forEach(sel => $(sel).addEventListener('change', reload));
    $('[data-size]').addEventListener('change', event => { state.size = Number(event.target.value); reload(); });
    $('[data-reset]').addEventListener('click', () => {
        $('[data-q]').value = '';
        $('[data-from]').value = '';
        $('[data-to]').value = '';
        person.setValue(null);
        garments.setValue(null);
        state.sort = 'bpoDate';
        state.dir = 'desc';
        reload();
    });

    // ------------------------------------------------------------------------------- export

    /** Opens the Jasper printout with the screen's own filter and sort; Excel downloads, PDF opens in a tab. */
    function exportReport(format, extra) {
        const params = new URLSearchParams({ format, sort: state.sort, dir: state.dir });
        Object.entries(Object.assign(extra ? {} : filters(), extra || {})).forEach(([k, v]) => { if (v !== undefined && v !== '') params.set(k, v); });
        const url = `/production/report/export?${params}`;
        if (format === 'pdf') {
            window.open(url, '_blank', 'noopener');
        } else {
            const a = document.createElement('a');
            a.href = url;
            a.rel = 'noopener';
            document.body.append(a);
            a.click();
            a.remove();
        }
        if (!extra && state.total > n(root.dataset.printLimit)) {
            App.toast(`The printout holds the first ${nf.format(n(root.dataset.printLimit))} of ${nf.format(state.total)} orders. Narrow the filter for the rest.`, 'warn');
        }
    }

    // ---------------------------------------------------------------------------- breakdown

    const LINE_STAGES = [
        ['Weaving', l => l.weaving, l => l.greigeRequired],
        ['Greige received', l => l.greigeReceived, l => l.greigeRequired],
        ['Processing', l => l.needsProcessing ? l.dyeing : null, l => l.needsProcessing ? l.quantity : null],
        ['Issued', l => l.needsProcessing ? l.greigeIssued : null, l => l.needsProcessing ? l.greigeRequired : null],
        ['Finished', l => l.needsProcessing ? n(l.finishedA) + n(l.finishedB) : null, l => l.needsProcessing ? l.quantity : null],
        ['Delivered', l => l.delivered, l => n(l.onDeliveryOrders) > 0 ? l.onDeliveryOrders : null]
    ];

    async function breakdown(bpoId) {
        const dialog = $('[data-breakdown]');
        const row = state.rows.find(r => Number(r.bpoId) === bpoId) || {};
        $('[data-breakdown-title]', dialog).textContent = `${row.bpoNo || 'Production order'} · colour lines`;
        $('[data-breakdown-sub]', dialog).textContent = [row.garments, row.buyer, row.dueDate && `Due ${fmt.date(row.dueDate)}`].filter(Boolean).join(' · ');
        const body = $('[data-breakdown-body]', dialog);
        body.innerHTML = '<div class="space-y-3">' + '<div class="skeleton h-5 w-2/3"></div>'.repeat(4) + '</div>';
        dialog.showModal();
        try {
            const lines = await api(`/api/production/report/${bpoId}/lines`);
            body.innerHTML = lines.length ? `<div class="table-wrap"><table class="table-grid pr-table">
                <thead><tr><th>Fabric · colour</th><th class="text-right">Ordered</th>${LINE_STAGES.map(([t]) => `<th>${esc(t)}</th>`).join('')}</tr></thead>
                <tbody>${lines.map(l => `<tr>
                    <td class="min-w-[10rem]"><span class="font-mono text-xs">${esc(l.construction || '')}</span>
                        <div class="font-medium">${esc(l.colorName || l.colorCode || 'Colour')}</div>
                        ${l.shortClosed ? '<span class="badge-amber">Short-closed</span>' : ''}</td>
                    <td class="num">${num(l.quantity)} <span class="text-xs text-gray-500">${esc(l.uom || '')}</span></td>
                    ${LINE_STAGES.map(([t, done, of]) => {
                        const o = of(l);
                        if (o == null || n(o) <= 0) return '<td class="pr-cell"><span class="text-gray-400">—</span></td>';
                        const d = n(done(l)), pct = Math.round(d * 100 / n(o));
                        return `<td class="pr-cell"><div class="pr-progress" data-band="${band(pct)}"><span class="pr-track">
                            <span class="pr-fill" style="width:${Math.min(100, pct)}%"></span></span><b>${pct}%</b></div>
                            <div class="pr-figure">${num(d)} / ${num(o)}</div></td>`;
                    }).join('')}</tr>`).join('')}</tbody></table></div>`
                : '<p class="text-sm text-gray-500">This order has no colour lines.</p>';
        } catch (error) {
            body.innerHTML = '';
            fail(error);
        }
    }

    columnsMenu();
    load();
});
