/*
 * AnalyticsCharts - the small chart kit behind Analytics & reports. Plain SVG, no library and no
 * build step, like the rest of the UI.
 *
 * Every chart follows one set of rules (the dataviz method this area is built to):
 *   - marks are thin: columns and bars at most 24px, a 4px rounded data end, square at the baseline,
 *     a 2px surface gap between touching segments; gridlines and axes are hairlines;
 *   - colour comes from roles on .viz (v-s1, v-s2, the v-o1..v-o5 ordinal ramp, status colours),
 *     validated for colour-blind separation in both themes - text never wears a data colour;
 *   - every mark answers hover AND keyboard focus with a tooltip of all its values, its hit area is
 *     larger than the mark, and a table of the same numbers is one click away (table());
 *   - labels are measured before they are placed: a label that does not fit is shortened with an
 *     ellipsis or moved to the tooltip, never clipped;
 *   - names from the data go into the page as text (textContent), never as markup.
 *
 *   AnalyticsCharts.columns(host, { categories, series: [{ name, cls, values }], format, stacked })
 *   AnalyticsCharts.hbars(host, { rows: [{ label, value, cls? }], format, detail(row) })
 *   AnalyticsCharts.stackbar(host, { segments: [{ label, value, cls, icon? }], format })
 *   AnalyticsCharts.table(host, { columns: [{ label, get, num? }], rows })
 */
