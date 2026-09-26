/*
 * Approvals: the centralised inbox. What is waiting for the signed-in user is searched, filtered and
 * paged on the server, decided in place one at a time or several at once, or opened on its own screen;
 * "All requests" is every approval in the teams the user can see. Deciding posts to
 * /api/documents/{id}/approve|return|reject (one) or /api/approvals/decide (several) - the same engine
 * and the same checks as the document screens, so the rules cannot differ between them.
 */
(() => {
    'use strict';

    const esc = s => App.esc(s);
    const nf = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const money = (amount, currency) => amount == null ? '<span class="text-gray-400">—</span>'
        : `<span class="tabular-nums">${esc(nf.format(amount))}</span> <span class="text-xs text-gray-500">${esc(currency || '')}</span>`;
    const docCell = r => `<div class="flex flex-col">
            <span class="doc-no">${esc(r.documentNo || 'Unnumbered')}</span>
            <span class="text-xs text-gray-500">${esc(r.documentTypeLabel)}</span></div>`;
    const openLink = r => r.screenPath
        ? `<a class="btn-ghost btn-sm" href="${esc(r.screenPath)}?open=${esc(r.documentId)}">${App.icon('eye')}Open</a>` : '';
    const levelCell = r => `<div class="flex flex-col">
            <span class="font-medium tabular-nums">${r.totalLevels > 1 ? `Level ${esc(r.level)} of ${esc(r.totalLevels)}` : 'Single level'}</span>
            <span class="text-xs text-gray-500">${esc(r.scope)}${r.approver ? ' · ' + esc(r.approver) : ''}</span></div>`;
    const teamCell = r => r.teamName ? `<span class="badge-gray">${esc(r.teamName)}</span>` : '<span class="text-gray-400">—</span>';
    const when = at => App.fmt.timeTag ? App.fmt.timeTag(at) : esc(at || '');

    const VERB = { APPROVED: 'approve', RETURNED: 'return', REJECTED: 'reject' };

    document.addEventListener('DOMContentLoaded', () => {
        const root = document.querySelector('[data-approvals]');
        if (!root) return;
        App.tabs(root, name => { if (name === 'all') requests.reload(true); });

        // ------------------------------------------------------------------ waiting for me

        const table = document.getElementById('inboxTable');
        const typeFilter = document.getElementById('inType');
        const bulkBar = root.querySelector('[data-bulk]');
        const selectAll = table.querySelector('[data-select-all]');
        const count = root.querySelector('[data-count="mine"]');
        const selected = new Map();   // documentId -> row, across pages

        const inbox = new App.Grid({
            url: '/api/approvals/inbox',
            table,
            search: document.getElementById('inSearch'),
            pager: document.getElementById('inPager'),
            params: () => ({ type: typeFilter.value }),
            emptyText: 'Nothing is waiting for you. Documents you may sign appear here as soon as they reach your level.',
            emptyIcon: 'check-circle',
            columns: [
                r => `<input type="checkbox" class="checkbox" data-select="${esc(r.documentId)}" aria-label="Select ${esc(r.documentNo || '')}"
                        ${selected.has(r.documentId) ? 'checked' : ''}>`,
                r => docCell(r),
                r => esc(r.partyName || '—'),
                r => teamCell(r),
                r => `<span class="block text-right">${money(r.amount, r.currency)}</span>`,
                r => levelCell(r),
                r => `<div class="flex flex-col"><span>${esc(r.raisedBy || '—')}</span>
                        <span class="text-xs text-gray-500">${when(r.submittedAt)}</span></div>`,
                r => App.rowActions(openLink(r),
                    App.rowButton('Reject', 'x', `data-decide="REJECTED" data-id="${r.documentId}"`, 'text-red-700'),
                    App.rowButton('Return', 'arrow-right', `data-decide="RETURNED" data-id="${r.documentId}"`),
                    `<button type="button" class="btn-primary btn-sm" data-decide="APPROVED" data-id="${r.documentId}">${App.icon('check')}Approve</button>`)
            ]
        });

        // Every render - paging, searching, filtering, after a decision - refreshes the badge and the ticks.
        const render = inbox.render.bind(inbox);
        inbox.render = () => {
            render();
            count.textContent = inbox.total || 0;
            syncSelection();
        };
        const reloadInbox = resetPage => inbox.reload(resetPage);
        reloadInbox(true);
        typeFilter.addEventListener('change', () => { selected.clear(); reloadInbox(true); });

        function syncSelection() {
            bulkBar.hidden = selected.size === 0;
            bulkBar.querySelector('[data-bulk-count]').textContent = selected.size;
            const boxes = [...table.querySelectorAll('tbody [data-select]')];
            selectAll.checked = boxes.length > 0 && boxes.every(b => b.checked);
            selectAll.indeterminate = !selectAll.checked && boxes.some(b => b.checked);
        }

        table.addEventListener('change', event => {
            const box = event.target;
            if (box.matches('[data-select-all]')) {
                table.querySelectorAll('tbody [data-select]').forEach(b => {
                    b.checked = box.checked;
                    const row = inbox.rows.find(r => String(r.documentId) === b.dataset.select);
                    if (box.checked && row) selected.set(row.documentId, row); else if (row) selected.delete(row.documentId);
                });
            } else if (box.matches('[data-select]')) {
                const row = inbox.rows.find(r => String(r.documentId) === box.dataset.select);
                if (row) box.checked ? selected.set(row.documentId, row) : selected.delete(row.documentId);
            }
            syncSelection();
        });

        /** Asks for the remark (a reason to return or reject) and confirms what will happen. */
        function askDecision(decision, rows) {
            const approve = decision === 'APPROVED';
            const one = rows.length === 1 ? rows[0] : null;
            const label = one ? (one.documentNo || 'this document') : `${rows.length} documents`;
            const last = one && one.level >= one.totalLevels;
            return App.form({
                title: { APPROVED: `Approve ${label}?`, REJECTED: `Reject ${label}?`, RETURNED: `Return ${label} to the maker?` }[decision],
                message: {
                    APPROVED: one ? (last ? 'It is locked for editing once approved.'
                                          : `This signs level ${one.level} of ${one.totalLevels}; it then goes to the next approver.`)
                                  : 'Each is signed at its current level: a final level approves it, any other sends it to the next approver.',
                    REJECTED: 'Refused. The maker sees your reason, and may correct it and submit it again.',
                    RETURNED: 'It goes back to the maker as a draft to correct and submit again.'
                }[decision],
                fields: [{ name: 'remarks', label: approve ? 'Remarks (optional)' : 'Reason', type: 'textarea',
                           required: !approve, maxlength: 1000 }],
                confirmText: { APPROVED: 'Approve', REJECTED: 'Reject', RETURNED: 'Return' }[decision],
                danger: decision === 'REJECTED'
            });
        }

        // One document, from its row.
        table.addEventListener('click', async event => {
            const btn = event.target.closest('[data-decide]');
            if (!btn) return;
            const row = inbox.rows.find(r => String(r.documentId) === btn.dataset.id);
            if (!row) return;
            const decision = btn.dataset.decide;
            const values = await askDecision(decision, [row]);
            if (!values) return;
            try {
                await App.api(`/api/documents/${row.documentId}/${VERB[decision]}`, { method: 'POST', query: { remarks: values.remarks } });
                App.toast({ APPROVED: row.level >= row.totalLevels ? 'Approved.' : 'Signed - sent to the next level.',
                            REJECTED: 'Rejected.', RETURNED: 'Returned to the maker.' }[decision], 'success');
                selected.delete(row.documentId);
            } catch (error) { App.fail(error); }
            reloadInbox(false);
        });

        // Several at once: one call, each decided and checked on its own server-side.
        bulkBar.addEventListener('click', async event => {
            const btn = event.target.closest('[data-bulk-decide]');
            if (!btn || !selected.size) return;
            const decision = btn.dataset.bulkDecide;
            const rows = [...selected.values()];
            const values = await askDecision(decision, rows);
            if (!values) return;
            try {
                const result = await App.api('/api/approvals/decide', { method: 'POST',
                    body: { documentIds: rows.map(r => r.documentId), decision, remarks: values.remarks || null } });
                const done = result.done || [];
                const failed = result.failed || [];
                done.forEach(d => selected.delete(d.id));
                if (done.length) {
                    App.toast(`${done.length} ${done.length === 1 ? 'document' : 'documents'} ${
                        { APPROVED: 'signed', REJECTED: 'rejected', RETURNED: 'returned' }[decision]}.`, 'success');
                }
                if (failed.length) {
                    const byId = new Map(rows.map(r => [r.documentId, r]));
                    App.toast(`${failed.length} not decided - ${failed.slice(0, 3).map(f =>
                        `${(byId.get(f.id) || {}).documentNo || '#' + f.id}: ${f.message}`).join('; ')}${failed.length > 3 ? '…' : ''}`, 'warn');
                }
            } catch (error) { App.fail(error); }
            reloadInbox(false);
        });

        // ------------------------------------------------------------------ all requests

        const filter = document.getElementById('apFilter');
        const apType = document.getElementById('apType');
        const requests = new App.Grid({
            url: '/api/approvals/requests',
            table: document.getElementById('requestsTable'),
            search: document.getElementById('apSearch'),
            pager: document.getElementById('apPager'),
            params: () => ({ pending: filter.value, type: apType.value }),
            emptyText: 'No approvals match.',
            emptyIcon: 'check-circle',
            columns: [
                r => docCell(r),
                r => teamCell(r),
                r => `<span class="block text-right">${money(r.amount, r.currency)}</span>`,
                r => esc(r.scope),
                r => r.pending
                    ? `<div class="flex flex-col"><span class="badge-amber badge-dot">Level ${esc(r.level)} of ${esc(r.totalLevels)}</span>
                         ${r.approver ? `<span class="mt-0.5 text-xs text-gray-500">Waiting for ${esc(r.approver)}</span>` : ''}</div>`
                    : { APPROVED: '<span class="badge-green badge-dot">Approved</span>',
                        RETURNED: '<span class="badge-blue badge-dot">Returned</span>',
                        REJECTED: '<span class="badge-red badge-dot">Rejected</span>' }[r.outcome] || '—',
                r => esc(r.raisedBy || '—'),
                r => when(r.submittedAt),
                r => App.rowActions(openLink(r))
            ]
        });
        filter.addEventListener('change', () => requests.reload(true));
        apType.addEventListener('change', () => requests.reload(true));
    });
})();
