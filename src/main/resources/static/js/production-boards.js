/*
 * The production screens that are not documents: the Production board, Ready to deliver, Fabric
 * stock and Process routes. Each page marks its root with data-board; everything here is read
 * from ProductionBoardService / FabricStockQueries / ProcessRouteService - figures, never typing.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const root = document.querySelector('[data-board]');
    if (!root) return;
    const { api, esc, fmt, toast, fail, icon } = App;
    const $ = (sel, r) => (r || root).querySelector(sel);
    const nf = new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 });
    const num = v => v == null || v === '' ? '—' : nf.format(Number(v));
    const n = v => Number(v) || 0;
    const yes = name => root.dataset[name] === 'true';

    function kpis(items) {
        const host = $('[data-kpis]');
        if (!host) return;
        host.innerHTML = items.map(([label, value, meta, tone]) => `<div class="kpi">
            <span class="kpi-label">${esc(label)}</span>
            <span class="kpi-value ${tone || ''}">${esc(value)}</span>
            ${meta ? `<span class="kpi-meta">${esc(meta)}</span>` : ''}</div>`).join('');
    }

    /** A paged, filtered table fed by one endpoint; pageSize rows at a time. */
    function pagedTable({ url, params, render, empty, onData, pageSize = 50 }) {
        const tbody = $('[data-rows] tbody');
        const pager = $('[data-pager]');
        const cols = $('[data-rows] thead tr').children.length;
        let page = 0, total = 0, draw = 0;
        async function load() {
            const mine = ++draw;
            tbody.style.opacity = '.55';
            try {
                const data = await api(url, { query: Object.assign({ page, size: pageSize }, params()) });
                if (mine !== draw) return;
                total = Number(data.total) || 0;
                const rows = data.rows || [];
                tbody.innerHTML = rows.length ? rows.map(render).join('')
                    : `<tr><td colspan="${cols}" class="whitespace-normal"><div class="empty"><span class="empty-icon">${icon('search', 'icon-lg')}</span>
                        <p class="empty-text">${esc(empty)}</p></div></td></tr>`;
                onData && onData(data);
                const from = total ? page * pageSize + 1 : 0, to = Math.min((page + 1) * pageSize, total);
                if (pager) pager.innerHTML = `<span><b class="font-medium">${from}–${to}</b> of <b class="font-medium">${total}</b></span>
                    <div class="flex items-center gap-1">
                        <button type="button" class="btn-icon btn-sm border border-gray-200 dark:border-gray-700" data-page="-1" aria-label="Previous page" ${page === 0 ? 'disabled' : ''}>${icon('chevron-left')}</button>
                        <button type="button" class="btn-icon btn-sm border border-gray-200 dark:border-gray-700" data-page="1" aria-label="Next page" ${to >= total ? 'disabled' : ''}>${icon('chevron-right')}</button></div>`;
            } catch (error) {
                if (mine !== draw) return;
                tbody.innerHTML = `<tr><td colspan="${cols}"><div class="empty"><p class="empty-title">Could not load this list</p>
                    <p class="empty-text">${esc(error.message)}</p></div></td></tr>`;
            } finally {
                tbody.style.opacity = '';
            }
        }
        pager?.addEventListener('click', e => {
            const b = e.target.closest('[data-page]');
            if (!b || b.disabled) return;
            page = Math.max(0, page + Number(b.dataset.page));
            load();
        });
        return { reload(reset) { if (reset) page = 0; return load(); } };
    }

    function wireFilters(table) {
        const reload = () => table.reload(true);
        $('[data-q]')?.addEventListener('input', App.debounce(reload, 250));
        root.querySelectorAll('select, input[type=date], input[type=checkbox]').forEach(el => {
            if (!el.closest('[data-rows]') && !el.closest('[data-panel="stores"]')) el.addEventListener('change', reload);
        });
        $('[data-reset]')?.addEventListener('click', () => {
            root.querySelectorAll('.toolbar input[type=search], .toolbar input[type=date]').forEach(el => { el.value = ''; });
            const buyer = $('[data-buyer]');
            if (buyer) App.RemoteSelect.of(buyer).setValue(null);
            reload();
        });
    }

    const due = (date, days) => {
        if (!date) return '<span class="text-gray-400">—</span>';
        const d = days != null ? Number(days) : Math.round((new Date(date) - new Date(new Date().toDateString())) / 864e5);
        const tone = d < 0 ? 'text-red-700 dark:text-red-400 font-medium' : d <= 7 ? 'text-amber-700 dark:text-amber-400' : 'text-gray-600';
        return `${esc(fmt.date(date))}<div class="text-xs ${tone}">${d < 0 ? `${-d} days overdue` : d === 0 ? 'Due today' : `in ${d} days`}</div>`;
    };

    // ======================================================================== production board
    if (root.dataset.board === 'production') {
        const table = pagedTable({
            url: '/api/production/board',
            params: () => ({ q: $('[data-q]').value, buyerId: $('[data-buyer]').value || undefined,
                dueBy: $('[data-due]').value || undefined, includeCompleted: $('[data-completed]').checked }),
            empty: 'No open production order lines match these filters.',
            onData: data => {
                const t = data.totals || {};
                kpis([['Open orders', num(t.orders)], ['Lines', num(t.lines)], ['Late', num(t.late), 'due within a week, not ready', n(t.late) ? 'text-red-700' : ''],
                      ['To weave', num(t.toWeave)], ['To dye', num(t.toDye)], ['To schedule', num(t.toSchedule)]]);
            },
            render: r => {
                const actions = [
                    r.needsWeaving && yes('canWeave') && `<a class="btn-ghost btn-sm" href="/weaving-wo?new=1&parent=${r.bpoId}">${icon('loom')}Weave</a>`,
                    r.needsDyeing && yes('canDye') && `<a class="btn-ghost btn-sm" href="/processing-wo?new=1&parent=${r.bpoId}">${icon('dyeing')}Dye</a>`,
                    r.needsSchedule && yes('canSchedule') && `<a class="btn-ghost btn-sm" href="/requestforpi?new=1&parent=${r.bpoId}">${icon('calendar')}Schedule</a>`
                ].filter(Boolean).join('');
                const greigeRoute = r.deliverStage !== 'FINISHED';
                const sub = t => `<div class="text-xs text-gray-500">${t}</div>`;
                const na = '<span class="text-gray-400">n/a</span>';
                return `<tr class="${r.late ? 'bg-red-50/60 dark:bg-red-950/20' : ''}">
                    <td><a class="doc-no" href="/bpo?open=${esc(r.bpoId)}">${esc(r.bpoNo)}</a>
                        <div class="mt-1 flex gap-1">${App.statusBadge(r.status)}${r.late ? '<span class="badge-red">Late</span>' : ''}${r.shortClosed ? '<span class="badge-amber">Short-closed</span>' : ''}</div></td>
                    <td>${esc(r.buyer || '—')}${sub(esc(r.team || ''))}</td>
                    <td>${esc(r.colorName || '—')} <span class="font-mono text-xs text-gray-500">${esc(r.construction || '')}</span>
                        ${sub(esc(r.fabricType || '') + (r.routeCode ? '' : ' · no route'))}</td>
                    <td>${due(r.requiredDate)}</td>
                    <td class="text-right tabular-nums">${num(r.quantity)} <span class="text-xs text-gray-500">${esc(r.uom || '')}</span></td>
                    <td class="text-right tabular-nums">${num(r.weaving)} <span class="text-gray-400">/ ${num(r.greigeRequired)}</span>${sub('received ' + num(r.greigeReceived))}</td>
                    <td class="text-right tabular-nums">${r.needsProcessing ? `${num(r.dyeing)} <span class="text-gray-400">/ ${num(r.quantity)}</span>`
                        + sub(`issued ${num(r.greigeIssued)} · A ${num(r.finishedA)}${n(r.finishedB) ? ` · B ${num(r.finishedB)}` : ''}`) : na}</td>
                    <td class="text-right tabular-nums font-medium">${num(r.ready)}${sub(greigeRoute ? 'greige' : 'finished A')}</td>
                    <td class="text-right tabular-nums">${num(r.scheduled)}${sub('delivered ' + num(r.delivered))}</td>
                    <td class="text-right tabular-nums font-medium">${num(r.balance)}</td>
                    <td><div class="flex gap-1">${actions}</div></td>
                </tr>`;
            }
        });
        wireFilters(table);
        table.reload(true);
    }

    // ======================================================================== ready to deliver
    if (root.dataset.board === 'delivery') {
        const table = pagedTable({
            url: '/api/production/delivery-board',
            params: () => ({ q: $('[data-q]').value, buyerId: $('[data-buyer]').value || undefined, deliverableOnly: $('[data-deliverable]').checked }),
            empty: 'Nothing scheduled can go out now. Untick "Deliverable now only" to see every approved schedule line.',
            onData: data => {
                const t = data.totals || {};
                kpis([['Schedule lines', num(t.lines)], ['Deliverable now', num(t.deliverableLines), `${num(t.deliverable)} ready to send`],
                      ['Still to order', num(t.toOrder)], ['Overdue', num(t.overdue), 'past date, not on a delivery order', n(t.overdue) ? 'text-red-700' : '']]);
            },
            render: r => `<tr>
                <td>${due(r.dueDate, r.daysDue)}</td>
                <td><a class="doc-no" href="/requestforpi?open=${esc(r.scheduleId)}">${esc(r.scheduleNo)}</a></td>
                <td>${esc(r.buyer || '—')}<div class="text-xs text-gray-500">${esc(r.garments || '')}</div></td>
                <td><a class="font-mono text-xs" href="/bpo?open=${esc(r.bpoId)}">${esc(r.bpoNo)}</a>
                    <div class="text-xs text-gray-500">${esc(r.fabricType || '')} · ${r.deliverStage === 'FINISHED' ? 'finished' : 'greige'}</div></td>
                <td>${esc(r.colorName || '—')}</td>
                <td class="text-right tabular-nums">${num(r.scheduled)} <span class="text-xs text-gray-500">${esc(r.uom || '')}</span></td>
                <td class="text-right tabular-nums">${num(r.onDeliveryOrders)}</td>
                <td class="text-right tabular-nums">${num(r.delivered)}</td>
                <td class="text-right tabular-nums">${num(r.ready)}</td>
                <td class="text-right tabular-nums font-semibold ${n(r.deliverable) > 0 ? 'text-emerald-700 dark:text-emerald-400' : 'text-gray-400'}">${num(r.deliverable)}</td>
                <td>${n(r.deliverable) > 0 && yes('canOrder')
                    ? `<a class="btn-primary btn-sm" href="/delivery-order?new=1&parent=${esc(r.scheduleId)}">${icon('clipboard-check')}Create DO</a>` : ''}</td>
            </tr>`
        });
        wireFilters(table);
        table.reload(true);
    }

    // ============================================================================ fabric stock
    if (root.dataset.board === 'stock') {
        App.tabs(root.querySelector('[data-tabs-root]'));
        const lotLabel = r => r.stage === 'GREIGE' ? 'Greige'
            : [r.dyeLot && `Lot ${r.dyeLot}`, r.shade && `Shade ${r.shade}`, `Grade ${r.grade}`].filter(Boolean).join(' · ');
        const table = pagedTable({
            url: '/api/stock/fabric',
            params: () => ({ q: $('[data-q]').value, warehouseId: $('[data-store]').value || undefined,
                stage: $('[data-stage]').value || undefined, withZero: $('[data-zero]').checked }),
            empty: 'No fabric in stock matches these filters.',
            onData: data => {
                const t = data.totals || {};
                kpis([['Lots', num(t.lots)], ['Greige on hand', num(t.greige)], ['Finished on hand', num(t.finished)],
                      ['Reserved', num(t.reserved), 'held for delivery orders'], ['Free', num(t.free)]]);
            },
            render: r => `<tr>
                <td>${esc(r.store)}</td>
                <td>${r.stage === 'GREIGE' ? '<span class="badge-gray">Greige</span>' : '<span class="badge-blue">Finished</span>'}</td>
                <td><a class="doc-no" href="/bpo?open=${esc(r.bpoId)}">${esc(r.bpoNo)}</a><div class="text-xs text-gray-500">${esc(r.team || '')}</div></td>
                <td>${esc(r.buyer || '—')}</td>
                <td><span class="font-mono text-xs">${esc(r.construction || '')}</span><div>${esc(r.colorName || 'All colours')}</div>
                    <div class="text-xs text-gray-500">${esc(r.fabricType || '')}</div></td>
                <td class="text-xs">${esc(lotLabel(r))}${r.grade === 'B' ? ' <span class="badge-amber">B</span>' : ''}</td>
                <td class="text-right tabular-nums">${num(r.quantity)} <span class="text-xs text-gray-500">${esc(r.uom || '')}</span></td>
                <td class="text-right tabular-nums">${num(r.reserved)}</td>
                <td class="text-right tabular-nums font-medium">${num(r.free)}</td>
                <td class="text-right tabular-nums">${num(r.rolls)}</td>
                <td>${App.rowActions(App.rowButton('Ledger', 'history', `data-lot="${esc(r.lotId)}" data-wh="${esc(r.warehouseId)}" data-label="${esc(r.bpoNo + ' · ' + (r.colorName || r.construction || '') + ' · ' + lotLabel(r))}"`))}</td>
            </tr>`
        });
        wireFilters(table);
        table.reload(true);

        const drawer = root.querySelector('[data-moves]');
        const TYPES = { GREIGE_RECEIVE: 'Greige receive', GREIGE_ISSUE: 'Greige issue', FINISHED_RECEIVE: 'Finished receive', DELIVERY: 'Delivery', REVERSAL: 'Reversal' };
        const SLUG = { GREIGE_RECEIVE: 'greige-receive', GREIGE_ISSUE: 'greige-issue', FINISHED_FABRICS_RECEIVE: 'finished-receive', FABRICS_DELIVERY: 'fabrics-delivery' };
        root.addEventListener('click', async e => {
            const b = e.target.closest('[data-lot]');
            if (!b) return;
            drawer.querySelector('[data-moves-sub]').textContent = b.dataset.label;
            drawer.querySelector('[data-moves-body]').innerHTML = '<div class="skeleton h-5 w-2/3"></div>';
            drawer.showModal();
            try {
                const moves = await api(`/api/stock/fabric/lots/${b.dataset.lot}/moves`, { query: { warehouseId: b.dataset.wh } });
                drawer.querySelector('[data-moves-body]').innerHTML = moves.length ? `<ol class="space-y-3">${moves.map(m => `<li class="flex gap-3">
                    <span class="mt-0.5 inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full ${n(m.quantity) >= 0 ? 'bg-emerald-50 text-emerald-600' : 'bg-red-50 text-red-600'}">
                        ${icon(n(m.quantity) >= 0 ? 'arrow-right' : 'arrow-up-right', 'h-3 w-3')}</span>
                    <div class="min-w-0 flex-1 text-sm">
                        <p><span class="font-medium tabular-nums">${n(m.quantity) >= 0 ? '+' : ''}${num(m.quantity)}</span>
                           <span class="text-gray-500">${esc(TYPES[m.moveType] || m.moveType)} · ${esc(m.store)}</span></p>
                        <p class="text-xs text-gray-500"><a class="card-link" href="/${esc(SLUG[m.documentType] || '')}?open=${esc(m.documentId)}">${esc(m.documentNo)}</a>
                           · ${esc(m.postedBy || '')} · ${fmt.timeTag(m.postedAt)}${m.rolls ? ` · ${num(m.rolls)} rolls` : ''}</p>
                        ${m.remarks ? `<p class="mt-1 text-xs text-gray-600">${esc(m.remarks)}</p>` : ''}
                    </div></li>`).join('')}</ol>` : '<p class="text-sm text-gray-500">No moves.</p>';
            } catch (error) { fail(error); }
        });

        root.querySelector('[data-panel="stores"]').addEventListener('change', async e => {
            const box = e.target.closest('[data-role]');
            if (!box) return;
            const tr = box.closest('tr');
            const body = { holdsGreige: tr.querySelector('[data-role="holdsGreige"]').checked, holdsFinished: tr.querySelector('[data-role="holdsFinished"]').checked };
            try {
                await api(`/api/stock/fabric/stores/${tr.dataset.storeId}`, { method: 'POST', body });
                toast('Store roles saved.', 'success');
            } catch (error) { box.checked = !box.checked; fail(error); }
        });
    }

    // ========================================================================== process routes
    if (root.dataset.board === 'routes') {
        const O = window.ROUTE_OPTIONS || {};
        const label = (list, v) => ((O[list] || []).find(o => o.value === v) || {}).label || v || '—';
        const canAmend = yes('canAmend');
        let routes = [];
        const tbody = $('[data-rows] tbody');
        async function load() {
            try {
                routes = await api('/api/process-routes');
                tbody.innerHTML = routes.map(r => `<tr>
                    <td class="font-medium">${esc(r.fabricType)}${r.active === false ? ' <span class="badge-gray">Inactive</span>' : ''}</td>
                    <td>${esc(r.routeLabel)}</td>
                    <td>${r.greigeKey === 'CONSTRUCTION' ? 'Per fabric line' : 'Per colour'}</td>
                    <td>${r.deliverStage === 'FINISHED' ? 'Finished store' : 'Greige store'}</td>
                    <td>${r.needsProcessing ? esc(label('processKinds', r.processKind)) : '<span class="text-gray-400">None</span>'}</td>
                    <td>${esc(label('yarnPreps', r.yarnPrep))}</td>
                    <td class="text-right tabular-nums">${num(r.greigeAllowancePct)} %</td>
                    <td class="text-right tabular-nums">${num(r.receiveTolerancePct)} %</td>
                    <td class="text-right tabular-nums">${num(r.deliveryTolerancePct)} %</td>
                    <td>${canAmend ? App.rowActions(App.editButton(r.id)) : ''}</td></tr>`).join('')
                    || `<tr><td colspan="10"><div class="empty"><p class="empty-text">No routes yet.</p></div></td></tr>`;
            } catch (error) { fail(error); }
        }
        async function edit(route) {
            const r = route || { yarnPrep: 'NONE', greigeKey: 'CONSTRUCTION', deliverStage: 'FINISHED', processKind: 'DYE',
                                 greigeAllowancePct: 10, receiveTolerancePct: 5, deliveryTolerancePct: 3, routeCode: 'PIECE_DYED' };
            const values = await App.form({
                title: route ? `Route for ${route.fabricType}` : 'New process route',
                message: 'Orders already raised keep the route they were saved with.',
                fields: [
                    { name: 'fabricType', label: 'Fabric type (as on the Booking)', required: true, maxlength: 60, value: r.fabricType || '' },
                    { name: 'routeCode', label: 'Route', type: 'select', options: O.routeCodes, value: r.routeCode },
                    { name: 'greigeKey', label: 'Weaving', type: 'select', options: O.greigeKeys, value: r.greigeKey },
                    { name: 'deliverStage', label: 'Delivered from', type: 'select', options: O.deliverStages, value: r.deliverStage },
                    { name: 'processKind', label: 'Dyeing (finished routes)', type: 'select', options: O.processKinds, value: r.processKind || 'DYE' },
                    { name: 'yarnPrep', label: 'Yarn preparation', type: 'select', options: O.yarnPreps, value: r.yarnPrep },
                    { name: 'greigeAllowancePct', label: 'Greige allowance %', type: 'number', value: r.greigeAllowancePct, hint: 'Extra greige woven to cover processing loss.' },
                    { name: 'receiveTolerancePct', label: 'Receive tolerance %', type: 'number', value: r.receiveTolerancePct },
                    { name: 'deliveryTolerancePct', label: 'Delivery tolerance %', type: 'number', value: r.deliveryTolerancePct },
                    { name: 'remarks', label: 'Remarks', maxlength: 300, value: r.remarks || '' }
                ],
                confirmText: 'Save'
            });
            if (!values) return;
            try {
                await api('/api/process-routes', { method: 'POST', query: { id: route ? route.id : undefined }, body: Object.assign({}, values, {
                    greigeAllowancePct: Number(values.greigeAllowancePct), receiveTolerancePct: Number(values.receiveTolerancePct),
                    deliveryTolerancePct: Number(values.deliveryTolerancePct), active: true }) });
                toast('Route saved.', 'success');
                load();
            } catch (error) { fail(error); }
        }
        root.addEventListener('click', e => {
            if (e.target.closest('[data-route-new]')) return edit(null);
            const b = e.target.closest('[data-edit]');
            if (b) edit(routes.find(r => String(r.id) === b.dataset.edit));
        });
        load();
    }
});