(() => {
    'use strict';

    const SVG = 'http://www.w3.org/2000/svg';
    const nfCompact = new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 });
    const nfInt = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 });
    const nf2 = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const fmt = {
        compact: v => v == null ? '—' : (Math.abs(v) < 1000 ? nfInt.format(v) : nfCompact.format(v)),
        int: v => v == null ? '—' : nfInt.format(v),
        dec2: v => v == null ? '—' : nf2.format(v),
        pct: v => v == null ? '—' : `${Number(v).toFixed(1)}%`
    };

    // ------------------------------------------------------------------ helpers

    // Colour roles as literal class names - the stylesheet build keeps only classes it can find
    // written out in full, so these are never assembled from pieces.
    const FILL = { s1: 'v-s1', s2: 'v-s2', o1: 'v-o1', o2: 'v-o2', o3: 'v-o3', o4: 'v-o4', o5: 'v-o5',
        good: 'v-good', warning: 'v-warning', critical: 'v-critical', neutral: 'v-neutral', mutedfill: 'v-mutedfill' };
    const KEY = { s1: 'k-s1', s2: 'k-s2', o1: 'k-o1', o2: 'k-o2', o3: 'k-o3', o4: 'k-o4', o5: 'k-o5',
        good: 'k-good', warning: 'k-warning', critical: 'k-critical', neutral: 'k-neutral', mutedfill: 'k-mutedfill' };
    const fill = cls => FILL[cls] || FILL.s1;
    const key = cls => KEY[cls] || KEY.s1;

    function el(name, attrs, parent) {
        const node = document.createElementNS(SVG, name);
        for (const [k, v] of Object.entries(attrs || {})) if (v !== undefined && v !== null) node.setAttribute(k, v);
        if (parent) parent.appendChild(node);
        return node;
    }

    function text(parent, x, y, value, cls, anchor) {
        const t = el('text', { x, y, class: cls, 'text-anchor': anchor || 'start', 'dominant-baseline': 'middle' }, parent);
        t.textContent = value;
        return t;
    }

    const canvas = document.createElement('canvas').getContext('2d');
    /** Rendered width of a label at a size, from the page's own font. */
    function measure(value, size, weight) {
        const family = getComputedStyle(document.body).fontFamily || 'sans-serif';
        canvas.font = `${weight || 400} ${size}px ${family}`;
        return canvas.measureText(String(value)).width;
    }

    /** The label, shortened with an ellipsis to fit width - measured, never clipped. */
    function fit(value, width, size, weight) {
        const s = String(value ?? '');
        if (measure(s, size, weight) <= width) return s;
        let lo = 0, hi = s.length;
        while (lo < hi) {
            const mid = Math.ceil((lo + hi) / 2);
            if (measure(s.slice(0, mid) + '…', size, weight) <= width) lo = mid; else hi = mid - 1;
        }
        return lo > 0 ? s.slice(0, lo) + '…' : '';
    }

    /** Clean axis ticks from zero: 0, 250, 500 … never 0, 237, 474. */
    function ticks(max, count) {
        if (!(max > 0)) return [0, 1];
        const raw = max / (count || 4);
        const mag = Math.pow(10, Math.floor(Math.log10(raw)));
        const step = [1, 2, 2.5, 5, 10].map(m => m * mag).find(s => s >= raw) || raw;
        const out = [];
        for (let v = 0; v <= max + step * 0.001; v += step) out.push(v);
        if (out[out.length - 1] < max) out.push(out[out.length - 1] + step);
        return out;
    }

    /** A path for a bar with rounded corners on its data end only. */
    function barPath(x, y, w, h, radius, end) {
        const r = Math.max(0, Math.min(radius, w / 2, h));
        if (h <= 0 || w <= 0) return '';
        if (end === 'top') {
            return `M${x},${y + h}V${y + r}Q${x},${y} ${x + r},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${y + h}Z`;
        }
        if (end === 'right') {
            return `M${x},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${y + h - r}Q${x + w},${y + h} ${x + w - r},${y + h}H${x}Z`;
        }
        return `M${x},${y}H${x + w}V${y + h}H${x}Z`;
    }

    // ------------------------------------------------------------------ tooltip (one per page)

    let tip;
    const tooltip = {
        /** rows: [{ label, value, cls, line? }] - value leads, label follows; keys are short strokes. */
        show(x, y, title, rows) {
            if (!tip) {
                tip = document.createElement('div');
                tip.className = 'an-tooltip viz';
                tip.setAttribute('role', 'tooltip');
                document.body.appendChild(tip);
            }
            tip.replaceChildren();
            if (title) {
                const h = document.createElement('div');
                h.className = 'an-tooltip-title';
                h.textContent = title;
                tip.appendChild(h);
            }
            for (const r of rows) {
                const row = document.createElement('div');
                row.className = 'an-tooltip-row';
                if (r.cls) {
                    const key = document.createElement('span');
                    key.className = `an-key-line ${KEY[r.cls] || KEY.s1}`;
                    row.appendChild(key);
                }
                const label = document.createElement('span');
                label.textContent = r.label;
                const value = document.createElement('b');
                value.textContent = r.value;
                row.append(label, value);
                tip.appendChild(row);
            }
            tip.hidden = false;
            const box = tip.getBoundingClientRect();
            const left = Math.min(window.innerWidth - box.width - 8, Math.max(8, x + 14));
            const top = y + box.height + 16 > window.innerHeight ? y - box.height - 12 : y + 14;
            tip.style.left = `${left}px`;
            tip.style.top = `${Math.max(8, top)}px`;
        },
        hide() { if (tip) tip.hidden = true; }
    };

    /** Hover and keyboard focus on a hit target both show the same tooltip. */
    function interactive(hit, title, rows) {
        hit.setAttribute('tabindex', '0');
        hit.setAttribute('class', 'v-hit');
        hit.setAttribute('role', 'img');
        hit.setAttribute('aria-label', `${title}: ${rows.map(r => `${r.label} ${r.value}`).join(', ')}`);
        hit.addEventListener('pointermove', e => tooltip.show(e.clientX, e.clientY, title, rows));
        hit.addEventListener('pointerleave', () => tooltip.hide());
        hit.addEventListener('focus', () => {
            const b = hit.getBoundingClientRect();
            tooltip.show(b.left + b.width / 2, b.top, title, rows);
        });
        hit.addEventListener('blur', () => tooltip.hide());
    }

    /** Redraws on resize, and keeps the last draw so the page can re-render in place. */
    function responsive(host, draw) {
        host._draw = draw;
        draw();
        if (!host._observer && 'ResizeObserver' in window) {
            let width = host.clientWidth;
            host._observer = new ResizeObserver(() => {
                if (Math.abs(host.clientWidth - width) < 4) return;
                width = host.clientWidth;
                host._draw && host._draw();
            });
            host._observer.observe(host);
        }
    }

    function empty(host, message) {
        host.replaceChildren();
        const box = document.createElement('div');
        box.className = 'an-empty';
        box.textContent = message || 'No bookings match these filters.';
        host.appendChild(box);
    }

    function legend(items) {
        const box = document.createElement('div');
        box.className = 'an-legend mb-2';
        for (const item of items) {
            const span = document.createElement('span');
            span.className = 'inline-flex items-center gap-1.5';
            const swatch = document.createElement('span');
            swatch.className = `an-key ${key(item.cls)}`;
            const label = document.createElement('span');
            label.textContent = item.label;
            span.append(swatch, label);
            box.appendChild(span);
        }
        return box;
    }

    // ------------------------------------------------------------------ columns (time series)

    function columns(host, opts) {
        const series = opts.series || [];
        const n = (opts.categories || []).length;
        const total = i => series.reduce((s, x) => s + (Number(x.values[i]) || 0), 0);
        if (!n || series.every(s => s.values.every(v => !Number(v)))) { empty(host, opts.emptyText); return; }
        const format = opts.format || fmt.compact;
        responsive(host, () => {
            host.replaceChildren();
            if (series.length > 1) host.appendChild(legend(series.map(s => ({ label: s.name, cls: s.cls }))));
            const width = Math.max(host.clientWidth, 280);
            const height = opts.height || 240;
            const max = Math.max(...Array.from({ length: n }, (_, i) => total(i)));
            const ys = ticks(max, 4);
            const top = ys[ys.length - 1];
            const left = Math.ceil(Math.max(...ys.map(v => measure(format(v), 11)))) + 10;
            const m = { top: 8, right: 4, bottom: 26, left };
            const plotW = width - m.left - m.right;
            const plotH = height - m.top - m.bottom;
            const svg = el('svg', { width, height, viewBox: `0 0 ${width} ${height}`, role: 'group', 'aria-label': opts.title || 'Chart' }, host);
            const y = v => m.top + plotH - (v / top) * plotH;
            for (const v of ys) {
                el('line', { x1: m.left, x2: width - m.right, y1: Math.round(y(v)) + 0.5, y2: Math.round(y(v)) + 0.5, class: v === 0 ? 'v-base' : 'v-grid' }, svg);
                text(svg, m.left - 8, y(v), format(v), 'v-tick', 'end');
            }
            const band = plotW / n;
            const barW = Math.max(2, Math.min(24, band * 0.62));
            // x labels: as many as fit without touching
            const labels = opts.labels || opts.categories;
            const widest = Math.max(...labels.map(l => measure(l, 11))) + 12;
            const every = Math.max(1, Math.ceil(widest / band));
            for (let i = 0; i < n; i++) {
                const cx = m.left + band * i + band / 2;
                const g = el('g', {}, svg);
                el('rect', { x: m.left + band * i, y: m.top, width: band, height: plotH, class: 'v-hover' }, g);
                let base = y(0);
                const visible = series.filter(s => Number(s.values[i]) > 0);
                visible.forEach((s, k) => {
                    const v = Number(s.values[i]);
                    let h = (v / top) * plotH;
                    const gap = k > 0 ? 2 : 0;
                    h = Math.max(0, h - gap);
                    const yTop = base - gap - h;
                    el('path', { d: barPath(cx - barW / 2, yTop, barW, h, 4, k === visible.length - 1 ? 'top' : 'none'), class: fill(s.cls) }, g);
                    base = yTop;
                });
                if (i % every === 0) text(svg, cx, height - 10, labels[i], 'v-tick', 'middle');
                const rows = series.map(s => ({ label: s.name, value: format(Number(s.values[i]) || 0, true), cls: s.cls }));
                if (series.length > 1) rows.push({ label: 'Total', value: format(total(i), true) });
                interactive(g, (opts.titles || opts.categories)[i], rows);
            }
        });
    }

    // ------------------------------------------------------------------ horizontal bars (ranking)

    function hbars(host, opts) {
        const rows = (opts.rows || []).filter(r => r.value != null);
        if (!rows.length || rows.every(r => !Number(r.value))) { empty(host, opts.emptyText); return; }
        const format = opts.format || fmt.compact;
        responsive(host, () => {
            host.replaceChildren();
            const width = Math.max(host.clientWidth, 280);
            const rowH = 30, barH = 14;
            const height = rows.length * rowH + 4;
            const labelW = Math.min(Math.ceil(Math.max(...rows.map(r => measure(r.label, 12)))) + 16, width * 0.38);
            const valueW = Math.max(...rows.map(r => measure(format(r.value), 12, 600))) + 10;
            const plotW = Math.max(40, width - labelW - valueW);
            const max = Math.max(...rows.map(r => Number(r.value) || 0)) || 1;
            const svg = el('svg', { width, height, viewBox: `0 0 ${width} ${height}`, role: 'group', 'aria-label': opts.title || 'Chart' }, host);
            el('line', { x1: labelW + 0.5, x2: labelW + 0.5, y1: 0, y2: height, class: 'v-base' }, svg);
            rows.forEach((r, i) => {
                const g = el('g', {}, svg);
                const yMid = i * rowH + rowH / 2 + 2;
                el('rect', { x: 0, y: i * rowH + 2, width, height: rowH, class: 'v-hover', rx: 4 }, g);
                text(g, labelW - 10, yMid, fit(r.label, labelW - 12, 12), 'v-label', 'end');
                const w = (Number(r.value) || 0) / max * plotW;
                if (w > 0) el('path', { d: barPath(labelW, yMid - barH / 2, Math.max(w, 2), barH, 4, 'right'), class: fill(r.cls || opts.cls) }, g);
                text(g, labelW + w + 6, yMid, format(r.value), 'v-value');
                const detail = opts.detail ? opts.detail(r) : [{ label: opts.valueLabel || 'Value', value: format(r.value, true) }];
                interactive(g, r.label, detail);
            });
        });
    }

    // ------------------------------------------------------------------ 100% bar (part of a whole)

    function stackbar(host, opts) {
        const segments = (opts.segments || []).filter(s => Number(s.value) > 0);
        const sum = segments.reduce((s, x) => s + Number(x.value), 0);
        if (!sum) { empty(host, opts.emptyText); return; }
        const format = opts.format || fmt.int;
        responsive(host, () => {
            host.replaceChildren();
            const width = Math.max(host.clientWidth, 240);
            const height = 22;
            const svg = el('svg', { width, height, viewBox: `0 0 ${width} ${height}`, role: 'group', 'aria-label': opts.title || 'Chart' }, host);
            const gaps = (segments.length - 1) * 2;
            let x = 0;
            segments.forEach((s, i) => {
                const w = Math.max(2, (Number(s.value) / sum) * (width - gaps));
                const g = el('g', {}, svg);
                const first = i === 0, last = i === segments.length - 1;
                const r = 4;
                let d;
                if (first && last) d = `M${x + r},0H${x + w - r}Q${x + w},0 ${x + w},${r}V${height - r}Q${x + w},${height} ${x + w - r},${height}H${x + r}Q${x},${height} ${x},${height - r}V${r}Q${x},0 ${x + r},0Z`;
                else if (first) d = `M${x + r},0H${x + w}V${height}H${x + r}Q${x},${height} ${x},${height - r}V${r}Q${x},0 ${x + r},0Z`;
                else if (last) d = barPath(x, 0, w, height, r, 'right');
                else d = barPath(x, 0, w, height, 0, 'none');
                el('path', { d, class: fill(s.cls) }, g);
                interactive(g, s.label, [{ label: opts.valueLabel || 'Bookings', value: format(s.value, true) },
                    { label: 'Share', value: fmt.pct(Number(s.value) / sum * 100) }]);
                x += w + 2;
            });
            // The legend doubles as the table: every segment's icon, label, count and share.
            const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
            const list = document.createElement('ul');
            list.className = 'mt-4 space-y-2 text-sm';
            list.innerHTML = opts.segments.map(s => `<li class="flex items-center gap-2">
                    <span class="an-key ${key(s.cls)}"></span>
                    ${s.icon && window.App ? App.icon(s.icon, 'h-3.5 w-3.5 shrink-0 text-gray-400') : ''}
                    <span class="text-gray-700 dark:text-gray-300">${esc(s.label)}</span>
                    <span class="ml-auto font-semibold tabular-nums text-gray-900 dark:text-white">${esc(format(s.value))}</span>
                    <span class="w-14 text-right text-xs tabular-nums text-gray-500">${esc(fmt.pct(Number(s.value || 0) / sum * 100))}</span>
                </li>`).join('');
            host.appendChild(list);
        });
    }

    // ------------------------------------------------------------------ table view (the accessible twin)

    function table(host, opts) {
        host._draw = null;
        host.replaceChildren();
        const wrap = document.createElement('div');
        wrap.className = 'overflow-x-auto';
        const t = document.createElement('table');
        t.className = 'an-table';
        const head = t.createTHead().insertRow();
        for (const c of opts.columns) {
            const th = document.createElement('th');
            th.textContent = c.label;
            if (c.num) th.className = 'num';
            head.appendChild(th);
        }
        const body = t.createTBody();
        for (const r of opts.rows) {
            const tr = body.insertRow();
            for (const c of opts.columns) {
                const td = tr.insertCell();
                td.textContent = c.get(r);
                if (c.num) td.className = 'num';
            }
        }
        wrap.appendChild(t);
        host.appendChild(wrap);
    }

    window.AnalyticsCharts = { columns, hbars, stackbar, table, tooltip, fmt, fit, measure };
})();
