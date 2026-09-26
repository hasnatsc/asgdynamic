/*
 * Booking analytics & reports. One filter row scopes the whole page: the Overview's tiles and
 * charts come from one /overview call, the Reports tab from /reports/{name} or the paged register,
 * all with the same filters - so every number on the page agrees with every other.
 *
 * The server decides whose bookings the viewer may see; this script only offers the views it was
 * told about. Filters live in the address bar, so a view can be bookmarked or sent to a colleague
 * (who sees it through their own scope).
 */
(() => {
    'use strict';

    const C = () => window.AnalyticsCharts;
    const API = '/api/analytics/booking';
    const esc = s => App.esc(s);

    document.addEventListener('DOMContentLoaded', () => {
        const root = document.querySelector('[data-analytics]');
        if (!root) return;
        const form = root.querySelector('[data-filters]');
        const f = name => form.elements.namedItem(name);
        const fmt = C().fmt;

        let data = null;             // the last overview
        let measure = 'value';       // trend: value | qty | count
        const tableMode = new Set(); // charts showing their table instead
        let report = 'REGISTER';
        let reportData = null;
        let loading = 0;

        // ------------------------------------------------------------------ period presets

        const iso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
        function period(key) {
            const t = new Date();
            const y = t.getFullYear(), m = t.getMonth();
            const q = Math.floor(m / 3) * 3;
            switch (key) {
                case 'thisMonth':   return [new Date(y, m, 1), t];
                case 'lastMonth':   return [new Date(y, m - 1, 1), new Date(y, m, 0)];
                case 'thisQuarter': return [new Date(y, q, 1), t];
                case 'lastQuarter': return [new Date(y, q - 3, 1), new Date(y, q, 0)];
                case 'ytd':         return [new Date(y, 0, 1), t];
                case 'lastYear':    return [new Date(y - 1, 0, 1), new Date(y - 1, 11, 31)];
                case 'last12':
                default:            return [new Date(y, m - 11, 1), t];
            }
        }

        function syncPeriod() {
            const custom = f('period').value === 'custom';
            root.querySelectorAll('[data-custom-range]').forEach(el => { el.hidden = !custom; });
            if (!custom) {
                const [from, to] = period(f('period').value);
                f('from').value = iso(from);
                f('to').value = iso(to);
            }
        }

        // ------------------------------------------------------------------ filters <-> address bar

        const KEYS = ['period', 'from', 'to', 'view', 'teamId', 'currency', 'status', 'bookingType'];

        function params(extra) {
            const p = {
                from: f('from').value, to: f('to').value, view: f('view').value || '',
                teamId: f('teamId').value || '', personId: f('personId').value || '',
                buyerId: f('buyerId').value || '', currency: f('currency').value || '',
                status: f('status').value || '', bookingType: f('bookingType').value || ''
            };
            return Object.assign(p, extra || {});
        }

        function saveToUrl() {
            const qs = new URLSearchParams();
            for (const k of KEYS) {
                const v = f(k).value;
                if (v && !(k !== 'period' && f('period').value !== 'custom' && (k === 'from' || k === 'to'))) qs.set(k, v);
            }
            if (report !== 'REGISTER') qs.set('report', report);
            history.replaceState(null, '', location.pathname + (qs.toString() ? '?' + qs : '') + location.hash);
        }

        function loadFromUrl() {
            const qs = new URLSearchParams(location.search);
            for (const k of KEYS) {
                if (!qs.has(k)) continue;
                const input = f(k);
                if (input.tagName === 'SELECT' && ![...input.options].some(o => o.value === qs.get(k))) {
                    input.insertAdjacentHTML('beforeend', `<option value="${esc(qs.get(k))}">${esc(qs.get(k))}</option>`);
                }
                input.value = qs.get(k);
            }
            if (qs.has('report')) report = qs.get('report');
            if (qs.get('tab') === 'reports' || qs.has('report')) tabs.select('reports');
        }

        // ------------------------------------------------------------------ person picker (scoped)

        const person = new App.RemoteSelect(f('personId'), {
            url: `${API}/persons`,
            placeholder: 'Anyone',
            allowClear: true,
            params: () => params({ personId: '' })
        });

        // ------------------------------------------------------------------ tabs

        const tabs = App.tabs(root.querySelector('[data-tabs-root]'), name => {
            if (name === 'reports') loadReport();
        });

        // ------------------------------------------------------------------ overview

        async function loadOverview() {
            const ticket = ++loading;
            root.querySelectorAll('.an-chart').forEach(h => h.setAttribute('data-loading', ''));
            try {
                const result = await App.api(`${API}/overview`, { query: params() });
                if (ticket !== loading) return;
                data = result;
                renderScope();
                renderKpis();
                renderCharts();
            } catch (error) {
                if (ticket === loading) App.fail(error);
            } finally {
                if (ticket === loading) root.querySelectorAll('.an-chart').forEach(h => h.removeAttribute('data-loading'));
            }
        }

        function renderScope() {
            const s = data.scope;
            root.querySelector('[data-scope-label]').textContent =
                `${s.views.find(v => v.key === s.view)?.label || ''} · ${s.reasons.join('; ')}`;
            const view = f('view');
            view.innerHTML = s.views.map(v => `<option value="${esc(v.key)}">${esc(v.label)}</option>`).join('');
            view.value = s.view;
            root.querySelector('[data-view-filter]').hidden = s.views.length < 2;

            const team = f('teamId');
            const chosenTeam = team.value;
            team.innerHTML = '<option value="">All teams</option>'
                + s.teams.map(t => `<option value="${esc(t.id)}">${esc(t.name)}</option>`).join('');
            team.value = s.teams.some(t => String(t.id) === chosenTeam) ? chosenTeam : '';
            root.querySelector('[data-team-filter]').hidden = s.view === 'MINE' || s.teams.length < 2;
            root.querySelector('[data-person-filter]').hidden = s.view === 'MINE';

            const currency = f('currency');
            const chosen = currency.value;
            currency.innerHTML = `<option value="">Most used${data.currency && !chosen ? ` (${esc(data.currency)})` : ''}</option>`
                + data.currencies.map(c => `<option value="${esc(c.code)}">${esc(c.code)} · ${fmt.int(c.bookings)}</option>`).join('')
                + '<option value="ALL">All currencies (no values)</option>';
            currency.value = [...currency.options].some(o => o.value === chosen) ? chosen : '';

            const p = data.period;
            const note = `${longDate(p.from)} – ${longDate(p.to)} · compared with ${longDate(p.previousFrom)} – ${longDate(p.previousTo)}`
                + (data.currency ? ` · values in ${data.currency}` : ' · counts and quantities only: choose a currency for values');
            root.querySelector('[data-period-note]').textContent = note;
            root.querySelector('[data-print-head]').textContent = `Booking analytics · ${note} · ${root.querySelector('[data-scope-label]').textContent}`;
            root.querySelector('[data-register-csv]').href = `${API}/register.csv?` + new URLSearchParams(
                Object.entries(params()).filter(([, v]) => v !== '' && v != null));
        }

        // ------------------------------------------------------------------ KPI tiles

        const money = v => v == null ? '—' : fmt.compact(v);
        const moneyFull = v => v == null ? '—' : `${data.currency || ''} ${fmt.dec2(v)}`.trim();
        const hours = h => h == null ? '—' : h < 48 ? `${fmt.int(h)} h` : `${(h / 24).toFixed(1)} d`;

        /** "▲ 12.4%" against the previous period; tone says whether that direction is good. */
        function delta(cur, prev, upIsGood = true, points = false) {
            if (cur == null || prev == null) return '';
            let change, label;
            if (points) {
                change = Number(cur) - Number(prev);
                label = `${change >= 0 ? '+' : '−'}${Math.abs(change).toFixed(1)} pts`;
            } else {
                if (Number(prev) === 0) return Number(cur) ? '<span class="an-delta" data-tone="flat">new</span>' : '';
                change = (Number(cur) - Number(prev)) / Math.abs(Number(prev)) * 100;
                label = `${Math.abs(change).toFixed(1)}%`;
            }
            if (Math.abs(change) < 0.05) return '<span class="an-delta" data-tone="flat">no change</span>';
            const tone = (change > 0) === upIsGood ? 'good' : 'bad';
            const arrow = change > 0 ? '▲' : '▼';
            return `<span class="an-delta" data-tone="${tone}" title="vs the previous period"><span aria-hidden="true">${arrow}</span>`
                + `<span class="sr-only">${change > 0 ? 'up' : 'down'}</span>${esc(label)}</span>`;
        }

        function tile(label, value, meta, opts = {}) {
            return `<div class="an-kpi${opts.hero ? ' an-kpi-hero' : ''}"${opts.title ? ` title="${esc(opts.title)}"` : ''}>
                <span class="an-kpi-label">${esc(label)}</span>
                <span class="an-kpi-value">${value}</span>
                <span class="an-kpi-meta">${meta}</span></div>`;
        }

        function renderKpis() {
            const k = data.kpis, p = data.previous;
            const cur = data.currency ? `<span class="ml-1 text-sm font-medium text-gray-500">${esc(data.currency)}</span>` : '';
            const noValue = '<span class="text-gray-400">Choose a currency</span>';
            root.querySelector('[data-kpis]').innerHTML = [
                tile('Confirmed value', data.currency ? esc(money(k.confirmedValue)) + cur : '—',
                    data.currency ? `${delta(k.confirmedValue, p.confirmedValue)} <span>avg ${esc(fmt.dec2(k.avgRate))}/unit</span>` : noValue,
                    { hero: true, title: moneyFull(k.confirmedValue) }),
                tile('Confirmed quantity', esc(fmt.compact(k.confirmedQty)),
                    `${delta(k.confirmedQty, p.confirmedQty)} <span>of ${esc(fmt.compact(k.quantity))} booked</span>`,
                    { title: fmt.int(k.confirmedQty) }),
                tile('Bookings', esc(fmt.int(k.bookings)),
                    `${delta(k.bookings, p.bookings)} <span>${esc(fmt.int(k.confirmed))} confirmed · ${esc(fmt.int(k.pipeline))} in pipeline</span>`),
                tile('Approval rate', esc(fmt.pct(k.approvalRatePct)),
                    `${delta(k.approvalRatePct, p.approvalRatePct, true, true)} <span>${esc(fmt.int(k.rejected))} rejected</span>`),
                tile('Avg approval time', esc(hours(k.avgApprovalHours)),
                    `${delta(k.avgApprovalHours, p.avgApprovalHours, false)} <span>submission to approval</span>`),
                tile('Margin vs break-even', data.currency ? esc(fmt.pct(k.marginPct)) : '—',
                    data.currency ? `${delta(k.marginPct, p.marginPct, true, true)} <span>on ${esc(fmt.pct(k.marginCoveragePct))} of confirmed qty</span>` : noValue)
            ].join('');
            const strip = [
                ['Pipeline value', data.currency ? `${money(k.pipelineValue)} ${data.currency}` : '—'],
                ['Awaiting approval', fmt.int(k.pending)],
                ['Drawn into production', fmt.pct(k.fulfilmentPct)],
                ['Avg lead time', k.avgLeadDays == null ? '—' : `${fmt.int(k.avgLeadDays)} days`],
                ['Active buyers', fmt.int(k.buyers)],
                ['Revised bookings', fmt.int(k.revised)]
            ];
            root.querySelector('[data-strip]').innerHTML = strip.map(([label, value]) =>
                `<div><span class="an-strip-label">${esc(label)}</span><span class="an-strip-value">${esc(value)}</span></div>`).join('');
        }

        // ------------------------------------------------------------------ charts

        const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        const parse = s => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d || 1); };
        const longDate = s => { const d = parse(s); return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`; };
        function bucketLabel(s, grain) {
            const d = parse(s);
            return grain === 'MONTH' ? `${MONTHS[d.getMonth()]} ${String(d.getFullYear()).slice(2)}` : `${d.getDate()} ${MONTHS[d.getMonth()]}`;
        }
        function bucketTitle(s, grain) {
            const d = parse(s);
            if (grain === 'MONTH') return `${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
            if (grain === 'WEEK') return `Week of ${longDate(s)}`;
            return longDate(s);
        }

        const card = key => root.querySelector(`[data-chart="${key}"]`);
        const host = key => card(key).querySelector('[data-host]');

        /** A breakdown's bar value: confirmed value with a currency, confirmed quantity without. */
        const measureOf = (byQty) => data.currency && !byQty
            ? { key: 'confirmedValue', label: `Confirmed value (${data.currency})`, format: (v, full) => full ? fmt.dec2(v) : fmt.compact(v) }
            : { key: 'confirmedQty', label: 'Confirmed quantity', format: (v, full) => full ? fmt.int(v) : fmt.compact(v) };

        function breakdownDetail(r) {
            const rows = [];
            if (data.currency) rows.push({ label: `Confirmed value (${data.currency})`, value: fmt.dec2(r.confirmedValue) });
            rows.push({ label: 'Confirmed qty', value: fmt.int(r.confirmedQty) });
            rows.push({ label: 'Bookings', value: `${fmt.int(r.bookings)} (${fmt.int(r.confirmed)} confirmed)` });
            if (data.currency && r.avgRate != null) rows.push({ label: 'Avg price', value: fmt.dec2(r.avgRate) });
            if (data.currency && r.marginPct != null) rows.push({ label: 'Margin vs break-even', value: fmt.pct(r.marginPct) });
            return rows;
        }

        function breakdownTable(rows) {
            const cols = [{ label: 'Name', get: r => r.label }, { label: 'Bookings', num: true, get: r => fmt.int(r.bookings) },
                { label: 'Confirmed', num: true, get: r => fmt.int(r.confirmed) },
                { label: 'Confirmed qty', num: true, get: r => fmt.int(r.confirmedQty) }];
            if (data.currency) {
                cols.push({ label: `Confirmed value (${data.currency})`, num: true, get: r => fmt.dec2(r.confirmedValue) },
                    { label: 'Avg price', num: true, get: r => fmt.dec2(r.avgRate) },
                    { label: 'Margin', num: true, get: r => fmt.pct(r.marginPct) });
            }
            return { columns: cols, rows };
        }

        function drawBreakdown(key, rows, byQty) {
            const c = card(key);
            c.hidden = rows == null;
            if (rows == null) return;
            const m = measureOf(byQty);
            const sub = c.querySelector('[data-sub]');
            if (sub) sub.textContent = m.label + '.';
            if (tableMode.has(key)) { C().table(host(key), breakdownTable(rows)); return; }
            C().hbars(host(key), {
                title: c.querySelector('.card-title').textContent,
                rows: rows.map(r => ({ label: r.label, value: r[m.key], cls: r.other ? 'neutral' : 's1', raw: r })),
                format: m.format,
                detail: row => breakdownDetail(row.raw)
            });
        }

        const STATUS = {
            CONFIRMED: { cls: 'good', icon: 'check-circle' },
            PENDING: { cls: 'warning', icon: 'clock' },
            DRAFT: { cls: 'neutral', icon: 'edit' },
            REJECTED: { cls: 'critical', icon: 'x-circle' },
            CANCELLED: { cls: 'mutedfill', icon: 'x' }
        };

        function renderCharts() {
            // trend
            const t = data.trend, grain = data.period.grain;
            const pick = {
                value: ['confirmedValue', 'pipelineValue', (v, full) => full ? fmt.dec2(v) : fmt.compact(v)],
                qty: ['confirmedQty', 'pipelineQty', (v, full) => full ? fmt.int(v) : fmt.compact(v)],
                count: ['confirmed', 'pipeline', v => fmt.int(v)]
            };
            if (!data.currency && measure === 'value') measure = 'qty';
            root.querySelector('[data-measure-key="value"]').disabled = !data.currency;
            root.querySelectorAll('[data-measure-key]').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.measureKey === measure)));
            const [ck, pk, format] = pick[measure];
            card('trend').querySelector('[data-sub]').textContent = {
                value: `Confirmed and pipeline value (${data.currency}) by booking date.`,
                qty: 'Confirmed and pipeline quantity by booking date.',
                count: 'Confirmed and pipeline bookings by booking date.'
            }[measure];
            if (tableMode.has('trend')) {
                C().table(host('trend'), { rows: t, columns: [
                    { label: 'Period', get: r => bucketTitle(r.bucket, grain) },
                    { label: 'Confirmed', num: true, get: r => format(r[ck], true) },
                    { label: 'Pipeline', num: true, get: r => format(r[pk], true) }] });
            } else {
                C().columns(host('trend'), {
                    title: 'Bookings over time',
                    categories: t.map(r => r.bucket),
                    labels: t.map(r => bucketLabel(r.bucket, grain)),
                    titles: t.map(r => bucketTitle(r.bucket, grain)),
                    series: [{ name: 'Confirmed', cls: 's1', values: t.map(r => r[ck]) },
                             { name: 'Pipeline (draft + awaiting approval)', cls: 's2', values: t.map(r => r[pk]) }],
                    format, height: 260
                });
            }

            // status mix - a part-to-whole; the legend lists every status with its count and share
            C().stackbar(host('status'), {
                title: 'Status mix',
                segments: data.status.map(s => ({ label: s.label, value: s.bookings, cls: STATUS[s.group].cls, icon: STATUS[s.group].icon })),
                format: v => fmt.int(v)
            });

            // approval queue - ordered buckets on the one-hue ordinal ramp
            const ages = data.aging;
            if (tableMode.has('aging')) {
                C().table(host('aging'), { rows: ages, columns: [{ label: 'Waiting', get: r => r.label },
                    { label: 'Bookings', num: true, get: r => fmt.int(r.bookings) },
                    { label: 'Quantity', num: true, get: r => fmt.int(r.quantity) }]
                    .concat(data.currency ? [{ label: `Value (${data.currency})`, num: true, get: r => fmt.dec2(r.value) }] : []) });
            } else {
                C().hbars(host('aging'), {
                    title: 'Approval queue', emptyText: 'Nothing is awaiting approval.',
                    rows: ages.map((r, i) => ({ label: r.label, value: r.bookings, cls: `o${i + 1}`, raw: r })),
                    format: v => fmt.int(v),
                    detail: row => [{ label: 'Bookings', value: fmt.int(row.raw.bookings) },
                        { label: 'Quantity', value: fmt.int(row.raw.quantity) }]
                        .concat(data.currency ? [{ label: `Value (${data.currency})`, value: fmt.dec2(row.raw.value) }] : [])
                });
            }

            drawBreakdown('buyer', data.byBuyer);
            drawBreakdown('fabric', data.byFabric, true);
            drawBreakdown('team', data.byTeam);
            drawBreakdown('person', data.byPerson);

            // delivery schedule - the weeks ahead as columns; overdue quantity is a callout above them,
            // in the critical status colour with its icon and words, since it would dwarf every week.
            const d = data.delivery;
            const overdue = d.find(r => r.bucket === 'OVERDUE') || { bookings: 0, openQty: 0, openValue: null };
            const weeks = d.filter(r => r.bucket !== 'OVERDUE');
            const callout = card('delivery').querySelector('[data-overdue]');
            callout.hidden = !Number(overdue.bookings);
            callout.innerHTML = `${App.icon('alert', 'h-4 w-4 shrink-0')}<span><b>${esc(fmt.int(overdue.openQty))}</b> overdue across `
                + `<b>${esc(fmt.int(overdue.bookings))}</b> ${Number(overdue.bookings) === 1 ? 'booking' : 'bookings'}`
                + (data.currency && overdue.openValue != null ? ` · ${esc(data.currency)} ${esc(fmt.compact(overdue.openValue))}` : '')
                + ' - past their delivery date with quantity still open.</span>';
            const qty = (v, full) => full ? fmt.int(v) : fmt.compact(v);
            if (tableMode.has('delivery')) {
                C().table(host('delivery'), { rows: d, columns: [{ label: 'Due', get: r => r.label },
                    { label: 'Bookings', num: true, get: r => fmt.int(r.bookings) },
                    { label: 'Open qty', num: true, get: r => fmt.int(r.openQty) }]
                    .concat(data.currency ? [{ label: `Open value (${data.currency})`, num: true, get: r => fmt.dec2(r.openValue) }] : []) });
            } else {
                C().columns(host('delivery'), {
                    title: 'Delivery schedule', emptyText: 'Nothing confirmed is due in the next twelve weeks.',
                    categories: weeks.map(r => r.bucket),
                    labels: weeks.map(r => r.bucket.length === 10 ? bucketLabel(r.bucket, 'DAY') : r.label),
                    titles: weeks.map(r => r.bucket.length === 10 ? `Due week of ${longDate(r.bucket)} · ${fmt.int(r.bookings)} bookings` : r.label),
                    series: [{ name: 'Open quantity', cls: 's1', values: weeks.map(r => r.openQty) }],
                    format: qty, height: 220
                });
            }
        }

        root.querySelector('[data-measure]').addEventListener('click', event => {
            const b = event.target.closest('[data-measure-key]');
            if (!b || b.disabled || !data) return;
            measure = b.dataset.measureKey;
            renderCharts();
        });

        root.querySelectorAll('[data-table-toggle]').forEach(b => b.addEventListener('click', () => {
            const key = b.closest('[data-chart]').dataset.chart;
            tableMode.has(key) ? tableMode.delete(key) : tableMode.add(key);
            b.textContent = tableMode.has(key) ? 'Chart' : 'Table';
            if (data) renderCharts();
        }));

        // ------------------------------------------------------------------ reports

        const register = new App.Grid({
            url: `${API}/register`,
            table: document.getElementById('anRegister'),
            pager: document.getElementById('anRegisterPager'),
            sort: { column: 'documentDate', dir: 'desc' },
            params: () => params({ search: document.getElementById('anRegisterSearch').value.trim() }),
            emptyText: 'No bookings match these filters.',
            columns: [
                r => `<span class="whitespace-nowrap"><a class="doc-no hover:underline" href="/booking?open=${esc(r.id)}">${esc(r.documentNo)}</a>${r.revisionNo ? ` <span class="badge-gray">R${esc(r.revisionNo)}</span>` : ''}</span>`,
                r => `<span class="whitespace-nowrap">${esc(r.documentDate || '')}</span>`,
                r => esc(r.buyer || '—'),
                r => esc(r.team || '—'),
                r => esc(r.person || '—'),
                r => `<span class="whitespace-nowrap">${esc(r.requiredDate || '—')}</span>`,
                r => App.statusBadge ? App.statusBadge(r.status) : esc(r.status),
                r => `<span class="block text-right tabular-nums">${esc(fmt.int(r.quantity))}</span>`,
                r => `<span class="block text-right tabular-nums">${esc(fmt.int(r.fulfilled))}</span>`,
                r => `<span class="block whitespace-nowrap text-right tabular-nums">${esc(fmt.dec2(r.value))} <span class="text-xs text-gray-500">${esc(r.currency)}</span></span>`
            ]
        });
        document.getElementById('anRegisterSearch').addEventListener('input', App.debounce(() => register.reload(true), 300));

        const reportList = root.querySelector('[data-report-list]');
        reportList.addEventListener('click', event => {
            const b = event.target.closest('[data-report]');
            if (!b) return;
            report = b.dataset.report;
            saveToUrl();
            loadReport();
        });

        const cellText = (type, v) => {
            if (v == null || v === '') return type === 'text' ? '' : '—';
            switch (type) {
                case 'count': case 'qty': return fmt.int(v);
                case 'money': case 'rate': return fmt.dec2(v);
                case 'pct': return fmt.pct(v);
                case 'days': return Number(v).toFixed(Number.isInteger(Number(v)) ? 0 : 1);
                case 'datetime': return String(v).replace('T', ' ').slice(0, 16);
                default: return String(v);
            }
        };

        async function loadReport() {
            reportList.querySelectorAll('[data-report]').forEach(b => b.setAttribute('aria-selected', String(b.dataset.report === report)));
            const title = root.querySelector('[data-report-title]');
            const sub = root.querySelector('[data-report-sub]');
            const regBox = root.querySelector('[data-register]');
            const tableBox = root.querySelector('[data-report-table]');
            if (report === 'REGISTER') {
                title.textContent = 'Booking register';
                sub.textContent = 'Every booking in the period, one row each - its latest revision. Open one to see it in full.';
                regBox.hidden = false;
                tableBox.hidden = true;
                reportData = null;
                register.reload(true);
                return;
            }
            regBox.hidden = true;
            tableBox.hidden = false;
            tableBox.style.opacity = '.55';
            try {
                reportData = await App.api(`${API}/reports/${encodeURIComponent(report)}`, { query: params() });
            } catch (error) {
                tableBox.style.opacity = '';
                App.fail(error);
                return;
            }
            tableBox.style.opacity = '';
            title.textContent = reportData.title;
            sub.textContent = (reportData.forwardLooking ? 'Every open booking in scope, whenever booked'
                : `${longDate(reportData.period.from)} – ${longDate(reportData.period.to)}`)
                + (reportData.currency ? ` · values in ${reportData.currency}` : ' · choose a currency for values')
                + ` · ${fmt.int(reportData.rows.length)} rows`;
            renderReportTable(tableBox);
        }

        function renderReportTable(box) {
            const { columns, rows, totals } = reportData;
            const numeric = c => c.type !== 'text' && c.type !== 'date' && c.type !== 'datetime';
            const linked = rows.length && rows[0].documentNo !== undefined;
            box.innerHTML = `<table class="an-table">
                <thead><tr>${columns.map(c => `<th class="${numeric(c) ? 'num' : ''}">${esc(c.label)}</th>`).join('')}</tr></thead>
                <tbody>${rows.length ? rows.map(r => `<tr>${columns.map(c => {
                    const v = cellText(c.type, r[c.key]);
                    const cell = linked && c.key === 'documentNo' ? `<a class="doc-no hover:underline" href="/booking?open=${esc(r.id)}">${esc(v)}</a>` : esc(v);
                    const overdue = c.key === 'daysToDue' && Number(r[c.key]) < 0 ? ' text-red-700 dark:text-red-400 font-medium' : '';
                    return `<td class="${numeric(c) ? 'num' : ''}${overdue}">${cell}</td>`;
                }).join('')}</tr>`).join('')
                    : `<tr><td colspan="${columns.length}" class="py-10 text-center text-gray-500">Nothing matches these filters.</td></tr>`}</tbody>
                ${rows.length && Object.keys(totals).length ? `<tfoot><tr>${columns.map((c, i) => i === 0
                    ? '<td>Total</td>' : `<td class="${numeric(c) ? 'num' : ''}">${totals[c.key] !== undefined ? esc(cellText(c.type, totals[c.key])) : ''}</td>`).join('')}</tr></tfoot>` : ''}
            </table>`;
        }

        root.querySelector('[data-action="report-csv"]').addEventListener('click', () => {
            if (report === 'REGISTER') { window.location.href = root.querySelector('[data-register-csv]').href; return; }
            if (!reportData) return;
            const { columns, rows, totals } = reportData;
            const raw = (c, v) => v == null ? '' : (c.type === 'text' || c.type === 'date' || c.type === 'datetime') ? v : Number(v);
            const lines = [columns.map(c => c.label + (c.type === 'money' && reportData.currency ? ` (${reportData.currency})` : ''))]
                .concat(rows.map(r => columns.map(c => raw(c, r[c.key]))));
            if (Object.keys(totals).length) lines.push(columns.map((c, i) => i === 0 ? 'Total' : raw(c, totals[c.key])));
            App.downloadCsv(`booking-${report.toLowerCase()}-${f('from').value}-to-${f('to').value}.csv`, lines);
        });

        // ------------------------------------------------------------------ wiring

        function refresh() {
            saveToUrl();
            loadOverview();
            if (!root.querySelector('[data-panel="reports"]').hidden) loadReport();
        }

        form.addEventListener('change', event => {
            if (event.target === f('period')) syncPeriod();
            if (event.target === f('from') || event.target === f('to')) f('period').value = 'custom';
            if (event.target === f('view')) {
                // A new view offers other teams and people: start them afresh.
                f('teamId').value = '';
                person.setValue(null);
            }
            if (event.target === f('view') || event.target === f('teamId')) person.refresh && person.refresh();
            refresh();
        });

        root.querySelector('[data-action="reset"]').addEventListener('click', () => {
            form.reset();
            f('period').value = 'last12';
            f('view').value = '';
            f('teamId').value = '';
            f('currency').value = '';
            setTimeout(() => { syncPeriod(); refresh(); });
        });

        root.querySelector('[data-action="print"]').addEventListener('click', () => window.print());

        loadFromUrl();
        if (f('period').value !== 'custom') syncPeriod();
        else root.querySelectorAll('[data-custom-range]').forEach(el => { el.hidden = false; });
        loadOverview();
    });
})();
