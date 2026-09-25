/*
 * Approvals: what is waiting for the signed-in user, decided in place or opened on its own screen,
 * and every request in the unit. Deciding posts to /api/documents/{id}/approve|return|reject - the
 * same calls the document screens make, so the rules cannot differ between the two.
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

    document.addEventListener('DOMContentLoaded', () => {
        const root = document.querySelector('[data-approvals]');
        if (!root) return;
        App.tabs(root, name => { if (name === 'all') requests.reload(true); });
        const inboxBody = document.querySelector('#inboxTable tbody');
        let inbox = [];

        // ------------------------------------------------------------------ waiting for me

        async function loadInbox() {
            inboxBody.innerHTML = `<tr><td colspan="7" class="py-6"><div class="skeleton mx-auto h-4 w-1/3"></div></td></tr>`;
            try {
                inbox = await App.api('/api/approvals/inbox');
            } catch (error) {
                App.fail(error);
                inbox = [];
            }
            root.querySelector('[data-count="mine"]').textContent = inbox.length;
            inboxBody.innerHTML = inbox.length ? inbox.map(r => `<tr>
                    <td>${docCell(r)}</td>
                    <td>${esc(r.partyName || '—')}</td>
                    <td>${r.teamName ? `<span class="badge-gray">${esc(r.teamName)}</span>` : '<span class="text-gray-400">—</span>'}</td>
                    <td class="num">${money(r.amount, r.currency)}</td>
                    <td>${levelCell(r)}</td>
                    <td><div class="flex flex-col"><span>${esc(r.raisedBy || '—')}</span>
                        <span class="text-xs text-gray-500">${App.fmt.timeTag ? App.fmt.timeTag(r.submittedAt) : esc(r.submittedAt || '')}</span></div></td>
                    <td>${App.rowActions(openLink(r),
                        App.rowButton('Reject', 'x', `data-decide="reject" data-id="${r.documentId}"`, 'text-red-700'),
                        App.rowButton('Return', 'arrow-right', `data-decide="return" data-id="${r.documentId}"`),
                        `<button type="button" class="btn-primary btn-sm" data-decide="approve" data-id="${r.documentId}">${App.icon('check')}Approve</button>`)}</td>
                </tr>`).join('')
                : `<tr><td colspan="7"><div class="empty py-10 text-center">
                       <p class="empty-title">Nothing is waiting for you</p>
                       <p class="mt-1 text-sm text-gray-500">Documents you may sign appear here as soon as they reach your level.</p>
                   </div></td></tr>`;
        }
        loadInbox();

        inboxBody.addEventListener('click', async event => {
            const btn = event.target.closest('[data-decide]');
            if (!btn) return;
            const row = inbox.find(r => String(r.documentId) === btn.dataset.id);
            const action = btn.dataset.decide;
            const approve = action === 'approve';
            const last = !row || row.level >= row.totalLevels;
            const values = await App.form({
                title: { approve: `Approve ${row.documentNo}?`, reject: `Reject ${row.documentNo}?`, return: `Return ${row.documentNo} to the maker?` }[action],
                message: {
                    approve: last ? 'It is locked for editing once approved.'
                                  : `This signs level ${row.level} of ${row.totalLevels}; it then goes to the next approver.`,
                    reject: 'It is refused. The maker sees your reason.',
                    return: 'It goes back to the maker as a draft to correct and submit again.'
                }[action],
                fields: [{ name: 'remarks', label: approve ? 'Remarks (optional)' : 'Reason', type: 'textarea',
                           required: !approve, maxlength: 1000 }],
                confirmText: { approve: 'Approve', reject: 'Reject', return: 'Return' }[action],
                danger: action === 'reject'
            });
            if (!values) return;
            try {
                await App.api(`/api/documents/${row.documentId}/${action}`, { method: 'POST', query: { remarks: values.remarks } });
                App.toast({ approve: last ? 'Approved.' : 'Signed - sent to the next level.',
                            reject: 'Rejected.', return: 'Returned to the maker.' }[action], 'success');
                loadInbox();
            } catch (error) { App.fail(error); loadInbox(); }
        });

        // ------------------------------------------------------------------ all requests

        const filter = document.getElementById('apFilter');
        const requests = new App.Grid({
            url: '/api/approvals/requests',
            table: document.getElementById('requestsTable'),
            pager: document.getElementById('apPager'),
            params: () => ({ pending: filter.value }),
            emptyText: 'No approvals match.',
            emptyIcon: 'check-circle',
            columns: [
                r => docCell(r),
                r => r.teamName ? `<span class="badge-gray">${esc(r.teamName)}</span>` : '<span class="text-gray-400">—</span>',
                r => `<span class="block text-right">${money(r.amount, r.currency)}</span>`,
                r => esc(r.scope),
                r => r.pending
                    ? `<span class="badge-amber badge-dot">Level ${esc(r.level)} of ${esc(r.totalLevels)}</span>`
                    : { APPROVED: '<span class="badge-green badge-dot">Approved</span>',
                        RETURNED: '<span class="badge-blue badge-dot">Returned</span>',
                        REJECTED: '<span class="badge-red badge-dot">Rejected</span>' }[r.outcome] || '—',
                r => esc(r.raisedBy || '—'),
                r => App.fmt.timeTag ? App.fmt.timeTag(r.submittedAt) : esc(r.submittedAt || ''),
                r => App.rowActions(openLink(r))
            ]
        });
        filter.addEventListener('change', () => requests.reload(true));
    });
})();
