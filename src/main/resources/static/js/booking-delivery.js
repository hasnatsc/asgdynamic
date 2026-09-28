/*
 * Booking analytics - the "Booking to delivery" tab (templates/analytics/booking.html). Every
 * booking in the page's filters and scope, followed down the chain: on production orders, woven,
 * finished, scheduled, delivered. Data: /api/analytics/booking/delivery (BookingDeliveryService),
 * read with the same filters as the rest of the page (booking-analytics.js hands them over as
 * root.analyticsParams and announces changes). Loads only while its tab is shown.
 *
 * Every figure opens what it came from: a tile, funnel stage, chart bar or month the bookings behind
 * it; a booking its document, or its production orders.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const page = document.querySelector('[data-analytics]');
    const root = page && page.querySelector('[data-bd-root]');
    if (!root) return;
    const { api, esc, fmt, fail, icon } = App;
    const C = window.AnalyticsCharts;
    const W = window.ChainWidgets;
    const $ = (sel, r) => (r || root).querySelector(sel);
    const $$ = (sel, r) => [...(r || root).querySelectorAll(sel)];
    const nf = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 });
    const n = v => Number(v) || 0;
    const num = v => v == null || v === '' ? '—' : nf.format(n(v));
    const pct = v => v == null ? '—' : `${Number(v).toFixed(1)}%`;
    const ratio = (a, b) => n(b) > 0 ? n(a) * 100 / n(b) : null;
    const iso = v => v == null ? null : typeof v === 'number' ? new Date(v).toISOString().slice(0, 10) : String(v).slice(0, 10);
    const day = v => (iso(v) ? fmt.date(iso(v)) : '—');
    const bookingUrl = id => `/booking?open=${encodeURIComponent(id)}`;
    const link = (href, text) => `<a class="font-medium text-brand-700 hover:underline" href="${esc(href)}">${esc(text)}</a>`;

    const state = { data: null, stale: true, draw: 0, page: 0, sort: { key: 'requiredDate', dir: 1 }, severity: '', asTable: new Set() };
    const shown = () => !root.hidden;

    // =========================================================================== loading

    async function load() {
        if (!shown()) { state.stale = true; return; }
        state.stale = false;
        const mine = ++state.draw;
        $$('.an-chart').forEach(h => h.setAttribute('data-loading', ''));
        try {
            const data = await api('/api/analytics/booking/delivery', { query: page.analyticsParams() });
            if (mine !== state.draw) return;
            state.data = data;
            state.page = 0;
            render();
        } catch (error) {
            if (mine === state.draw) fail(error);
        } finally {
            if (mine === state.draw) $$('.an-chart').forEach(h => h.removeAttribute('data-loading'));
        }
    }

    page.addEventListener('analytics:filters', () => { state.stale = true; if (shown()) load(); });
    page.addEventListener('analytics:tab', e => {
        if (e.detail.name !== 'delivery') return;
        if (state.stale || !state.data) load();
        else redrawCharts();                    // drawn while hidden, at zero width
    });

    function render() {
        const d = state.data;
        const confirmed = d.bookings.filter(b => b.statusGroup === 'CONFIRMED').length;
        $('[data-bd-note]').innerHTML = `${num(d.bookings.length)} booking(s) booked ${esc(day(d.from))} – ${esc(day(d.to))}, ${num(confirmed)} confirmed.
            Quantities are summed as booked; cancelled bookings are left out.
            ${d.truncated ? '<span class="badge-amber ml-1">Showing the newest 2,000 - narrow the filters</span>' : ''}`;
        renderKpis(d.kpis);
        renderFunnel(d.funnel);
        redrawCharts();
        renderTable();
        renderTimeline();
        renderAlerts();
    }

    // =========================================================================== KPIs

    function renderKpis(k) {
        const tile = (key, label, value, meta, tone) => `<button type="button" class="pd-kpi" data-bd-kpi="${key}">
            <span class="an-kpi-label">${esc(label)}</span><span class="an-kpi-value ${tone || ''}">${esc(value)}</span>
            <span class="an-kpi-meta">${meta}</span></button>`;
        $('[data-bd-kpis]').innerHTML = [
            tile('confirmed', 'Confirmed bookings', num(k.confirmed), `${num(k.bookings)} in all · ${num(k.pipeline)} draft or awaiting approval`),
            tile('booked', 'Booked quantity', num(k.booked), 'confirmed bookings'),
            tile('ordered', 'On production orders', pct(k.orderedPct), `${num(k.ordered)} ordered · <b>${num(k.stillToOrder)}</b> still to order`),
            tile('produced', 'Produced', pct(k.producedPct), `${num(k.produced)} in the store it delivers from`),
            tile('delivered', 'Delivered', pct(k.deliveredPct), `${num(k.delivered)} delivered · ${num(k.fullyDelivered)} booking(s) complete`),
            tile('open', 'Balance to deliver', num(k.balance), 'confirmed, not yet delivered'),
            tile('overdue', 'Overdue bookings', num(k.overdue), `${num(k.overdueBalance)} past its delivery date`, n(k.overdue) ? 'text-red-700 dark:text-red-400' : ''),
            tile('attention', 'At risk / not ordered', `${num(k.atRisk)} / ${num(k.notOrdered)}`, 'due within 7 days and not ready / confirmed, nothing ordered')
        ].join('');
    }

    const PICK = {
        confirmed: ['Confirmed bookings', b => b.statusGroup === 'CONFIRMED'],
        booked: ['Confirmed bookings', b => b.statusGroup === 'CONFIRMED'],
        ordered: ['Bookings on production orders', b => n(b.ordered) > 0],
        produced: ['Bookings with production', b => n(b.produced) > 0],
        delivered: ['Bookings with deliveries', b => n(b.delivered) > 0],
        open: ['Bookings still to deliver', b => b.open],
        overdue: ['Overdue bookings', b => b.overdue],
        attention: ['At risk or not ordered', b => b.atRisk || (b.statusGroup === 'CONFIRMED' && b.stage === 'Not ordered')]
    };

    root.addEventListener('click', e => {
        const k = e.target.closest('[data-bd-kpi]');
        if (k) { const [title, test] = PICK[k.dataset.bdKpi]; drillBookings(title, state.data.bookings.filter(test)); }
    });

    // =========================================================================== funnel

    const FUNNEL_TEST = {
        booked: b => b.statusGroup === 'CONFIRMED', ordered: b => n(b.ordered) > 0, greigeReceived: b => n(b.greigeReceived) > 0,
        finished: b => n(b.finished) > 0, scheduled: b => n(b.scheduled) > 0, onDeliveryOrders: b => n(b.onDeliveryOrders) > 0,
        delivered: b => n(b.delivered) > 0
    };

    function renderFunnel(stages) {
        const booked = n(stages[0].value);
        const host = $('[data-bd-funnel]');
        if (!booked) { host.innerHTML = '<div class="an-empty !min-h-[8rem]">No confirmed bookings in these filters.</div>'; return; }
        host.innerHTML = stages.map(s => `<button type="button" class="block w-full rounded-lg text-left hover:bg-gray-50 dark:hover:bg-gray-800" data-bd-stage="${esc(s.key)}">
                <div class="mb-1 flex items-baseline justify-between gap-2 text-sm"><span class="text-gray-700 dark:text-gray-200">${esc(s.label)}</span>
                    <span class="tabular-nums"><b class="text-gray-900 dark:text-white">${num(s.value)}</b>
                    <span class="text-xs text-gray-500">${pct(s.pct)}</span></span></div>
                <div class="pd-meter !h-2.5"><span style="width:${Math.min(100, n(s.value) * 100 / booked)}%"></span></div></button>`).join('')
            + `<p class="pt-1 text-xs text-gray-500">Still to order: <b class="text-gray-800 dark:text-gray-100">${num(state.data.kpis.stillToOrder)}</b>
                · still to deliver: <b class="text-gray-800 dark:text-gray-100">${num(state.data.kpis.balance)}</b></p>`;
    }

    root.addEventListener('click', e => {
        const s = e.target.closest('[data-bd-stage]');
        if (!s) return;
        const stage = state.data.funnel.find(f => f.key === s.dataset.bdStage);
        drillBookings(stage.label, state.data.bookings.filter(b => b.statusGroup === 'CONFIRMED' && FUNNEL_TEST[stage.key](b)));
    });

    // =========================================================================== charts

    function chart(name, draw, table) {
        const card = $(`[data-bd-chart="${name}"]`);
        const host = $('[data-host]', card);
        if (state.asTable.has(name)) C.table(host, table()); else draw(host);
    }

    const MONTH_SERIES = [
        { key: 'booked', name: 'Booked', cls: 'o2' }, { key: 'ordered', name: 'Ordered', cls: 'o3' },
        { key: 'produced', name: 'Produced', cls: 'o4' }, { key: 'delivered', name: 'Delivered', cls: 'o5' }];

    function redrawCharts() {
        const d = state.data;
        if (!d) return;
        chart('stages', host => C.hbars(host, {
            title: 'Bookings by stage', cls: 's1', valueLabel: 'Bookings', format: C.fmt.int,
            rows: d.stageMix.map(s => ({ label: s.stage, value: s.bookings, stage: s.stage })),
            detail: r => { const s = d.stageMix.find(x => x.stage === r.stage);
                return [{ label: 'Bookings', value: num(s.bookings) }, { label: 'Booked', value: num(s.booked) }, { label: 'To deliver', value: num(s.balance) }]; },
            emptyText: 'No bookings in these filters.',
            onSelect: r => drillBookings(`Bookings at "${r.stage}"`, d.bookings.filter(b => b.stage === r.stage))
        }), () => ({ columns: [{ label: 'Stage', get: s => s.stage }, { label: 'Bookings', get: s => num(s.bookings), num: true },
            { label: 'Booked', get: s => num(s.booked), num: true }, { label: 'To deliver', get: s => num(s.balance), num: true }], rows: d.stageMix }));

        const monthLabel = m => new Date(m + '-01T00:00:00').toLocaleDateString(undefined, { month: 'short', year: 'numeric' });
        chart('months', host => C.columns(host, {
            title: 'Booked, ordered, produced and delivered by month', grouped: true, height: 250,
            categories: d.byMonth.map(m => monthLabel(m.month)),
            titles: d.byMonth.map(m => `${new Date(m.month + '-01T00:00:00').toLocaleDateString(undefined, { month: 'long', year: 'numeric' })} · ${m.bookings} booking(s)`),
            series: MONTH_SERIES.map(s => ({ name: s.name, cls: s.cls, values: d.byMonth.map(m => n(m[s.key])) })),
            emptyText: 'No confirmed bookings in these filters.',
            onSelect: i => drillBookings(`Booked in ${monthLabel(d.byMonth[i].month)}`, d.bookings.filter(b =>
                b.statusGroup === 'CONFIRMED' && iso(b.documentDate).slice(0, 7) === d.byMonth[i].month))
        }), () => ({ columns: [{ label: 'Month', get: m => m.month }, { label: 'Bookings', get: m => num(m.bookings), num: true }]
            .concat(MONTH_SERIES.map(s => ({ label: s.name, get: m => num(m[s.key]), num: true })))
            .concat([{ label: 'Delivered %', get: m => pct(ratio(m.delivered, m.booked)), num: true }]), rows: d.byMonth }));
    }

    $$('[data-bd-table-toggle]').forEach(b => b.addEventListener('click', () => {
        const name = b.closest('[data-bd-chart]').dataset.bdChart;
        if (state.asTable.has(name)) state.asTable.delete(name); else state.asTable.add(name);
        b.textContent = state.asTable.has(name) ? 'Chart' : 'Table';
        redrawCharts();
    }));

    // =========================================================================== progress table

    const PAGE = 15;
    const SHOW = {
        open: b => b.open, overdue: b => b.overdue, atRisk: b => b.atRisk,
        notOrdered: b => b.statusGroup === 'CONFIRMED' && n(b.stillToOrder) > 0,
        delivered: b => b.stage === 'Delivered'
    };

    function badges(b) {
        return (b.overdue ? ' <span class="badge-red badge-dot">Overdue</span>' : b.atRisk ? ' <span class="badge-amber badge-dot">At risk</span>' : '');
    }

    function rows() {
        const q = ($('[data-bd-search]').value || '').trim().toLowerCase();
        const show = SHOW[$('[data-bd-show]').value];
        const { key, dir } = state.sort;
        return state.data.bookings
            .filter(b => (!q || [b.documentNo, b.referenceNo, b.buyer, b.team, b.person].some(v => String(v || '').toLowerCase().includes(q)))
                && (!show || show(b)))
            .sort((a, b) => {
                const x = a[key] ?? '', y = b[key] ?? '';
                return (typeof x === 'number' || key === 'ready' ? n(x) - n(y) : String(x).localeCompare(String(y))) * dir;
            });
    }

    function renderTable() {
        const all = rows();
        const pages = Math.max(1, Math.ceil(all.length / PAGE));
        state.page = Math.min(state.page, pages - 1);
        const slice = all.slice(state.page * PAGE, state.page * PAGE + PAGE);
        $('[data-bd-table] tbody').innerHTML = slice.length ? slice.map((b, i) => `<tr>
            <td class="tabular-nums text-gray-500">${state.page * PAGE + i + 1}</td>
            <td>${link(bookingUrl(b.id), b.documentNo)}<div class="text-[11px] text-gray-500">${day(b.documentDate)}${b.referenceNo ? ' · ' + esc(b.referenceNo) : ''}</div></td>
            <td class="max-w-[14rem]"><div class="truncate">${esc(b.buyer || '')}</div>
                <div class="truncate text-[11px] text-gray-500">${esc([b.team, b.person].filter(Boolean).join(' · '))}</div></td>
            <td class="whitespace-nowrap">${day(b.requiredDate)}<div class="text-[11px] ${b.overdue ? 'text-red-700 dark:text-red-400' : 'text-gray-500'}">${
                b.daysLeft == null || !b.open ? '' : b.daysLeft < 0 ? `${-b.daysLeft} day(s) late` : `${b.daysLeft} day(s) left`}</div></td>
            <td>${W.progress(b.ordered, b.booked)}</td>
            <td>${W.progress(b.produced, b.booked, { left: false })}</td>
            <td>${W.progress(b.delivered, b.booked, { tone: b.overdue ? 'critical' : '' })}</td>
            <td class="text-right tabular-nums"><b>${num(b.ready)}</b><div class="text-[11px] text-gray-500">${esc(b.uom || '')}</div></td>
            <td><span class="text-xs font-medium text-gray-700 dark:text-gray-200">${esc(b.stage)}</span>${badges(b)}</td>
            <td>${App.rowActions(
                `<a class="btn-icon btn-sm" href="${bookingUrl(b.id)}" title="Open ${esc(b.documentNo)}" aria-label="Open ${esc(b.documentNo)}">${icon('eye')}</a>`,
                b.orders.length ? `<button type="button" class="btn-icon btn-sm" data-bd-orders="${esc(b.id)}" title="Its production orders" aria-label="Production orders of ${esc(b.documentNo)}">${icon('clipboard-check')}</button>` : '')}</td>
        </tr>`).join('') : '<tr><td colspan="10"><div class="empty"><p class="empty-text">No bookings match.</p></div></td></tr>';
        const from = all.length ? state.page * PAGE + 1 : 0, to = Math.min((state.page + 1) * PAGE, all.length);
        $('[data-bd-pager]').innerHTML = `<span>Showing <b class="font-medium">${from}–${to}</b> of <b class="font-medium">${all.length}</b></span>
            <div class="flex items-center gap-1">
                <button type="button" class="btn-icon btn-sm border border-gray-200 dark:border-gray-700" data-page="-1" aria-label="Previous page" ${state.page === 0 ? 'disabled' : ''}>${icon('chevron-left')}</button>
                <span class="px-2 text-xs tabular-nums">${state.page + 1} / ${pages}</span>
                <button type="button" class="btn-icon btn-sm border border-gray-200 dark:border-gray-700" data-page="1" aria-label="Next page" ${to >= all.length ? 'disabled' : ''}>${icon('chevron-right')}</button></div>`;
        $$('[data-bd-table] th[data-sort]').forEach(th => th.setAttribute('aria-sort',
            th.dataset.sort === state.sort.key ? (state.sort.dir > 0 ? 'ascending' : 'descending') : 'none'));
    }

    $('[data-bd-pager]').addEventListener('click', e => {
        const b = e.target.closest('[data-page]');
        if (!b || b.disabled) return;
        state.page += Number(b.dataset.page);
        renderTable();
    });
    $('[data-bd-search]').addEventListener('input', App.debounce(() => { state.page = 0; renderTable(); }, 200));
    $('[data-bd-show]').addEventListener('change', () => { state.page = 0; renderTable(); });
    $$('[data-bd-table] th[data-sort]').forEach(th => {
        th.classList.add('cursor-pointer', 'select-none');
        th.addEventListener('click', () => {
            const key = th.dataset.sort;
            state.sort = { key, dir: state.sort.key === key ? -state.sort.dir : 1 };
            renderTable();
        });
    });
    $('[data-bd-table]').addEventListener('click', e => {
        const b = e.target.closest('[data-bd-orders]');
        if (b) drillOrders(state.data.bookings.find(x => String(x.id) === b.dataset.bdOrders));
    });
    $('[data-bd-export]').addEventListener('click', () => {
        App.downloadCsv(`booking-to-delivery-${iso(state.data.today)}.csv`, [[
            'Booking no', 'Booking date', 'Reference', 'Delivery date', 'Buyer', 'Team', 'Marketing person', 'Status', 'Stage', 'Overdue', 'At risk',
            'UOM', 'Booked', 'On production orders', 'Still to order', 'Greige received', 'Finished', 'Produced', 'Scheduled', 'On delivery orders',
            'Delivered', 'Balance', 'In stock (ready)', 'Production orders'
        ]].concat(rows().map(b => [b.documentNo, iso(b.documentDate), b.referenceNo, iso(b.requiredDate), b.buyer, b.team, b.person, b.status,
            b.stage, b.overdue ? 'yes' : '', b.atRisk ? 'yes' : '', b.uom, b.booked, b.ordered, b.stillToOrder, b.greigeReceived, b.finished,
            b.produced, b.scheduled, b.onDeliveryOrders, b.delivered, b.balance, b.ready, b.orders.map(o => o.documentNo).join('; ')])));
    });

    // =========================================================================== timeline

    function renderTimeline() {
        const open = state.data.bookings.filter(b => b.open && b.documentDate)
            .sort((a, b) => (iso(a.requiredDate) || '9999').localeCompare(iso(b.requiredDate) || '9999'));
        W.timeline($('[data-bd-timeline]'), open.map(b => ({
            href: bookingUrl(b.id), label: b.documentNo, sub: b.buyer, start: b.documentDate, end: b.requiredDate || b.documentDate,
            done: ratio(b.delivered, b.booked) || 0, overdue: b.overdue,
            tip: { title: b.documentNo, rows: [{ label: 'Booked', value: day(b.documentDate) }, { label: 'Delivery', value: day(b.requiredDate) },
                { label: 'Delivered', value: `${num(b.delivered)} / ${num(b.booked)} ${b.uom || ''}` }, { label: 'Stage', value: b.stage }] }
        })), state.data.today, { limit: 30, noun: 'open bookings', emptyText: 'No open bookings in these filters.' });
    }

    // =========================================================================== alerts

    const ALERT_ICON = { OVERDUE: 'clock', AT_RISK: 'alert', NOT_ORDERED: 'clipboard-check' };

    function renderAlerts() {
        const alerts = state.data.alerts;
        const critical = alerts.filter(a => a.severity === 'critical').length;
        const badge = page.querySelector('[data-bd-alert-count]');
        badge.hidden = !alerts.length;
        badge.textContent = alerts.length;
        badge.className = `${critical ? 'badge-red' : 'badge-amber'} ml-1`;
        const list = alerts.filter(a => !state.severity || a.severity === state.severity);
        $('[data-bd-alerts]').innerHTML = list.map(a => `<a class="pd-alert" data-severity="${esc(a.severity)}" href="${bookingUrl(a.bookingId)}">
                <span class="pd-alert-icon">${icon(ALERT_ICON[a.kind] || 'alert')}</span>
                <span class="min-w-0 flex-1"><span class="flex flex-wrap items-center gap-2 text-sm"><b class="text-gray-900 dark:text-white">${esc(a.title)}</b>
                    <span class="${a.severity === 'critical' ? 'badge-red' : 'badge-amber'}">${a.severity === 'critical' ? 'Critical' : 'Warning'}</span></span>
                    <span class="block text-xs text-gray-600 dark:text-gray-300"><span class="font-medium">${esc(a.documentNo)}</span>${a.buyer ? ' · ' + esc(a.buyer) : ''}</span>
                    <span class="block text-xs text-gray-500">${esc(a.detail)}</span></span>
                <span class="text-gray-400">${icon('chevron-right')}</span></a>`).join('')
            || '<div class="an-empty !min-h-[8rem]">Nothing needs attention.</div>';
        $$('[data-bd-alert-filter] [data-severity]').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.severity === state.severity)));
    }

    $('[data-bd-alert-filter]').addEventListener('click', e => {
        const b = e.target.closest('[data-severity]');
        if (!b) return;
        state.severity = b.dataset.severity;
        renderAlerts();
    });

    // =========================================================================== drawer

    const drawer = $('[data-bd-drill]');
    $('[data-close]', drawer).addEventListener('click', () => drawer.close());
    drawer.addEventListener('click', e => { if (e.target === drawer) drawer.close(); });

    function open(title, sub, head, cells) {
        $('[data-bd-drill-title]', drawer).textContent = title;
        $('[data-bd-drill-sub]', drawer).textContent = sub;
        $('[data-bd-drill-body]', drawer).innerHTML = cells.length ? `<div class="table-wrap"><table class="table-grid">
            <thead><tr>${head.map(h => `<th>${esc(h)}</th>`).join('')}</tr></thead>
            <tbody>${cells.map(r => `<tr>${r.map(c => `<td>${c}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`
            : '<div class="an-empty">Nothing here.</div>';
        if (!drawer.open) drawer.showModal();
    }

    function drillBookings(title, list) {
        const booked = list.reduce((s, b) => s + n(b.booked), 0);
        open(title, `${list.length} booking(s) · ${num(booked)} booked`, ['Booking', 'Buyer', 'Delivery', 'Delivered', 'Stage', 'Production orders'],
            list.map(b => [link(bookingUrl(b.id), b.documentNo) + `<div class="text-[11px] text-gray-500">${esc(day(b.documentDate))}</div>`,
                esc(b.buyer || ''), day(b.requiredDate) + badges(b), W.progress(b.delivered, b.booked, { left: false }), esc(b.stage),
                b.orders.map(o => link(`/bpo?open=${o.id}`, o.documentNo)).join('<br>') || '<span class="text-gray-400">None</span>']));
    }

    function drillOrders(b) {
        open(`Production orders of ${b.documentNo}`, `${b.orders.length} order(s) · ${num(b.ordered)} of ${num(b.booked)} booked is on them`,
            ['Production order', 'Fabric / colours', 'Required', 'Delivered', 'Stage'],
            b.orders.map(o => [link(`/bpo?open=${o.id}`, o.documentNo) + ` ${App.statusBadge(o.status)}`,
                `<div class="max-w-[12rem] truncate">${esc((o.fabricTypes || []).join(', '))}</div><div class="max-w-[12rem] truncate text-[11px] text-gray-500">${esc((o.colours || []).join(', '))}</div>`,
                day(o.requiredDate) + (o.overdue ? ' <span class="badge-red badge-dot">Overdue</span>' : o.late ? ' <span class="badge-amber badge-dot">At risk</span>' : ''),
                W.progress(o.delivered, o.quantity, { left: false, tone: o.overdue ? 'critical' : '' }), esc(o.stage)]));
    }

    // The tab may already be open (from the address) before this script listened.
    if (shown()) load();
});
