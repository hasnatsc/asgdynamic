/*
 * The production dashboard (templates/production/dashboard.html). One request fills the page
 * (ProductionDashboardService); the drawer asks for the documents behind a figure. Charts are the
 * AnalyticsCharts kit - its colour roles, hover and keyboard tooltips and table views - and the
 * pipeline, status board, timeline and calendar are plain HTML on the same tokens.
 *
 * Every figure opens what it came from: a stage or KPI its documents, a fabric type or colour
 * filters the page by it, a card, bar, alert or calendar line its document.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const root = document.querySelector('[data-pd-root]');
    if (!root) return;
    const { api, esc, fmt, fail, icon } = App;
    const C = window.AnalyticsCharts;
    const $ = (sel, r) => (r || root).querySelector(sel);
    const $$ = (sel, r) => [...(r || root).querySelectorAll(sel)];
    const nf = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 });
    const n = v => Number(v) || 0;
    const num = v => v == null || v === '' ? '—' : nf.format(n(v));
    const pct = v => v == null ? '—' : `${Number(v).toFixed(1)}%`;
    /** A date from the server - "2026-09-28", a timestamp, or null - as yyyy-mm-dd. */
    const iso = v => v == null ? null : typeof v === 'number' ? new Date(v).toISOString().slice(0, 10) : String(v).slice(0, 10);
    const day = v => (iso(v) ? fmt.date(iso(v)) : '—');
    const STEP = Object.fromEntries((window.PD_STEPS || []).map(s => [s.key, s]));
    const docUrl = (slug, id) => `/${slug}?open=${encodeURIComponent(id)}`;
    const docLink = (slug, id, no) => `<a class="font-medium text-brand-700 hover:underline" href="${docUrl(slug, id)}">${esc(no || '#' + id)}</a>`;

    const form = $('[data-pd-filters]');
    const state = { data: null, month: null, orderPage: 0, orderSort: { key: 'requiredDate', dir: 1 }, severity: '', day: null, draw: 0 };

    // =========================================================================== filters

    App.remoteSelects(form);
    const buyer = App.RemoteSelect.of($('[name="buyerId"]', form));
    const order = App.RemoteSelect.of($('[name="bpoId"]', form));

    function filters() {
        const v = name => form.elements[name].value || undefined;
        return { from: v('from'), to: v('to'), fabricType: v('fabricType'), color: v('color'),
            buyerId: v('buyerId'), bpoId: v('bpoId'), status: v('status') };
    }

    /** The filters live in the address, so a view can be bookmarked and shared. */
    function syncUrl() {
        const q = new URLSearchParams();
        Object.entries(filters()).forEach(([k, v]) => { if (v) q.set(k, v); });
        if (state.month) q.set('month', state.month);
        history.replaceState(null, '', `${location.pathname}${q.toString() ? '?' + q : ''}${location.hash}`);
    }

    async function restoreFromUrl() {
        const q = new URLSearchParams(location.search);
        ['from', 'to', 'status'].forEach(k => { if (q.get(k)) form.elements[k].value = q.get(k); });
        if (q.get('month')) state.month = q.get('month');
        if (q.get('buyerId')) await buyer.setValue(q.get('buyerId'));
        if (q.get('bpoId')) await order.setValue(q.get('bpoId'));
        return q;
    }

    async function loadOptions(q) {
        try {
            const o = await api('/api/production/dashboard/options');
            const fill = (select, values, all) => {
                select.innerHTML = `<option value="">${esc(all)}</option>` + values.map(v => `<option value="${esc(v)}">${esc(v)}</option>`).join('');
            };
            fill(form.elements.fabricType, o.fabricTypes, 'All fabric types');
            fill(form.elements.color, o.colours, 'All colours');
            ['fabricType', 'color'].forEach(k => {
                const v = q.get(k);
                if (v && ![...form.elements[k].options].some(op => op.value === v)) form.elements[k].add(new Option(v, v));
                if (v) form.elements[k].value = v;
            });
        } catch (error) { fail(error); }
    }

    /** Sets one filter from a chart or table and reloads. */
    function applyFilter(name, value) {
        const el = form.elements[name];
        if (value && ![...el.options].some(o => o.value === value)) el.add(new Option(value, value));
        el.value = value || '';
        state.orderPage = 0;
        load();
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    form.addEventListener('change', () => { state.orderPage = 0; load(); });
    $('[data-pd-reset]').addEventListener('click', () => {
        form.reset();
        buyer.setValue(null);
        order.setValue(null);
        state.month = null;
        load();
    });
    $('[data-pd-refresh]').addEventListener('click', () => load());
    $('[data-pd-print]').addEventListener('click', () => window.print());

    // =========================================================================== load

    async function load() {
        syncUrl();
        const mine = ++state.draw;
        root.querySelectorAll('.an-chart').forEach(h => h.setAttribute('data-loading', ''));
        try {
            const data = await api('/api/production/dashboard', { query: Object.assign(filters(), { month: state.month || undefined }) });
            if (mine !== state.draw) return;
            state.data = data;
            state.month = state.month || data.calendar.month;
            render();
        } catch (error) {
            if (mine === state.draw) fail(error);
        } finally {
            if (mine === state.draw) root.querySelectorAll('.an-chart').forEach(h => h.removeAttribute('data-loading'));
        }
    }

    function render() {
        const d = state.data;
        renderScope(d);
        renderKpis(d);
        renderPipeline(d.pipeline);
        renderPlan(d.byFabricType);
        renderPending(d.pending);
        renderAlerts(d.alerts);
        renderEfficiency(d.efficiency, d.flow);
        renderBoard(d.statusBoard);
        renderOrders();
        renderGantt(d.orders, d.today);
        renderWorkOrders(d.workOrders);
        renderFlow(d.flow);
        renderGroups(d.byFabricType, d.byColour);
        renderStock(d.stock, d.movements);
        renderCalendar(d.calendar, d.today);
    }

    function renderScope(d) {
        const orders = d.orders.length;
        const f = filters();
        const chips = [];
        if (f.from || f.to) chips.push(`Ordered ${f.from ? day(f.from) : 'any time'} – ${f.to ? day(f.to) : 'today'}`);
        if (f.fabricType) chips.push(f.fabricType);
        if (f.color) chips.push(f.color);
        if (f.buyerId) chips.push(buyer.selected?.text || 'One customer');
        if (f.bpoId) chips.push(order.selected?.text || 'One order');
        $('[data-pd-scope]').innerHTML = `<span><b class="text-gray-800 dark:text-gray-100">${nf.format(orders)}</b> production order(s),
            ${nf.format(d.lineCount)} colour line(s)</span>${chips.map(c => `<span class="an-scope">${esc(c)}</span>`).join('')}
            ${d.truncated ? '<span class="badge-amber">Showing the first 5,000 lines - narrow the filters</span>' : ''}`;
    }

    // =========================================================================== KPIs

    function renderKpis(d) {
        const k = d.kpis;
        const uom = d.orders[0]?.uom || '';
        const tile = (key, label, value, meta, action, tone) => `<button type="button" class="pd-kpi" data-kpi="${key}" data-action="${esc(action)}">
            <span class="an-kpi-label">${esc(label)}</span>
            <span class="an-kpi-value ${tone || ''}">${esc(value)}</span>
            <span class="an-kpi-meta">${meta}</span></button>`;
        const kpiOverdue = n(k.overdue.orders);
        $('[data-pd-kpis]').innerHTML = [
            tile('bookings', 'Bookings', num(k.bookings.count),
                `${num(k.bookings.quantity)} ${esc(uom)} booked · <b>${num(k.bookings.awaitingOrder)}</b> still to order`, 'drill:BOOKING'),
            tile('orders', 'Production orders', num(k.orders.count),
                `${num(k.orders.open)} open · ${num(k.orders.quantity)} ${esc(uom)} · ${pct(ratio(k.orders.delivered, k.orders.quantity))} delivered`, 'tab:orders'),
            tile('weaving', 'Weaving WOs', num(k.weaving.documents),
                `${num(k.weaving.open)} open · ${num(k.weaving.done)} of ${num(k.weaving.ordered)} received`, 'drill:WWO'),
            tile('dyeing', 'Dyeing WOs', num(k.dyeing.documents),
                `${num(k.dyeing.open)} open · ${num(k.dyeing.done)} of ${num(k.dyeing.ordered)} finished`, 'drill:PWO'),
            tile('greige', 'Greige stock', num(k.greigeStock.quantity),
                `${num(k.greigeStock.free)} free · ${num(k.greigeStock.reserved)} reserved · ${num(k.greigeStock.lots)} lot(s)`, 'tab:stock'),
            tile('finished', 'Finished stock', num(k.finishedStock.quantity),
                `A ${num(k.finishedStock.gradeA)} · B ${num(k.finishedStock.gradeB)} · ${num(k.finishedStock.reserved)} reserved`, 'tab:stock'),
            tile('pending', 'Pending delivery', num(k.pendingDelivery.quantity),
                `${num(k.pendingDelivery.lines)} schedule line(s) · ${num(k.pendingDelivery.deliverable)} deliverable now`, 'tab:deliveries'),
            tile('overdue', 'Overdue orders', num(kpiOverdue),
                `${num(k.overdue.balance)} ${esc(uom)} late · ${num(k.overdue.late)} more at risk`, 'alerts', kpiOverdue ? 'text-red-700 dark:text-red-400' : '')
        ].join('');
    }

    const ratio = (a, b) => n(b) > 0 ? n(a) * 100 / n(b) : null;

    root.addEventListener('click', e => {
        const kpi = e.target.closest('[data-kpi]');
        if (kpi) {
            const [kind, arg] = kpi.dataset.action.split(':');
            if (kind === 'drill') return drillDocuments(arg, '');
            if (kind === 'tab') return tabs.select(arg);
            if (kind === 'alerts') { state.severity = ''; renderAlerts(state.data.alerts); return tabs.select('alerts'); }
        }
        const go = e.target.closest('[data-goto]');
        if (go) return tabs.select(go.dataset.goto);
        const drill = e.target.closest('[data-drill]');
        if (drill) return drillDocuments(drill.dataset.drill, drill.dataset.group || '');
    });

    // =========================================================================== pipeline

    function renderPipeline(nodes) {
        const by = Object.fromEntries(nodes.map(x => [x.key, x]));
        const arrow = `<span class="pd-arrow" aria-hidden="true">${icon('chevron-right')}</span>`;
        const node = x => {
            if (!x) return '<span></span>';
            const flags = [
                n(x.awaiting) ? `<span class="badge-amber" title="Draft or awaiting approval">${num(x.awaiting)} pending</span>` : '',
                n(x.stuck) ? `<span class="badge-red" title="Waiting for approval more than 3 days">${num(x.stuck)} stuck</span>` : ''
            ].join('');
            return `<button type="button" class="pd-node" data-drill="${esc(x.key)}" aria-label="${esc(x.label)}: ${num(x.documents)} documents, ${esc(x.measure)} ${num(x.quantity)}">
                <span class="pd-node-head"><span>${esc(x.label)}</span><span class="pd-node-docs">${num(x.documents)} doc${n(x.documents) === 1 ? '' : 's'}</span></span>
                <span class="pd-node-qty">${num(x.quantity)}</span>
                <span class="pd-node-sub">${esc(x.measure)}</span>
                ${x.next ? `<span class="pd-node-sub">${esc(x.next.label)}: <b class="text-gray-700 dark:text-gray-200">${num(x.next.value)}</b></span>` : ''}
                ${flags ? `<span class="mt-1 flex flex-wrap gap-1">${flags}</span>` : ''}</button>`;
        };
        const lane = keys => `<div class="pd-lane">${keys.map((k, i) => (i ? arrow : '') + node(by[k])).join('')}${keys.length < 3 ? arrow.replace('pd-arrow', 'pd-arrow invisible') + '<span></span>' : ''}</div>`;
        $('[data-pd-pipeline]').innerHTML = `${node(by.BOOKING)}${arrow}${node(by.BPO)}${arrow}
            <div class="pd-lanes">
                <div><p class="mb-1 text-[11px] font-semibold uppercase tracking-wide text-gray-500">Weaving → greige store</p>${lane(['WWO', 'GR'])}</div>
                <div><p class="mb-1 text-[11px] font-semibold uppercase tracking-wide text-gray-500">Dyeing → finished store</p>${lane(['PWO', 'GI', 'FFR'])}</div>
                <div><p class="mb-1 text-[11px] font-semibold uppercase tracking-wide text-gray-500">Delivery</p>${lane(['RPI', 'DO', 'FD'])}</div>
            </div>`;
    }

    // =========================================================================== charts with a table view

    /** A chart card whose Table button swaps the chart for the same numbers as a table. */
    function chartCard(name, drawChart, tableSpec) {
        const card = $(`[data-chart="${name}"]`);
        const host = $('[data-host]', card);
        const toggle = $('[data-table-toggle]', card);
        const draw = () => (card.dataset.asTable === 'true' ? C.table(host, tableSpec()) : drawChart(host));
        if (toggle && !toggle._wired) {
            toggle._wired = true;
            toggle.addEventListener('click', () => {
                card.dataset.asTable = card.dataset.asTable === 'true' ? 'false' : 'true';
                toggle.textContent = card.dataset.asTable === 'true' ? 'Chart' : 'Table';
                card._draw();
            });
        }
        card._draw = draw;
        draw();
    }

    // Planned -> produced -> received -> delivered are stages of one flow: the ordinal ramp, light to dark.
    const PLAN_SERIES = [
        { key: 'quantity', name: 'Planned', cls: 'o2' },
        { key: 'greigeReceived', name: 'Produced (greige)', cls: 'o3' },
        { key: 'produced', name: 'Received (delivering store)', cls: 'o4' },
        { key: 'delivered', name: 'Delivered', cls: 'o5' }];

    function renderPlan(rows) {
        const top = rows.slice(0, 12);
        chartCard('plan', host => C.columns(host, {
            title: 'Planned vs produced vs received vs delivered by fabric type',
            categories: top.map(r => r.key), grouped: true, height: 260,
            series: PLAN_SERIES.map(s => ({ name: s.name, cls: s.cls, values: top.map(r => n(r[s.key])) })),
            emptyText: 'No production orders match these filters.',
            onSelect: i => applyFilter('fabricType', top[i].key === 'Not set' ? '' : top[i].key)
        }), () => ({
            columns: [{ label: 'Fabric type', get: r => r.key }].concat(PLAN_SERIES.map(s => ({ label: s.name, get: r => num(r[s.key]), num: true })))
                .concat([{ label: 'Delivered %', get: r => pct(ratio(r.delivered, r.quantity)), num: true }]),
            rows
        }));
    }

    function renderPending(rows) {
        chartCard('pending', host => C.hbars(host, {
            title: 'Pending quantity by stage',
            rows: rows.map(r => ({ label: r.label, value: n(r.value), key: r.key })),
            cls: 's1', valueLabel: 'Pending',
            emptyText: 'Nothing pending on the open orders.',
            onSelect: r => drillPending(r.key, r.label)
        }), () => ({ columns: [{ label: 'Stage', get: r => r.label }, { label: 'Pending', get: r => num(r.value), num: true }], rows }));
    }

    /** The open orders with something pending at a stage - worked out from the order rows the page already has. */
    function drillPending(key, label) {
        const open = o => ['APPROVED', 'PARTIAL', 'PROCESSING'].includes(o.status);
        const left = {
            weave: o => n(o.greigeRequired) - n(o.weaving),
            receive: o => n(o.weaving) - n(o.greigeReceived),
            dye: o => o.dyed ? n(o.dyedQuantity) - n(o.dyeing) : 0,
            finish: o => o.dyed ? n(o.dyeing) - n(o.finished) : 0,
            schedule: o => n(o.quantity) - n(o.scheduled),
            deliver: o => n(o.balance)
        }[key];
        const rows = state.data.orders.filter(o => open(o) && left(o) > 0.005)
            .sort((a, b) => left(b) - left(a));
        drillTable(label, `${rows.length} open order(s)`, ['Order', 'Customer', 'Required', 'Pending', 'Stage'], rows.map(o => [
            docLink('bpo', o.id, o.documentNo), esc(o.buyer || ''), day(o.requiredDate) + badgesFor(o),
            `<span class="tabular-nums">${num(left(o))} ${esc(o.uom || '')}</span>`, esc(o.stage)]));
    }

    // =========================================================================== efficiency

    function renderEfficiency(e, flow) {
        const cell = (label, value, sub) => `<div><span class="text-xs text-gray-500">${esc(label)}</span>
            <span class="text-xl font-semibold text-gray-900 dark:text-white">${esc(value)}</span>
            <span class="text-[11px] text-gray-500">${sub}</span></div>`;
        const loss = flow.closedBatches;
        $('[data-pd-efficiency]').innerHTML = [
            cell('Weaving completion', pct(e.weavingCompletion), 'greige received of what is on weaving WOs'),
            cell('Dyeing completion', pct(e.dyeingCompletion), 'finished received of what is on dyeing WOs'),
            cell('First quality', pct(e.gradeA), 'grade A of finished received'),
            cell('Process loss', pct(loss.lossPct), `${num(loss.loss)} lost on ${num(loss.batches)} closed batch(es)`),
            cell('On-time delivery', pct(e.onTime), `${num(e.onTimeLines)} of ${num(e.completedLines)} completed schedule line(s)`)
        ].join('');
    }

    // =========================================================================== status board

    const COLUMN_LABEL = { DRAFT: 'Draft', SUBMITTED: 'Awaiting approval', APPROVED: 'Approved', IN_PROGRESS: 'In progress', COMPLETED: 'Completed' };
    const CARDS_PER_COLUMN = 8;

    function badgesFor(o) {
        return (o.overdue ? ' <span class="badge-red badge-dot">Overdue</span>' : o.late ? ' <span class="badge-amber badge-dot">At risk</span>' : '')
            + (o.noRoute ? ' <span class="badge-red">No route</span>' : '');
    }

    function renderBoard(columns) {
        const byId = new Map(state.data.orders.map(o => [o.id, o]));
        $('[data-pd-board]').innerHTML = columns.map(c => {
            const cards = c.ids.slice(0, CARDS_PER_COLUMN).map(id => byId.get(id)).filter(Boolean).map(o => {
                const done = ratio(o.delivered, o.quantity);
                return `<a class="pd-card" href="${docUrl('bpo', o.id)}">
                    <span class="flex items-center justify-between gap-2"><b class="doc-no text-gray-900 dark:text-white">${esc(o.documentNo)}</b>${badgesFor(o)}</span>
                    <span class="truncate text-gray-600 dark:text-gray-300">${esc(o.buyer || '')}</span>
                    <span class="truncate text-gray-500">${esc(o.fabricTypes.join(', '))}</span>
                    <span class="pd-meter" data-tone="${o.overdue ? 'critical' : ''}"><span style="width:${done == null ? 0 : Math.min(100, done)}%"></span></span>
                    <span class="flex justify-between text-[11px] tabular-nums text-gray-500"><span>${num(o.delivered)} / ${num(o.quantity)} ${esc(o.uom || '')}</span>
                        <span>${o.requiredDate ? 'Due ' + esc(day(o.requiredDate)) : ''}</span></span>
                    <span class="text-[11px] text-gray-500">${esc(o.stage)}</span></a>`;
            }).join('');
            const more = c.count - Math.min(c.count, CARDS_PER_COLUMN);
            return `<div class="pd-col">
                <div class="pd-col-head"><span>${esc(COLUMN_LABEL[c.status] || c.status)}</span><span class="badge-gray">${num(c.count)}</span></div>
                ${cards || '<p class="px-1 py-3 text-xs text-gray-400">None</p>'}
                ${more > 0 ? `<button type="button" class="btn-subtle btn-sm" data-board-more="${esc(c.status)}">${more} more</button>` : ''}
            </div>`;
        }).join('');
    }

    $('[data-pd-board]').addEventListener('click', e => {
        const more = e.target.closest('[data-board-more]');
        if (!more) return;
        const column = state.data.statusBoard.find(c => c.status === more.dataset.boardMore);
        const ids = new Set(column.ids);
        const rows = state.data.orders.filter(o => ids.has(o.id));
        drillTable(COLUMN_LABEL[column.status], `${rows.length} production order(s)`, ['Order', 'Customer', 'Required', 'Delivered', 'Stage'],
            rows.map(o => [docLink('bpo', o.id, o.documentNo), esc(o.buyer || ''), day(o.requiredDate) + badgesFor(o),
                `${num(o.delivered)} / ${num(o.quantity)}`, esc(o.stage)]));
    });

    // =========================================================================== order progress table

    const PAGE = 15;

    function progress(done, of, { left = true, tone } = {}) {
        if (!(n(of) > 0)) return '<span class="text-gray-400">—</span>';
        const p = Math.min(100, n(done) * 100 / n(of));
        const rest = Math.max(0, n(of) - n(done));
        return `<div class="pd-progress" title="${num(done)} of ${num(of)}">
            <div class="pd-progress-top"><span class="pd-meter" data-tone="${tone || (p >= 100 ? 'good' : '')}"><span style="width:${p}%"></span></span>
                <span class="pd-progress-pct">${Math.floor(p)}%</span></div>
            <div class="pd-progress-sub"><span>${num(done)} / ${num(of)}</span>${left ? `<span>(${num(rest)} left)</span>` : ''}</div></div>`;
    }

    function filteredOrders() {
        const q = ($('[data-pd-order-search]').value || '').trim().toLowerCase();
        const show = $('[data-pd-order-show]').value;
        const { key, dir } = state.orderSort;
        const value = o => key === 'stock' ? n(o.ready) : o[key] ?? '';
        return state.data.orders.filter(o => (!q || [o.documentNo, o.buyer, ...o.colours, ...o.fabricTypes].some(v => String(v || '').toLowerCase().includes(q)))
                && (!show || (show === 'overdue' ? o.overdue : show === 'late' ? o.late || o.overdue
                    : ['APPROVED', 'PARTIAL', 'PROCESSING'].includes(o.status))))
            .sort((a, b) => {
                const x = value(a), y = value(b);
                return (typeof x === 'number' ? x - y : String(x).localeCompare(String(y))) * dir;
            });
    }

    function renderOrders() {
        const rows = filteredOrders();
        const pages = Math.max(1, Math.ceil(rows.length / PAGE));
        state.orderPage = Math.min(state.orderPage, pages - 1);
        const slice = rows.slice(state.orderPage * PAGE, state.orderPage * PAGE + PAGE);
        $('[data-pd-orders] tbody').innerHTML = slice.length ? slice.map((o, i) => `<tr>
            <td class="tabular-nums text-gray-500">${state.orderPage * PAGE + i + 1}</td>
            <td>${docLink('bpo', o.id, o.documentNo)}<div class="text-[11px] text-gray-500">${day(o.documentDate)}</div></td>
            <td class="max-w-[14rem]"><div class="truncate">${esc(o.buyer || '')}</div>
                <div class="truncate text-[11px] text-gray-500">${esc(o.fabricTypes.join(', '))} · ${esc(o.colours.slice(0, 3).join(', '))}${o.colours.length > 3 ? ` +${o.colours.length - 3}` : ''}</div></td>
            <td class="whitespace-nowrap">${day(o.requiredDate)}<div class="text-[11px] ${o.overdue ? 'text-red-700 dark:text-red-400' : 'text-gray-500'}">${o.daysLeft == null ? ''
                : o.daysLeft < 0 ? `${-o.daysLeft} day(s) late` : `${o.daysLeft} day(s) left`}</div></td>
            <td>${progress(o.greigeReceived, o.greigeRequired)}</td>
            <td>${o.dyed ? progress(o.finished, o.dyedQuantity) : '<span class="text-xs text-gray-400">Not dyed</span>'}</td>
            <td>${progress(o.delivered, o.quantity, { tone: o.overdue ? 'critical' : '' })}</td>
            <td class="text-right tabular-nums"><b>${num(o.ready)}</b><div class="text-[11px] text-gray-500">${esc(o.uom || '')}</div></td>
            <td>${App.statusBadge(o.status)}<div class="mt-1 text-[11px] text-gray-500">${esc(o.stage)}</div>${badgesFor(o)}</td>
            <td>${App.rowActions(`<a class="btn-icon btn-sm" href="${docUrl('bpo', o.id)}" title="Open ${esc(o.documentNo)}" aria-label="Open ${esc(o.documentNo)}">${icon('eye')}</a>`)}</td>
        </tr>`).join('') : `<tr><td colspan="10"><div class="empty"><p class="empty-text">No production orders match.</p></div></td></tr>`;
        const from = rows.length ? state.orderPage * PAGE + 1 : 0, to = Math.min((state.orderPage + 1) * PAGE, rows.length);
        $('[data-pd-order-pager]').innerHTML = `<span>Showing <b class="font-medium">${from}–${to}</b> of <b class="font-medium">${rows.length}</b></span>
            <div class="flex items-center gap-1">
                <button type="button" class="btn-icon btn-sm border border-gray-200 dark:border-gray-700" data-page="-1" aria-label="Previous page" ${state.orderPage === 0 ? 'disabled' : ''}>${icon('chevron-left')}</button>
                <span class="px-2 text-xs tabular-nums">${state.orderPage + 1} / ${pages}</span>
                <button type="button" class="btn-icon btn-sm border border-gray-200 dark:border-gray-700" data-page="1" aria-label="Next page" ${to >= rows.length ? 'disabled' : ''}>${icon('chevron-right')}</button></div>`;
        $$('[data-pd-orders] th[data-sort]').forEach(th => th.setAttribute('aria-sort',
            th.dataset.sort === state.orderSort.key ? (state.orderSort.dir > 0 ? 'ascending' : 'descending') : 'none'));
    }

    $('[data-pd-order-pager]').addEventListener('click', e => {
        const b = e.target.closest('[data-page]');
        if (!b || b.disabled) return;
        state.orderPage += Number(b.dataset.page);
        renderOrders();
    });
    $('[data-pd-order-search]').addEventListener('input', App.debounce(() => { state.orderPage = 0; renderOrders(); }, 200));
    $('[data-pd-order-show]').addEventListener('change', () => { state.orderPage = 0; renderOrders(); });
    $$('[data-pd-orders] th[data-sort]').forEach(th => {
        th.classList.add('cursor-pointer', 'select-none');
        th.addEventListener('click', () => {
            const key = th.dataset.sort;
            state.orderSort = { key, dir: state.orderSort.key === key ? -state.orderSort.dir : 1 };
            renderOrders();
        });
    });
    $('[data-pd-export]').addEventListener('click', () => {
        const rows = filteredOrders();
        App.downloadCsv(`production-orders-${state.data.today}.csv`, [[
            'Order no', 'Order date', 'Required', 'Customer', 'Fabric types', 'Colours', 'Status', 'Stage', 'Overdue', 'At risk', 'UOM',
            'Ordered', 'Greige required', 'On weaving WOs', 'Greige received', 'Dyed quantity', 'On dyeing WOs', 'Finished A', 'Finished B',
            'Scheduled', 'On delivery orders', 'Delivered', 'Balance', 'In stock (ready)'
        ]].concat(rows.map(o => [o.documentNo, iso(o.documentDate), iso(o.requiredDate), o.buyer, o.fabricTypes.join('; '), o.colours.join('; '),
            o.status, o.stage, o.overdue ? 'yes' : '', o.late ? 'yes' : '', o.uom, o.quantity, o.greigeRequired, o.weaving, o.greigeReceived,
            o.dyedQuantity, o.dyeing, o.finishedA, o.finishedB, o.scheduled, o.onDeliveryOrders, o.delivered, o.balance, o.ready])));
    });

    // =========================================================================== timeline (Gantt)

    const GANTT_ROWS = 30;
    const DAY = 86400000;

    function renderGantt(orders, today) {
        const host = $('[data-pd-gantt]');
        const open = orders.filter(o => ['APPROVED', 'PARTIAL', 'PROCESSING', 'SUBMITTED', 'DRAFT'].includes(o.status) && o.documentDate)
            .sort((a, b) => (iso(a.requiredDate) || '9999').localeCompare(iso(b.requiredDate) || '9999'));
        if (!open.length) { host.innerHTML = '<div class="an-empty">No open production orders match these filters.</div>'; return; }
        const rows = open.slice(0, GANTT_ROWS);
        const t = s => new Date(iso(s) + 'T00:00:00').getTime();
        const now = t(today);
        let start = Math.min(...rows.map(o => t(o.documentDate)), now);
        let end = Math.max(...rows.map(o => t(o.requiredDate || o.documentDate)), ...rows.flatMap(o => (o.plans || []).map(p => t(p.deliveryDate))), now + 7 * DAY);
        start -= 2 * DAY; end += 2 * DAY;
        const span = end - start;
        const x = ms => ((ms - start) / span * 100).toFixed(2);
        // Axis: month starts, or weeks when the span is short.
        const ticks = [];
        const weekly = span < 75 * DAY;
        const cursor = new Date(start);
        cursor.setHours(0, 0, 0, 0);
        if (weekly) cursor.setDate(cursor.getDate() + ((8 - cursor.getDay()) % 7)); else { cursor.setDate(1); cursor.setMonth(cursor.getMonth() + 1); }
        while (cursor.getTime() < end) {
            ticks.push(cursor.getTime());
            if (weekly) cursor.setDate(cursor.getDate() + 7); else cursor.setMonth(cursor.getMonth() + 1);
        }
        const label = ms => new Date(ms).toLocaleDateString(undefined, weekly ? { day: 'numeric', month: 'short' } : { month: 'short', year: '2-digit' });
        const axis = `<div class="pd-axis mb-1"><span></span><div class="relative h-4">${ticks.map(ms =>
            `<span class="absolute -translate-x-1/2 whitespace-nowrap" style="left:${x(ms)}%">${esc(label(ms))}</span>`).join('')}</div></div>`;
        const body = rows.map(o => {
            const s = t(o.documentDate), e = t(o.requiredDate || o.documentDate);
            const done = ratio(o.delivered, o.quantity) || 0;
            const plans = (o.plans || []).map(p => `<span class="pd-plan" style="left:${x(t(p.deliveryDate))}%"
                title="${esc(p.deliveryType)} · ${esc(p.colorName || '')} · ${num(p.quantity)} on ${esc(day(p.deliveryDate))}"></span>`).join('');
            return `<div class="pd-gantt-row">
                <div class="pd-gantt-label"><a class="font-medium text-brand-700 hover:underline" href="${docUrl('bpo', o.id)}">${esc(o.documentNo)}</a>
                    <div class="truncate text-[11px] text-gray-500">${esc(o.buyer || '')}</div></div>
                <div class="pd-track">
                    <a class="pd-span" href="${docUrl('bpo', o.id)}" data-tone="${o.overdue ? 'critical' : ''}" style="left:${x(s)}%;width:${Math.max(0.6, x(e) - x(s))}%"
                       aria-label="${esc(o.documentNo)}: ordered ${esc(day(o.documentDate))}, due ${esc(day(o.requiredDate))}, ${Math.floor(done)}% delivered"
                       data-tip="${esc(o.documentNo)}|${esc(day(o.documentDate))}|${esc(day(o.requiredDate))}|${num(o.delivered)} / ${num(o.quantity)} ${esc(o.uom || '')}|${esc(o.stage)}"><span style="width:${Math.min(100, done)}%"></span></a>
                    ${plans}
                </div></div>`;
        }).join('');
        const todayLine = `<div class="pd-axis pointer-events-none absolute inset-x-4 bottom-4 top-8"><span></span><div class="relative"><span class="pd-today" style="left:${x(now)}%"></span></div></div>`;
        host.innerHTML = axis + body + todayLine
            + (open.length > GANTT_ROWS ? `<p class="mt-2 text-xs text-gray-500">The ${GANTT_ROWS} due soonest of ${open.length} - filter to see others.</p>` : '');
    }

    // Timeline bars share the kit's tooltip, on hover and keyboard focus alike.
    function spanTip(e) {
        const a = e.target.closest('[data-tip]');
        if (!a) return C.tooltip.hide();
        const [no, from, due, delivered, stage] = a.dataset.tip.split('|');
        const r = a.getBoundingClientRect();
        C.tooltip.show(e.clientX || r.left + r.width / 2, e.clientY || r.top, no, [
            { label: 'Ordered', value: from }, { label: 'Required', value: due }, { label: 'Delivered', value: delivered }, { label: 'Stage', value: stage }]);
    }
    $('[data-pd-gantt]').addEventListener('pointermove', spanTip);
    $('[data-pd-gantt]').addEventListener('pointerleave', () => C.tooltip.hide());
    $('[data-pd-gantt]').addEventListener('focusin', spanTip);
    $('[data-pd-gantt]').addEventListener('focusout', () => C.tooltip.hide());

    // =========================================================================== weaving & dyeing

    function renderWorkOrders(rows) {
        const due = w => `${day(w.requiredDate)}${w.overdue ? ' <span class="badge-red badge-dot">Overdue</span>' : ''}`;
        const lead = w => `${docLink(w.slug, w.id, w.documentNo)} ${App.statusBadge(w.status)}
            <div class="text-[11px] text-gray-500">${esc(w.vendor || 'In-house')}${w.processKind ? ' · ' + esc(w.processKind) : ''}</div>`;
        const of = w => `${docLink('bpo', w.bpoId, w.bpoNo)}<div class="max-w-[12rem] truncate text-[11px] text-gray-500">${esc(w.kind === 'WWO' ? w.fabricType || '' : w.colours || '')}</div>`;
        const weaving = rows.filter(w => w.kind === 'WWO');
        const dyeing = rows.filter(w => w.kind === 'PWO');
        const empty = cols => `<tr><td colspan="${cols}"><div class="empty"><p class="empty-text">No open work orders.</p></div></td></tr>`;
        $('[data-pd-wo="WWO"] tbody').innerHTML = weaving.length ? weaving.map(w => `<tr>
            <td>${lead(w)}</td><td>${of(w)}</td><td class="whitespace-nowrap">${due(w)}</td>
            <td>${progress(w.greigeReceived, w.ordered, { tone: w.overdue ? 'critical' : '' })}</td></tr>`).join('') : empty(4);
        $('[data-pd-wo="PWO"] tbody').innerHTML = dyeing.length ? dyeing.map(w => `<tr>
            <td>${lead(w)}${w.batchClosed ? ' <span class="badge-violet">Batch closed</span>' : ''}</td><td>${of(w)}</td><td class="whitespace-nowrap">${due(w)}</td>
            <td>${progress(w.greigeIssued, w.ordered, { left: false })}</td>
            <td>${progress(w.finished, w.ordered, { tone: w.overdue ? 'critical' : '' })}</td></tr>`).join('') : empty(5);
    }

    function renderFlow(flow) {
        const base = n(flow.stages[0].value) || Math.max(...flow.stages.map(s => n(s.value)), 0);
        const host = $('[data-pd-flow]');
        if (!base) { host.innerHTML = '<div class="an-empty !min-h-[6rem]">No dyed fabric on these orders.</div>'; return; }
        // Each stage against the one it comes out of: both grades out of what was issued, delivered out of what was finished.
        const value = key => n(flow.stages.find(x => x.key === key)?.value);
        const from = { ISSUED: ['WOVEN', 'of greige received'], FINISHED_A: ['ISSUED', 'of greige issued'],
            FINISHED_B: ['ISSUED', 'of greige issued'] };
        host.innerHTML = flow.stages.map(s => {
            const share = n(s.value) * 100 / base;
            const of = s.key === 'DELIVERED' ? [value('FINISHED_A') + value('FINISHED_B'), 'of finished received']
                : from[s.key] ? [value(from[s.key][0]), from[s.key][1]] : null;
            const conv = of && of[0] > 0 ? ` · ${(n(s.value) * 100 / of[0]).toFixed(0)}% ${of[1]}` : '';
            const tag = s.step ? 'button' : 'div';
            return `<${tag} ${s.step ? `type="button" data-drill="${esc(s.step)}"` : ''} class="block w-full text-left ${s.step ? 'rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800' : ''}">
                <div class="mb-1 flex items-baseline justify-between gap-2 text-sm"><span class="text-gray-700 dark:text-gray-200">${esc(s.label)}</span>
                    <span class="tabular-nums"><b class="text-gray-900 dark:text-white">${num(s.value)}</b>
                    <span class="text-xs text-gray-500">${share.toFixed(0)}%${conv}</span></span></div>
                <div class="pd-meter !h-2.5"><span style="width:${Math.min(100, share)}%"></span></div></${tag}>`;
        }).join('') + `<p class="flex flex-wrap gap-x-6 gap-y-1 pt-1 text-xs text-gray-500">
            <span>Greige in store: <b class="text-gray-800 dark:text-gray-100">${num(flow.greigeOnHand)}</b></span>
            <span>In process (issued, not yet finished): <b class="text-gray-800 dark:text-gray-100">${num(flow.inProcess)}</b></span>
            <span>Measured loss on closed batches: <b class="text-gray-800 dark:text-gray-100">${num(flow.closedBatches.loss)}</b> (${pct(flow.closedBatches.lossPct)})</span></p>`;
    }

    // =========================================================================== fabric-type and colour tables

    function groupTable(name, rows, first, filterName, filterValue) {
        const host = $(`[data-table="${name}"] [data-host]`);
        const cols = [
            ['Ordered', 'quantity'], ['Greige req.', 'greigeRequired'], ['On weaving', 'weaving'], ['Greige rec.', 'greigeReceived'],
            ['On dyeing', 'dyeing'], ['Finished A', 'finishedA'], ['Finished B', 'finishedB'], ['Scheduled', 'scheduled'],
            ['Delivered', 'delivered'], ['Balance', 'balance'], ['In stock', 'ready']];
        if (!rows.length) { host.innerHTML = '<div class="an-empty">No production orders match these filters.</div>'; return; }
        const total = key => rows.reduce((s, r) => s + n(r[key]), 0);
        host.innerHTML = `<table class="an-table">
            <thead><tr>${first.map(f => `<th>${esc(f[0])}</th>`).join('')}<th class="num">Orders</th>${cols.map(c => `<th class="num">${esc(c[0])}</th>`).join('')}<th>Delivered</th></tr></thead>
            <tbody>${rows.map(r => `<tr class="cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800" tabindex="0" data-filter-name="${filterName}" data-filter-value="${esc(filterValue(r))}">
                ${first.map(f => `<td class="${f[2] || ''}">${esc(f[1](r))}</td>`).join('')}<td class="num">${num(r.orders)}</td>
                ${cols.map(c => `<td class="num">${num(r[c[1]])}</td>`).join('')}
                <td>${progress(r.delivered, r.quantity, { left: false })}</td></tr>`).join('')}</tbody>
            <tfoot><tr><td colspan="${first.length}">Total</td><td class="num"></td>${cols.map(c => `<td class="num">${num(total(c[1]))}</td>`).join('')}
                <td>${progress(total('delivered'), total('quantity'), { left: false })}</td></tr></tfoot></table>`;
    }

    function renderGroups(byType, byColour) {
        groupTable('fabric', byType, [['Fabric type', r => r.key, 'font-medium']], 'fabricType', r => r.key === 'Not set' ? '' : r.key);
        groupTable('colour', byColour, [['Fabric type', r => r.fabricType], ['Colour', r => r.colour, 'font-medium']], 'color', r => r.colour === '—' ? '' : r.colour);
    }

    root.addEventListener('click', e => {
        const row = e.target.closest('tr[data-filter-name]');
        if (row) applyFilter(row.dataset.filterName, row.dataset.filterValue);
    });
    root.addEventListener('keydown', e => {
        const row = e.target.closest('tr[data-filter-name]');
        if (row && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); applyFilter(row.dataset.filterName, row.dataset.filterValue); }
    });

    // =========================================================================== stock & movement

    const MOVE_LABEL = { GREIGE_RECEIVE: 'Greige receive', GREIGE_ISSUE: 'Greige issue', FINISHED_RECEIVE: 'Finished receive',
        DELIVERY: 'Delivery', REVERSAL: 'Reversal', TRANSFER_OUT: 'Transfer out', TRANSFER_IN: 'Transfer in' };

    function renderStock(stock, moves) {
        const g = stock.greige, f = stock.finished;
        const tile = (label, value, meta) => `<div class="an-kpi"><span class="an-kpi-label">${esc(label)}</span>
            <span class="an-kpi-value">${esc(value)}</span><span class="an-kpi-meta">${meta}</span></div>`;
        $('[data-pd-stock-kpis]').innerHTML = [
            tile('Greige on hand', num(g.quantity), `${num(g.lots)} lot(s)`),
            tile('Greige free', num(g.free), `${num(g.reserved)} reserved for delivery orders`),
            tile('Finished on hand', num(f.quantity), `grade A ${num(f.gradeA)} · grade B ${num(f.gradeB)}`),
            tile('Finished free', num(f.free), `${num(f.reserved)} reserved for delivery orders`)].join('');

        const storeHost = $('[data-table="stores"] [data-host]');
        storeHost.innerHTML = stock.rows.length ? `<table class="an-table"><thead><tr><th>Store</th><th>Stage</th><th>Grade</th>
                <th class="num">Lots</th><th class="num">Rolls</th><th class="num">On hand</th><th class="num">Reserved</th><th class="num">Free</th></tr></thead>
            <tbody>${stock.rows.map(r => `<tr><td>${esc(r.store)}</td><td>${r.stage === 'GREIGE' ? 'Greige' : 'Finished'}</td><td>${esc(r.grade || '—')}</td>
                <td class="num">${num(r.lots)}</td><td class="num">${num(r.rolls)}</td><td class="num">${num(r.quantity)}</td>
                <td class="num">${num(r.reserved)}</td><td class="num">${num(r.free)}</td></tr>`).join('')}</tbody></table>`
            : '<div class="an-empty">No stock for these orders.</div>';

        const typeHost = $('[data-table="movetypes"] [data-host]');
        typeHost.innerHTML = moves.byType.length ? `<table class="an-table"><thead><tr><th>Movement</th><th>Stage</th><th class="num">Moves</th>
                <th class="num">Rolls</th><th class="num">Quantity</th></tr></thead>
            <tbody>${moves.byType.map(r => `<tr><td>${esc(MOVE_LABEL[r.moveType] || r.moveType)}</td><td>${r.stage === 'GREIGE' ? 'Greige' : 'Finished'}</td>
                <td class="num">${num(r.moves)}</td><td class="num">${num(r.rolls)}</td><td class="num">${num(r.quantity)}</td></tr>`).join('')}</tbody></table>`
            : '<div class="an-empty">No movement in the period.</div>';

        const card = $('[data-chart="moves"]');
        $('[data-sub]', card).textContent = `In and out of the fabric stores, by ${moves.bucket}, ${day(moves.from)} – ${day(moves.to)}.`;
        const label = p => new Date(iso(p) + 'T00:00:00').toLocaleDateString(undefined,
            moves.bucket === 'month' ? { month: 'short', year: '2-digit' } : { day: 'numeric', month: 'short' });
        chartCard('moves', host => C.columns(host, {
            title: 'Stock movement', grouped: true, height: 240,
            categories: moves.series.map(s => label(s.period)),
            titles: moves.series.map(s => (moves.bucket === 'week' ? 'Week of ' : '') + day(s.period)),
            series: [{ name: 'In', cls: 's1', values: moves.series.map(s => n(s.inQty)) },
                     { name: 'Out', cls: 's2', values: moves.series.map(s => n(s.outQty)) }],
            emptyText: 'No fabric moved in the period.'
        }), () => ({ columns: [{ label: 'Period', get: s => day(s.period) }, { label: 'In', get: s => num(s.inQty), num: true },
            { label: 'Out', get: s => num(s.outQty), num: true }, { label: 'Net', get: s => num(n(s.inQty) - n(s.outQty)), num: true }],
            rows: moves.series }));
    }

    // =========================================================================== deliveries calendar

    const STATE = { done: 'Delivered', ready: 'Stock ready', short: 'Short of stock', overdue: 'Overdue', plan: 'Planned (pre-delivery)' };

    function renderCalendar(cal, today) {
        const [y, m] = cal.month.split('-').map(Number);
        const first = new Date(y, m - 1, 1);
        $('[data-pd-month-title]').textContent = `Delivery calendar - ${first.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}`;
        $('[data-pd-cal-legend]').innerHTML = Object.entries(STATE).map(([k, v]) => `<span class="pd-chip" data-state="${k}">${esc(v)}</span>`).join('');
        const events = cal.schedule.map(s => ({ date: iso(s.dueDate), state: s.state, kind: 'schedule', row: s }))
            .concat(cal.planned.map(p => ({ date: iso(p.deliveryDate), state: 'plan', kind: 'plan', row: p })));
        const byDate = new Map();
        events.forEach(ev => { if (!byDate.has(ev.date)) byDate.set(ev.date, []); byDate.get(ev.date).push(ev); });

        const startOffset = (first.getDay() + 6) % 7;                 // weeks start on Monday
        const cells = [];
        const cursor = new Date(y, m - 1, 1 - startOffset);
        const todayIso = iso(today);
        do {
            for (let i = 0; i < 7; i++) {
                const key = `${cursor.getFullYear()}-${String(cursor.getMonth() + 1).padStart(2, '0')}-${String(cursor.getDate()).padStart(2, '0')}`;
                const list = byDate.get(key) || [];
                const qty = list.filter(ev => ev.kind === 'schedule').reduce((s, ev) => s + n(ev.row.scheduled), 0);
                const chips = summarize(list);
                cells.push(`<button type="button" class="pd-day" data-day="${key}" ${cursor.getMonth() !== m - 1 ? 'data-other' : ''}
                        ${key === todayIso ? 'data-today' : ''} aria-pressed="${state.day === key}"
                        aria-label="${esc(fmt.date(key))}: ${list.length} delivery line(s)">
                    <span class="flex w-full items-center justify-between"><span class="pd-day-no">${cursor.getDate()}</span>
                        ${qty ? `<span class="text-[10px] tabular-nums text-gray-500">${num(qty)}</span>` : ''}</span>
                    ${chips}</button>`);
                cursor.setDate(cursor.getDate() + 1);
            }
        } while (cursor.getMonth() === m - 1);
        $('[data-pd-calendar]').innerHTML = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
            .map(d => `<div class="pd-cal-head">${d}</div>`).join('') + cells.join('');
        renderDay();
    }

    /** At most three chips a day: one per state, with its count. */
    function summarize(list) {
        const counts = {};
        list.forEach(ev => { counts[ev.state] = (counts[ev.state] || 0) + 1; });
        return ['overdue', 'short', 'ready', 'plan', 'done'].filter(s => counts[s]).slice(0, 3)
            .map(s => `<span class="pd-chip" data-state="${s}">${counts[s]} ${esc(STATE[s].split(' ')[0].toLowerCase())}</span>`).join('');
    }

    function renderDay() {
        const cal = state.data.calendar;
        const inDay = v => !state.day || iso(v) === state.day;
        const rows = cal.schedule.filter(s => inDay(s.dueDate)).map(s => `<tr>
                <td class="whitespace-nowrap">${day(s.dueDate)}</td>
                <td>${docLink('requestforpi', s.scheduleId, s.scheduleNo)}<div class="text-[11px] text-gray-500">Delivery schedule</div></td>
                <td>${esc(s.buyer || '')}</td>
                <td>${docLink('bpo', s.bpoId, s.bpoNo)}<div class="text-[11px] text-gray-500">${esc(s.colorName || '')}</div></td>
                <td class="text-right tabular-nums">${num(s.scheduled)}</td><td class="text-right tabular-nums">${num(s.delivered)}</td>
                <td class="text-right tabular-nums">${num(s.ready)}</td><td><span class="pd-chip inline-flex" data-state="${s.state}">${esc(STATE[s.state])}</span></td></tr>`)
            .concat(cal.planned.filter(p => inDay(p.deliveryDate)).map(p => `<tr>
                <td class="whitespace-nowrap">${day(p.deliveryDate)}</td>
                <td>${esc(p.deliveryType)}<div class="text-[11px] text-gray-500">Pre-delivery plan · serial ${esc(p.serialNo)}</div></td>
                <td>${esc(p.buyer || '')}</td>
                <td>${docLink('bpo', p.bpoId, p.bpoNo)}<div class="text-[11px] text-gray-500">${esc(p.colorName || '')}</div></td>
                <td class="text-right tabular-nums">${num(p.quantity)}</td><td class="text-right">—</td><td class="text-right">—</td>
                <td><span class="pd-chip inline-flex" data-state="plan">${esc(STATE.plan)}</span></td></tr>`));
        $('[data-pd-day-title]').textContent = state.day ? `Deliveries on ${fmt.date(state.day)}` : 'This month\'s deliveries';
        $('[data-pd-day] tbody').innerHTML = rows.length ? rows.join('')
            : '<tr><td colspan="8"><div class="empty"><p class="empty-text">Nothing due.</p></div></td></tr>';

        const k = state.data.kpis.pendingDelivery;
        const monthDue = cal.schedule.reduce((s, x) => s + Math.max(0, n(x.scheduled) - n(x.delivered)), 0);
        const tile = (label, value, meta) => `<div class="an-kpi"><span class="an-kpi-label">${esc(label)}</span>
            <span class="an-kpi-value">${esc(value)}</span><span class="an-kpi-meta">${meta}</span></div>`;
        $('[data-pd-delivery-kpis]').innerHTML = [
            tile('Pending delivery', num(k.quantity), `${num(k.lines)} open schedule line(s)`),
            tile('Deliverable now', num(k.deliverable), 'free stock against open schedule lines'),
            tile('Overdue lines', num(k.overdue), 'past due and not delivered'),
            tile('Still due this month', num(monthDue), `${cal.schedule.length} line(s) · ${cal.planned.length} planned pre-deliveries`)].join('');
    }

    $('[data-pd-calendar]').addEventListener('click', e => {
        const cell = e.target.closest('[data-day]');
        if (!cell) return;
        state.day = state.day === cell.dataset.day ? null : cell.dataset.day;
        $$('[data-day]', $('[data-pd-calendar]')).forEach(c => c.setAttribute('aria-pressed', String(c.dataset.day === state.day)));
        renderDay();
    });
    root.addEventListener('click', e => {
        const nav = e.target.closest('[data-pd-month]');
        if (!nav) return;
        const step = Number(nav.dataset.pdMonth);
        if (step === 0) {
            state.month = null;
        } else {
            const [y, m] = (state.month || state.data.calendar.month).split('-').map(Number);
            const d = new Date(y, m - 1 + step, 1);
            state.month = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        }
        state.day = null;
        load();
    });

    // =========================================================================== alerts

    const ALERT_ICON = { OVERDUE: 'clock', AT_RISK: 'alert', BLOCKED: 'lock', STUCK: 'history', REJECTED: 'x-circle', NO_STOCK: 'inventory' };

    function alertRow(a) {
        return `<a class="pd-alert" data-severity="${esc(a.severity)}" href="${docUrl(a.slug, a.documentId)}">
            <span class="pd-alert-icon">${icon(ALERT_ICON[a.kind] || 'alert')}</span>
            <span class="min-w-0 flex-1"><span class="flex flex-wrap items-center gap-2 text-sm">
                <b class="text-gray-900 dark:text-white">${esc(a.title)}</b>
                <span class="${a.severity === 'critical' ? 'badge-red' : 'badge-amber'}">${a.severity === 'critical' ? 'Critical' : 'Warning'}</span></span>
                <span class="block text-xs text-gray-600 dark:text-gray-300"><span class="font-medium">${esc(a.documentNo || '')}</span>${a.buyer ? ' · ' + esc(a.buyer) : ''}</span>
                <span class="block text-xs text-gray-500">${esc(a.detail || '')}</span></span>
            <span class="text-gray-400">${icon('chevron-right')}</span></a>`;
    }

    function renderAlerts(alerts) {
        const critical = alerts.filter(a => a.severity === 'critical').length;
        const badge = $('[data-pd-alert-count]');
        badge.textContent = alerts.length;
        badge.className = `${critical ? 'badge-red' : alerts.length ? 'badge-amber' : 'badge-gray'} ml-1`;
        const none = '<div class="an-empty !min-h-[8rem]">Nothing needs attention.</div>';
        $('[data-pd-top-alerts]').innerHTML = alerts.slice(0, 5).map(alertRow).join('') || none;
        const shown = alerts.filter(a => !state.severity || a.severity === state.severity);
        $('[data-pd-alerts]').innerHTML = shown.map(alertRow).join('') || none;
        $$('[data-pd-alert-filter] [data-severity]').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.severity === state.severity)));
    }

    $('[data-pd-alert-filter]').addEventListener('click', e => {
        const b = e.target.closest('[data-severity]');
        if (!b) return;
        state.severity = b.dataset.severity;
        renderAlerts(state.data.alerts);
    });

    // =========================================================================== drill-down drawer

    const drawer = $('[data-pd-drill]');
    $('[data-close]', drawer).addEventListener('click', () => drawer.close());
    drawer.addEventListener('click', e => { if (e.target === drawer) drawer.close(); });

    function drillTable(title, sub, head, rows) {
        $('[data-pd-drill-title]', drawer).textContent = title;
        $('[data-pd-drill-sub]', drawer).textContent = sub;
        $('[data-pd-drill-body]', drawer).innerHTML = rows.length ? `<div class="table-wrap"><table class="table-grid">
            <thead><tr>${head.map(h => `<th>${esc(h)}</th>`).join('')}</tr></thead>
            <tbody>${rows.map(r => `<tr>${r.map(c => `<td>${c}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`
            : '<div class="an-empty">No documents.</div>';
        if (!drawer.open) drawer.showModal();
    }

    async function drillDocuments(step, group) {
        const label = step === 'BOOKING' ? 'Bookings' : (STEP[step]?.label || step) + 's';
        $('[data-pd-drill-title]', drawer).textContent = label;
        $('[data-pd-drill-sub]', drawer).textContent = 'Loading…';
        $('[data-pd-drill-body]', drawer).innerHTML = '<div class="an-empty">Loading…</div>';
        if (!drawer.open) drawer.showModal();
        try {
            const docs = await api('/api/production/dashboard/documents', { query: Object.assign(filters(), { step, group: group || undefined }) });
            const total = docs.reduce((s, d) => s + n(d.totalQuantity), 0);
            // Store documents are posted, not approved: for them "approved" is done, and the only other state is a draft.
            const posting = ['GR', 'GI', 'FFR', 'FD'].includes(step);
            const choices = posting ? [['', 'All'], ['awaiting', 'Not yet posted']] : [['', 'All'], ['open', 'Open'], ['awaiting', 'Draft / awaiting approval']];
            const tabs = step === 'BOOKING' ? '' : `<div class="segmented mb-3" role="group" aria-label="Which">${choices
                .map(([g, l]) => `<button type="button" data-drill-group="${g}" data-drill-step="${esc(step)}" aria-pressed="${(group || '') === g}">${esc(l)}</button>`).join('')}</div>`;
            drillTable(label, `${docs.length} document(s) · ${num(total)} total quantity${docs.length >= 500 ? ' · the latest 500' : ''}`,
                ['Document', 'Date', 'Status', step === 'BOOKING' ? 'Customer' : 'Against', 'Quantity'],
                docs.map(d => [docLink(d.slug, d.id, d.documentNo) + (d.buyer && step !== 'BOOKING' ? `<div class="text-[11px] text-gray-500">${esc(d.buyer)}</div>` : ''),
                    day(d.documentDate) + (d.requiredDate ? `<div class="text-[11px] text-gray-500">Due ${esc(day(d.requiredDate))}</div>` : ''),
                    App.statusBadge(d.status),
                    esc(step === 'BOOKING' ? d.buyer || '' : d.parentNo || (d.store || '')),
                    `<span class="tabular-nums">${num(d.totalQuantity)}</span>`]));
            $('[data-pd-drill-body]', drawer).insertAdjacentHTML('afterbegin', tabs);
        } catch (error) {
            drawer.close();
            fail(error);
        }
    }

    drawer.addEventListener('click', e => {
        const g = e.target.closest('[data-drill-group]');
        if (g) drillDocuments(g.dataset.drillStep, g.dataset.drillGroup);
    });

    // =========================================================================== start

    const tabs = App.tabs($('[data-pd-tabs]'), name => {
        // Charts in a tab that was hidden drew at zero width: redraw them now they can be measured.
        $$(`[data-panel="${name}"] [data-chart]`).forEach(card => card._draw && card._draw());
        if (name === 'orders' && state.data) renderGantt(state.data.orders, state.data.today);
        history.replaceState(null, '', `${location.pathname}${location.search}#${name}`);
    });

    (async () => {
        const q = await restoreFromUrl();
        await loadOptions(q);
        await load();
        const tab = location.hash.slice(1);
        if (['overview', 'orders', 'production', 'stock', 'deliveries', 'alerts'].includes(tab)) tabs.select(tab);
    })();
});
