/*
 * ChainWidgets - the two pieces the production chain's management views share: a progress cell
 * ("98%  1,000 / 1,020  (20 left)") and a timeline of documents from start to due date. Used by the
 * Production dashboard and Booking analytics' Booking to delivery tab, on the same .pd-* styles and
 * the chart kit's tooltip, so both read alike.
 *
 *   ChainWidgets.progress(done, of, { left, tone })                  -> HTML
 *   ChainWidgets.timeline(host, items, today, { limit, emptyText, noun })
 *     items: [{ href, label, sub, start, end, done (0-100), overdue, tip: { title, rows: [{ label, value }] },
 *               marks: [{ date, title }] }]
 */
(() => {
    'use strict';

    const nf = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 });
    const n = v => Number(v) || 0;
    const num = v => v == null || v === '' ? '—' : nf.format(n(v));
    const esc = s => App.esc(s);
    const iso = v => v == null ? null : typeof v === 'number' ? new Date(v).toISOString().slice(0, 10) : String(v).slice(0, 10);
    const DAY = 86400000;

    /** How much of {@code of} is done: a bar, the percentage, the figures and what is left. */
    function progress(done, of, { left = true, tone } = {}) {
        if (!(n(of) > 0)) return '<span class="text-gray-400">—</span>';
        const p = Math.min(100, n(done) * 100 / n(of));
        const rest = Math.max(0, n(of) - n(done));
        return `<div class="pd-progress" title="${num(done)} of ${num(of)}">
            <div class="pd-progress-top"><span class="pd-meter" data-tone="${tone || (p >= 100 ? 'good' : '')}"><span style="width:${p}%"></span></span>
                <span class="pd-progress-pct">${Math.floor(p)}%</span></div>
            <div class="pd-progress-sub"><span>${num(done)} / ${num(of)}</span>${left ? `<span>(${num(rest)} left)</span>` : ''}</div></div>`;
    }

    /**
     * Documents as bars from start to due date, filled by how much is done, with marks for planned
     * dates, today's line and a red outline when overdue. Weekly ticks for a short span, monthly
     * otherwise. Bars and their tooltips answer hover and keyboard focus alike.
     */
    function timeline(host, items, today, opts = {}) {
        const limit = opts.limit || 30;
        const noun = opts.noun || 'documents';
        const all = items.filter(i => i.start);
        if (!all.length) { host.innerHTML = `<div class="an-empty">${esc(opts.emptyText || 'Nothing to show.')}</div>`; return; }
        const rows = all.slice(0, limit);
        const t = s => new Date(iso(s) + 'T00:00:00').getTime();
        const now = t(today);
        let start = Math.min(...rows.map(i => t(i.start)), now);
        let end = Math.max(...rows.map(i => t(i.end || i.start)), ...rows.flatMap(i => (i.marks || []).map(m => t(m.date))), now + 7 * DAY);
        start -= 2 * DAY;
        end += 2 * DAY;
        const span = end - start;
        const x = ms => ((ms - start) / span * 100).toFixed(2);

        const weekly = span < 75 * DAY;
        const ticks = [];
        const cursor = new Date(start);
        cursor.setHours(0, 0, 0, 0);
        if (weekly) cursor.setDate(cursor.getDate() + ((8 - cursor.getDay()) % 7));
        else { cursor.setDate(1); cursor.setMonth(cursor.getMonth() + 1); }
        while (cursor.getTime() < end) {
            ticks.push(cursor.getTime());
            if (weekly) cursor.setDate(cursor.getDate() + 7); else cursor.setMonth(cursor.getMonth() + 1);
        }
        const label = ms => new Date(ms).toLocaleDateString(undefined, weekly ? { day: 'numeric', month: 'short' } : { month: 'short', year: 'numeric' });
        const axis = `<div class="pd-axis mb-1"><span></span><div class="relative h-4">${ticks.map(ms =>
            `<span class="absolute -translate-x-1/2 whitespace-nowrap" style="left:${x(ms)}%">${esc(label(ms))}</span>`).join('')}</div></div>`;
        host._tips = [];
        const body = rows.map(i => {
            const s = t(i.start), e = t(i.end || i.start);
            const k = host._tips.push(i.tip || { title: i.label, rows: [] }) - 1;
            const marks = (i.marks || []).map(m => `<span class="pd-plan" style="left:${x(t(m.date))}%" title="${esc(m.title)}"></span>`).join('');
            return `<div class="pd-gantt-row">
                <div class="pd-gantt-label"><a class="font-medium text-brand-700 hover:underline" href="${esc(i.href)}">${esc(i.label)}</a>
                    <div class="truncate text-[11px] text-gray-500">${esc(i.sub || '')}</div></div>
                <div class="pd-track">
                    <a class="pd-span" href="${esc(i.href)}" data-tone="${i.overdue ? 'critical' : ''}" data-tip="${k}"
                       style="left:${x(s)}%;width:${Math.max(0.6, x(e) - x(s))}%"
                       aria-label="${esc(i.label)}: ${Math.floor(n(i.done))}% done${i.overdue ? ', overdue' : ''}"><span style="width:${Math.min(100, n(i.done))}%"></span></a>
                    ${marks}
                </div></div>`;
        }).join('');
        const todayLine = `<div class="pd-axis pointer-events-none absolute inset-x-4 bottom-4 top-8"><span></span><div class="relative"><span class="pd-today" style="left:${x(now)}%"></span></div></div>`;
        host.innerHTML = axis + body + todayLine
            + (all.length > limit ? `<p class="mt-2 text-xs text-gray-500">The ${limit} due soonest of ${all.length} ${esc(noun)} - filter to see others.</p>` : '');

        if (!host._wired) {
            host._wired = true;
            const C = window.AnalyticsCharts;
            const show = e => {
                const a = e.target.closest('[data-tip]');
                if (!a || !host._tips) return C.tooltip.hide();
                const tip = host._tips[Number(a.dataset.tip)];
                const r = a.getBoundingClientRect();
                C.tooltip.show(e.clientX || r.left + r.width / 2, e.clientY || r.top, tip.title, tip.rows);
            };
            host.addEventListener('pointermove', show);
            host.addEventListener('pointerleave', () => C.tooltip.hide());
            host.addEventListener('focusin', show);
            host.addEventListener('focusout', () => C.tooltip.hide());
        }
    }

    window.ChainWidgets = { progress, timeline };
})();
