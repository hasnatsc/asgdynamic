/*
 * The home page's attention cards and an app's page (templates/home.html, templates/module.html):
 * a card opens the user's own documents of that kind in a drawer (/api/home/my-work - within this
 * app on an app's page); an app's page also draws its weekly activity with the chart kit.
 */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const drawer = document.querySelector('[data-my-work-drawer]');
    if (!drawer) return;
    const { api, esc, fmt, fail, icon } = App;
    const page = document.querySelector('[data-module-page]');
    const moduleKey = page ? page.dataset.module : undefined;
    const $ = (sel, r) => (r || document).querySelector(sel);

    // Status words become the app's own badges.
    document.querySelectorAll('[data-status-badge]').forEach(el => { el.outerHTML = App.statusBadge(el.dataset.statusBadge); });

    // ------------------------------------------------------------------ the user's work, in a drawer

    const TITLES = {
        approval: ['Pending your approval', 'Waiting for your signature'],
        returned: ['Returned to you', 'Sent back by an approver - correct and resubmit'],
        rejected: ['Rejected', 'Refused - revise and resubmit, or cancel'],
        draft: ['Your drafts', 'Started but not yet submitted']
    };
    const iso = v => v == null ? null : String(v).slice(0, 10);

    $('[data-close]', drawer).addEventListener('click', () => drawer.close());
    drawer.addEventListener('click', e => { if (e.target === drawer) drawer.close(); });

    document.addEventListener('click', async e => {
        const card = e.target.closest('[data-my-work]');
        if (!card) return;
        const kind = card.dataset.myWork;
        const [title, sub] = TITLES[kind];
        $('[data-my-work-title]', drawer).textContent = title;
        $('[data-my-work-sub]', drawer).textContent = 'Loading…';
        $('[data-my-work-body]', drawer).innerHTML = '<div class="an-empty">Loading…</div>';
        if (!drawer.open) drawer.showModal();
        try {
            const rows = await api('/api/home/my-work', { query: { kind, module: moduleKey } });
            $('[data-my-work-sub]', drawer).textContent = `${rows.length} document(s) · ${sub}`;
            $('[data-my-work-body]', drawer).innerHTML = rows.length ? `<ul class="divide-y divide-gray-100 dark:divide-gray-800">${rows.map(r => `
                <li><a class="flex items-start gap-3 py-3 ${r.path ? 'hover:bg-gray-50 dark:hover:bg-gray-800' : 'pointer-events-none'}" href="${esc(r.path || '#')}">
                    <span class="min-w-0 flex-1">
                        <span class="flex flex-wrap items-center gap-2"><b class="text-sm text-gray-900 dark:text-white">${esc(r.documentNo || 'Unnumbered')}</b>
                            ${App.statusBadge(r.status)}</span>
                        <span class="block text-xs text-gray-500">${esc(r.typeLabel || '')}${r.moduleLabel && !moduleKey ? ' · ' + esc(r.moduleLabel) : ''}${r.party ? ' · ' + esc(r.party) : ''}</span>
                        ${r.lastRemarks ? `<span class="mt-1 block text-xs text-gray-600 dark:text-gray-300">“${esc(r.lastRemarks)}”</span>` : ''}
                        ${r.detail ? `<span class="mt-0.5 block text-xs text-gray-500">${esc(r.detail)}</span>` : ''}
                    </span>
                    <span class="shrink-0 text-right text-[11px] text-gray-400">${esc(fmt.date(iso(r.lastActionAt || r.at || r.updatedAt || r.documentDate)) || '')}
                        <span class="mt-1 block text-gray-400">${icon('chevron-right')}</span></span>
                </a></li>`).join('')}</ul>`
                : '<div class="an-empty">Nothing here - you are up to date.</div>';
        } catch (error) {
            drawer.close();
            fail(error);
        }
    });

    // ------------------------------------------------------------------ an app's weekly activity

    const card = document.querySelector('[data-mod-chart="activity"]');
    if (card && window.AnalyticsCharts) {
        const C = window.AnalyticsCharts;
        const weeks = window.MODULE_ACTIVITY || [];
        const host = $('[data-host]', card);
        const toggle = $('[data-mod-table-toggle]', card);
        let asTable = false;
        const label = w => new Date(iso(w.week) + 'T00:00:00').toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
        const draw = () => asTable
            ? C.table(host, { columns: [{ label: 'Week of', get: w => fmt.date(iso(w.week)) }, { label: 'Documents raised', get: w => C.fmt.int(w.documents), num: true }], rows: weeks })
            : C.columns(host, { title: 'Documents raised each week', height: 200, format: C.fmt.int,
                categories: weeks.map(label), titles: weeks.map(w => `Week of ${fmt.date(iso(w.week))}`),
                series: [{ name: 'Documents raised', cls: 's1', values: weeks.map(w => w.documents) }],
                emptyText: 'Nothing raised in the last twelve weeks.' });
        toggle.addEventListener('click', () => { asTable = !asTable; toggle.textContent = asTable ? 'Chart' : 'Table'; draw(); });
        draw();
    }
});
